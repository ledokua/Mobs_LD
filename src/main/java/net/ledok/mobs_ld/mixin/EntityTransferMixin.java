package net.ledok.mobs_ld.mixin;

import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mob changing dimension is re-created there from its own save (restoreFrom). That is not a fight abandoned on
 * disk, so the new copy keeps going instead of resetting toward a home in the old dimension.
 */
@Mixin(Entity.class)
public class EntityTransferMixin {
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void mobs_ld$transferred(Entity original, CallbackInfo ci) {
        MobCombat combat = CombatHost.of((Entity) (Object) this);
        if (combat != null) {
            combat.onTransferred();
        }
    }
}
