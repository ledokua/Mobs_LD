package net.ledok.mobs_ld.effect;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Base class for custom boss-inflicted status effects that carry both a custom Minecraft HUD icon
 * ({@code assets/mobs_ld/textures/mob_effect/<id>.png}) and custom particle visuals on afflicted entities and
 * lingering {@link net.minecraft.world.entity.AreaEffectCloud} hazards.
 */
public abstract class BossStatusEffect extends MobEffect {
    private final ParticleOptions cloudParticle;

    protected BossStatusEffect(MobEffectCategory category, int color,
                               ParticleOptions entityParticle, ParticleOptions cloudParticle) {
        super(category, color, entityParticle);
        this.cloudParticle = cloudParticle;
    }

    /** Particle used when this effect is emitted by a ground {@code cloud} event instead of generic potion swirls. */
    public ParticleOptions cloudParticle() {
        return cloudParticle;
    }
}
