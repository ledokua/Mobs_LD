package net.ledok.mobs_ld.entity.ai;

import net.ledok.mobs_ld.entity.BaseDungeonMob;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class MoveToIdealDistanceGoal extends Goal {
    private final BaseDungeonMob mob;
    private final double speedModifier;
    private int recalcPathTicks = 0;

    public MoveToIdealDistanceGoal(BaseDungeonMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || mob.isWindingUp()) {
            return false;
        }
        return !canAttackNow(target);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || mob.isWindingUp()) {
            return false;
        }
        return !canAttackNow(target);
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }

        double dist = mob.distanceTo(target);
        double ideal = mob.getIdealAttackDistance();

        // Far away: walk toward a stand-off point on the line to the target (avoids the diagonal-corner
        // settle that pathing straight onto a wide mob causes).
        if (dist > ideal + 0.6) {
            if (++recalcPathTicks >= 10) {
                recalcPathTicks = 0;
                Vec3 toMob = mob.position().subtract(target.position());
                if (ideal > 0.0 && toMob.lengthSqr() > 1.0e-6) {
                    Vec3 standoff = target.position().add(toMob.normalize().scale(ideal));
                    mob.getNavigation().moveTo(standoff.x, standoff.y, standoff.z, speedModifier);
                } else {
                    mob.getNavigation().moveTo(target, speedModifier);
                }
            }
            return;
        }

        // In the range band: stop pathing and square the body up to the target. Only once we actually face
        // it do we adjust position — moving relative to that facing so we keep looking at the target.
        recalcPathTicks = 0;
        mob.getNavigation().stop();
        if (turnBodyToward(target, 15.0F)) {
            holdPosition(target, ideal);
        }
    }

    /**
     * Eases the mob toward its desired standing spot — ideal distance from the target, plus an optional
     * sideways shift ({@link BaseDungeonMob#getLateralStandoffOffset()}) — moving relative to its facing so it
     * keeps looking at the target (so it backs up when too close and drifts sideways to hold the offset).
     */
    private void holdPosition(LivingEntity target, double ideal) {
        Vec3 fromTarget = horizontal(mob.position().subtract(target.position()));
        if (fromTarget.lengthSqr() < 1.0e-6) {
            return;
        }
        Vec3 dir = fromTarget.normalize();
        Vec3 desired = target.position().add(dir.scale(ideal));
        double lateral = mob.getLateralStandoffOffset();
        if (lateral != 0.0) {
            // dir points target -> mob (the target's forward); the target's left is (dir.z, -dir.x).
            desired = desired.add(new Vec3(dir.z, 0.0, -dir.x).scale(lateral));
        }

        Vec3 error = horizontal(desired.subtract(mob.position()));
        if (error.lengthSqr() < 0.04) { // within ~0.2 blocks — close enough, don't jitter
            return;
        }
        Vec3 forward = dir.scale(-1.0);                       // toward the target
        Vec3 left = new Vec3(forward.z, 0.0, -forward.x);     // the mob's own left
        float zza = (float) Mth.clamp(error.dot(forward) * 2.0, -1.0, 1.0);
        float xxa = (float) Mth.clamp(error.dot(left) * 2.0, -1.0, 1.0);
        mob.getMoveControl().strafe(zza, xxa);
    }

    private static Vec3 horizontal(Vec3 v) {
        return new Vec3(v.x, 0.0, v.z);
    }

    /** Rotates the mob's whole body toward the target by at most {@code maxStep} degrees; returns true once aligned. */
    private boolean turnBodyToward(LivingEntity target, float maxStep) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        if (dx * dx + dz * dz < 1.0e-6) {
            return true;
        }
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float delta = Mth.wrapDegrees(desiredYaw - mob.getYRot());
        float newYaw = mob.getYRot() + Mth.clamp(delta, -maxStep, maxStep);
        mob.setYRot(newYaw);
        mob.yBodyRot = newYaw;
        mob.yHeadRot = newYaw;
        return Math.abs(delta) <= 20.0F;
    }

    private boolean canAttackNow(LivingEntity target) {
        if (mob.getAttackCooldown() > 0) {
            return false;
        }
        double reach = mob.getAttackZone().maxForwardReach() + mob.getZoneForwardOffset();
        if (mob.distanceTo(target) > reach) {
            return false;
        }
        // Treat "target is behind me" as not-yet-attackable so this goal stays active and keeps turning to
        // face — mirrors the same forward check TelegraphedAttackGoal uses before it commits.
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        if (dx * dx + dz * dz > 1.0e-6) {
            double yawRad = Math.toRadians(mob.getYHeadRot());
            double len = Math.sqrt(dx * dx + dz * dz);
            double forwardDot = (dx / len) * -Math.sin(yawRad) + (dz / len) * Math.cos(yawRad);
            if (forwardDot <= 0.0) {
                return false;
            }
        }
        return true;
    }
}
