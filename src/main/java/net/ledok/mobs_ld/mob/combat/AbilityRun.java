package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitEvent;
import net.ledok.mobs_ld.mob.behaviour.event.Telegraph;
import net.ledok.mobs_ld.mob.behaviour.event.VulnerableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One ability in flight: its clock (scaled by attack speed), aim, facing, and the state of each timeline event.
 * Until {@code track_until} the mob keeps turning toward its aim; after that aim position and facing are locked, so
 * telegraphs drawn from then on show exactly where the hits will land.
 */
public final class AbilityRun {
    /** Degrees per tick a mob may turn while tracking its aim; big bodies should not snap around. */
    private static final float TRACK_TURN_RATE = 12.0F;
    /** Seconds before a beat with its own {@code aim_side} lands that the body starts turning for it. */
    public static final double AIM_LEAD = 0.3;

    private final Mob mob;
    private final MobCombat combat;
    private final String name;
    private final MobBehaviour.Ability ability;
    private final double speed;
    private final double trackUntil;
    private final LivingEntity aim;
    private final List<Slot> slots = new ArrayList<>();
    private final List<Marker> markers = new ArrayList<>();
    /**
     * Where each {@code hit} event's zones locked (when its telegraph appeared; one per fighter for
     * {@code origin: each_fighter}), for particles drawn along them.
     */
    private final Map<HitEvent, List<HitZone>> hitZones = new IdentityHashMap<>();
    /** Victims a {@code once} hit of this run has already struck (see {@link #strikeOnce}). */
    private final Set<UUID> struckOnce = new HashSet<>();
    /** Work left for the end of this tick, once every event due now has run (see {@link #afterEvents}). */
    private final List<Runnable> afterEvents = new ArrayList<>();

    private final AbilityClock clock;
    private float yaw;
    /** The facing when tracking ended (while it still tracks: now); {@code hit.rotate} turns zones from it. */
    private float lockedYaw;
    private Vec3 aimPosition;
    private boolean cancelled;
    private boolean staggered;
    private String chainTo;
    /** Whom the chained-into ability must aim at instead of the usual rule (a {@code listen}'s first offender). */
    private LivingEntity chainAim;
    /** A {@code chain} with {@code taunt_wins}: a taunt lock on the mob beats the kept aim. */
    private boolean chainTauntWins;
    /** Facing an event asked to turn to (re-aim between beats); approached at {@link #steerRate}. */
    private Float steerYaw;
    /** Degrees per tick toward {@link #steerYaw}: the tracking rate, or a timed turn's ({@link #turnBody}). */
    private float steerRate = TRACK_TURN_RATE;
    /** The ability's own clip, sent on the run's first tick (see {@link #openWith}); null once sent, or none. */
    private String openingClip;

    AbilityRun(Mob mob, MobCombat combat, String name, MobBehaviour.Ability ability, LivingEntity aim, double speed) {
        this.mob = mob;
        this.combat = combat;
        this.name = name;
        this.ability = ability;
        this.aim = aim;
        this.speed = speed;
        this.clock = new AbilityClock(speed);
        this.yaw = mob.getYRot();
        this.aimPosition = aim != null ? aim.position() : mob.position();
        double firstBegin = ability.duration();
        for (AbilityEvent event : ability.events()) {
            slots.add(new Slot(event));
            firstBegin = Math.min(firstBegin, event.begin());
        }
        this.trackUntil = ability.trackUntil().orElse(ability.events().isEmpty() ? 0.0 : firstBegin);
        if (aim != null) {
            yaw = aimedYaw(aim.position(), ability.aimSide());
        }
        lockedYaw = yaw;
    }

