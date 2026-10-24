package net.ledok.mobs_ld.mixin.wither;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.ledok.mobs_ld.mob.combat.Skulls;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mobs_LD's skulls ({@link Skulls}) touch no block: the block a projectile lands on reacts to it (a decorated pot or a
 * chorus flower breaks, a target block or bell fires), and for those skulls that reaction is skipped, as their blast
 * is. Every other projectile costs one type check per block hit.
 */
@Mixin(Projectile.class)
public class ProjectileBlockHitMixin {
    @WrapWithCondition(method = "onHitBlock(Lnet/minecraft/world/phys/BlockHitResult;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;onProjectileHit(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/entity/projectile/Projectile;)V"))
    private boolean mobs_ld$skullsTouchNoBlock(BlockState block, Level level, BlockState state, BlockHitResult hit,
                                               Projectile projectile) {
        return !(projectile instanceof WitherSkull) || level.isClientSide() || Skulls.specName(projectile) == null;
    }
}
