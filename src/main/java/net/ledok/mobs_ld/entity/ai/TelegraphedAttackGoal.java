package net.ledok.mobs_ld.entity.ai;

import net.ledok.mobs_ld.entity.BaseDungeonMob;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TelegraphedAttackGoal extends Goal {
    private static final int BRIGHT_WINDOW_TICKS = 2;
    private static final float ATTACK_TRIGGER_EXTRA_RANGE = 0F;
    private static final double ZONE_MIN_Y_OFFSET = -0.5;
    private static final double ZONE_MAX_Y_OFFSET = 2.0;

    private final BaseDungeonMob mob;
    private int windupTimer = -1;
    private int damageTimer = -1;
    private AttackZoneDisplay display;
    private Vec3 lockedOrigin = Vec3.ZERO;
    private Vec3 lockedTargetPos = Vec3.ZERO;
    private float lockedYaw;
    private final Set<UUID> alreadyHit = new HashSet<>();

    public TelegraphedAttackGoal(BaseDungeonMob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel)) {
            return false;
        }
        if (mob.getTarget() == null) {
            return false;
        }
        Vec3 toTarget = mob.getTarget().position().subtract(mob.position());
        if (toTarget.lengthSqr() > 1.0e-6) {
            Vec3 forward = new Vec3(
                    -Math.sin(Math.toRadians(mob.getYHeadRot())),
                    0.0,
                    Math.cos(Math.toRadians(mob.getYHeadRot()))
            );
            Vec3 toTargetFlat = new Vec3(toTarget.x, 0.0, toTarget.z).normalize();
            if (forward.dot(toTargetFlat) <= 0.0) {
                return false;
            }
        }

        if (mob.getAttackCooldown() > 0) {
            return false;
        }

        return mob.distanceTo(mob.getTarget()) <= mob.getAttackZone().maxForwardReach() + ATTACK_TRIGGER_EXTRA_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        return windupTimer >= 0 || damageTimer >= 0;
    }

    @Override
    public void start() {
        windupTimer = mob.getWindupTicks();
        damageTimer = -1;
        lockedOrigin = snapToGround(mob.position());
        if (mob.getAttackZone() instanceof AttackZone.CircleTarget && mob.getTarget() != null) {
            lockedTargetPos = snapToGround(mob.getTarget().position());
        } else {
            lockedTargetPos = Vec3.ZERO;
        }
        if (mob.getTarget() != null) {
            Vec3 toTarget = mob.getTarget().position().subtract(mob.position());
            if (toTarget.lengthSqr() > 1.0e-6) {
                lockedYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
            } else {
                lockedYaw = mob.getYRot();
            }
        } else {
            lockedYaw = mob.getYRot();
        }
        Vec3 displayOrigin = getZoneOrigin();
        display = AttackZoneDisplay.spawn(
                (ServerLevel) mob.level(),
                displayOrigin,
                lockedYaw,
                mob.getAttackZone(),
                mob.getDisplayConfig(),
                mob.getWindupTicks()
        );
        alreadyHit.clear();
        mob.getNavigation().stop();
        mob.setWindingUp(true);
        if (mob.isCooldownStartingAtWindupStart()) {
            mob.setAttackCooldown(mob.getAttackCooldownTicks());
        }
    }

    @Override
    public void tick() {
        if (windupTimer >= 0) {
            windupTimer--;

            if (display != null) {
                display.update(windupTimer, mob.getWindupTicks());
            }

            if (windupTimer <= 0) {
                int persist = mob.getDamagePersistTicks();
                if (persist > 0) {
                    damageTimer = persist;
                    if (mob.canMoveWhilePersistingPhase()) {
                        mob.setWindingUp(false);
                    }
                    if (display != null) {
                        display.setBrightRed();
                    }
                    windupTimer = -1;
                    return;
                }

                if (display != null) {
                    display.setBrightRed();
                }
                applyDamage();
                if (display != null) {
                    display.remove();
                    display = null;
                }
                windupTimer = -1;
                mob.setWindingUp(false);
                if (!mob.isCooldownStartingAtWindupStart()) {
                    mob.setAttackCooldown(mob.getAttackCooldownTicks());
                }
                return;
            }
        }

        if (damageTimer > 0) {
            applyDamage();
            damageTimer--;
            if (display != null) {
                display.setBrightRed();
            }
            if (damageTimer == 0) {
                if (display != null) {
                    display.remove();
                    display = null;
                }
                damageTimer = -1;
                mob.setWindingUp(false);
                if (!mob.isCooldownStartingAtWindupStart()) {
                    mob.setAttackCooldown(mob.getAttackCooldownTicks());
                }
            }
        }
    }

    @Override
    public void stop() {
        if (display != null) {
            display.remove();
            display = null;
        }
        windupTimer = -1;
        damageTimer = -1;
        lockedTargetPos = Vec3.ZERO;
        alreadyHit.clear();
        mob.setWindingUp(false);
    }

    private void applyDamage() {
        ServerLevel world = (ServerLevel) mob.level();
        AttackZone zone = mob.getAttackZone();
        Vec3 zoneOrigin = getZoneOrigin();
        float reach = zone.maxForwardReach();
        double horizontal = reach + 1.0F;
        AABB broad = new AABB(
                zoneOrigin.x - horizontal,
                zoneOrigin.y + ZONE_MIN_Y_OFFSET,
                zoneOrigin.z - horizontal,
                zoneOrigin.x + horizontal,
                zoneOrigin.y + ZONE_MAX_Y_OFFSET,
                zoneOrigin.z + horizontal
        );
        List<ServerPlayer> candidates = world.getEntitiesOfClass(ServerPlayer.class, broad);

        Vec3 forward = new Vec3(
                -Math.sin(Math.toRadians(lockedYaw)),
                0.0,
                Math.cos(Math.toRadians(lockedYaw))
        );

        for (ServerPlayer player : candidates) {
            if (alreadyHit.contains(player.getUUID())) {
                continue;
            }
            if (isInZone(player.position(), forward, zone, zoneOrigin)) {
                player.hurt(
                        world.damageSources().mobAttack(mob),
                        (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE)
                );
                alreadyHit.add(player.getUUID());
            }
        }
    }

    private boolean isInZone(Vec3 pos, Vec3 forward, AttackZone zone, Vec3 zoneOrigin) {
        if (pos.y < zoneOrigin.y + ZONE_MIN_Y_OFFSET || pos.y > zoneOrigin.y + ZONE_MAX_Y_OFFSET) {
            return false;
        }

        Vec3 toTarget = pos.subtract(zoneOrigin);

        return switch (zone) {
            case AttackZone.Rectangle r -> {
                Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
                double fwdDist = toTarget.dot(forward);
                double sideDist = Math.abs(toTarget.dot(right));
                yield fwdDist >= r.offsetForward()
                        && fwdDist <= r.offsetForward() + r.length()
                        && sideDist <= r.width() / 2.0F;
            }
            case AttackZone.Cone c -> {
                double dist = toTarget.length();
                if (dist > c.maxDistance() || dist <= 1.0e-6) {
                    yield false;
                }
                double angle = Math.toDegrees(Math.acos(toTarget.normalize().dot(forward)));
                yield angle <= c.angleDegrees() / 2.0F;
            }
            case AttackZone.Circle c -> toTarget.lengthSqr() <= (double) c.radius() * c.radius();
            case AttackZone.CircleTarget c -> toTarget.lengthSqr() <= (double) c.radius() * c.radius();
            case AttackZone.CircleRays r -> {
                float baseYawRad = (float) Math.atan2(-forward.x, forward.z);
                int rectCount = Math.max(1, r.rayCount() / 2);
                Vec3 toFlat = new Vec3(toTarget.x, 0.0, toTarget.z);
                for (int i = 0; i < rectCount; i++) {
                    float yawRad = baseYawRad + (float) Math.toRadians(i * (180.0 / rectCount));
                    Vec3 rayForward = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
                    Vec3 rayRight = new Vec3(-rayForward.z, 0.0, rayForward.x);
                    double alongRay = Math.abs(toFlat.dot(rayForward));
                    double acrossRay = Math.abs(toFlat.dot(rayRight));
                    if (alongRay <= r.length() && acrossRay <= r.width() * 0.5F) {
                        yield true;
                    }
                }
                yield false;
            }
        };
    }

    private Vec3 getZoneOrigin() {
        if (mob.getAttackZone() instanceof AttackZone.CircleTarget) {
            return lockedTargetPos;
        }
        return lockedOrigin;
    }

    private Vec3 snapToGround(Vec3 position) {
        if (!(mob.level() instanceof ServerLevel world)) {
            return position;
        }
        int x = Mth.floor(position.x);
        int z = Mth.floor(position.z);
        int minY = world.getMinBuildHeight();
        int maxY = world.getMaxBuildHeight() - 1;
        int startY = Mth.clamp(Mth.floor(position.y) + 2, minY, maxY);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, startY, z);

        for (int y = startY; y >= minY; y--) {
            cursor.setY(y);
            var shape = world.getBlockState(cursor).getCollisionShape(world, cursor);
            if (!shape.isEmpty()) {
                double top = y + shape.max(Direction.Axis.Y);
                return new Vec3(position.x, top + 0.05, position.z);
            }
        }
        return position;
    }
}