    /** Advances one server tick; returns false once the ability is over. */
    boolean tick() {
        if (cancelled) {
            return false;
        }
        if (openingClip != null) {
            String clip = openingClip;
            openingClip = null;
            ((CombatHost) mob).mobs_ld$playAction(clip, speed);
        }
        double seconds = seconds();
        if (seconds < trackUntil && aim != null && aim.isAlive()) {
            aimPosition = aim.position();
            yaw = Mth.approachDegrees(yaw, aimedYaw(aimPosition, ability.aimSide()), TRACK_TURN_RATE);
            lockedYaw = yaw;
        } else if (steerYaw != null) {
            yaw = Mth.approachDegrees(yaw, steerYaw, steerRate);
        }
        face(yaw);
        if (!mob.isDeadOrDying()) {
            mob.getNavigation().stop();
        }
        for (Slot slot : slots) {
            slot.tick(this, seconds);
            if (cancelled) {
                afterEvents.clear();
                return false;
            }
        }
        if (!afterEvents.isEmpty()) {
            List<Runnable> due = List.copyOf(afterEvents);
            afterEvents.clear();
            due.forEach(Runnable::run);
        }
        for (Marker marker : markers) {
            marker.update(this);
        }
        clock.advance();
        // Over only once the clip time has passed AND every event has run: an event may start at (or after) the
        // ability's duration, e.g. a chase that begins right as the wind-up clip ends.
        return seconds() < ability.duration() || slots.stream().anyMatch(slot -> slot.state != State.DONE);
    }

    /**
     * Sends {@code clip} (the ability's own) on the run's first tick, the tick its timeline reads 0, wherever the run
     * was started from (a goal, the fight tick, a chain, a command): a clip-timed hit at 1.7 s then lands exactly
     * 34 ticks after its clip began.
     */
    void openWith(String clip) {
        openingClip = clip;
    }

    /** Interrupts the ability: active events clean up (telegraphs vanish, dashes stop). */
    void cancel() {
        if (cancelled) {
            return;
        }
        cancelled = true;
        for (Slot slot : slots) {
            if (slot.state == State.ACTIVE) {
                slot.instance.cancel(this);
            }
        }
        for (Marker marker : List.copyOf(markers)) {
            clear(marker);
        }
    }

    /**
     * Drops the run without touching the world (the mob is leaving with its chunk): no event cleanup, nothing
     * discarded. Returns the telegraphs still drawn, for the caller to remove once that is safe.
     */
    List<AttackZoneDisplay> abandon() {
        cancelled = true;
        List<AttackZoneDisplay> displays = new ArrayList<>();
        for (Marker marker : markers) {
            displays.addAll(marker.displays);
        }
        markers.clear();
        return displays;
    }

    // --- For events ---

    public Mob mob() {
        return mob;
    }

    public MobCombat combat() {
        return combat;
    }

    public ServerLevel level() {
        return (ServerLevel) mob.level();
    }

    public String name() {
        return name;
    }

    public MobBehaviour.Ability ability() {
        return ability;
    }

    /** Seconds since the ability started, on its own clock (faster when the phase speeds attacks up). */
    public double seconds() {
        return clock.seconds();
    }

    public double speed() {
        return speed;
    }

    /** Current facing in degrees; fixed after the tracking window. */
    public float yaw() {
        return yaw;
    }

    /**
     * The facing the ability locked when its tracking window ended (the current one while it still tracks); later
     * turns (re-aims, {@code turn_body}) do not move it. {@code hit.rotate} is measured from it.
     */
    public float lockedYaw() {
        return lockedYaw;
    }

    /** Who the ability aims at; may be null (e.g. a transition with nobody around). */
    public LivingEntity aim() {
        return aim;
    }

    /** Where the aim stood when tracking ended (or now, while still tracking). */
    public Vec3 aimPosition() {
        return aimPosition;
    }

    /** Ends the ability and puts the mob into its stagger window (if the behaviour defines one). */
    public void stagger() {
        combat.trace(name + ": hit a wall at " + String.format("%.1f %.1f %.1f", mob.getX(), mob.getY(), mob.getZ()));
        staggered = true;
        cancel();
    }

    boolean wasStaggered() {
        return staggered;
    }

    /** Ends this ability and starts the named one right away, with the same aim (e.g. a charge into its finisher). */
    public void chain(String abilityName) {
        chain(abilityName, null, false);
    }

