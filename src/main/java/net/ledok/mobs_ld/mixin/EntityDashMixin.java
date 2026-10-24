package net.ledok.mobs_ld.mixin;

import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A dashing mob runs through entities that have collision boxes (a boat or shulker in the lane would otherwise stop a
 * low rush like a wall and give a free stagger): only blocks and the world border count as walls for a dash. This is
 * the filter both vanilla's entity collisions and Lithium's lazy ones go through, asked of the moving entity.
 */
@Mixin(Entity.class)
public class EntityDashMixin {
    @Inject(method = "canCollideWith(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void mobs_ld$dashThrough(Entity other, CallbackInfoReturnable<Boolean> cir) {
        MobCombat combat = CombatHost.of((Entity) (Object) this);
        if (combat != null && combat.dashingThisTick()) {
            cir.setReturnValue(false);
        }
    }
}
