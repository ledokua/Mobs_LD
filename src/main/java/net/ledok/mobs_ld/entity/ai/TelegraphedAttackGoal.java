package net.ledok.mobs_ld.entity.ai;

import net.ledok.mobs_ld.entity.BaseDungeonMob;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class TelegraphedAttackGoal extends Goal {
    private final BaseDungeonMob mob;
    private int windupTimer = -1;
    private AttackZoneDisplay display;
    private Vec3 lockedOrigin = Vec3.ZERO;
    private float lockedYaw;

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
                    -Math.sin(Math.toRadians(mob.getYRot())),
                    0.0,
                    Math.cos(Math.toRadians(mob.getYRot()))
            );
            Vec3 toTargetFlat = new Vec3(toTarget.x, 0.0, toTarget.z).normalize();
            if (forward.dot(toTargetFlat) <= 0.0) {
                return false;
            }
        }

        return mob.getAttackCooldown() <= 0
                && mob.distanceTo(mob.getTarget()) <= mob.getAttackZone().maxForwardReach() + 0.5F;
    }

    @Override
    public boolean canContinueToUse() {
        return windupTimer >= 0;
    }

    @Override
    public void start() {
        windupTimer = mob.getWindupTicks();
        lockedOrigin = mob.position();
        lockedYaw = mob.getYRot();
        display = AttackZoneDisplay.spawn((ServerLevel) mob.level(), lockedOrigin, lockedYaw, mob.getAttackZone());
        mob.getNavigation().stop();
        mob.setWindingUp(true);
    }

    @Override
    public void tick() {
        windupTimer--;
        if (display != null) {
            if (windupTimer > mob.getWindupTicks() / 2) {
                display.drawDimRed();
            } else {
                display.setBrightRed();
            }
        }

        if (windupTimer <= 0) {
            applyDamage();
            if (display != null) {
                display.remove();
                display = null;
            }
            windupTimer = -1;
            mob.setWindingUp(false);
            mob.setAttackCooldown(mob.getAttackCooldownTicks());
        }
    }

    @Override
    public void stop() {
        if (display != null) {
            display.remove();
            display = null;
        }
        windupTimer = -1;
        mob.setWindingUp(false);
    }

    private void applyDamage() {
        ServerLevel world = (ServerLevel) mob.level();
        AttackZone zone = mob.getAttackZone();
        float reach = zone.maxForwardReach();
        AABB broad = new AABB(lockedOrigin, lockedOrigin).inflate(reach + 1.0F);
        List<ServerPlayer> candidates = world.getEntitiesOfClass(ServerPlayer.class, broad);

        Vec3 forward = new Vec3(
                -Math.sin(Math.toRadians(lockedYaw)),
                0.0,
                Math.cos(Math.toRadians(lockedYaw))
        );

        for (ServerPlayer player : candidates) {
            if (isInZone(player.position(), forward, zone)) {
                player.hurt(
                        world.damageSources().mobAttack(mob),
                        (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE)
                );
            }
        }
    }

    private boolean isInZone(Vec3 pos, Vec3 forward, AttackZone zone) {
        Vec3 toTarget = pos.subtract(lockedOrigin);

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
        };
    }
}
