package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviours;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.CombatTargets;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What landing a hit does to one victim.
 *
 * <p>The damage is either {@code damage} (a multiplier on the mob's live {@code attack_damage}, the LdMob way) or
 * {@code amount}: absolute raw damage at level 0 on Normal, multiplied at hit time by R = the mob's live
 * {@code attack_damage} / the {@code attack_damage} its behaviour declares in {@code base_attributes} (1 if it declares
 * none). R is 1 for an unscaled mob and carries whatever scales its attack damage (Dungeon Difficulty levels, soft
 * enrage), so a balance sheet can list every hit as one number. Neither set = {@code damage: 1}.
 *
 * @param damage     multiplier on the mob's {@code attack_damage} attribute
 * @param amount     absolute damage at level 0 / Normal (times R); not together with {@code damage}
 * @param damageType damage type id (any datapack's); default: the hitter's usual type ({@code minecraft:mob_attack}
 *                   for ability hits, vanilla's {@code minecraft:wither_skull} for skulls)
 * @param knockback  horizontal push away from the hit origin
 * @param knockUp    upward launch speed (0.4 is about a jump)
 */
public record HitSpec(Optional<Double> damage, Optional<Double> amount, Optional<ResourceLocation> damageType,
                      List<EffectSpec> effects, double knockback, double knockUp) {
    public static final HitSpec DEFAULT = new HitSpec(Optional.empty(), Optional.empty(), Optional.empty(), List.of(),
            0.4, 0.0);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<EntityType<?>> NO_ATTACK_DAMAGE = new HashSet<>();
    private static final Set<ResourceLocation> UNKNOWN_TYPES = new HashSet<>();
    private static final ResourceLocation ATTACK_DAMAGE = BuiltInRegistries.ATTRIBUTE.getKey(Attributes.ATTACK_DAMAGE.value());

    public static final Codec<HitSpec> CODEC = RecordCodecBuilder.<HitSpec>create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("damage").forGetter(HitSpec::damage),
            Codec.doubleRange(0.0, 1.0e6).optionalFieldOf("amount").forGetter(HitSpec::amount),
            ResourceLocation.CODEC.optionalFieldOf("damage_type").forGetter(HitSpec::damageType),
            EffectSpec.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(HitSpec::effects),
            Codec.DOUBLE.optionalFieldOf("knockback", 0.4).forGetter(HitSpec::knockback),
            Codec.DOUBLE.optionalFieldOf("knock_up", 0.0).forGetter(HitSpec::knockUp)
    ).apply(i, HitSpec::new)).validate(HitSpec::validate);

    private static DataResult<HitSpec> validate(HitSpec spec) {
        return spec.damage.isPresent() && spec.amount.isPresent()
                ? DataResult.error(() -> "A hit sets both 'damage' (x attack_damage) and 'amount' (absolute); use one")
                : DataResult.success(spec);
    }

    /** Damages, debuffs and pushes {@code victim} for the running ability; {@code from} is where the blow came from. */
    public void apply(AbilityRun run, LivingEntity victim, Vec3 from) {
        Mob mob = run.mob();
        float amount = rawDamage(mob);
        // Ability hits are authored beats that each land at most once per event; vanilla's 10-tick hurt immunity
        // would silently swallow a combo's second blow or a slash right after a trample.
        victim.invulnerableTime = 0;
        Vec3 before = victim.getDeltaMovement();
        boolean marked = victim.hurtMarked;
        DamageSource source = damageSource(mob.level(), mob, mob, () -> mob.damageSources().mobAttack(mob));
        boolean hurt = amount <= 0.0F || victim.hurt(source, amount);
        if (!hurt) {
            return; // blocked, invulnerable or in damage cooldown: no rider effects either
        }
        // hurt() also shoves the victim by vanilla's fixed 0.4, which knocks a player out of a combo's next beat; the
        // push is exactly the authored one below, so undo vanilla's (keeping a sync another hit already queued).
        victim.setDeltaMovement(before);
        victim.hurtMarked = marked;
        run.combat().trace(String.format("hit %s for %.1f at %.1f blocks (feet %+.1f vs mob)", victim.getName().getString(),
                amount, CombatTargets.distance(mob, victim), victim.getY() - mob.getY()));
        applyEffects(victim, mob);
        if (knockback > 0.0 || knockUp > 0.0) {
            double dx = victim.getX() - from.x;
            double dz = victim.getZ() - from.z;
            if (dx * dx + dz * dz <= 1.0e-6) {
                // Standing right on the origin (a still target of an `origin: target` slam): push away from the mob.
                dx = victim.getX() - mob.getX();
                dz = victim.getZ() - mob.getZ();
                if (dx * dx + dz * dz <= 1.0e-6) {
                    Vec3 ahead = AbilityRun.forward(mob.getYRot());
                    dx = ahead.x;
                    dz = ahead.z;
                }
            }
            double resist = 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            if (knockback > 0.0) {
                victim.knockback(knockback, -dx, -dz);
            }
            if (knockUp > 0.0 && resist > 0.0) {
                Vec3 motion = victim.getDeltaMovement();
                victim.setDeltaMovement(motion.x, Math.max(motion.y, knockUp * resist), motion.z);
            }
            victim.hurtMarked = true; // sync the new velocity to players
        }
    }

    /** Puts this hit's effects on {@code victim}, credited to {@code source} (call only once the hit has landed). */
    public void applyEffects(LivingEntity victim, Entity source) {
        for (EffectSpec effect : effects) {
            effect.instance().ifPresent(instance -> victim.addEffect(instance, source));
        }
    }

    /** The raw damage {@code mob} deals with this hit now, before the victim's armour and difficulty scaling. */
    public float rawDamage(Mob mob) {
        if (amount.isPresent()) {
            return (float) (amount.get() * damageScale(mob));
        }
        return (float) (damage.orElse(1.0) * attackDamage(mob));
    }

    /**
     * R: the mob's live {@code attack_damage} over the base its behaviour declares ({@code base_attributes}, else
     * {@code reference_attributes}; 1 when it declares neither, or a non-positive one). Multiplies every
     * {@code amount}.
     */
    public static double damageScale(Mob mob) {
        return damageScale(attackDamage(mob), MobBehaviours.forMob(mob));
    }

    /** R for a mob whose live {@code attack_damage} is {@code attackDamage}, running {@code behaviour} (or none). */
    public static double damageScale(double attackDamage, MobBehaviour behaviour) {
        double declared = behaviour == null ? 0.0 : behaviour.declaredBase(ATTACK_DAMAGE).orElse(0.0);
        return attackDamage / (declared <= 0.0 ? 1.0 : declared);
    }

    /**
     * The source this hit deals: {@code damage_type} with the given entities, or {@code fallback} when it sets none
     * (or names a type the server does not have, reported once).
     */
    public DamageSource damageSource(Level level, Entity direct, Entity causing, Supplier<DamageSource> fallback) {
        if (damageType.isEmpty()) {
            return fallback.get();
        }
        Optional<Holder.Reference<DamageType>> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, damageType.get()));
        if (type.isEmpty()) {
            synchronized (UNKNOWN_TYPES) {
                if (UNKNOWN_TYPES.add(damageType.get())) {
                    LOGGER.warn("Mob behaviour hit uses unknown damage type {}; dealing its default type instead",
                            damageType.get());
                }
            }
            return fallback.get();
        }
        return new DamageSource(type.get(), direct, causing);
    }

    /** The mob's attack damage; 0 for mobs without the attribute (cow, ghast, bat), with one warning per type. */
    private static double attackDamage(Mob mob) {
        AttributeInstance attribute = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute != null) {
            return attribute.getValue();
        }
        synchronized (NO_ATTACK_DAMAGE) {
            if (NO_ATTACK_DAMAGE.add(mob.getType())) {
                LOGGER.warn("Mob behaviour hits on {} deal no damage: it has no attack_damage attribute (knockback and "
                        + "effects still apply)", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
            }
        }
        return 0.0;
    }

    /** A mob effect by registry id (any mod's, e.g. {@code spell_engine:bleed}); seconds, 0-based amplifier. */
    public record EffectSpec(ResourceLocation id, double duration, int amplifier) {
        private static final Logger LOGGER = LogUtils.getLogger();
        private static final Set<ResourceLocation> WARNED = new HashSet<>();

        public static final Codec<EffectSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(EffectSpec::id),
                Codec.DOUBLE.fieldOf("duration").forGetter(EffectSpec::duration),
                Codec.INT.optionalFieldOf("amplifier", 0).forGetter(EffectSpec::amplifier)
        ).apply(i, EffectSpec::new));

        /** Resolved at use time, so a datapack may name effects from optional mods; missing ones are skipped. */
        public Optional<MobEffectInstance> instance() {
            Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.getHolder(id);
            if (effect.isEmpty()) {
                synchronized (WARNED) {
                    if (WARNED.add(id)) {
                        LOGGER.warn("Mob behaviour applies unknown effect {}; skipping it", id);
                    }
                }
                return Optional.empty();
            }
            return Optional.of(new MobEffectInstance(effect.get(), (int) Math.round(duration * 20.0), amplifier));
        }
    }
}
