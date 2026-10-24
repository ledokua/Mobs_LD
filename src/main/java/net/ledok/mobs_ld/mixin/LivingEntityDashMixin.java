package net.ledok.mobs_ld.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A dashing mob is never on a climbable: vines, ladders, scaffolding or cave vines in the lane would clamp its speed
 * to a climb's 0.15 blocks per tick (and lift it at a wall), stalling the rush.
 */
@Mixin(LivingEntity.class)
public class LivingEntityDashMixin {
    @ModifyReturnValue(method = "onClimbable()Z", at = @At("RETURN"))
    private boolean mobs_ld$noClimbWhileDashing(boolean climbing) {
        if (!climbing) {
            return false;
        }
        MobCombat combat = CombatHost.of((LivingEntity) (Object) this);
        return combat == null || !combat.dashingThisTick();
    }
}
