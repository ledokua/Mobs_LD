package net.ledok.mobs_ld.mixin;

import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The drag an accelerating projectile flies with (a wither skull: 0.95, dangerous 0.73), to launch it at a set speed. */
@Mixin(AbstractHurtingProjectile.class)
public interface HurtingProjectileAccessor {
    @Invoker("getInertia")
    float mobs_ld$inertia();
}
