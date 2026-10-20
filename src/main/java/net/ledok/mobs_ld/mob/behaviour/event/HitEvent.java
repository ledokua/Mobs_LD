package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Area attack: telegraphed from {@link Telegraph#start}, lands at {@code at} on everyone hostile inside the shape.
 * Origin and facing are fixed when the telegraph appears, so what is drawn is exactly what hits.
 *
 * @param offset blocks to push the origin along the facing (e.g. a fist landing in front of the body)
 * @param height how far above the origin a victim's feet may be and still be hit
 */
public record HitEvent(double at, Shape shape, Origin origin, double offset, double height, Telegraph telegraph,
                       HitSpec hit) implements AbilityEvent {
    /** Default warning lead when the data does not say. */
    private static final double DEFAULT_LEAD = 0.8;

    public static final MapCodec<HitEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(HitEvent::at),
            Shape.CODEC.fieldOf("shape").forGetter(HitEvent::shape),
            Origin.CODEC.optionalFieldOf("origin", Origin.SELF).forGetter(HitEvent::origin),
            Codec.DOUBLE.optionalFieldOf("offset", 0.0).forGetter(HitEvent::offset),
            Codec.DOUBLE.optionalFieldOf("height", 3.0).forGetter(HitEvent::height),
            Telegraph.CODEC.optionalFieldOf("telegraph", Telegraph.DEFAULT).forGetter(HitEvent::telegraph),
            HitSpec.CODEC.optionalFieldOf("hit", HitSpec.DEFAULT).forGetter(HitEvent::hit)
    ).apply(i, HitEvent::new));

    @Override
    public String type() {
        return "hit";
    }

    @Override
    public double begin() {
        return telegraph.show() ? telegraph.start(at, DEFAULT_LEAD) : at;
    }

    @Override
    public double end() {
        return at;
    }

    @Override
    public Instance start(AbilityRun run) {
        float yaw = run.yaw();
        Vec3 base = origin == Origin.TARGET ? run.aimPosition() : run.mob().position();
        Vec3 point = run.groundAt(base.add(AbilityRun.forward(yaw).scale(offset)));
        AbilityRun.Marker marker = telegraph.show() && begin() < at
                ? run.telegraph(point, yaw, shape, telegraph, at)
                : null;
        return new Instance() {
            @Override
            public void finish(AbilityRun run) {
                for (LivingEntity victim : run.enemiesIn(shape, point, yaw, height)) {
                    hit.apply(run.mob(), victim, point);
                }
                run.flash(marker);
            }

            @Override
            public void cancel(AbilityRun run) {
                run.clear(marker);
            }
        };
    }
}
