package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.Fighters;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Continuous spatial force field active from {@code from} to {@code until}, pushing, pulling, orbiting, or lifting
 * hostile entities inside {@code shape}.
 *
 * <p>Designed for tactile player-boss interaction (storm gales, spinning cyclone vortices, gravity collapses, and
 * updrafts) with minimal server overhead: no helper entities are spawned and velocity updates sync via vanilla
 * {@code hurtMarked}. Horizontal velocity is clamped to avoid tunneling or anti-cheat rubber-banding.
 */
public record PullEvent(
        double from,
        double until,
        Shape shape,
        Origin origin,
        Mode mode,
        double strength,
        double lift,
        double offset,
        double side,
        Optional<Double> height,
        Optional<Double> depth,
        boolean followMob,
        boolean resetFall
) implements AbilityEvent {
    private static final double MAX_HORIZONTAL_SPEED = 0.92;
    private static final double MIN_CENTER_DIST = 1.15;

    public static final MapCodec<PullEvent> CODEC = RecordCodecBuilder.<PullEvent>mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("from").forGetter(PullEvent::from),
            Codec.DOUBLE.fieldOf("until").forGetter(PullEvent::until),
            Shape.CODEC.fieldOf("shape").forGetter(PullEvent::shape),
            Origin.CODEC.optionalFieldOf("origin", Origin.SELF).forGetter(PullEvent::origin),
            Mode.CODEC.optionalFieldOf("mode", Mode.INWARD).forGetter(PullEvent::mode),
            Codec.doubleRange(0.0, 2.0).optionalFieldOf("strength", 0.18).forGetter(PullEvent::strength),
            Codec.doubleRange(-1.5, 1.5).optionalFieldOf("lift", 0.0).forGetter(PullEvent::lift),
            Codec.DOUBLE.optionalFieldOf("offset", 0.0).forGetter(PullEvent::offset),
            Codec.DOUBLE.optionalFieldOf("side", 0.0).forGetter(PullEvent::side),
            Codec.DOUBLE.optionalFieldOf("height").forGetter(PullEvent::height),
            Codec.DOUBLE.optionalFieldOf("depth").forGetter(PullEvent::depth),
            Codec.BOOL.optionalFieldOf("follow_mob", true).forGetter(PullEvent::followMob),
            Codec.BOOL.optionalFieldOf("reset_fall", false).forGetter(PullEvent::resetFall)
    ).apply(i, PullEvent::new)).validate(PullEvent::validate);

    private static DataResult<PullEvent> validate(PullEvent event) {
        if (event.from < 0.0) {
            return DataResult.error(() -> "A pull event's 'from' (" + event.from + ") cannot be negative");
        }
        if (event.until < event.from) {
            return DataResult.error(() -> "A pull event ends (until " + event.until
                    + ") before it starts (from " + event.from + ")");
        }
        return DataResult.success(event);
    }

    public enum Mode implements StringRepresentable {
        /** Draws victims horizontally toward the zone center. */
        INWARD("inward"),
        /** Drives victims horizontally away from the zone center (or along the facing). */
        OUTWARD("outward"),
        /** Gently floats victims upward while keeping them near the column. */
        UPDRAFT("updraft"),
        /** Spirals victims tangentially around the zone center while pulling them inward. */
        ORBIT("orbit");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @Override
    public String type() {
        return "pull";
    }

    @Override
    public double begin() {
        return from;
    }

    @Override
    public double end() {
        return until;
    }

    @Override
    public Instance start(AbilityRun run) {
        float lockedYaw = run.yaw();
        List<Vec3> lockedBases = resolveAnchors(run, lockedYaw);
        return new Instance() {
            @Override
            public void tick(AbilityRun run) {
                applyForce(run, lockedYaw, lockedBases);
            }

            @Override
            public void finish(AbilityRun run) {
                if (from == until) {
                    applyForce(run, lockedYaw, lockedBases);
                }
            }
        };
    }

    private void applyForce(AbilityRun run, float lockedYaw, List<Vec3> lockedBases) {
        float yaw = (origin == Origin.SELF && followMob) ? run.yaw() : lockedYaw;
        List<Vec3> anchors = (origin == Origin.SELF && followMob) ? resolveAnchors(run, yaw) : lockedBases;
        double up = height.orElse(run.combat().reachUp() + 3.5);
        double down = depth.orElse(run.combat().reachDown());

        for (Vec3 center : anchors) {
            for (LivingEntity victim : run.enemiesIn(shape, center, yaw, up, down)) {
                if (victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
                    continue;
                }
                double resist = Math.max(0.0, 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                double scale = 0.35 + 0.65 * resist;
                double dx = victim.getX() - center.x;
                double dz = victim.getZ() - center.z;
                double dist = Math.hypot(dx, dz);
                Vec3 ahead = AbilityRun.forward(yaw);
                double nx = dist > 1.0e-4 ? dx / dist : ahead.x;
                double nz = dist > 1.0e-4 ? dz / dist : ahead.z;

                double ax = 0.0;
                double az = 0.0;
                double force = strength * scale;
                switch (mode) {
                    case INWARD -> {
                        if (dist > MIN_CENTER_DIST) {
                            double damp = Math.min(1.0, (dist - MIN_CENTER_DIST) / 1.5);
                            ax = -nx * force * damp;
                            az = -nz * force * damp;
                        }
                    }
                    case OUTWARD -> {
                        // Directional shapes (cone / rectangle) push along the gust axis + radial fan.
                        if (shape instanceof Shape.Cone || shape instanceof Shape.Rectangle) {
                            ax = (ahead.x * 0.65 + nx * 0.35) * force;
                            az = (ahead.z * 0.65 + nz * 0.35) * force;
                        } else {
                            ax = nx * force;
                            az = nz * force;
                        }
                    }
                    case ORBIT -> {
                        // Tangential swirl (-nz, nx) combined with inward suction outside the inner eye.
                        double tx = -nz;
                        double tz = nx;
                        double inwardWeight = dist > 1.7 ? 0.48 : 0.0;
                        ax = (tx * 0.72 - nx * inwardWeight) * force;
                        az = (tz * 0.72 - nz * inwardWeight) * force;
                    }
                    case UPDRAFT -> {
                        if (dist > MIN_CENTER_DIST && force > 0.0) {
                            ax = -nx * force * 0.35;
                            az = -nz * force * 0.35;
                        }
                    }
                }

                Vec3 current = victim.getDeltaMovement();
                double vx = current.x + ax;
                double vz = current.z + az;
                double hSpeed = Math.hypot(vx, vz);
                if (hSpeed > MAX_HORIZONTAL_SPEED) {
                    double clamp = MAX_HORIZONTAL_SPEED / hSpeed;
                    vx *= clamp;
                    vz *= clamp;
                }

                double vy = current.y;
                if (lift > 0.0) {
                    double targetVy = lift * scale;
                    vy = mode == Mode.UPDRAFT
                            ? Mth.lerp(0.45, vy, targetVy)
                            : Math.max(vy, targetVy);
                } else if (lift < 0.0 && !victim.onGround()) {
                    vy = Math.min(vy, lift * scale);
                }

                victim.setDeltaMovement(vx, vy, vz);
                victim.hurtMarked = true;
                if (resetFall || mode == Mode.UPDRAFT) {
                    victim.resetFallDistance();
                }
            }
        }
    }

    private List<Vec3> resolveAnchors(AbilityRun run, float yaw) {
        List<Vec3> bases = switch (origin) {
            case SELF -> List.of(run.mob().position());
            case TARGET -> List.of(run.aimPosition());
            case EACH_FIGHTER -> {
                List<Vec3> list = new ArrayList<>();
                for (Player fighter : Fighters.of(run.mob())) {
                    list.add(fighter.position());
                }
                if (list.isEmpty()) {
                    list.add(run.aimPosition());
                }
                yield list;
            }
        };
        List<Vec3> result = new ArrayList<>(bases.size());
        for (Vec3 base : bases) {
            result.add(run.groundAt(base.add(AbilityRun.forward(yaw).scale(offset))
                    .add(AbilityRun.right(yaw).scale(side))));
        }
        return result;
    }
}