    /**
     * Ends this ability and starts the named one right away. {@code aimAt} (if alive then) is its aim whatever its
     * own rule says; else it aims as a plain chain does, except that with {@code tauntWins} a taunt lock on the mob
     * beats a kept aim.
     */
    public void chain(String abilityName, LivingEntity aimAt, boolean tauntWins) {
        LivingEntity next = aimAt != null ? aimAt : aim;
        combat.trace(name + ": chaining into " + abilityName + (next != null
                ? String.format(" at %.1f blocks", CombatTargets.distance(mob, next)) : " (no aim)"));
        chainTo = abilityName;
        chainAim = aimAt;
        chainTauntWins = tauntWins;
        cancel();
    }

    String chainTo() {
        return chainTo;
    }

    /** The aim a chain asked for explicitly (see {@link #chain(String, LivingEntity, boolean)}), else null. */
    LivingEntity chainAim() {
        return chainAim;
    }

    boolean chainTauntWins() {
        return chainTauntWins;
    }

    /** Whether the run was cut off or dropped (a plain run that played out to its end reads false). */
    public boolean cutOff() {
        return cancelled;
    }

    /**
     * For a {@code hit} with {@code once}: true the first time {@code victim} is struck by such a hit in this run,
     * false after (the Tremor Line's circles overlap, but each player takes one).
     */
    public boolean strikeOnce(LivingEntity victim) {
        return struckOnce.add(victim.getUUID());
    }

    /**
     * Turns (at the tracking rate, not a snap) so a strike line {@code side} blocks to the mob's right meets the aim
     * where it stands now; returns the facing it is turning to, for telegraphs that must be drawn where it will hit.
     */
    public float reaim(double side) {
        if (aim == null || !aim.isAlive()) {
            return yaw;
        }
        steerYaw = aimFor(side);
        steerRate = TRACK_TURN_RATE;
        return steerYaw;
    }

    /**
     * The facing that would put a strike line {@code side} blocks to the mob's right through the aim where it stands
     * now (which also becomes {@link #aimPosition()}), without turning; pair with {@link #steer} to turn later.
     */
    public float aimFor(double side) {
        if (aim == null || !aim.isAlive()) {
            return yaw;
        }
        aimPosition = aim.position();
        return aimedYaw(aimPosition, side);
    }

    /** Turns toward {@code degrees} at the tracking rate (not a snap), e.g. into a telegraph drawn earlier. */
    public void steer(float degrees) {
        steerYaw = degrees;
        steerRate = TRACK_TURN_RATE;
    }

    /**
     * Turns the body to {@code degrees} evenly over {@code seconds} of the ability's clock (a {@code turn_body} hit:
     * the Warden turning 22.5 degrees to its second wave). No turn left = nothing to do.
     */
    public void turnBody(float degrees, double seconds) {
        float left = Math.abs(Mth.wrapDegrees(degrees - yaw));
        if (left < 1.0e-3F) {
            return;
        }
        double ticks = Math.max(1.0, seconds * 20.0 / speed);
        steerYaw = degrees;
        steerRate = (float) (left / ticks);
    }

    /**
     * Runs {@code work} at the end of this tick, after every event due now has started and fired (e.g. particles
     * along the zones of hits that land on the same beat, whatever order the events are listed in). Dropped if the
     * ability is cancelled first.
     */
    public void afterEvents(Runnable work) {
        afterEvents.add(work);
    }

    /** A {@code hit} event locked its zones: kept for {@link #lockedZones}. */
    public void noteZones(HitEvent event, List<HitZone> zones) {
        hitZones.put(event, List.copyOf(zones));
    }

    /** Where {@code event}'s zones locked; empty if it has not started (yet) in this run. */
    public List<HitZone> lockedZones(HitEvent event) {
        return hitZones.getOrDefault(event, List.of());
    }

    /**
     * Damage taken multiplier from the {@code vulnerable} windows open now (1 = none): every such event between its
     * {@code at} and {@code until}.
     */
    public double damageTakenMultiplier() {
        double multiplier = 1.0;
        for (Slot slot : slots) {
            if (slot.state == State.ACTIVE && slot.event instanceof VulnerableEvent window) {
                multiplier *= window.multiplier();
            }
        }
        return multiplier;
    }

    /** Lets an event steer the mob (e.g. a homing chase); the run keeps facing this way. */
    public void setYaw(float degrees) {
        yaw = degrees;
    }

