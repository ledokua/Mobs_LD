package net.ledok.mobs_ld.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Target selection of a mob that runs the ability engine skips everyone the engine refuses (see
 * {@link MobCombat#refusesTarget}: players it gave up on as unreachable, and anyone while it walks home). Filtering the
 * conditions every vanilla target goal tests means a nearest-player search falls through to the next reachable one and
 * retaliation simply does not start, instead of setting a target that would be refused. Combat conditions only, so
 * looking at players and other non-combat checks are unaffected. Hot path: every other attacker costs one type check
 * and a field read, and only after the vanilla test has passed.
 */
@Mixin(TargetingConditions.class)
public class TargetingConditionsMixin {
    @Shadow
    @Final
    private boolean isCombat;

    @ModifyReturnValue(method = "test", at = @At("RETURN"))
    private boolean mobs_ld$skipRefusedTargets(boolean valid, LivingEntity attacker, LivingEntity target) {
        if (!valid || !isCombat) {
            return valid;
        }
        MobCombat combat = CombatHost.of(attacker);
        return combat == null || !combat.refusesTarget(target);
    }
}
