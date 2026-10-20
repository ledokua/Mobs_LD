package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Applies mob effects without damage: to the mob itself ({@code radius} 0, e.g. a self-buff), or to every enemy
 * within {@code radius} blocks (e.g. a roar that slows).
 */
public record EffectEvent(double at, double radius, List<HitSpec.EffectSpec> effects) implements AbilityEvent.Instant {
    public static final MapCodec<EffectEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(EffectEvent::at),
            Codec.DOUBLE.optionalFieldOf("radius", 0.0).forGetter(EffectEvent::radius),
            HitSpec.EffectSpec.CODEC.listOf().fieldOf("effects").forGetter(EffectEvent::effects)
    ).apply(i, EffectEvent::new));

    @Override
    public String type() {
        return "effect";
    }

    @Override
    public void fire(AbilityRun run) {
        if (radius <= 0.0) {
            apply(run.mob());
            return;
        }
        for (LivingEntity victim : run.enemiesNear(new AABB(run.mob().blockPosition()).inflate(radius))) {
            if (victim.distanceToSqr(run.mob()) <= radius * radius) {
                apply(victim);
            }
        }
    }

    private void apply(LivingEntity target) {
        for (HitSpec.EffectSpec effect : effects) {
            effect.instance().ifPresent(target::addEffect);
        }
    }
}
