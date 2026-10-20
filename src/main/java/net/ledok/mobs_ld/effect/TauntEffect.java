package net.ledok.mobs_ld.effect;

import net.ledok.mobs_ld.entity.threat.ThreatEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Duration taunt: every tick it re-pins nearby mobs onto the caster and keeps the caster numerically above
 * everyone else, so it is a hard lock for the effect's duration. When it expires the caster is left sitting at
 * top*multiplier, holding aggro until teammates genuinely out-threat that frozen number.
 */
public class TauntEffect extends MobEffect {
    public TauntEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        // Fires once per application (including re-casts) — this is what makes the most recent taunt win.
        ThreatEffects.markTaunt(entity);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        ThreatEffects.applyTaunt(entity, amplifier);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}