    /**
     * Enemies standing in {@code shape} whose feet are at most {@code height} above and {@code depth} below the
     * origin, i.e. within the mob's reach band unless the event says otherwise.
     */
    public List<LivingEntity> enemiesIn(Shape shape, Vec3 origin, float yawDegrees, double height, double depth) {
        double reach = shape.reach() + 1.0;
        AABB box = new AABB(origin.x - reach, origin.y - depth - 2.0, origin.z - reach,
                origin.x + reach, origin.y + height + 0.5, origin.z + reach);
        List<LivingEntity> inside = new ArrayList<>();
        for (LivingEntity candidate : enemiesNear(box)) {
            double dy = candidate.getY() - origin.y;
            if (dy > height || dy < -depth) {
                continue;
            }
            if (inside(shape, candidate.getX() - origin.x, candidate.getZ() - origin.z, yawDegrees)) {
                inside.add(candidate);
            }
        }
        return inside;
    }

    public List<LivingEntity> enemiesNear(AABB box) {
        return level().getEntitiesOfClass(LivingEntity.class, box, candidate -> CombatTargets.isEnemy(mob, candidate));
    }

    /**
     * Ground level under a point, so telegraphs lie on the floor even when the mob is mid-step or airborne; the point
     * itself when there is no floor within reach (see {@link #groundBelow}).
     */
    public Vec3 groundAt(Vec3 point) {
        return groundBelow(point).orElse(point);
    }

    /**
     * The first floor under a point, scanned from 2 blocks above it (so a zone on a slope finds the step it is on).
     * See {@link #groundBelow(Vec3, int)}.
     */
    public Optional<Vec3> groundBelow(Vec3 point) {
        return groundBelow(point, 2);
    }

    /**
     * The first floor under a point: scanned block by block from {@code fromAbove} blocks above it down
     * {@code movement.ground_scan} blocks (no heightmap, which would find the Nether roof). The surface of a lake or a
     * lava sea below the point counts as floor (not its bed); a point under the surface (a mob wading or swimming)
     * still gets the bed. Empty over the void or a drop deeper than the scan, so callers can skip a zone or spot that
     * has nothing to lie on.
     */
    public Optional<Vec3> groundBelow(Vec3 point, int fromAbove) {
        ServerLevel level = level();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(Mth.floor(point.x), 0, Mth.floor(point.z));
        int top = Mth.floor(point.y) + fromAbove;
        int bottom = Math.max(level.getMinBuildHeight(), top - combat.movement().groundScan());
        boolean fluidAbove = !level.getFluidState(cursor.setY(top + 1)).isEmpty();
        for (int y = top; y >= bottom; y--) {
            cursor.setY(y);
            double surface = Double.NEGATIVE_INFINITY;
            VoxelShape collision = level.getBlockState(cursor).getCollisionShape(level, cursor);
            if (!collision.isEmpty()) {
                surface = y + collision.max(Direction.Axis.Y);
            }
            FluidState fluid = level.getFluidState(cursor);
            if (!fluid.isEmpty() && !fluidAbove) {
                double fluidTop = y + fluid.getHeight(level, cursor);
                if (fluidTop <= point.y + 1.0e-3) {
                    surface = Math.max(surface, fluidTop);
                }
            }
            if (surface > Double.NEGATIVE_INFINITY) {
                return Optional.of(new Vec3(point.x, surface, point.z));
            }
            fluidAbove = !fluid.isEmpty();
        }
        return Optional.empty();
    }

    /**
     * The floor under a point of a path (a dash strip's samples): as {@link #groundBelow(Vec3)}, except where the scan
     * would start inside terrain (ground rising more than 2 blocks above the point): the scan then starts at the first
     * open block above, so the zone lies on top of that terrain instead of underground. Steps up at most
     * {@code movement.ground_scan} blocks; empty if it is still inside terrain then, or over the void.
     */
    public Optional<Vec3> floorNear(Vec3 point) {
        ServerLevel level = level();
        int start = Mth.floor(point.y) + 2;
        int limit = Math.min(level.getMaxBuildHeight() - 1, start + combat.movement().groundScan());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(Mth.floor(point.x), start, Mth.floor(point.z));
        int y = start;
        while (!level.getBlockState(cursor.setY(y)).getCollisionShape(level, cursor).isEmpty()) {
            if (++y > limit) {
                return Optional.empty();
            }
        }
        return y == start ? groundBelow(point) : groundBelow(new Vec3(point.x, y, point.z), 0);
    }

