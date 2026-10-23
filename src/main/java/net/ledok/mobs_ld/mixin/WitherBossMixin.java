package net.ledok.mobs_ld.mixin;

import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A summoned Wither starts at a third of its max health and its 11 s charge heals a fixed 10 every 10 ticks, which
 * only fills a vanilla 300 bar: with health scaled up (Dungeon Difficulty), it would start the fight at about half.
 * The charge now ends at full health.
 */
@Mixin(WitherBoss.class)
public class WitherBossMixin {
    @Unique
    private boolean mobs_ld$charging;

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void mobs_ld$noteCharge(CallbackInfo ci) {
        mobs_ld$charging = ((WitherBoss) (Object) this).getInvulnerableTicks() > 0;
    }

    // RETURN, not TAIL: the charge branch returns early, and TAIL only matches the method's last return.
    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void mobs_ld$fillAfterCharge(CallbackInfo ci) {
        WitherBoss self = (WitherBoss) (Object) this;
        if (mobs_ld$charging && self.getInvulnerableTicks() <= 0) {
            self.setHealth(self.getMaxHealth());
        }
    }
}
