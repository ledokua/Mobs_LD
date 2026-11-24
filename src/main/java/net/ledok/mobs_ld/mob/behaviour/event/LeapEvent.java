package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Leaps to a marked spot and hits it only if the mob reaches the landing. Terrain can stop the leap. */
public record LeapEvent(double at, double until, double maxDistance, double height, double radius,
                        Telegraph telegraph, HitSpec hit) implements AbilityEvent {
    private static final double DEFAULT_LEAD = 0.55;
    private static final double LANDING_TOLERANCE = 0.65;

    public static final MapCodec<LeapEvent> CODEC = RecordCodecBuilder.<LeapEvent>mapCodec(i -> i.group(
            Codec.doubleRange(0.0, 30.0).fieldOf("at").forGetter(LeapEvent::at),
            Codec.doubleRange(0.0, 30.0).fieldOf("until").forGetter(LeapEvent::until),
            Codec.doubleRange(0.1, 16.0).fieldOf("max_distance").forGetter(LeapEvent::maxDistance),
            Codec.doubleRange(0.25, 8.0).fieldOf("height").forGetter(LeapEvent::height),
            Codec.doubleRange(0.1, 8.0).fieldOf("radius").forGetter(LeapEvent::radius),
            Telegraph.CODEC.optionalFieldOf("telegraph", Telegraph.DEFAULT).forGetter(LeapEvent::telegraph),
            HitSpec.CODEC.fieldOf("hit").forGetter(LeapEvent::hit)
    ).apply(i, LeapEvent::new)).validate(event -> event.until() > event.at()
            ? DataResult.success(event) : DataResult.error(() -> "A leap's until must be after at"));

    @Override
    public String type() {
        return "leap";
    }

    @Override
    public double begin() {
        return telegraph.show() || telegraph.from().isPresent()
                ? Math.min(at, telegraph.start(until, DEFAULT_LEAD)) : at;
    }

    @Override
    public double end() {
        return until;
    }

    @Override
    public Instance start(AbilityRun run) {
        Mob mob = run.mob();
        Vec3 aim = run.aimPosition();
        Vec3 across = aim.subtract(mob.position()).multiply(1.0, 0.0, 1.0);
        if (across.lengthSqr() > maxDistance * maxDistance) {
            across = across.normalize().scale(maxDistance);
        }
        Optional<Vec3> floor = run.floorNear(new Vec3(mob.getX() + across.x, aim.y, mob.getZ() + across.z));
        if (floor.isEmpty() || Math.abs(floor.get().y - mob.getY()) > height) {
            return new Instance() {
            };
        }
        Vec3 landing = floor.get();
        Shape.Circle impact = new Shape.Circle(radius);
        run.noteLeap(this, new AbilityRun.HitZone(impact, landing, run.yaw()));
        AbilityRun.Marker marker = telegraph.show() ? run.telegraph(landing, run.yaw(), impact, telegraph, until) : null;
        double duration = until - at;

        return new Instance() {
            private Vec3 launch;
            private boolean blocked;

            @Override
            public void tick(AbilityRun run) {
                double now = run.seconds();
                if (blocked || now < at || now >= until) {
                    return;
                }
                if (launch == null) {
                    launch = mob.position();
                    Vec3 direction = landing.subtract(launch);
                    if (direction.horizontalDistance() > maxDistance + LANDING_TOLERANCE) {
                        miss(run);
                        return;
                    }
                    if (direction.horizontalDistanceSqr() > 1.0e-4) {
                        run.setYaw((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
                    }
                } else if (mob.horizontalCollision) {
                    stop(run);
                    miss(run);
                    return;
                }
                double next = Mth.clamp((now + run.speed() / 20.0 - at) / duration, 0.0, 1.0);
                // Let the last descending move touch the floor so a miss in open air can be distinguished.
                double lift = next >= 1.0 - 1.0e-6 ? -0.06 : 4.0 * height * next * (1.0 - next);
                Vec3 velocity = launch.lerp(landing, next).add(0.0, lift, 0.0).subtract(mob.position());
                mob.resetFallDistance();
                run.moveBody(velocity.x, velocity.y, velocity.z);
            }

            @Override
            public void finish(AbilityRun run) {
                stop(run);
                if (!blocked && launch != null && mob.onGround() && mob.position().distanceTo(landing) <= LANDING_TOLERANCE) {
                    for (LivingEntity victim : run.enemiesIn(impact, landing, run.yaw(),
                            run.combat().reachUp(), run.combat().reachDown())) {
                        hit.apply(run, victim, landing);
                    }
                    run.flash(marker);
                } else {
                    miss(run);
                }
            }

            @Override
            public void cancel(AbilityRun run) {
                stop(run);
                miss(run);
            }

            private void miss(AbilityRun run) {
                if (!blocked) {
                    blocked = true;
                    run.clear(marker);
                    run.missLeap(LeapEvent.this);
                }
            }

            private void stop(AbilityRun run) {
                if (launch != null) {
                    mob.resetFallDistance();
                    run.moveBody(0.0, Math.min(0.0, mob.getDeltaMovement().y), 0.0);
                }
            }
        };
    }
}
