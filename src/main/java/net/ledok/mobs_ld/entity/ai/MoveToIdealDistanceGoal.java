package net.ledok.mobs_ld.entity.ai;

import net.ledok.mobs_ld.entity.BaseDungeonMob;
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
        return mob.getTarget() != null && !mob.isWindingUp();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }

        double dist = mob.distanceTo(target);
        double ideal = mob.getIdealAttackDistance();

        if (++recalcPathTicks >= 10) {
            recalcPathTicks = 0;

            if (dist > ideal * 1.2) {
                mob.getNavigation().moveTo(target, speedModifier);
            } else if (dist < ideal * 0.7) {
                mob.getNavigation().stop();
                Vec3 awayDir = mob.position().subtract(target.position());
                if (awayDir.lengthSqr() > 1.0e-6) {
                    awayDir = awayDir.normalize();
                    Vec3 backTarget = mob.position().add(awayDir.scale(ideal));
                    mob.getNavigation().moveTo(backTarget.x, backTarget.y, backTarget.z, speedModifier);
                }
            } else {
                mob.getNavigation().stop();
            }
        }
    }
}
