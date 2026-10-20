package net.ledok.mobs_ld.mixin;

import net.ledok.mobs_ld.registry.ModAttributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Grants every player the {@code mobs_ld:threat_multiplier} attribute by appending it to the player's defaults. */
@Mixin(Player.class)
public class PlayerThreatAttributeMixin {
    @Inject(method = "createAttributes", at = @At("RETURN"))
    private static void mobs_ld$addThreatMultiplier(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        cir.getReturnValue().add(ModAttributes.THREAT_MULTIPLIER);
    }
}
