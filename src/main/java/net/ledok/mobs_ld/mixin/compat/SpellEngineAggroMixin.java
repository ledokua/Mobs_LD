package net.ledok.mobs_ld.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ledok.mobs_ld.config.ThreatConfig;
import net.ledok.mobs_ld.entity.threat.ThreatEffects;
import net.ledok.mobs_ld.entity.threat.ThreatEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Routes Spell Engine's AGGRO impact through the threat system. Its only {@code Mob.setTarget} calls in
 * {@code SpellImpacts.performImpact} are the AGGRO branch: {@code setTarget(caster)} for SET and
 * {@code setTarget(null)} for CLEAR. A bare setTarget is undone next tick by ThreatTargetGoal, so we turn SET into
 * a single-mob provoke and CLEAR into dropping the currently targeted player from that mob's table.
 *
 * <p>{@code @Pseudo} + {@code require = 0}: silently skipped when Spell Engine is absent or the code moves.
 */
@Pseudo
@Mixin(targets = "net.spell_engine.internals.impact.SpellImpacts")
public class SpellEngineAggroMixin {
    @WrapOperation(
            method = "performImpact",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;setTarget(Lnet/minecraft/world/entity/LivingEntity;)V",
                    remap = true),
            remap = false,
            require = 0
    )
    private static void mobs_ld$aggroThroughThreat(Mob mob, LivingEntity target, Operation<Void> original) {
        if (ThreatConfig.get().spellEngineAggro && ThreatEvents.shouldThreatTarget(mob)) {
            if (target instanceof Player caster) {
                ThreatEffects.provokeMob(mob, caster, ThreatConfig.get().spellEngineAggroAmplifier);
            } else if (target == null && mob.getTarget() instanceof Player cleared) {
                ThreatEffects.fadeMob(mob, cleared);
            }
        }
        original.call(mob, target);
    }
}
