package net.ledok.mobs_ld.mixin;

import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Players who start or stop tracking a mob that runs the ability engine see or lose its engine boss bar. Hooked on
 * Entity (the declaring class) so it covers every mob class, including those that override these and call super.
 */
@Mixin(Entity.class)
public class EntityCombatViewersMixin {
    @Inject(method = "startSeenByPlayer", at = @At("TAIL"))
    private void mobs_ld$showBar(ServerPlayer player, CallbackInfo ci) {
        MobCombat combat = CombatHost.of((Entity) (Object) this);
        if (combat != null) {
            combat.startSeenBy(player);
        }
    }

    @Inject(method = "stopSeenByPlayer", at = @At("TAIL"))
    private void mobs_ld$hideBar(ServerPlayer player, CallbackInfo ci) {
        MobCombat combat = CombatHost.of((Entity) (Object) this);
        if (combat != null) {
            combat.stopSeenBy(player);
        }
    }
}