    /**
     * Vertical speed for an event that steers the body only sideways (a dash, a chase): none for a host that holds
     * its altitude while the engine owns it (the Wither, whose move control would let it sink), else the body's own
     * (gravity, a jump in progress).
     */
    public double sidewaysY() {
        return combat.adapter().holdsAltitude() ? 0.0 : mob.getDeltaMovement().y;
    }

    /**
     * Sets the body's velocity for this tick (an event steering the mob: hover, dash, chase). It also tells the host
     * that something drives the body now, so a host that holds still during abilities leaves it alone.
     */
    public void moveBody(double x, double y, double z) {
        mob.setDeltaMovement(x, y, z);
        mob.hasImpulse = true;
        combat.markBodyMoved();
    }

    /**
     * Draws a telegraph that fills up until the impact at {@code impactSeconds}. A rectangle whose telegraph has
     * {@code arrow} gets an arrowhead past its far end.
     */
    public Marker telegraph(Vec3 origin, float yawDegrees, Shape shape, Telegraph style, double impactSeconds) {
        return telegraph(List.of(zoneOf(origin, yawDegrees, shape, style)), style, impactSeconds);
    }

    /**
     * The telegraph zone drawn for {@code shape} at {@code origin}: its own zone, or for a rectangle whose telegraph
     * has {@code arrow}, a strip with an arrowhead past its far end.
     */
    public static Zone zoneOf(Vec3 origin, float yawDegrees, Shape shape, Telegraph style) {
        if (style.arrow() && shape instanceof Shape.Rectangle rectangle) {
            float width = (float) rectangle.width();
            AttackZone strip = new AttackZone.Strip(width, (float) rectangle.length(),
                    (float) DashLane.headLength(width), 0.0F, 1.0F);
            Vec3 start = origin.add(forward(yawDegrees).scale(rectangle.offset()));
            return new Zone(start, yawDegrees, strip);
        }
        return new Zone(origin, yawDegrees, shape.zone());
    }

    /**
     * Draws one telegraph made of several zones (e.g. a dash strip laid on the ground in segments), which fill up
     * together until the impact at {@code impactSeconds} and flash, fade or vanish together. No zones = nothing drawn
     * (the marker is still valid).
     */
    public Marker telegraph(List<Zone> zones, Telegraph style, double impactSeconds) {
        return telegraph(zones, style, impactSeconds, combat.reachUp(), combat.reachDown());
    }

    /**
     * {@link #telegraph(List, Telegraph, double)} for a hit whose height band ({@code up} / {@code down} blocks around
     * each zone's floor) is not the mob's reach band: the client lays the telegraph only on floors inside it.
     */
    public Marker telegraph(List<Zone> zones, Telegraph style, double impactSeconds, double up, double down) {
        int total = Math.max(1, (int) Math.round((impactSeconds - seconds()) * 20.0 / speed));
        AttackDisplayConfig config = new AttackDisplayConfig(style.style().animation(), style.prepareColor(),
                style.invokeColor());
        List<AttackZoneDisplay> displays = new ArrayList<>();
        for (Zone zone : zones) {
            displays.add(AttackZoneDisplay.spawn(level(), zone.origin(), zone.yaw(), zone.zone(), config, total,
                    (float) up, (float) down));
        }
        Marker marker = new Marker(List.copyOf(displays), impactSeconds, total);
        markers.add(marker);
        return marker;
    }

    /** The impact happened: flash the telegraph briefly, then let it go. */
    public void flash(Marker marker) {
        if (marker != null && markers.remove(marker)) {
            for (AttackZoneDisplay display : marker.displays) {
                display.setBrightRed();
                combat.fadeOut(display);
            }
        }
    }

    public void clear(Marker marker) {
        if (marker != null && markers.remove(marker)) {
            marker.displays.forEach(AttackZoneDisplay::remove);
        }
    }

