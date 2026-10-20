package net.ledok.mobs_ld.entity.threat;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * Soft-dependency bridge to the Rogues mod's stealth effects.
 *
 * <p>Rogues hides a player from mobs by shrinking the vanilla {@code TargetGoal.getFollowRange()} (see its
 * {@code TrackTargetGoalStealth} mixin), which makes the current target read as "out of range" and lets the goal
 * drop it. Our {@link net.ledok.mobs_ld.entity.ai.ThreatTargetGoal} extends {@code Goal} directly, so it never
 * inherits that hook and keeps targeting stealthed players. We replicate the check here: a player carrying
 * {@code rogues:stealth} or {@code rogues:shadow_step} is untargetable.
 *
 * <p>Referenced entirely by registry id, so this compiles and no-ops when Rogues is not installed.
 */
public final class StealthCompat {
    private static final Set<ResourceLocation> HIDING_EFFECTS = Set.of(
            ResourceLocation.fromNamespaceAndPath("rogues", "stealth"),
            ResourceLocation.fromNamespaceAndPath("rogues", "shadow_step")
    );

    private StealthCompat() {
    }

    /** True if the entity carries a Rogues stealth effect that should hide it from threat targeting. */
    public static boolean isHidden(LivingEntity entity) {
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            Holder<MobEffect> effect = instance.getEffect();
            ResourceLocation id = effect.unwrapKey().map(key -> key.location()).orElse(null);
            if (id != null && HIDING_EFFECTS.contains(id)) {
                return true;
            }
        }
        return false;
    }
}
