package net.ledok.mobs_ld.effect;

import net.ledok.mobs_ld.registry.ModDamageTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.Map;

/**
 * A small lingering wound from a cleaver strike, visible as violet dust: up to 1 damage every {@value #TICK_INTERVAL}
 * ticks, never lethal (it stops at 1 health). The damage is {@code mobs_ld:amethyst_splinters} (armour reduces it)
 * caused by the mob that applied the effect ({@link SourcedEffect}). It ticks on the victim's own clock, not on the
 * effect's remaining duration, so a hit that refreshes the effect never resets or skips its next tick.
 */
public final class AmethystSplintersEffect extends BossStatusEffect implements SourcedEffect {
    private static final DustParticleOptions SHARDS =
            new DustParticleOptions(new Vector3f(0.68F, 0.38F, 0.89F), 0.55F);
    private static final int TICK_INTERVAL = 30;

    private final Map<LivingEntity, WeakReference<Entity>> sources = SourcedEffect.newSources();

    public AmethystSplintersEffect() {
        super(MobEffectCategory.HARMFUL, 0xAE61E3, SHARDS, SHARDS);
    }

    @Override
    public Map<LivingEntity, WeakReference<Entity>> sources() {
        return sources;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // the cadence is decided in applyEffectTick, from the victim's tick count
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() && entity.tickCount % TICK_INTERVAL == 0 && entity.getHealth() > 1.0F) {
            Entity source = sourceOf(entity);
            entity.hurt(entity.damageSources().source(ModDamageTypes.AMETHYST_SPLINTERS, source, source),
                    Math.min(1.0F, entity.getHealth() - 1.0F));
        }
        return true;
    }
}
