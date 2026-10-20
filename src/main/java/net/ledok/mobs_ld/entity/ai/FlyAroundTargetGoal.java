package net.ledok.mobs_ld.entity.ai;

import net.ledok.mobs_ld.entity.BaseDungeonMob;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class FlyAroundTargetGoal extends Goal {
    private final BaseDungeonMob mob;
    private final double speedScale;
    private int orbitDirection = 1;
    private int directionSwapTicks = 0;

    public FlyAroundTargetGoal(BaseDungeonMob mob, double speedScale) {
        this.mob = mob;
        this.speedScale = speedScale;
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null && !mob.isWindingUp();
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() != null && !mob.isWindingUp();
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
        pickDirection();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }

        mob.getNavigation().stop();

        if (--directionSwapTicks <= 0) {
            pickDirection();
        }

        Vec3 toMob = mob.position().subtract(target.position());
        Vec3 flat = new Vec3(toMob.x, 0.0, toMob.z);
        if (flat.lengthSqr() < 1.0e-6) {
            flat = new Vec3(1.0, 0.0, 0.0);
        }

        Vec3 radialOut = flat.normalize();
        Vec3 tangent = new Vec3(-radialOut.z, 0.0, radialOut.x).scale(orbitDirection);

        double desiredDist = Math.max(1.0, mob.getIdealAttackDistance());
        double dist = flat.length();
        double radialError = Mth.clamp((dist - desiredDist) / desiredDist, -1.0, 1.0);
        Vec3 radialCorrection = radialOut.scale(-radialError);

        Vec3 desiredDir = tangent.scale(0.9).add(radialCorrection.scale(0.8));
        if (desiredDir.lengthSqr() < 1.0e-6) {
            desiredDir = tangent;
        }
        desiredDir = desiredDir.normalize();

        double speed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedScale;
        Vec3 velocity = mob.getDeltaMovement();
        Vec3 wanted = new Vec3(desiredDir.x * speed, velocity.y, desiredDir.z * speed);

        mob.setDeltaMovement(
                Mth.lerp(0.35, velocity.x, wanted.x),
                velocity.y,
                Mth.lerp(0.35, velocity.z, wanted.z)
        );

        Vec3 motion = mob.getDeltaMovement();
        double horizontalSpeedSqr = motion.x * motion.x + motion.z * motion.z;
        if (horizontalSpeedSqr > 1.0e-6) {
            float yaw = (float) Math.toDegrees(Math.atan2(-motion.x, motion.z));
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
        }
    }

    @Override
    public void stop() {
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(v.x * 0.5, v.y, v.z * 0.5);
    }

    private void pickDirection() {
        orbitDirection = mob.getRandom().nextBoolean() ? 1 : -1;
        directionSwapTicks = 40 + mob.getRandom().nextInt(80);
    }
}
