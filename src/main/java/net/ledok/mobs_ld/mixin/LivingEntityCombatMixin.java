package net.ledok.mobs_ld.mixin;

import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Damage and death hooks of the ability engine, for every mob that runs one (see {@link CombatHost}). A subclass
 * override of {@code hurt} (e.g. the Wither's charge immunity) runs its own checks before reaching these. Damage that
 * bypasses invulnerability ({@code /kill}) is never refused or scaled.
 */
@Mixin(LivingEntity.class)
public class LivingEntityCombatMixin {
    /** Invulnerable while walking home or during an ability marked {@code invulnerable} (transitions, intros). */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void mobs_ld$refuseDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        MobCombat combat = mobs_ld$combatFor(source);
        if (combat != null && combat.invulnerable()) {
            cir.setReturnValue(false);
        }
    }

    /** Phase {@code damage_taken} multipliers. */
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float mobs_ld$scaleDamage(float amount, DamageSource source) {
        MobCombat combat = mobs_ld$combatFor(source);
        return combat != null ? combat.adjustDamage(source, amount) : amount;
    }

    /** The {@code on_death} timeline plays out while the body falls, whatever the subclass does in tickDeath. */
    @Inject(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;tickDeath()V"))
    private void mobs_ld$tickDeathTimeline(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        MobCombat combat = CombatHost.of(self);
        if (combat != null && !self.level().isClientSide()) {
            combat.tickDeath();
        }
    }

    /** The server-side combat that judges this hit, or null (not a host, client side, or {@code /kill}). */
    @Unique
    private MobCombat mobs_ld$combatFor(DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        MobCombat combat = CombatHost.of(self);
        if (combat == null || self.level().isClientSide() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return null;
        }
        return combat;
    }
}