    // --- Geometry helpers ---

    /**
     * Whether a point {@code dx}/{@code dz} blocks (world axes) from a zone's origin is inside {@code shape} laid
     * along facing {@code yawDegrees}: the hit test of every zone.
     */
    public static boolean inside(Shape shape, double dx, double dz, float yawDegrees) {
        Vec3 ahead = forward(yawDegrees);
        double along = dx * ahead.x + dz * ahead.z;
        double across = dx * -ahead.z + dz * ahead.x;
        return shape.contains(along, across);
    }

    /** A point {@code forward}/{@code right} blocks from {@code origin} along facing {@code yawDegrees}. */
    public static Vec3 local(Vec3 origin, float yawDegrees, double forward, double right) {
        return origin.add(forward(yawDegrees).scale(forward)).add(right(yawDegrees).scale(right));
    }

    public static Vec3 forward(float yawDegrees) {
        double rad = Math.toRadians(yawDegrees);
        return new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
    }

    /** Facing that puts a line {@code side} blocks to the mob's right through {@code target}. */
    private float aimedYaw(Vec3 target, double side) {
        float toTarget = yawTo(target);
        if (side == 0.0) {
            return toTarget;
        }
        double distance = Math.hypot(target.x - mob.getX(), target.z - mob.getZ());
        if (distance < 1.0e-3) {
            return toTarget;
        }
        // Turning toward the mob's left swings a right-hand strike line onto the target.
        return toTarget - (float) Math.toDegrees(Math.asin(Mth.clamp(side / distance, -1.0, 1.0)));
    }

    /** Unit vector to the mob's right for a facing. */
    public static Vec3 right(float yawDegrees) {
        double rad = Math.toRadians(yawDegrees);
        return new Vec3(-Math.cos(rad), 0.0, -Math.sin(rad));
    }

    private float yawTo(Vec3 target) {
        double dx = target.x - mob.getX();
        double dz = target.z - mob.getZ();
        if (dx * dx + dz * dz < 1.0e-6) {
            return yaw;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private void face(float degrees) {
        mob.setYRot(degrees);
        mob.yBodyRot = degrees;
        mob.yHeadRot = degrees;
    }

    /** One zone of a telegraph: where it is drawn (on the ground), its facing and its shape. */
    public record Zone(Vec3 origin, float yaw, AttackZone zone) {
    }

    /** Where a {@code hit} event's area locked: its origin on the ground, its facing and its shape. */
    public record HitZone(Shape shape, Vec3 origin, float yaw) {
    }

    /** A live telegraph on the ground (one or more zones). */
    public static final class Marker {
        private final List<AttackZoneDisplay> displays;
        private final double impactSeconds;
        private final int totalTicks;

        private Marker(List<AttackZoneDisplay> displays, double impactSeconds, int totalTicks) {
            this.displays = displays;
            this.impactSeconds = impactSeconds;
            this.totalTicks = totalTicks;
        }

        private void update(AbilityRun run) {
            int remaining = Math.max(0, (int) Math.round((impactSeconds - run.seconds()) * 20.0 / run.speed()));
            for (AttackZoneDisplay display : displays) {
                display.update(Math.min(remaining, totalTicks), totalTicks);
            }
        }
    }

    private enum State { PENDING, ACTIVE, DONE }

    private static final class Slot {
        private final AbilityEvent event;
        private AbilityEvent.Instance instance;
        private State state = State.PENDING;
        private boolean aimed;

        private Slot(AbilityEvent event) {
            this.event = event;
        }

        private void tick(AbilityRun run, double seconds) {
            if (!aimed && event.preAim().isPresent() && seconds >= event.begin() - AIM_LEAD) {
                aimed = true;
                run.reaim(event.preAim().get());
            }
            if (state == State.PENDING && seconds >= event.begin()) {
                instance = event.start(run);
                state = State.ACTIVE;
            }
            if (state == State.ACTIVE) {
                instance.tick(run);
                if (run.cancelled) {
                    return;
                }
                if (seconds >= event.end()) {
                    state = State.DONE;
                    instance.finish(run);
                }
            }
        }
    }
}
