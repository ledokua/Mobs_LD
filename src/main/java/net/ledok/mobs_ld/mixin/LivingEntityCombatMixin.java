package net.ledok.mobs_ld.mixin;

import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.DeathDrops;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.ledok.mobs_ld.mob.combat.SummonBond;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Damage and death hooks of the ability engine, for every mob that runs one (see {@link CombatHost}). A subclass
 * override of {@code hurt} (e.g. the Wither's charge immunity) runs its own checks before reaching these. Damage that
 * bypasses invulnerability ({@code /kill}) is never refused or scaled. Also the drop rules of summons marked
 * {@code loot: false} / {@code xp: false} (tags, see {@link SummonBond}); other entities pay a tag lookup or two when
 * they die.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCombatMixin implements DeathDrops {
    /** Inside its own {@code die} as a {@code loot: false} add (see {@link DeathDrops}). */
    @Unique
    private boolean mobs_ld$blockingDeathDrops;

    @Shadow
    protected abstract void dropExperience(Entity killer);

    /** Invulnerable while walking home or during an ability marked {@code invulnerable} (transitions, intros). */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void mobs_ld$refuseDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        MobCombat combat = mobs_ld$combatFor(source);
        if (combat != null && combat.invulnerable()) {
            cir.setReturnValue(false);
        }
    }

    /** Outgoing ward bonuses and the recipient's damage taken multipliers. */
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float mobs_ld$scaleDamage(float amount, DamageSource source) {
        if (!((LivingEntity) (Object) this).level().isClientSide() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            Entity attacker = source.getEntity();
            MobCombat dealer = attacker == null ? null : CombatHost.of(attacker);
            if (dealer != null) {
                amount = dealer.adjustWardDamageDealt(amount);
            }
        }
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

    /**
     * A summon marked {@code loot: false} drops nothing on death: the whole death drop step is skipped (the loot
     * table, so no wither skeleton skull; custom death loot, so no charged creeper's one skull is spent on it; its
     * equipment), and only its experience still follows its own {@code xp} rule. Gated here rather than at
     * {@code shouldDropLoot}, which Monster overrides without asking its super. The mark is an entity tag, so it
     * survives saves. While its {@code die} runs, anything else it drops is held back too (EntityDropsMixin).
     */
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void mobs_ld$noLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        if (((LivingEntity) (Object) this).getTags().contains(SummonBond.NO_LOOT_TAG)) {
            ci.cancel();
            dropExperience(source.getEntity());
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void mobs_ld$enterDeath(DamageSource source, CallbackInfo ci) {
        mobs_ld$blockingDeathDrops = ((LivingEntity) (Object) this).getTags().contains(SummonBond.NO_LOOT_TAG);
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void mobs_ld$leaveDeath(DamageSource source, CallbackInfo ci) {
        mobs_ld$blockingDeathDrops = false;
    }

    @Override
    public boolean mobs_ld$blocksDeathDrops() {
        return mobs_ld$blockingDeathDrops;
    }

    /** A summon marked {@code xp: false} drops no experience (nor do mods that scale it get any to scale). */
    @Inject(method = "dropExperience", at = @At("HEAD"), cancellable = true)
    private void mobs_ld$noExperience(Entity killer, CallbackInfo ci) {
        if (((LivingEntity) (Object) this).getTags().contains(SummonBond.NO_XP_TAG)) {
            ci.cancel();
        }
    }

    @Inject(method = "getExperienceReward", at = @At("HEAD"), cancellable = true)
    private void mobs_ld$noExperienceReward(ServerLevel level, Entity killer, CallbackInfoReturnable<Integer> cir) {
        if (((LivingEntity) (Object) this).getTags().contains(SummonBond.NO_XP_TAG)) {
            cir.setReturnValue(0);
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
