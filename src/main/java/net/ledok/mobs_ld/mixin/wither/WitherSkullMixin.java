package net.ledok.mobs_ld.mixin.wither;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.ledok.mobs_ld.mob.behaviour.event.HitSpec;
import net.ledok.mobs_ld.mob.combat.Skulls;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mobs_LD's skulls ({@link Skulls}: vanilla wither skulls tagged {@code mobs_ld_skull:<spec>}) deal their spec instead
 * of vanilla's hit while their owner is a hosted mob that still has the spec (looked up when they land, so a reload
 * or phase change applies to skulls in flight, and they survive save and load): the direct hit deals amount x R with
 * the spec's damage type and effects (only if it lands), the owner is not healed by a kill, the blast becomes a
 * splash that breaks nothing, and they fly through the owner's adds and undead allies. A tagged skull whose owner is
 * gone keeps vanilla's hit but still breaks no blocks (nor does the block it lands on react: ProjectileBlockHitMixin).
 * Untagged skulls (any vanilla or unhosted Wither's) are vanilla.
 * Inside {@code onHitEntity} and {@code onHit} only calls are wrapped (Open Parties and Claims' HEAD inject on
 * {@code onHit} still runs); {@code tick} and {@code canHitEntity}, which WitherSkull inherits, get overrides that run
 * the inherited code first.
 */
@Mixin(WitherSkull.class)
public abstract class WitherSkullMixin extends AbstractHurtingProjectile {
    @Unique
    private String mobs_ld$spec;
    @Unique
    private boolean mobs_ld$specRead;

    protected WitherSkullMixin(EntityType<? extends AbstractHurtingProjectile> type, Level level) {
        super(type, level);
    }

    /** The spec this skull was tagged with (read once; tags are set before it enters the world), or null. */
    @Unique
    private String mobs_ld$specName() {
        if (!mobs_ld$specRead) {
            mobs_ld$specRead = true;
            mobs_ld$spec = level().isClientSide() ? null : Skulls.specName(this);
        }
        return mobs_ld$spec;
    }

    /** The shot this skull deals now, or null to behave as vanilla. Server side only. */
    @Unique
    private Skulls.Shot mobs_ld$shot() {
        String spec = mobs_ld$specName();
        return spec == null ? null : Skulls.resolve(spec, getOwner());
    }

    /** Vanilla's 8 to whatever it hits, as the skull's owner: the spec's amount x R and damage type instead. */
    @WrapOperation(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", ordinal = 0))
    private boolean mobs_ld$specHit(Entity victim, DamageSource source, float amount, Operation<Boolean> original) {
        Skulls.Shot shot = mobs_ld$shot();
        if (shot == null) {
            return original.call(victim, source, amount);
        }
        HitSpec hit = shot.spec().hit();
        float damage = hit.rawDamage(shot.owner());
        DamageSource own = hit.damageSource(level(), this, shot.owner(), () -> source);
        boolean landed = damage > 0.0F && original.call(victim, own, damage);
        // Effects only for a hit that landed: a roll's i-frames or a shield clear them too. Applied here rather than
        // in place of vanilla's Wither II, which vanilla skips entirely on Easy.
        if (landed && victim instanceof LivingEntity living && living.isAlive()) {
            hit.applyEffects(living, getEffectSource());
        }
        Skulls.traceHit(shot, victim, damage, landed);
        return landed;
    }

    /** A kill by a skull heals vanilla's Wither by 5; not a hosted one. */
    @WrapWithCondition(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V"))
    private boolean mobs_ld$noHealOnKill(LivingEntity owner, float amount) {
        return mobs_ld$shot() == null;
    }

    /** Vanilla's Wither II (10 s Normal, 40 s Hard); the spec's own effects were applied with the hit. */
    @WrapOperation(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean mobs_ld$noVanillaWither(LivingEntity victim, MobEffectInstance effect, Entity source,
                                            Operation<Boolean> original) {
        return mobs_ld$shot() == null ? original.call(victim, effect, source) : false;
    }

    /**
     * Vanilla's blast (radius 1, breaks blocks with mobGriefing) becomes the spec's splash, which touches no block and
     * spares the one hit directly. A tagged skull without a live owner still blasts, but never breaks blocks.
     */
    @WrapOperation(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"))
    private Explosion mobs_ld$splash(Level level, Entity source, double x, double y, double z, float radius,
                                     boolean fire, Level.ExplosionInteraction interaction, Operation<Explosion> original,
                                     @Local(argsOnly = true) HitResult result) {
        if (mobs_ld$specName() == null) {
            return original.call(level, source, x, y, z, radius, fire, interaction);
        }
        Skulls.Shot shot = mobs_ld$shot();
        if (shot == null) {
            return original.call(level, source, x, y, z, radius, fire, Level.ExplosionInteraction.NONE);
        }
        Skulls.splash(this, shot, result);
        return null; // the caller discards the result
    }

    /**
     * Clients fly every skull with vanilla's acceleration (it is not synced), so one launched at another speed would
     * drift ahead of or behind the server's between position updates and snap back: re-sending the velocity every
     * tick keeps what players see (and dodge) on the server's line. A few bytes per skull per tick.
     */
    @Override
    public void tick() {
        super.tick();
        if (!isRemoved() && mobs_ld$specName() != null) {
            hurtMarked = true;
        }
    }

    /** Through the owner, its adds and undead allies (non-player {@code #wither_friends}), as if they were not there. */
    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }
        if (level().isClientSide() || mobs_ld$specName() == null) {
            return true;
        }
        Skulls.Shot shot = mobs_ld$shot();
        return shot == null || !Skulls.passesThrough(shot, target);
    }
}
