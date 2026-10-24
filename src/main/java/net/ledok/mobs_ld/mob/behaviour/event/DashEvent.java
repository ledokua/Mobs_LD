package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Charge in a straight line from {@code at} to {@code until} toward where the aim target stood when the telegraph
 * appeared, {@code overshoot} blocks past it, at most {@code maxDistance}. Anyone the body sweeps through takes
 * {@code hit} once. Running into a wall ends the charge, by default in a stagger.
 */
public record DashEvent(double at, double until, double maxDistance, double overshoot, double width,
                        Optional<HitSpec> hit, WallRule onWall, Telegraph telegraph) implements AbilityEvent {
    private static final double DEFAULT_LEAD = 0.5;

    public static final MapCodec<DashEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(DashEvent::at),
            Codec.DOUBLE.fieldOf("until").forGetter(DashEvent::until),
            Codec.DOUBLE.fieldOf("max_distance").forGetter(DashEvent::maxDistance),
            Codec.DOUBLE.optionalFieldOf("overshoot", 1.0).forGetter(DashEvent::overshoot),
            Codec.DOUBLE.optionalFieldOf("width", 2.0).forGetter(DashEvent::width),
            HitSpec.CODEC.optionalFieldOf("hit").forGetter(DashEvent::hit),
            WallRule.CODEC.optionalFieldOf("on_wall", WallRule.STAGGER).forGetter(DashEvent::onWall),
            Telegraph.CODEC.optionalFieldOf("telegraph", Telegraph.DEFAULT).forGetter(DashEvent::telegraph)
    ).apply(i, DashEvent::new));

    public enum WallRule implements StringRepresentable {
        STAGGER("stagger"),
        STOP("stop");

        public static final Codec<WallRule> CODEC = StringRepresentable.fromEnum(WallRule::values);
        private final String name;

        WallRule(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @Override
    public String type() {
        return "dash";
    }

    @Override
    public double begin() {
        return telegraph.show() ? telegraph.start(at, DEFAULT_LEAD) : at;
    }

    @Override
    public double end() {
        return Math.max(at, until);
    }

    @Override
    public Instance start(AbilityRun run) {
        Mob mob = run.mob();
        float yaw = run.yaw();
        Vec3 direction = AbilityRun.forward(yaw);
        Vec3 aim = run.aimPosition();
        double toAim = Math.hypot(aim.x - mob.getX(), aim.z - mob.getZ());
        double distance = Math.max(0.0, Math.min(maxDistance, toAim + overshoot));
        Shape path = new Shape.Rectangle(width, distance, 0.0);
        AbilityRun.Marker marker = telegraph.show() && begin() < at
                ? run.telegraph(run.groundAt(mob.position()), yaw, path, telegraph, at)
                : null;
        Set<UUID> struck = new HashSet<>();

        return new Instance() {
            private double travelled;
            private boolean started;
            private boolean stopped;

            @Override
            public void tick(AbilityRun run) {
                if (stopped || run.seconds() < at) {
                    return;
                }
                if (!started) {
                    started = true;
                    run.flash(marker);
                }
                double ticks = Math.max(1.0, (end() - at) * 20.0 / run.speed());
                double step = Math.min(distance / ticks, distance - travelled);
                if (step <= 1.0e-3) {
                    halt();
                    return;
                }
                if (mob.horizontalCollision && travelled > 0.0) {
                    halt();
                    if (onWall == WallRule.STAGGER) {
                        run.stagger();
                    }
                    return;
                }
                run.moveBody(direction.x * step, run.sidewaysY(), direction.z * step);
                travelled += step;
                hit.ifPresent(spec -> {
                    for (LivingEntity victim : run.enemiesNear(mob.getBoundingBox().inflate(width / 2.0, 0.5, width / 2.0))) {
                        if (struck.add(victim.getUUID())) {
                            spec.apply(run, victim, mob.position());
                        }
                    }
                });
            }

            @Override
            public void finish(AbilityRun run) {
                halt();
            }

            @Override
            public void cancel(AbilityRun run) {
                halt();
                run.clear(marker);
            }

            private void halt() {
                if (!stopped) {
                    stopped = true;
                    mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
                }
            }
        };
    }
}
