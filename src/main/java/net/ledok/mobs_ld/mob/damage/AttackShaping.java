package net.ledok.mobs_ld.mob.damage;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.ledok.mobs_ld.config.DamageConfig;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviours;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sizes mob hits on players in two steps (see docs/behaviour.md, "Attack profiles").
 *
 * <p>{@link #shape}: at the very start of {@code LivingEntity.hurt} (wrapping every other mod's injections there), the
 * dealer's attack profile resizes the hit, before DD Loot Stage's {@code loot_stage:damage_dealt}, the Arenas tier and
 * armour scale it. The dealer is who DD Loot Stage scales by: the attacker, else a living direct source (never a
 * player or the victim). A hit with neither is matched against {@code no_attacker} attacks: the nearest living mob of
 * a profile's type within that attack's {@code within} of the player stands in as the dealer.
 *
 * <p>{@link Result#cap}: the share of the player's max health one hit of that dealer may take, applied at the start of
 * the player's {@code actuallyHurt}, so after damage_dealt and the Arenas tier and before armour and absorption: the
 * profile's {@code cap_of_target_max_health}, else (when the config turns caps on) the config's share for bosses or
 * normal mobs. Damage that bypasses invulnerability ({@code /kill}) and hits without a mob behind them (falling, lava)
 * are never touched.
 */
public final class AttackShaping {
    private AttackShaping() {
    }

    /** The hit's new amount and its cap as a share of the player's max health (NaN: no cap). */
    public record Result(float amount, double cap) {
        static Result unchanged(float amount) {
            return new Result(amount, Double.NaN);
        }
    }

    public static Result shape(Player victim, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return Result.unchanged(amount);
        }
        LivingEntity dealer = dealer(victim, source);
        Entity direct = source.getDirectEntity();
        ResourceLocation directId = direct == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType());
        if (dealer != null) {
            AttackProfile profile = AttackProfiles.get(dealer.getType());
            float shaped = amount;
            if (profile != null) {
                Optional<AttackProfile.Attack> attack = profile.match(source, directId, MobCombat.abilityOf(dealer));
                if (attack.isPresent()) {
                    shaped = attack.get().apply(amount, profile.referenceHit().orElse(0.0), r(dealer, profile));
                }
            }
            return new Result(shaped, cap(dealer, profile));
        }
        if (source.getEntity() == null && direct == null) {
            for (Map.Entry<EntityType<?>, AttackProfile> entry : AttackProfiles.withNoAttacker()) {
                AttackProfile profile = entry.getValue();
                for (AttackProfile.Attack attack : profile.attacks()) {
                    if (!attack.match().noAttacker() || !attack.match().matches(source, null, null)) {
                        continue;
                    }
                    LivingEntity near = nearest(victim, entry.getKey(), attack.match().within());
                    if (near != null) {
                        float shaped = attack.apply(amount, profile.referenceHit().orElse(0.0), r(near, profile));
                        return new Result(shaped, cap(near, profile));
                    }
                }
            }
        }
        return Result.unchanged(amount);
    }

    /** The attacker, else a living direct source; null for players, the victim itself and hits with neither. */
    static LivingEntity dealer(LivingEntity victim, DamageSource source) {
        LivingEntity dealer = source.getEntity() instanceof LivingEntity living ? living
                : source.getDirectEntity() instanceof LivingEntity living ? living : null;
        return dealer == null || dealer == victim || dealer instanceof Player ? null : dealer;
    }

    /** {@code dealer}'s live attack_damage over the profile's reference (the type's default without one); 1 if unknown. */
    static double r(LivingEntity dealer, AttackProfile profile) {
        AttributeInstance attack = dealer.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return 1.0;
        }
        double reference = profile.referenceAttackDamage().orElseGet(() -> defaultAttack(dealer.getType(), attack));
        return reference > 0.0 ? attack.getValue() / reference : 1.0;
    }

    @SuppressWarnings("unchecked")
    private static double defaultAttack(EntityType<?> type, AttributeInstance live) {
        if (DefaultAttributes.hasSupplier(type)) {
            var supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
            if (supplier.hasAttribute(Attributes.ATTACK_DAMAGE)) {
                return supplier.getBaseValue(Attributes.ATTACK_DAMAGE);
            }
        }
        return live.getBaseValue();
    }

    /** The share of the player's max health one hit of {@code dealer} may take; NaN for no cap. */
    static double cap(LivingEntity dealer, AttackProfile profile) {
        if (profile != null && profile.capOfTargetMaxHealth().isPresent()) {
            return profile.capOfTargetMaxHealth().get();
        }
        DamageConfig config = DamageConfig.get();
        if (!config.capsEnabled) {
            return Double.NaN;
        }
        return isBoss(dealer) ? config.bossCapOfTargetMaxHealth : config.capOfTargetMaxHealth;
    }

    private static boolean isBoss(LivingEntity dealer) {
        if (dealer.getType().is(ConventionalEntityTypeTags.BOSSES)) {
            return true;
        }
        if (dealer instanceof Mob mob) {
            MobBehaviour behaviour = MobBehaviours.forMob(mob);
            return behaviour != null && behaviour.boss().isPresent();
        }
        return false;
    }

    private static LivingEntity nearest(Player victim, EntityType<?> type, double within) {
        AABB box = victim.getBoundingBox().inflate(within);
        List<LivingEntity> found = victim.level().getEntitiesOfClass(LivingEntity.class, box, entity -> entity.getType() == type
                && entity.isAlive() && entity.distanceToSqr(victim) <= within * within);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : found) {
            double distance = entity.distanceToSqr(victim);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }
}
