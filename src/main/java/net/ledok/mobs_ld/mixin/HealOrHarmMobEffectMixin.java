package net.ledok.mobs_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.ledok.mobs_ld.entity.threat.ThreatEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Heal threat for instant health from potions: drunk ({@code source} = the drinker), splashed or from a lingering
 * cloud ({@code indirectSource} = whoever threw it). Only health actually restored counts.
 */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect") // package-private
public class HealOrHarmMobEffectMixin {
    @WrapOperation(method = "applyInstantenousEffect",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V"))
    private void mobs_ld$healThreat(LivingEntity patient, float amount, Operation<Void> original,
                                    @Local(argsOnly = true, ordinal = 0) Entity source,
                                    @Local(argsOnly = true, ordinal = 1) Entity indirectSource) {
        float before = patient.getHealth();
        original.call(patient, amount);
        ThreatEvents.onHeal(indirectSource != null ? indirectSource : source, patient, patient.getHealth() - before);
    }
}
