package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Area attack: telegraphed from {@link Telegraph#start}, lands at {@code at} on everyone hostile inside the shape.
 * Origin and facing are fixed when the telegraph appears, so what is drawn is exactly what hits.
 *
 * @param offset blocks to push the origin along the facing (e.g. a fist landing in front of the body)
 * @param side   blocks to push the origin to the mob's right (under a one-handed weapon)
 * @param aimSide turn so a strike line this far to the right meets the target: aimed when the telegraph appears and
 *                turned into during the last {@link AbilityRun#AIM_LEAD} seconds before the impact (so the body
 *                does not leave a beat that is still swinging); without a telegraph, turned into before the impact
 * @param height how far above the origin a victim's feet may be and still be hit; empty = the mob's reach
 * @param depth  how far below the origin; empty = the mob's reach depth
 * @param rotate degrees added to the facing the ability locked when its tracking ended (positive = to the mob's
 *               right, as yaw grows): the zone and its hit test are turned by it (the Warden's second Circle wave at
 *               22.5). Empty = the current facing, as before
 * @param turnBody when the telegraph appears, the body turns to the zone's facing over {@link #TURN_SECONDS}
 */
public record HitEvent(double at, Shape shape, Origin origin, double offset, double side, Optional<Double> aimSide,
                       Optional<Double> height, Optional<Double> depth, Telegraph telegraph, HitSpec hit,
                       Optional<Double> rotate, boolean turnBody)
        implements AbilityEvent {
    /** Default warning lead when the data does not say. */
    private static final double DEFAULT_LEAD = 0.8;
    /** How long a {@code turn_body} turn takes (seconds of the ability's clock). */
    public static final double TURN_SECONDS = 0.3;

    public static final MapCodec<HitEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(HitEvent::at),
            Shape.CODEC.fieldOf("shape").forGetter(HitEvent::shape),
            Origin.CODEC.optionalFieldOf("origin", Origin.SELF).forGetter(HitEvent::origin),
            Codec.DOUBLE.optionalFieldOf("offset", 0.0).forGetter(HitEvent::offset),
            Codec.DOUBLE.optionalFieldOf("side", 0.0).forGetter(HitEvent::side),
            Codec.DOUBLE.optionalFieldOf("aim_side").forGetter(HitEvent::aimSide),
            Codec.DOUBLE.optionalFieldOf("height").forGetter(HitEvent::height),
            Codec.DOUBLE.optionalFieldOf("depth").forGetter(HitEvent::depth),
            Telegraph.CODEC.optionalFieldOf("telegraph", Telegraph.DEFAULT).forGetter(HitEvent::telegraph),
            HitSpec.CODEC.optionalFieldOf("hit", HitSpec.DEFAULT).forGetter(HitEvent::hit),
            Codec.doubleRange(-360.0, 360.0).optionalFieldOf("rotate").forGetter(HitEvent::rotate),
            Codec.BOOL.optionalFieldOf("turn_body", false).forGetter(HitEvent::turnBody)
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

    /** Only an untelegraphed hit needs the run to turn ahead of it; a telegraphed one turns itself (see start). */
    @Override
    public Optional<Double> preAim() {
        return telegraphed() ? Optional.empty() : aimSide;
    }

    @Override
    public Instance start(AbilityRun run) {
        // The zone locks here, but the body only turns into it just before the impact: an earlier beat may still be
        // swinging along the old facing. Without a telegraph the run has already been turning; keep following.
        boolean telegraphed = telegraphed();
        float aimed = aimSide.isEmpty() ? run.yaw() : telegraphed ? run.aimFor(aimSide.get()) : run.reaim(aimSide.get());
        float yaw = zoneYaw(aimed, run.lockedYaw());
        Vec3 base = origin == Origin.TARGET ? run.aimPosition() : run.mob().position();
        Vec3 point = run.groundAt(base.add(AbilityRun.forward(yaw).scale(offset)).add(AbilityRun.right(yaw).scale(side)));
        run.noteZone(this, new AbilityRun.HitZone(shape, point, yaw));
        if (turnBody) {
            run.turnBody(yaw, TURN_SECONDS);
        }
        AbilityRun.Marker marker = telegraphed ? run.telegraph(point, yaw, shape, telegraph, at) : null;
        return new Instance() {
            private boolean turned = !telegraphed || aimSide.isEmpty();

            @Override
            public void tick(AbilityRun run) {
                if (!turned && run.seconds() >= at - AbilityRun.AIM_LEAD) {
                    turned = true;
                    run.steer(yaw);
                }
            }

            @Override
            public void finish(AbilityRun run) {
                double up = height.orElse(run.combat().reachUp());
                double down = depth.orElse(run.combat().reachDown());
                for (LivingEntity victim : run.enemiesIn(shape, point, yaw, up, down)) {
                    hit.apply(run, victim, point);
                }
                run.flash(marker);
            }

            @Override
            public void cancel(AbilityRun run) {
                run.clear(marker);
            }
        };
    }

    /**
     * The facing the zone locks at: {@code rotate} degrees from the ability's locked facing, or (no {@code rotate})
     * {@code aimed}, the current facing or this beat's own re-aim. With {@code aim_side} as well, the rotation is
     * added to the re-aim.
     */
    public float zoneYaw(float aimed, float locked) {
        if (rotate.isEmpty()) {
            return aimed;
        }
        float from = aimSide.isPresent() ? aimed : locked;
        return Mth.wrapDegrees(from + rotate.get().floatValue());
    }

    private boolean telegraphed() {
        return telegraph.show() && begin() < at;
    }
}
