package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Applies mob effects without damage: to the mob itself ({@code radius} 0, e.g. a self-buff), or to every enemy
 * within {@code radius} blocks (e.g. a roar that slows); with {@code on: aim}, to the ability's aim alone (the
 * Warden's Hunting Sniff marks its player with Glowing), credited to the mob.
 */
public record EffectEvent(double at, double radius, List<HitSpec.EffectSpec> effects, On on)
        implements AbilityEvent.Instant {
    public static final MapCodec<EffectEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(EffectEvent::at),
            Codec.DOUBLE.optionalFieldOf("radius", 0.0).forGetter(EffectEvent::radius),
            HitSpec.EffectSpec.CODEC.listOf().fieldOf("effects").forGetter(EffectEvent::effects),
            On.CODEC.optionalFieldOf("on", On.AROUND).forGetter(EffectEvent::on)
    ).apply(i, EffectEvent::new));

    @Override
    public String type() {
        return "effect";
    }

    @Override
    public void fire(AbilityRun run) {
        if (on == On.AIM) {
            LivingEntity aim = run.aim();
            if (aim != null && aim.isAlive()) {
                for (HitSpec.EffectSpec effect : effects) {
                    effect.instance().ifPresent(instance -> aim.addEffect(instance, run.mob()));
                }
            }
            return;
        }
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

    /** Who gets the effects. */
    public enum On implements StringRepresentable {
        /** The mob itself ({@code radius} 0) or every enemy within {@code radius} (as effects always worked). */
        AROUND("around"),
        /** The ability's aim (if it is alive); {@code radius} is not used. */
        AIM("aim");

        public static final Codec<On> CODEC = StringRepresentable.fromEnum(On::values);
        private final String name;

        On(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
