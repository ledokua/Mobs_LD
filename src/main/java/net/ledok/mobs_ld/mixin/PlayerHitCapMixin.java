package net.ledok.mobs_ld.mixin;

import net.ledok.mobs_ld.mob.damage.HitCapHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Caps a mob's hit on a player at the share of max health {@link net.ledok.mobs_ld.mob.damage.AttackShaping} chose,
 * at the start of {@code actuallyHurt}: after DD Loot Stage's damage_dealt and the Arenas tier, before armour,
 * Resistance and absorption. During i-frames vanilla passes only the excess over the last hit, which is capped alike.
 */
@Mixin(Player.class)
public abstract class PlayerHitCapMixin {
    @ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true)
    private float mobs_ld$capHit(float amount, DamageSource source) {
        double cap = ((HitCapHolder) this).mobs_ld$hitCap();
        if (Double.isNaN(cap)) {
            return amount;
        }
        float limit = (float) (cap * ((Player) (Object) this).getMaxHealth());
        return Math.min(amount, limit);
    }
}
