package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.DashLane;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Charge in a straight line toward where the aim target stood when the dash locked, {@code overshoot} blocks past
 * it, at most {@code maxDistance}. It locks (direction, length, lane) when its telegraph appears, or at {@code at}
 * without one; to lock earlier without a telegraph, end the ability's {@code track_until} earlier. The lane is exactly
 * the strip the telegraph draws: anyone whose centre is on it when the body passes takes {@code hit}, once per dash
 * (even if a dodge blocked it). The test is swept every tick (old to new position of both), so a fast body cannot
 * skip anyone. Running into a wall ends the charge, in a stagger once it has travelled {@code staggerMinDistance}.
 *
 * <p>Movement: from {@code at} to {@code until} at a steady distance / time, or at a constant {@code speed} (blocks per
 * tick, faster with the phase's attack speed, like the timeline) until it has covered the lane. A sideways drift is
 * pulled back onto the centre line. With {@code height} the body's height is driven too (a flyer rushing low);
 * without it the body keeps its own vertical speed (gravity), or its altitude on hosts that hold it (the Wither).
 *
 * @param until               end of the run; empty with {@code speed}: once the longest lane is covered
 * @param speed               constant blocks per tick instead of distance / (until - at)
 * @param width               lane width: blocks, or the host's rendered body ({@code body})
 * @param height              blocks above the aim's feet (where it stood when the dash locked) the body's feet are held
 *                            at while it runs; empty = no vertical drive
 * @param push                which way {@code hit}'s knockback shoves: away from the body, or sideways out of the lane
 * @param staggerMinDistance  a wall only staggers after this much travel; closer, the dash just stops
 */
public record DashEvent(double at, Optional<Double> until, Optional<Double> speed, double maxDistance, double overshoot,
                        Width width, Optional<Double> height, Push push, Optional<HitSpec> hit, WallRule onWall,
                        double staggerMinDistance, Telegraph telegraph) implements AbilityEvent {
    private static final double DEFAULT_LEAD = 0.5;
    /** A {@code speed} dash's event ends this long after the longest lane would be covered (rounding to ticks). */
    private static final double END_MARGIN = 0.1;
    /** Closer than this to its aim the dash keeps its facing instead of turning to the aim. */
    private static final double MIN_AIM_DISTANCE = 0.5;
    /** How far above and below its hitbox the body still touches someone (a step, a jump). */
    private static final double VERTICAL_SLACK = 0.5;
    /** Extra room around the swept lane when looking for victims (their own movement, their width). */
    private static final double SEARCH_MARGIN = 1.5;
    /** Blocks per tick the height drive may always move, even on a slow dash. */
    private static final double MIN_CLIMB = 0.4;

    public static final MapCodec<DashEvent> CODEC = RecordCodecBuilder.<DashEvent>mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(DashEvent::at),
            Codec.DOUBLE.optionalFieldOf("until").forGetter(DashEvent::until),
            Codec.doubleRange(0.01, 8.0).optionalFieldOf("speed").forGetter(DashEvent::speed),
            Codec.doubleRange(0.0, 256.0).fieldOf("max_distance").forGetter(DashEvent::maxDistance),
            Codec.DOUBLE.optionalFieldOf("overshoot", 1.0).forGetter(DashEvent::overshoot),
            Width.CODEC.optionalFieldOf("width", Width.of(2.0)).forGetter(DashEvent::width),
            Codec.doubleRange(-64.0, 64.0).optionalFieldOf("height").forGetter(DashEvent::height),
            Push.CODEC.optionalFieldOf("push", Push.AWAY).forGetter(DashEvent::push),
            HitSpec.CODEC.optionalFieldOf("hit").forGetter(DashEvent::hit),
            WallRule.CODEC.optionalFieldOf("on_wall", WallRule.STAGGER).forGetter(DashEvent::onWall),
            Codec.doubleRange(0.0, 256.0).optionalFieldOf("stagger_min_distance", 4.0).forGetter(DashEvent::staggerMinDistance),
            Telegraph.CODEC.optionalFieldOf("telegraph", Telegraph.DEFAULT).forGetter(DashEvent::telegraph)
    ).apply(i, DashEvent::new)).validate(DashEvent::validate);

    private static DataResult<DashEvent> validate(DashEvent event) {
        if (event.until.isEmpty() && event.speed.isEmpty()) {
            return DataResult.error(() -> "A dash needs 'until' (it covers its distance by then) or 'speed' (blocks per tick)");
        }
        if (event.until.isPresent() && event.until.get() < event.at) {
            return DataResult.error(() -> "A dash ends (until " + event.until.get() + ") before it starts (at " + event.at + ")");
        }
        return DataResult.success(event);
    }

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

    /** Which way a dash's hit shoves its victims. */
    public enum Push implements StringRepresentable {
        /** Away from the body (the hit's usual knockback). */
        AWAY("away"),
        /** Sideways out of the lane, on the side the victim stands (dead centre: to the lane's right). */
        SIDE("side");

        public static final Codec<Push> CODEC = StringRepresentable.fromEnum(Push::values);
        private final String name;

        Push(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** A lane width: a number of blocks, or {@code body} (empty): the host's rendered body width. */
    public record Width(Optional<Double> blocks) {
        public static final Width BODY = new Width(Optional.empty());
        private static final Codec<Width> BODY_CODEC = Codec.STRING.comapFlatMap(text -> "body".equals(text)
                ? DataResult.success(BODY)
                : DataResult.error(() -> "Unknown dash width '" + text + "' (expected a number of blocks or body)"), width -> "body");
        public static final Codec<Width> CODEC = Codec.either(Codec.doubleRange(0.1, 64.0), BODY_CODEC).xmap(
                either -> either.map(Width::of, body -> body),
                width -> width.blocks.<Either<Double, Width>>map(Either::left).orElseGet(() -> Either.right(width)));

        public static Width of(double blocks) {
            return new Width(Optional.of(blocks));
        }

        public double resolve(AbilityRun run) {
            return blocks.orElseGet(() -> run.combat().adapter().bodyWidth(run.mob()));
        }
    }

    @Override
    public String type() {
        return "dash";
    }

    @Override
    public double begin() {
        if (telegraph.show()) {
            return telegraph.start(at, DEFAULT_LEAD);
        }
        // Hidden telegraph with an explicit `from` still locks the dash corridor early without spawning ground arrows,
        // allowing a custom 3D `vfx` event (origin: locked) to telegraph the exact locked lane.
        return telegraph.from().map(from -> Math.min(at, from)).orElse(at);
    }

    @Override
    public double end() {
        return Math.max(at, until.orElseGet(() -> at + maxDistance / (speed.orElse(1.0) * 20.0) + END_MARGIN));
    }

    @Override
    public Instance start(AbilityRun run) {
        Mob mob = run.mob();
        Vec3 aim = run.aimPosition();
        Vec3 origin = mob.position();
        double dx = aim.x - origin.x;
        double dz = aim.z - origin.z;
        double toAim = Math.hypot(dx, dz);
        float yaw = toAim > MIN_AIM_DISTANCE ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : run.yaw();
        run.setYaw(yaw);
        Vec3 forward = AbilityRun.forward(yaw);
        Vec3 right = AbilityRun.right(yaw);
        DashLane lane = new DashLane(width.resolve(run), Math.min(maxDistance, toAim + overshoot));
        run.noteDash(this, new AbilityRun.DashLock(origin, yaw, lane.length(), lane.width()));
        Optional<Double> targetY = height.map(blocks -> aim.y + blocks);
        double halfDepth = mob.getBbWidth() / 2.0;
        AbilityRun.Marker marker = telegraph.show() && begin() < at
                ? drawStrip(run, origin, yaw, forward, lane, targetY.orElse(mob.getY()))
                : null;
        Set<UUID> struck = new HashSet<>();
        DashLane.Sweep<UUID> sweep = new DashLane.Sweep<>(lane, halfDepth);
        if (MobCombat.isDebug()) {
            run.combat().trace(String.format("%s: dash locked, %.1f blocks long, %.1f wide", run.name(), lane.length(),
                    lane.width()));
        }

        return new Instance() {
            private boolean started;
            private boolean stopped;
            /** The body's height span at the previous check, so contact covers its vertical move too. */
            private double bottomBefore;
            private double topBefore;
            private int moves;

            @Override
            public void tick(AbilityRun run) {
                if (stopped || run.seconds() < at) {
                    return;
                }
                double now = along(mob.position());
                AABB body = mob.getBoundingBox();
                if (!started) {
                    started = true;
                    bottomBefore = body.minY;
                    topBefore = body.maxY;
                    run.flash(marker);
                }
                sweep.check(now);
                double step = Math.min(perTick(run, lane), Math.max(0.0, lane.length() - now));
                touch(run, body, step);
                bottomBefore = body.minY;
                topBefore = body.maxY;
                // Blocks and the world border only: a dashing body passes through entities (see markDashing).
                if (moves > 0 && mob.horizontalCollision) {
                    hitWall(run, now);
                    return;
                }
                if (lane.length() - now <= 1.0e-3) {
                    halt();
                    return;
                }
                double back = Mth.clamp(-across(mob.position()), -step, step); // back onto the drawn centre line
                double climb = Math.max(step, MIN_CLIMB);
                double vy = targetY.isPresent() ? Mth.clamp(targetY.get() - mob.getY(), -climb, climb) : run.sidewaysY();
                run.moveBody(forward.x * step + right.x * back, vy, forward.z * step + right.z * back);
                run.combat().markDashing();
                moves++;
            }

            @Override
            public void finish(AbilityRun run) {
                if (!stopped && started) {
                    double now = along(mob.position());
                    AABB body = mob.getBoundingBox();
                    sweep.check(now);
                    touch(run, body, 0.0);
                }
                halt();
            }

            @Override
            public void cancel(AbilityRun run) {
                halt();
                run.clear(marker);
            }

            /**
             * Hits everyone the body met since the last check (swept, once each). Victims are tracked from
             * {@link DashLane.Sweep#LOOKAHEAD} blocks ahead of the body's front (plus the coming step), so the sweep
             * knows where they were before the body reaches them.
             */
            private void touch(AbilityRun run, AABB body, double step) {
                if (hit.isEmpty()) {
                    return;
                }
                double bottom = Math.min(bottomBefore, body.minY) - VERTICAL_SLACK;
                double top = Math.max(topBefore, body.maxY) + VERTICAL_SLACK;
                Vec3 back = origin.add(forward.scale(Math.min(sweep.bodyBefore(), sweep.bodyNow()) - halfDepth - SEARCH_MARGIN));
                Vec3 front = origin.add(forward.scale(Math.max(sweep.bodyBefore(), sweep.bodyNow()) + halfDepth + step
                        + DashLane.Sweep.LOOKAHEAD));
                double margin = lane.width() / 2.0 + SEARCH_MARGIN;
                AABB area = new AABB(Math.min(back.x, front.x) - margin, bottom - SEARCH_MARGIN,
                        Math.min(back.z, front.z) - margin, Math.max(back.x, front.x) + margin, top + SEARCH_MARGIN,
                        Math.max(back.z, front.z) + margin);
                for (LivingEntity victim : run.enemiesNear(area)) {
                    if (struck.contains(victim.getUUID())) {
                        continue;
                    }
                    Vec3 position = victim.position();
                    double across = across(position);
                    AABB box = victim.getBoundingBox();
                    boolean level = box.maxY >= bottom && box.minY <= top;
                    if (!sweep.meets(victim.getUUID(), along(position), across) || !level) {
                        continue;
                    }
                    struck.add(victim.getUUID()); // once per dash, even when a dodge's i-frames block this one
                    Vec3 from = push == Push.SIDE ? position.subtract(right.scale(DashLane.sideSign(across))) : mob.position();
                    hit.get().apply(run, victim, from);
                }
            }

            private void hitWall(AbilityRun run, double travelled) {
                halt();
                if (onWall == WallRule.STAGGER && travelled >= staggerMinDistance) {
                    run.level().sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX() + forward.x * halfDepth,
                            mob.getY() + mob.getBbHeight() * 0.5, mob.getZ() + forward.z * halfDepth, 30,
                            lane.width() / 3.0, mob.getBbHeight() / 3.0, lane.width() / 3.0, 0.02);
                    run.stagger();
                } else {
                    run.combat().trace(String.format("%s: stopped by a wall after %.1f blocks", run.name(), travelled));
                }
            }

            private double along(Vec3 point) {
                return (point.x - origin.x) * forward.x + (point.z - origin.z) * forward.z;
            }

            private double across(Vec3 point) {
                return (point.x - origin.x) * right.x + (point.z - origin.z) * right.z;
            }

            private void halt() {
                if (!stopped) {
                    stopped = true;
                    mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
                }
            }
        };
    }

    /** Blocks the body moves this tick: the constant speed, or the lane spread evenly over the run's movement ticks. */
    private double perTick(AbilityRun run, DashLane lane) {
        if (speed.isPresent()) {
            return speed.get() * run.speed();
        }
        return stepPerTick(lane.length(), end() - at, run.speed());
    }

    /**
     * Step per movement tick for an {@code until}-timed dash: floors fractional ticks (with float tolerance) so a
     * duration like 0.32 s (6.4 ticks) covers the whole lane in its 6 actual movement ticks instead of stopping short.
     */
    public static double stepPerTick(double length, double durationSeconds, double attackSpeed) {
        double moveTicks = Math.max(1.0, Math.floor(durationSeconds * 20.0 / attackSpeed + 1.0e-6));
        return length / moveTicks;
    }

    /**
     * Lays the lane on the ground: one floor sample per block along the centre line ({@link AbilityRun#floorNear},
     * the deep scan from 2 blocks above {@code scanY}, the height the body will rush at, stepping up out of terrain
     * that rises above it), cut into segments of up to {@link DashLane#MAX_SEGMENT} blocks that each lie on their own
     * floor; parts over the void are left out. With {@code arrow} the arrowhead past the end is its own zone, on the
     * highest floor of its own samples. The segments fill one after the other with the SWEEP style, from the mob
     * toward the tip.
     */
    private AbilityRun.Marker drawStrip(AbilityRun run, Vec3 origin, float yaw, Vec3 forward, DashLane lane, double scanY) {
        double[] floors = floors(run, origin, forward, 0.0, lane.length(), scanY);
        double head = telegraph.arrow() ? DashLane.headLength(lane.width()) : 0.0;
        double total = lane.length() + head;
        List<AbilityRun.Zone> zones = new ArrayList<>();
        for (DashLane.Segment segment : DashLane.segments(lane.length(), DashLane.MAX_SEGMENT, floors)) {
            Vec3 start = origin.add(forward.scale(segment.start()));
            AttackZone zone = new AttackZone.Strip((float) lane.width(), (float) segment.length(), 0.0F,
                    (float) (segment.start() / total), (float) (segment.end() / total));
            zones.add(new AbilityRun.Zone(new Vec3(start.x, segment.floor(), start.z), yaw, zone));
        }
        double headFloor = head > 0.0 ? DashLane.headFloor(floors(run, origin, forward, lane.length(), head, scanY)) : Double.NaN;
        if (!Double.isNaN(headFloor)) {
            Vec3 tip = origin.add(forward.scale(lane.length()));
            AttackZone zone = new AttackZone.Strip((float) lane.width(), 0.0F, (float) head,
                    (float) (lane.length() / total), 1.0F);
            zones.add(new AbilityRun.Zone(new Vec3(tip.x, headFloor, tip.z), yaw, zone));
        }
        return run.telegraph(zones, telegraph, at);
    }

    /** Floor samples along the centre line, one per started block of {@code length} from {@code from} (NaN = none). */
    private static double[] floors(AbilityRun run, Vec3 origin, Vec3 forward, double from, double length, double scanY) {
        int count = DashLane.samples(length);
        double[] floors = new double[count];
        for (int k = 0; k < count; k++) {
            Vec3 point = origin.add(forward.scale(from + DashLane.sampleAt(k, length)));
            floors[k] = run.floorNear(new Vec3(point.x, scanY, point.z)).map(Vec3::y).orElse(Double.NaN);
        }
        return floors;
    }
}
