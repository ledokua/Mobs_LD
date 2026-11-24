package net.ledok.mobs_ld.mixin;

import net.ledok.mobs_ld.mob.behaviour.MobBehaviours;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A creeper that runs a Mobs_LD behaviour never fuses: its explosion would kill it, so its attacks are the behaviour's
 * abilities instead (a telegraphed blast it survives). Its swell direction is held at -1 before and after its tick, so
 * neither vanilla's fuse nor a subclass's own (Creeper Overhaul swells in its tick before calling vanilla's) can build
 * up, whatever a swell goal decided, and lighting it (flint and steel) does nothing. Creepers without a behaviour, or
 * outside a file's {@code host.only_tagged}, are vanilla.
 */
@Mixin(Creeper.class)
public abstract class CreeperNoFuseMixin {
    @Shadow
    public abstract void setSwellDir(int direction);

    @Unique
    private boolean mobs_ld$hosted() {
        Creeper self = (Creeper) (Object) this;
        return !self.level().isClientSide() && MobBehaviours.forMob(self) != null;
    }

    @Inject(method = "tick", at = {@At("HEAD"), @At("TAIL")})
    private void mobs_ld$noFuse(CallbackInfo ci) {
        if (mobs_ld$hosted()) {
            setSwellDir(-1);
        }
    }

    @Inject(method = "ignite", at = @At("HEAD"), cancellable = true)
    private void mobs_ld$noIgnite(CallbackInfo ci) {
        if (mobs_ld$hosted()) {
            ci.cancel();
        }
    }
}
