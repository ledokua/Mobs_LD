package net.ledok.mobs_ld.mixin.wither;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.ledok.mobs_ld.mob.host.wither.WitherAdapter;
import net.ledok.mobs_ld.mob.host.wither.WitherHostOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Applies a hosted Wither's {@link WitherHostOptions} and lets the engine own its body during abilities (see
 * {@link WitherAdapter}). Every hook leaves the Wither vanilla while it is not hosted. Calls inside the vanilla
 * methods are wrapped, never the methods themselves: other mods hook {@code customServerAiStep} at HEAD (Open Parties
 * and Claims) and {@code dropCustomDeathLoot} (Soulslike Weaponry), which is not touched at all. Full descriptors
 * everywhere: {@code performRangedAttack} is overloaded three ways.
 */
@Mixin(WitherBoss.class)
public class WitherBossHostMixin {
    @Shadow
    private int destroyBlocksTick;

    @Unique
    private WitherBoss mobs_ld$self() {
        return (WitherBoss) (Object) this;
    }

    /**
     * The vanilla chase (fly straight at the target, climb above it) runs only while its target id is set; reading 0
     * skips it, so it stands still while the engine holds movement. The y damping before it still applies.
     */
    @ModifyExpressionValue(method = "aiStep()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;getAlternativeTarget(I)I", ordinal = 0))
    private int mobs_ld$holdChase(int target) {
        return target > 0 && WitherAdapter.holdsMovement(mobs_ld$self()) ? 0 : target;
    }

    /** The chase climbs while it is less than this far above its target (only while not powered). */
    @ModifyConstant(method = "aiStep()V", constant = @Constant(doubleValue = 5.0))
    private double mobs_ld$hoverHeight(double height) {
        WitherHostOptions options = WitherAdapter.options(mobs_ld$self());
        return options == null ? height : options.hoverHeight();
    }

    /** Ordinal 1 is the 1 HP per second regeneration; ordinal 0 is the summoning charge's heal, which stays. */
    @WrapWithCondition(method = "customServerAiStep()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;heal(F)V", ordinal = 1))
    private boolean mobs_ld$regenerate(WitherBoss self, float amount) {
        WitherHostOptions options = WitherAdapter.options(self);
        if (options == null || options.regenWhileEngaged()) {
            return true;
        }
        MobCombat combat = CombatHost.of(self);
        return combat == null || !combat.engaged();
    }

    /**
     * A hit arms {@code destroyBlocksTick}, and the tick that counts it down to 0 breaks the blocks around the body.
     * Clearing it before the count runs (not cancelling anything) is all it takes to keep the blocks.
     */
    @Inject(method = "customServerAiStep()V", at = @At("HEAD"))
    private void mobs_ld$blockBreaking(CallbackInfo ci) {
        if (destroyBlocksTick <= 0) {
            return;
        }
        WitherHostOptions options = WitherAdapter.options(mobs_ld$self());
        if (options != null && !options.blockBreaking().allows(mobs_ld$self())) {
            destroyBlocksTick = 0;
        }
    }

    /** Below half health vanilla shrugs off arrows and wind charges; {@code arrow_immunity: false} lets them hit. */
    @ModifyExpressionValue(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;isPowered()Z"))
    private boolean mobs_ld$arrowImmunity(boolean powered) {
        if (!powered) {
            return false;
        }
        WitherHostOptions options = WitherAdapter.options(mobs_ld$self());
        return options == null || options.arrowImmunity();
    }

    /**
     * Every vanilla skull (the middle head's goal, the side heads, the idle blue skulls) and its shoot sound go
     * through this one method; a Wither whose host options name skulls fires its own instead (WitherSkulls).
     */
    @Inject(method = "performRangedAttack(IDDDZ)V", at = @At("HEAD"), cancellable = true)
    private void mobs_ld$ownSkulls(int head, double x, double y, double z, boolean dangerous, CallbackInfo ci) {
        if (WitherAdapter.ownsSkulls(mobs_ld$self())) {
            ci.cancel();
        }
    }

    /** A side head without a target picks a random living thing nearby; target slots pick for it instead. */
    @WrapOperation(method = "customServerAiStep()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getNearbyEntities(Ljava/lang/Class;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <T extends LivingEntity> List<T> mobs_ld$noSideHeadPicks(Level level, Class<T> type, TargetingConditions conditions,
                                                                     LivingEntity source, AABB area, Operation<List<T>> original) {
        return WitherAdapter.ownsSideHeads(mobs_ld$self()) ? List.of() : original.call(level, type, conditions, source, area);
    }

    /**
     * Ordinal 0 drops a side head's target that fails vanilla's check (30 blocks, sight from the body's eyes); a target
     * slot keeps its own rules (sight from that head, lost after a grace time), so the head does not flicker.
     */
    @WrapWithCondition(method = "customServerAiStep()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;setAlternativeTarget(II)V", ordinal = 0))
    private boolean mobs_ld$keepSlotTarget(WitherBoss self, int head, int target) {
        return !WitherAdapter.ownsSideHeads(self);
    }
}
