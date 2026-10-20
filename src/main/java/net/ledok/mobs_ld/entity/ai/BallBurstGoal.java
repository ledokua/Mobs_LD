package net.ledok.mobs_ld.entity.ai;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.mob.Ball;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Drives the ball's plate_burst attack. Two interchangeable damage flavours are selected by
 * {@link Ball#burstMode()}: a single telegraphed circle AOE, or 14 individual plate hitboxes that
 * sweep outward (reusing the real fly-out vectors from the plate_burst animation).
 */
public class BallBurstGoal extends Goal {
    private static final double ZONE_MIN_Y_OFFSET = -0.5;
    private static final double ZONE_MAX_Y_OFFSET = 2.5;

    /**
     * Each plate's fully-extended offset from the hub at the burst peak, in model units (x, z). These are the
     * 1.2917s keyframes from {@link net.ledok.mobs_ld.client.animation.BallAnimations#PLATE_BURST}, so the
     * server-side hitboxes track exactly where the rendered plates fly. Vertical (y) is dropped — the sweep is
     * treated as a flat ring and players are caught by a y-band instead.
     */
    private static final double[][] PLATE_OFFSETS = {
            {-46.157, -20.1666},
            {-31.7618, -44.0988},
            {-12.003, -47.3808},
            {34.7774, -37.9358},
            {54.2492, 0.2236},
            {29.2324, 38.5442},
            {13.157, 48.5812},
            {-36.9982, 40.3492},
            {-51.856, 21.4376},
            {50.9376, -24.2794},
            {47.9262, 20.9792},
            {-12.2476, 48.6824},
            {-53.8446, 4.8494},
            {14.588, -49.785}
    };

    private final Ball mob;
    private int elapsed = 0;
    private boolean wentBrightRed = false;
    private AttackZoneDisplay display;
    private final Set<UUID> alreadyHit = new HashSet<>();

    public BallBurstGoal(Ball mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel)) {
            return false;
        }
        if (mob.isBursting() || mob.getBurstCooldown() > 0) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && mob.distanceTo(target) <= mob.burstTriggerRange();
    }

    @Override
    public boolean canContinueToUse() {
        return elapsed < Ball.BURST_DURATION_TICKS;
    }

    @Override
    public void start() {
        elapsed = 0;
        wentBrightRed = false;
        alreadyHit.clear();
        mob.setBursting(true);
        mob.setBurstCooldown(mob.burstCooldownTicks());
        mob.getNavigation().stop();
        faceTarget();

        if (mob.burstMode() == Ball.BurstMode.TELEGRAPHED_CIRCLE) {
            display = AttackZoneDisplay.spawn(
                    (ServerLevel) mob.level(),
                    new Vec3(mob.getX(), mob.getY(), mob.getZ()),
                    mob.getYRot(),
                    new AttackZone.Circle(mob.burstRadius()),
                    AttackDisplayConfig.DEFAULT,
                    Ball.BURST_WINDUP_TICKS
            );
        }
    }

    @Override
    public void tick() {
        elapsed++;
        mob.getNavigation().stop();

        if (mob.burstMode() == Ball.BurstMode.TELEGRAPHED_CIRCLE) {
            tickTelegraphedCircle();
        } else {
            tickPerPlate();
        }
    }

    @Override
    public void stop() {
        mob.setBursting(false);
        removeDisplay();
        elapsed = 0;
        wentBrightRed = false;
        alreadyHit.clear();
    }

    private void tickTelegraphedCircle() {
        // Grow the warning while it winds up (ticks 0..20).
        if (display != null && elapsed < Ball.BURST_WINDUP_TICKS) {
            display.updatePosition(new Vec3(mob.getX(), mob.getY(), mob.getZ()));
            display.update(Ball.BURST_WINDUP_TICKS - elapsed, Ball.BURST_WINDUP_TICKS);
        }
        // The damage zone is live and shown bright red across ticks 20..30; players are caught on first contact.
        if (elapsed >= Ball.BURST_WINDUP_TICKS && elapsed <= Ball.BURST_DAMAGE_END_TICK) {
            if (!wentBrightRed && display != null) {
                display.setBrightRed();
                wentBrightRed = true;
            }
            applyCircleDamage();
        }
        if (display != null && elapsed > Ball.BURST_DAMAGE_END_TICK) {
            removeDisplay();
        }
    }

    private void tickPerPlate() {
        // Plates fling out (20..26), stay extended through the damage window (..30), then retract.
        if (elapsed < Ball.BURST_PLATE_OUT_START_TICK || elapsed > Ball.BURST_DAMAGE_END_TICK) {
            return;
        }
        float progress = (float) Mth.clamp(
                (double) (elapsed - Ball.BURST_PLATE_OUT_START_TICK)
                        / (Ball.BURST_PLATE_PEAK_TICK - Ball.BURST_PLATE_OUT_START_TICK),
                0.0, 1.0
        );
        applyPlateDamage(progress);
    }

    private void applyCircleDamage() {
        ServerLevel world = (ServerLevel) mob.level();
        double radius = mob.burstRadius();
        for (ServerPlayer player : broadCandidates(radius + 1.0)) {
            if (alreadyHit.contains(player.getUUID()) || !withinYBand(player)) {
                continue;
            }
            double dx = player.getX() - mob.getX();
            double dz = player.getZ() - mob.getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                hurt(world, player);
                alreadyHit.add(player.getUUID());
            }
        }
    }

    private void applyPlateDamage(float progress) {
        ServerLevel world = (ServerLevel) mob.level();
        double yawRad = Math.toRadians(mob.yBodyRot);
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double hitRadius = mob.plateHitRadius();
        double broad = mob.burstRadius() + hitRadius + 1.0;
        List<ServerPlayer> candidates = broadCandidates(broad);
        if (candidates.isEmpty()) {
            return;
        }

        for (double[] offset : PLATE_OFFSETS) {
            // Convert the model-space offset (1 unit = 1/16 block) to a world-space ring point at this progress.
            double reach = Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]) / 16.0 * progress;
            if (reach <= 1.0e-4) {
                continue;
            }
            double len = Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]);
            double dirX = offset[0] / len;
            double dirZ = offset[1] / len;
            double worldX = mob.getX() + (dirX * cos - dirZ * sin) * reach;
            double worldZ = mob.getZ() + (dirX * sin + dirZ * cos) * reach;

            for (ServerPlayer player : candidates) {
                if (alreadyHit.contains(player.getUUID()) || !withinYBand(player)) {
                    continue;
                }
                double pad = player.getBbWidth() * 0.5 + hitRadius;
                double dx = player.getX() - worldX;
                double dz = player.getZ() - worldZ;
                if (dx * dx + dz * dz <= pad * pad) {
                    hurt(world, player);
                    alreadyHit.add(player.getUUID());
                }
            }
        }
    }

    private List<ServerPlayer> broadCandidates(double horizontalRadius) {
        ServerLevel world = (ServerLevel) mob.level();
        AABB box = new AABB(
                mob.getX() - horizontalRadius, mob.getY() + ZONE_MIN_Y_OFFSET, mob.getZ() - horizontalRadius,
                mob.getX() + horizontalRadius, mob.getY() + ZONE_MAX_Y_OFFSET, mob.getZ() + horizontalRadius
        );
        return world.getEntitiesOfClass(ServerPlayer.class, box);
    }

    private boolean withinYBand(ServerPlayer player) {
        return player.getY() >= mob.getY() + ZONE_MIN_Y_OFFSET && player.getY() <= mob.getY() + ZONE_MAX_Y_OFFSET;
    }

    private void hurt(ServerLevel world, ServerPlayer player) {
        player.hurt(world.damageSources().mobAttack(mob), mob.burstDamage());
    }

    private void faceTarget() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        if (dx * dx + dz * dz < 1.0e-6) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.yHeadRot = yaw;
    }

    private void removeDisplay() {
        if (display != null) {
            display.remove();
            display = null;
        }
    }
}
