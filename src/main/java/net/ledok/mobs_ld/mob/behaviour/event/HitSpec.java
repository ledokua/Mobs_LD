package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.LdMob;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What landing a hit does to one victim.
 *
 * @param damage    multiplier on the mob's {@code attack_damage} attribute
 * @param knockback horizontal push away from the hit origin
 * @param knockUp   upward launch speed (0.4 is about a jump)
 */
public record HitSpec(double damage, List<EffectSpec> effects, double knockback, double knockUp) {
    public static final HitSpec DEFAULT = new HitSpec(1.0, List.of(), 0.4, 0.0);
    public static final Codec<HitSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("damage", 1.0).forGetter(HitSpec::damage),
            EffectSpec.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(HitSpec::effects),
            Codec.DOUBLE.optionalFieldOf("knockback", 0.4).forGetter(HitSpec::knockback),
            Codec.DOUBLE.optionalFieldOf("knock_up", 0.0).forGetter(HitSpec::knockUp)
    ).apply(i, HitSpec::new));

    /** Damages, debuffs and pushes {@code victim}; {@code from} is where the blow came from. */
    public void apply(LdMob mob, LivingEntity victim, Vec3 from) {
        float amount = (float) (mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * damage);
        boolean hurt = amount <= 0.0F || victim.hurt(mob.damageSources().mobAttack(mob), amount);
        if (!hurt) {
            return; // blocked, invulnerable or in damage cooldown: no rider effects either
        }
        mob.combat().trace(String.format("hit %s for %.1f (feet %+.1f vs mob)", victim.getName().getString(), amount,
                victim.getY() - mob.getY()));
        for (EffectSpec effect : effects) {
            effect.instance().ifPresent(instance -> victim.addEffect(instance, mob));
        }
        if (knockback > 0.0 || knockUp > 0.0) {
            double dx = victim.getX() - from.x;
            double dz = victim.getZ() - from.z;
            double resist = 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            if (knockback > 0.0 && dx * dx + dz * dz > 1.0e-6) {
                victim.knockback(knockback, -dx, -dz);
            }
            if (knockUp > 0.0 && resist > 0.0) {
                Vec3 motion = victim.getDeltaMovement();
                victim.setDeltaMovement(motion.x, Math.max(motion.y, knockUp * resist), motion.z);
            }
            victim.hurtMarked = true; // sync the new velocity to players
        }
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
