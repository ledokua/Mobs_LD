package net.ledok.mobs_ld.mixin;

import net.ledok.mobs_ld.effect.SourcedEffect;
import net.ledok.mobs_ld.entity.threat.ThreatEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Buff/debuff threat. {@code onEffectAdded} runs only when an effect is newly applied (not when a running one is
 * refreshed), with the entity that applied it: Spell Engine passes the caster, potions their thrower, tipped arrows
 * their shooter.
 */
@Mixin(LivingEntity.class)
public class LivingEntityEffectThreatMixin {
    @Inject(method = "onEffectAdded", at = @At("HEAD"))
    private void mobs_ld$effectThreat(MobEffectInstance effect, Entity source, CallbackInfo ci) {
        ThreatEvents.onEffectAdded((LivingEntity) (Object) this, effect, source);
        mobs_ld$noteSource(effect, source);
    }

    /** A refresh by a hit (no new threat) still names its applier for a {@link SourcedEffect}. */
    @Inject(method = "onEffectUpdated", at = @At("HEAD"))
    private void mobs_ld$effectRefreshed(MobEffectInstance effect, boolean reapplied, Entity source, CallbackInfo ci) {
        mobs_ld$noteSource(effect, source);
    }

    @Unique
    private void mobs_ld$noteSource(MobEffectInstance effect, Entity source) {
        if (effect.getEffect().value() instanceof SourcedEffect sourced && !((LivingEntity) (Object) this).level().isClientSide()) {
            sourced.noteSource((LivingEntity) (Object) this, source);
        }
    }
}
