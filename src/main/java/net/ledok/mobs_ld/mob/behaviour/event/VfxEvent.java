package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.BeamLane;
import net.ledok.mobs_ld.mob.combat.PaladinBarriers;
import net.ledok.mobs_ld.network.ModNetworking;
import net.ledok.mobs_ld.network.SpellVfxKind;
import net.ledok.mobs_ld.network.SpellVfxPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom client-side 3D spell visual effect triggered at {@code at}.
 *
 * <p>Designed for dedicated-server efficiency: the server spawns no entities and performs no per-tick work for the
 * visual. Instead it resolves the anchor once and sends a single compact {@link SpellVfxPayload} to nearby clients,
 * which render smooth 3D geometry and local particles on their own. When {@code windup > 0} and the ability is
 * interrupted before impact ({@code at + windup}), a 4-byte cancel packet tells clients to fade the charge out cleanly.
 *
 * <p>With {@code origin: "locked"}, the effect binds to the locked hit or grab zones, dash corridor, or leap landing
 * whose impact / start occurs at {@code at + windup}, inheriting their exact locked
 * position, facing, and default dimensions (radius, length, width, angle, ray count) even when their flat ground
 * telegraphs are hidden ({@code "telegraph": { "show": false, "from": ... }}).
 * An interceptable beam or a leap sends its impact separately so the effect follows the actual hit or landing.
 */
