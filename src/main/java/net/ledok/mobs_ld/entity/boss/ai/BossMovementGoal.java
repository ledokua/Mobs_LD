package net.ledok.mobs_ld.entity.boss.ai;

import net.ledok.mobs_ld.entity.boss.BaseBossMob;
import net.ledok.mobs_ld.entity.boss.MovementType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class BossMovementGoal extends Goal {
    private static final float ATTACK_TRIGGER_EXTRA_RANGE = 0.2F;

    private final BaseBossMob boss;
    private int recalcPathTicks = 0;

    public BossMovementGoal(BaseBossMob boss) {
        this.boss = boss;
    }

    @Override
    public boolean canUse() {
        if (boss.isMovementLocked()) {
            return false;
        }
        return boss.getActivePhase().movementType() == MovementType.FREE
                && boss.getTarget() != null
                && !boss.isWindingUp()
                && !canAttackNow();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        LivingEntity target = boss.getTarget();
        if (target == null) {
            return;
        }

        double dist = boss.distanceTo(target);
        double ideal = boss.getIdealAttackDistance();
        if (++recalcPathTicks >= 10) {
            recalcPathTicks = 0;
            if (dist > ideal + 0.6) {
                boss.getNavigation().moveTo(target, 1.0D);
            } else if (ideal > 0.0 && dist < ideal - 0.5) {
                boss.getNavigation().stop();
                Vec3 away = boss.position().subtract(target.position());
                if (away.lengthSqr() > 1.0e-6) {
                    Vec3 dir = away.normalize();
                    Vec3 backTarget = boss.position().add(dir.scale(ideal));
                    boss.getNavigation().moveTo(backTarget.x, backTarget.y, backTarget.z, 1.0D);
                }
            } else {
                boss.getNavigation().stop();
            }
        }
    }

    private boolean canAttackNow() {
        if (boss.getTarget() == null || boss.getAttackCooldown() > 0) {
            return false;
        }
        return boss.distanceTo(boss.getTarget()) <= 3.0F + ATTACK_TRIGGER_EXTRA_RANGE;
    }
}
