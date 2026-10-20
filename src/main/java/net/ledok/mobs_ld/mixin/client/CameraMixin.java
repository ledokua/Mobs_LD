package net.ledok.mobs_ld.mixin.client;

import net.ledok.mobs_ld.client.ScreenShake;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nudges the camera by the current {@link ScreenShake} amplitude after vanilla finishes positioning it. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Invoker("move")
    protected abstract void mobs_ld$move(float distance, float vertical, float horizontal);

    @Inject(method = "setup", at = @At("TAIL"))
    private void mobs_ld$applyScreenShake(
            BlockGetter level,
            Entity entity,
            boolean detached,
            boolean mirror,
            float partialTick,
            CallbackInfo ci
    ) {
        float amount = ScreenShake.currentIntensity(partialTick);
        if (amount <= 0.0F) {
            return;
        }
        float vertical = (float) (Math.random() * 2.0 - 1.0) * amount;
        float horizontal = (float) (Math.random() * 2.0 - 1.0) * amount;
        mobs_ld$move(0.0F, vertical, horizontal);
    }
}
