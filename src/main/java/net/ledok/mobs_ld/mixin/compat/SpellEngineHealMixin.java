package net.ledok.mobs_ld.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.ledok.mobs_ld.entity.threat.ThreatEvents;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Heal threat for Spell Engine heals (every class mod's heal spells): the only {@code LivingEntity.heal} call in
 * {@code SpellImpacts.performImpact} is the HEAL impact. Measuring health around it gives the health actually
 * restored, so overheal earns no threat; the caster is the method's only {@code LivingEntity} parameter.
 *
 * <p>{@code @Pseudo} + {@code require = 0}: silently skipped when Spell Engine is absent or the code moves.
 */
@Pseudo
@Mixin(targets = "net.spell_engine.internals.impact.SpellImpacts")
public class SpellEngineHealMixin {
    @WrapOperation(
            method = "performImpact",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V", remap = true),
            remap = false,
            require = 0
    )
    private static void mobs_ld$healThreat(LivingEntity patient, float amount, Operation<Void> original,
                                           @Local(argsOnly = true) LivingEntity caster) {
        float before = patient.getHealth();
        original.call(patient, amount);
        ThreatEvents.onHeal(caster, patient, patient.getHealth() - before);
    }
}
