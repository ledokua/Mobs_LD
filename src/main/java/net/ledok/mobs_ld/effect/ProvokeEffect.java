package net.ledok.mobs_ld.effect;

import net.ledok.mobs_ld.entity.threat.ThreatEffects;
import net.minecraft.world.effect.InstantenousMobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Instant area provoke: the moment it lands, the caster jumps to the top of nearby mobs' threat tables at the
 * stronger provoke multiplier and takes a short override window ({@code provokeOverrideTicks}) on each of them,
 * beating even an active taunt. When the window lapses a still-active taunt reclaims the mob; otherwise the caster
 * just keeps the boosted threat number until teammates out-damage it.
 */
public class ProvokeEffect extends InstantenousMobEffect {
    public ProvokeEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        ThreatEffects.applyProvoke(entity, amplifier);
        return true;
    }
}