public record VfxEvent(
        double at,
        SpellVfxKind kind,
        VfxOrigin origin,
        Placement placement,
        double windup,
        double duration,
        Colors colors,
        Dimensions dimensions
) implements AbilityEvent {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    /** Hard cap on packets emitted by a single locked each_fighter VFX trigger, protecting raid bandwidth. */
    private static final int MAX_LOCKED_SPAWNS = 16;

    public static final int DEFAULT_PRIMARY_COLOR = 0xF08C00;
    public static final int DEFAULT_SECONDARY_COLOR = 0xFFF3B0;

    public static final MapCodec<VfxEvent> CODEC = RecordCodecBuilder.<VfxEvent>mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(VfxEvent::at),
            SpellVfxKind.CODEC.fieldOf("kind").forGetter(VfxEvent::kind),
            VfxOrigin.CODEC.optionalFieldOf("origin", VfxOrigin.SELF).forGetter(VfxEvent::origin),
            Placement.CODEC.forGetter(VfxEvent::placement),
            Codec.doubleRange(0.0, 30.0).optionalFieldOf("windup", 0.0).forGetter(VfxEvent::windup),
            Codec.doubleRange(0.05, 30.0).fieldOf("duration").forGetter(VfxEvent::duration),
            Colors.CODEC.forGetter(VfxEvent::colors),
            Dimensions.CODEC.forGetter(VfxEvent::dimensions)
    ).apply(i, VfxEvent::new)).validate(VfxEvent::validate);

    private static DataResult<VfxEvent> validate(VfxEvent event) {
        if (event.at < 0.0) {
            return DataResult.error(() -> "A vfx event's 'at' (" + event.at + ") cannot be negative");
        }
        if (event.duration <= event.windup) {
            return DataResult.error(() -> "A vfx event's 'duration' (" + event.duration
                    + ") must be longer than its 'windup' (" + event.windup + ")");
        }
        return DataResult.success(event);
    }

    /** Where the 3D effect is anchored. */
    public enum VfxOrigin implements StringRepresentable {
        /** Anchored to the mob's ground position + local offsets. */
        SELF("self"),
        /** Anchored to the ability's aim position + local offsets. */
        TARGET("target"),
        /**
         * Anchored to every locked {@code hit} zone or {@code dash} corridor whose impact / start is at
         * {@code at + windup}, inheriting its exact position, facing, and default shape dimensions.
         */
        LOCKED("locked");

        public static final Codec<VfxOrigin> CODEC = StringRepresentable.fromEnum(VfxOrigin::values);
        private final String name;

        VfxOrigin(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Spatial anchor offsets and optional facing rotation (flattened into the event JSON object). */
    public record Placement(double offset, double side, double up, Optional<Double> rotate) {
        public static final Placement DEFAULT = new Placement(0.0, 0.0, 0.0, Optional.empty());
        public static final MapCodec<Placement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("offset", 0.0).forGetter(Placement::offset),
                Codec.DOUBLE.optionalFieldOf("side", 0.0).forGetter(Placement::side),
                Codec.DOUBLE.optionalFieldOf("up", 0.0).forGetter(Placement::up),
                Codec.doubleRange(-360.0, 360.0).optionalFieldOf("rotate").forGetter(Placement::rotate)
        ).apply(i, Placement::new));
    }

    /** Primary outer flame / energy colour and hot core / accent colour (flattened into the event JSON object). */
    public record Colors(int primary, int secondary) {
        public static final Colors DEFAULT = new Colors(DEFAULT_PRIMARY_COLOR, DEFAULT_SECONDARY_COLOR);
        public static final MapCodec<Colors> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Telegraph.HEX.optionalFieldOf("color", DEFAULT_PRIMARY_COLOR).forGetter(Colors::primary),
                Telegraph.HEX.optionalFieldOf("secondary_color", DEFAULT_SECONDARY_COLOR).forGetter(Colors::secondary)
        ).apply(i, Colors::new));
    }

    /**
     * Optional geometric parameters (flattened into the event JSON object). Omitted values inherit from the matched
     * locked hit/dash shape when {@code origin: "locked"}, or fall back to clean archetype defaults.
     */
    public record Dimensions(
            Optional<Double> radius,
            Optional<Double> length,
            Optional<Double> width,
            Optional<Double> height,
            Optional<Double> angle,
            Optional<Integer> count,
            double tilt,
            boolean reverse,
            boolean particles
    ) {
        public static final Dimensions DEFAULT = new Dimensions(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0.0, false, true);

        public static final MapCodec<Dimensions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.doubleRange(0.1, 64.0).optionalFieldOf("radius").forGetter(Dimensions::radius),
                Codec.doubleRange(0.1, 64.0).optionalFieldOf("length").forGetter(Dimensions::length),
                Codec.doubleRange(0.0, 64.0).optionalFieldOf("width").forGetter(Dimensions::width),
                Codec.doubleRange(0.1, 64.0).optionalFieldOf("height").forGetter(Dimensions::height),
                Codec.doubleRange(1.0, 360.0).optionalFieldOf("angle").forGetter(Dimensions::angle),
                Codec.intRange(1, 24).optionalFieldOf("count").forGetter(Dimensions::count),
                Codec.doubleRange(-180.0, 180.0).optionalFieldOf("tilt", 0.0).forGetter(Dimensions::tilt),
                Codec.BOOL.optionalFieldOf("reverse", false).forGetter(Dimensions::reverse),
                Codec.BOOL.optionalFieldOf("particles", true).forGetter(Dimensions::particles)
        ).apply(i, Dimensions::new));
    }

    @Override
    public String type() {
        return "vfx";
    }

    @Override
    public double begin() {
        return at;
    }

    @Override
    public double end() {
        // Stays active only through the windup window (zero tick work) so cancelling an interrupted ability can clear
        // charging visuals before impact. Impact effects (windup == 0) complete immediately.
        return at + windup;
    }

    @Override
    public Instance start(AbilityRun run) {
        List<SentVfx> sent = new ArrayList<>();
        OptionalDouble confirmedImpact = confirmedImpact(run);
        boolean resolveImpact = confirmedImpact.isPresent();
        Runnable emit = () -> emitAll(run, sent, resolveImpact ? Stage.WINDUP : Stage.FULL);
        if (origin == VfxOrigin.LOCKED) {
            // Defer until the end of this tick so HitEvent / DashEvent at the same beat have already locked their zones
            // regardless of JSON ordering.
            if (!resolveImpact || windup > 0.0) {
                run.afterEvents(emit);
            }
        } else {
            emit.run();
        }
        return new Instance() {
            @Override
            public boolean readyToFinish(AbilityRun run, double scheduledEnd) {
                return Instance.super.readyToFinish(run, confirmedImpact.orElse(scheduledEnd));
            }

            @Override
            public void finish(AbilityRun run) {
                if (resolveImpact) {
                    run.afterEvents(() -> emitAll(run, new ArrayList<>(), Stage.IMPACT));
                }
            }

            @Override
            public void cancel(AbilityRun run) {
                if (windup > 0.0) {
                    cancelSent(run, sent);
                }
            }
        };
    }

    private OptionalDouble confirmedImpact(AbilityRun run) {
        if (origin != VfxOrigin.LOCKED) {
            return OptionalDouble.empty();
        }
        boolean barriers = PaladinBarriers.available();
        return run.ability().events().stream()
                .filter(event -> event instanceof LeapEvent
                        || (barriers && event instanceof HitEvent hit && hit.blockedByBarrier()))
                .mapToDouble(AbilityEvent::end)
                .filter(impact -> FxEvent.sameMoment(impact, end()))
                .max();
    }

    private static void cancelSent(AbilityRun run, List<SentVfx> sent) {
        for (SentVfx item : sent) {
            ModNetworking.sendVfxCancel(run.level(), item.origin(), item.id());
        }
        sent.clear();
    }

    private void emitAll(AbilityRun run, List<SentVfx> sent, Stage stage) {
        if (run.cutOff()) {
            return;
        }
        double speed = Math.max(0.1, run.speed());
        float realWindup = stage == Stage.IMPACT ? 0.0F : (float) (windup / speed);
        float realDuration = (float) (switch (stage) {
            case FULL -> duration;
            case WINDUP -> windup;
            case IMPACT -> duration - windup;
        } / speed);
        Mob mob = run.mob();
        ServerLevel level = run.level();
        int sourceId = mob.getId();

        if (origin == VfxOrigin.LOCKED) {
            List<ResolvedAnchor> anchors = resolveLocked(run, stage);
            if (!anchors.isEmpty() || stage != Stage.FULL) {
                int emitted = 0;
                for (ResolvedAnchor anchor : anchors) {
                    if (emitted >= MAX_LOCKED_SPAWNS) {
                        break;
                    }
                    broadcast(level, sourceId, anchor, realWindup, realDuration, sent, stage);
                    emitted++;
                }
                if (emitted > 0 && stage != Stage.IMPACT) {
                    watchLockedVfx(run, sent);
                }
                return;
            }
        }

        Vec3 base = origin == VfxOrigin.TARGET ? run.aimPosition() : mob.position();
        float yaw = placement.rotate()
                .map(deg -> Mth.wrapDegrees(run.lockedYaw() + deg.floatValue()))
                .orElse(run.yaw());
        Vec3 pos = applyPlacement(run.groundAt(base), yaw);
        ResolvedAnchor anchor = new ResolvedAnchor(pos, yaw,
                dimensions.radius().orElse(defaultRadius()),
                dimensions.length().orElse(defaultLength()),
                dimensions.width().orElse(defaultWidth()),
                dimensions.height().orElse(defaultHeight()),
                dimensions.angle().orElse(defaultAngle()),
                dimensions.count().orElse(defaultCount()), false);
        broadcast(level, sourceId, anchor, realWindup, realDuration, sent, stage);
    }

    private void watchLockedVfx(AbilityRun run, List<SentVfx> sent) {
        boolean barrierRoll = kind == SpellVfxKind.ROLL_TRAIL && PaladinBarriers.available();
        for (AbilityEvent event : run.ability().events()) {
            if (event instanceof LeapEvent leap && FxEvent.sameMoment(leap.end(), end())
                    && run.lockedLeap(leap).isPresent()) {
                run.onLockedVfxCancel(leap, () -> cancelSent(run, sent));
            } else if (barrierRoll && event instanceof DashEvent dash && dash.onBarrier().isPresent()
                    && FxEvent.sameMoment(dash.at(), end())) {
                run.onLockedVfxCancel(dash, () -> cancelSent(run, sent));
            }
        }
    }

    private void broadcast(ServerLevel level, int sourceId, ResolvedAnchor anchor, float realWindup, float realDuration,
                           List<SentVfx> sent, Stage stage) {
        int id = NEXT_ID.incrementAndGet();
        int flags = SpellVfxPayload.packFlags(dimensions.reverse(), dimensions.particles());
        if (stage == Stage.WINDUP) {
            flags |= SpellVfxPayload.FLAG_WINDUP_ONLY;
        }
        if (anchor.lockedBeam) {
            flags |= SpellVfxPayload.FLAG_LOCKED_BEAM;
        }
        SpellVfxPayload payload = new SpellVfxPayload(
                id,
                sourceId,
                kind,
                anchor.pos.x,
                anchor.pos.y,
                anchor.pos.z,
                anchor.yaw,
                colors.primary(),
                colors.secondary(),
                realWindup,
                realDuration,
                (float) anchor.radius,
                (float) anchor.length,
                (float) anchor.width,
                (float) anchor.height,
                (float) anchor.angle,
                (float) dimensions.tilt(),
                (byte) Mth.clamp(anchor.count, 1, 24),
                (byte) flags
        );
        ModNetworking.sendVfx(level, anchor.pos, payload);
        sent.add(new SentVfx(id, anchor.pos));
    }

    /**
     * Collects locked zones matching {@code at + windup}, inheriting their world position, facing, and shape.
     */
    private List<ResolvedAnchor> resolveLocked(AbilityRun run, Stage stage) {
        double impact = at + windup;
        List<ResolvedAnchor> anchors = new ArrayList<>();
        for (AbilityEvent event : run.ability().events()) {
            if (event instanceof HitEvent hit && FxEvent.sameMoment(hit.at(), impact)) {
                for (AbilityRun.HitZone zone : run.lockedZones(hit)) {
                    List<BeamLane> clipped = stage == Stage.IMPACT ? run.interceptedBeam(zone) : List.of();
                    if (clipped.isEmpty()) {
                        anchors.add(fromHitZone(zone));
                    } else {
                        for (BeamLane lane : clipped) {
                            if (lane.length() > 0.0) {
                                anchors.add(fromBeamLane(lane));
                            }
                        }
                    }
                }
            } else if (event instanceof GrabEvent grab && FxEvent.sameMoment(grab.at(), impact)) {
                run.lockedGrab(grab).ifPresent(zone -> anchors.add(fromHitZone(zone)));
            } else if (event instanceof LeapEvent leap && FxEvent.sameMoment(leap.end(), impact)) {
                run.lockedLeap(leap).ifPresent(zone -> anchors.add(fromHitZone(zone)));
            } else if (event instanceof DashEvent dash && FxEvent.sameMoment(dash.at(), impact)) {
                run.lockedDash(dash).ifPresent(lock -> anchors.add(fromDashLock(run, lock)));
            }
        }
        return anchors;
    }

    private ResolvedAnchor fromBeamLane(BeamLane lane) {
        Vec3 pos = lane.origin().add(0, placement.up(), 0);
        return new ResolvedAnchor(pos, lane.yaw(), lane.length(), lane.length(), lane.width(),
                dimensions.height().orElse(defaultHeight()), dimensions.angle().orElse(defaultAngle()), 1, true);
    }

    private ResolvedAnchor fromHitZone(AbilityRun.HitZone zone) {
        float yaw = placement.rotate()
                .map(deg -> Mth.wrapDegrees(zone.yaw() + deg.floatValue()))
                .orElse(zone.yaw());
        double extraOffset = 0.0;
        double radius = defaultRadius();
        double length = defaultLength();
        double width = defaultWidth();
        double angle = defaultAngle();
        int count = defaultCount();

        Shape shape = zone.shape();
        if (shape instanceof Shape.Circle circle) {
            radius = circle.radius();
            length = circle.radius();
        } else if (shape instanceof Shape.Ring ring) {
            width = ring.inner();
            radius = ring.outer();
            length = ring.outer();
        } else if (shape instanceof Shape.Cone cone) {
            radius = cone.radius();
            length = cone.radius();
            angle = cone.angle();
        } else if (shape instanceof Shape.Rectangle rect) {
            width = rect.width();
            length = rect.length();
            radius = rect.length();
            extraOffset = placement.offset() == 0.0 ? rect.offset() : 0.0;
        } else if (shape instanceof Shape.Rays rays) {
            count = rays.count();
            length = rays.length();
            width = rays.width();
            radius = rays.length();
            angle = 360.0;
        }

        Vec3 base = zone.origin().add(AbilityRun.forward(yaw).scale(extraOffset));
        Vec3 pos = applyPlacement(base, yaw);
        return new ResolvedAnchor(
                pos,
                yaw,
                dimensions.radius().orElse(radius),
                dimensions.length().orElse(length),
                dimensions.width().orElse(width),
                dimensions.height().orElse(defaultHeight()),
                dimensions.angle().orElse(angle),
                dimensions.count().orElse(count),
                false
        );
    }

    private ResolvedAnchor fromDashLock(AbilityRun run, AbilityRun.DashLock lock) {
        float yaw = placement.rotate()
                .map(deg -> Mth.wrapDegrees(lock.yaw() + deg.floatValue()))
                .orElse(lock.yaw());
        Vec3 pos = applyPlacement(run.groundAt(lock.origin()), yaw);
        return new ResolvedAnchor(
                pos,
                yaw,
                dimensions.radius().orElse(lock.width()),
                dimensions.length().orElse(lock.length()),
                dimensions.width().orElse(lock.width()),
                dimensions.height().orElse(defaultHeight()),
                dimensions.angle().orElse(defaultAngle()),
                dimensions.count().orElse(defaultCount()),
                false
        );
    }

    private Vec3 applyPlacement(Vec3 base, float yaw) {
        return base.add(AbilityRun.forward(yaw).scale(placement.offset()))
                .add(AbilityRun.right(yaw).scale(placement.side()))
                .add(0.0, placement.up(), 0.0);
    }

    private double defaultRadius() {
        return switch (kind) {
            case EYE_CHARGE -> 1.4;
            case METEOR_STRIKE -> 2.8;
            case NOVA_DOME -> 6.0;
            case SHOCKWAVE_RING -> 10.0;
            case FLAME_PILLAR -> 2.4;
            case AMBER_SPIRE -> 1.8;
            case AMBER_GEODE_TRAP -> 2.3;
            case GROUND_FRACTURE -> 3.2;
            case RESONANCE_RING -> 7.5;
            case CLAW_RAKE -> 3.4;
            case ROOT_TENDRILS -> 2.5;
            case BURROW_MOUND -> 4.5;
            case DRUM_TREMOR -> 4.2;
            case RIB_FURNACE -> 6.8;
            case MADNESS_GRASP -> 4.4;
            case SPEAR_ARC -> 5.2;
            case LIGHTNING_BOLT -> 3.0;
            case STORM_GALE -> 10.0;
            case TEMPEST_CYCLONE -> 6.2;
            case THUNDER_SPEAR_DIVE -> 4.2;
            case STORM_NOVA -> 7.5;
            default -> 4.8;
        };
    }

    private double defaultLength() {
        return switch (kind) {
            case SWEEPING_BEAM -> 26.0;
            case LANCE_LUNGE, FLAME_WAVE -> 12.0;
            case MAGMA_FISSURES -> 14.0;
            case SHARD_SPRAY -> 8.0;
            case CINDER_ERUPTION -> 20.0;
            case ROLL_TRAIL -> 15.0;
            case STORM_GALE -> 12.0;
            case THUNDER_SPEAR_DIVE -> 15.0;
            default -> 6.0;
        };
    }

    private double defaultWidth() {
        return switch (kind) {
            case SHOCKWAVE_RING, RESONANCE_RING -> 3.5;
            case SWEEPING_BEAM -> 1.4;
            case CINDER_ERUPTION -> 3.0;
            case ROLL_TRAIL -> 2.8;
            case THUNDER_SPEAR_DIVE -> 2.6;
            default -> 2.0;
        };
    }

    private double defaultHeight() {
        return switch (kind) {
            case METEOR_STRIKE -> 14.0;
            case FLAME_PILLAR -> 7.5;
            case MAGMA_FISSURES -> 3.4;
            case FLAME_WAVE -> 3.2;
            case SHOCKWAVE_RING -> 2.6;
            case SWEEPING_BEAM -> 1.1;
            case SLASH_ARC -> 1.6;
            case AMBER_SPIRE -> 3.1;
            case AMBER_GEODE_TRAP -> 2.4;
            case GROUND_FRACTURE -> 1.1;
            case SHARD_SPRAY -> 2.4;
            case RESONANCE_RING -> 2.5;
            case ROOT_TENDRILS -> 2.8;
            case CINDER_ERUPTION -> 2.4;
            case ROLL_TRAIL -> 1.4;
            case BURROW_MOUND -> 1.6;
            case DRUM_TREMOR -> 1.1;
            case RIB_FURNACE -> 3.4;
            case MADNESS_GRASP -> 2.4;
            case SPEAR_ARC -> 1.9;
            case LIGHTNING_BOLT -> 15.0;
            case STORM_GALE -> 2.8;
            case TEMPEST_CYCLONE -> 4.2;
            case THUNDER_SPEAR_DIVE -> 7.0;
            case STORM_NOVA -> 3.6;
            default -> 2.5;
        };
    }

    private double defaultAngle() {
        return switch (kind) {
            case SWEEPING_BEAM -> 50.0;
            case FLAME_WAVE -> 95.0;
            case SHARD_SPRAY -> 85.0;
            case CLAW_RAKE -> 110.0;
            case MADNESS_GRASP -> 80.0;
            case SPEAR_ARC -> 120.0;
            case STORM_GALE -> 95.0;
            default -> 125.0;
        };
    }

    private int defaultCount() {
        return switch (kind) {
            case MAGMA_FISSURES -> 6;
            case EYE_CHARGE -> 3;
            case GROUND_FRACTURE -> 6;
            case SHARD_SPRAY -> 9;
            case RESONANCE_RING -> 12;
            case AMBER_GEODE_TRAP -> 4;
            case ROOT_TENDRILS -> 6;
            case CINDER_ERUPTION -> 1;
            case BURROW_MOUND -> 8;
            case DRUM_TREMOR -> 8;
            case RIB_FURNACE -> 10;
            case MADNESS_GRASP -> 5;
            case LIGHTNING_BOLT -> 3;
            case STORM_GALE -> 5;
            case TEMPEST_CYCLONE -> 6;
            case THUNDER_SPEAR_DIVE -> 4;
            case STORM_NOVA -> 8;
            default -> 4;
        };
    }

    private record SentVfx(int id, Vec3 origin) {
    }

    private enum Stage { FULL, WINDUP, IMPACT }

    private record ResolvedAnchor(
            Vec3 pos,
            float yaw,
            double radius,
            double length,
            double width,
            double height,
            double angle,
            int count,
            boolean lockedBeam
    ) {
    }
}
