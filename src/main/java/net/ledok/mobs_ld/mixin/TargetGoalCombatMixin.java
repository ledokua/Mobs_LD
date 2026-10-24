package net.ledok.mobs_ld.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A vanilla target goal stops once the engine refuses its target (not allowed by {@code boss.engage_on}, given up on as
 * unreachable, or anyone while walking home). Without this, a goal whose target the engine dropped falls back to its
 * own remembered target, re-sets it (or has that refused) and keeps running, holding the TARGET flag so no other
 * target goal (e.g. the players-only nearest-target goal) can start. Hot path: other mobs cost one field read.
 */
@Mixin(TargetGoal.class)
public class TargetGoalCombatMixin {
    @Shadow
    @Final
    protected Mob mob;
    @Shadow
    protected LivingEntity targetMob;

    @ModifyReturnValue(method = "canContinueToUse()Z", at = @At("RETURN"))
    private boolean mobs_ld$dropRefusedTarget(boolean keep) {
        if (!keep) {
            return false;
        }
        MobCombat combat = CombatHost.of(mob);
        if (combat == null) {
            return true;
        }
        // The target the goal just kept: the mob's own, or the goal's remembered one when that was dropped.
        LivingEntity target = mob.getTarget() != null ? mob.getTarget() : targetMob;
        return target == null || (combat.mayTarget(target) && !combat.refusesTarget(target));
    }
}
