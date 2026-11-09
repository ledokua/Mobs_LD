package net.ledok.mobs_ld.entity.threat;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * Soft-dependency bridge to the stealth effects of Rogues and of mrpg_lib (Archers Expansion's Infiltrator's Vanish).
 *
 * <p>Both hide a player from mobs by shrinking the vanilla {@code TargetGoal.getFollowRange()} (their
 * {@code TrackTargetGoalStealth} mixins), which makes the current target read as "out of range" and lets the goal
 * drop it. Our {@link net.ledok.mobs_ld.entity.threat.ThreatTargetGoal} extends {@code Goal} directly, so it never
 * inherits that hook and keeps targeting stealthed players. We replicate the check here: a player carrying
 * {@code rogues:stealth}, {@code rogues:shadow_step} or any mrpg_lib {@code StealthStatusEffect} is untargetable.
 *
 * <p>Referenced by registry id and class name, so this compiles and no-ops when neither mod is installed.
 */
public final class StealthCompat {
    private static final Set<ResourceLocation> HIDING_EFFECTS = Set.of(
            ResourceLocation.fromNamespaceAndPath("rogues", "stealth"),
            ResourceLocation.fromNamespaceAndPath("rogues", "shadow_step")
    );
    /** mrpg_lib's base class for stealth effects (Infiltrator's Vanish and others), or null without mrpg_lib. */
    private static final Class<?> MRPG_STEALTH = mrpgStealthClass();

    private StealthCompat() {
    }

    /** True if the entity carries a stealth effect that should hide it from threat targeting. */
    public static boolean isHidden(LivingEntity entity) {
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            Holder<MobEffect> effect = instance.getEffect();
            if (MRPG_STEALTH != null && MRPG_STEALTH.isInstance(effect.value())) {
                return true;
            }
            ResourceLocation id = effect.unwrapKey().map(key -> key.location()).orElse(null);
            if (id != null && HIDING_EFFECTS.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> mrpgStealthClass() {
        try {
            return Class.forName("com.mrpg_lib.effect.StealthStatusEffect", false, StealthCompat.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
