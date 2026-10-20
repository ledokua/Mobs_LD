package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.mob.LdMob;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.ledok.mobs_ld.mob.behaviour.event.Telegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * One ability in flight: its clock (scaled by attack speed), aim, facing, and the state of each timeline event.
 * Until {@code track_until} the mob keeps turning toward its aim; after that aim position and facing are locked, so
 * telegraphs drawn from then on show exactly where the hits will land.
 */
public final class AbilityRun {
    /** Degrees per tick a mob may turn while tracking its aim; big bodies should not snap around. */
    private static final float TRACK_TURN_RATE = 12.0F;

    private final LdMob mob;
    private final MobCombat combat;
    private final String name;
    private final MobBehaviour.Ability ability;
    private final double speed;
    private final double trackUntil;
    private final LivingEntity aim;
    private final List<Slot> slots = new ArrayList<>();
    private final List<Marker> markers = new ArrayList<>();

    private double ticks;
    private float yaw;
    private Vec3 aimPosition;
    private boolean cancelled;
    private boolean staggered;

    AbilityRun(LdMob mob, MobCombat combat, String name, MobBehaviour.Ability ability, LivingEntity aim, double speed) {
        this.mob = mob;
        this.combat = combat;
        this.name = name;
        this.ability = ability;
        this.aim = aim;
        this.speed = speed;
        this.yaw = mob.getYRot();
        this.aimPosition = aim != null ? aim.position() : mob.position();
        double firstBegin = ability.duration();
        for (AbilityEvent event : ability.events()) {
            slots.add(new Slot(event));
            firstBegin = Math.min(firstBegin, event.begin());
        }
        this.trackUntil = ability.trackUntil().orElse(ability.events().isEmpty() ? 0.0 : firstBegin);
        if (aim != null) {
            yaw = yawTo(aim.position());
        }
    }

    /** Advances one server tick; returns false once the ability is over. */
    boolean tick() {
        if (cancelled) {
            return false;
        }
        double seconds = seconds();
        if (seconds < trackUntil && aim != null && aim.isAlive()) {
            aimPosition = aim.position();
            yaw = Mth.approachDegrees(yaw, yawTo(aimPosition), TRACK_TURN_RATE);
        }
        face(yaw);
        if (!mob.isDeadOrDying()) {
            mob.getNavigation().stop();
        }
        for (Slot slot : slots) {
            slot.tick(this, seconds);
            if (cancelled) {
                return false;
            }
        }
        for (Marker marker : markers) {
            marker.update(this);
        }
        ticks += speed;
        return seconds() < ability.duration() || slots.stream().anyMatch(slot -> slot.state == State.ACTIVE);
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

    // --- For events ---

    public LdMob mob() {
        return mob;
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
        return ticks / 20.0;
    }

    public double speed() {
        return speed;
    }

    /** Current facing in degrees; fixed after the tracking window. */
    public float yaw() {
        return yaw;
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
        staggered = true;
        cancel();
    }

    boolean wasStaggered() {
        return staggered;
    }

    public List<LivingEntity> enemiesIn(Shape shape, Vec3 origin, float yawDegrees, double height) {
        Vec3 ahead = forward(yawDegrees);
        double reach = shape.reach() + 1.0;
        AABB box = new AABB(origin.x - reach, origin.y - 1.0, origin.z - reach,
                origin.x + reach, origin.y + height, origin.z + reach);
        List<LivingEntity> inside = new ArrayList<>();
        for (LivingEntity candidate : enemiesNear(box)) {
            if (candidate.getY() > origin.y + height || candidate.getBoundingBox().maxY < origin.y - 0.5) {
                continue;
            }
            double dx = candidate.getX() - origin.x;
            double dz = candidate.getZ() - origin.z;
            double along = dx * ahead.x + dz * ahead.z;
            double across = dx * -ahead.z + dz * ahead.x;
            if (shape.contains(along, across)) {
                inside.add(candidate);
            }
        }
        return inside;
    }

    public List<LivingEntity> enemiesNear(AABB box) {
        return level().getEntitiesOfClass(LivingEntity.class, box, candidate -> CombatTargets.isEnemy(mob, candidate));
    }

    /** Ground level under a point, so telegraphs lie on the floor even when the mob is mid-step or airborne. */
    public Vec3 groundAt(Vec3 point) {
        ServerLevel level = level();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(Mth.floor(point.x), 0, Mth.floor(point.z));
        int top = Mth.floor(point.y) + 2;
        for (int y = top; y >= Math.max(level.getMinBuildHeight(), top - 8); y--) {
            cursor.setY(y);
            VoxelShape collision = level.getBlockState(cursor).getCollisionShape(level, cursor);
            if (!collision.isEmpty()) {
                return new Vec3(point.x, y + collision.max(Direction.Axis.Y), point.z);
            }
        }
        return point;
    }

    /** Draws a telegraph that fills up until the impact at {@code impactSeconds}. */
    public Marker telegraph(Vec3 origin, float yawDegrees, Shape shape, Telegraph style, double impactSeconds) {
        int total = Math.max(1, (int) Math.round((impactSeconds - seconds()) * 20.0 / speed));
        AttackDisplayConfig config = new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.GROW,
                style.prepareColor(), style.invokeColor());
        Marker marker = new Marker(AttackZoneDisplay.spawn(level(), origin, yawDegrees, shape.zone(), config, total),
                impactSeconds, total);
        markers.add(marker);
        return marker;
    }

    /** The impact happened: flash the telegraph briefly, then let it go. */
    public void flash(Marker marker) {
        if (marker != null && markers.remove(marker)) {
            marker.display.setBrightRed();
            combat.fadeOut(marker.display);
        }
    }

    public void clear(Marker marker) {
        if (marker != null && markers.remove(marker)) {
            marker.display.remove();
        }
    }

    // --- Geometry helpers ---

    public static Vec3 forward(float yawDegrees) {
        double rad = Math.toRadians(yawDegrees);
        return new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
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

    /** A live telegraph on the ground. */
    public static final class Marker {
        private final AttackZoneDisplay display;
        private final double impactSeconds;
        private final int totalTicks;

        private Marker(AttackZoneDisplay display, double impactSeconds, int totalTicks) {
            this.display = display;
            this.impactSeconds = impactSeconds;
            this.totalTicks = totalTicks;
        }

        private void update(AbilityRun run) {
            int remaining = Math.max(0, (int) Math.round((impactSeconds - run.seconds()) * 20.0 / run.speed()));
            display.update(Math.min(remaining, totalTicks), totalTicks);
        }
    }

    private enum State { PENDING, ACTIVE, DONE }

    private static final class Slot {
        private final AbilityEvent event;
        private AbilityEvent.Instance instance;
        private State state = State.PENDING;

        private Slot(AbilityEvent event) {
            this.event = event;
        }

        private void tick(AbilityRun run, double seconds) {
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
