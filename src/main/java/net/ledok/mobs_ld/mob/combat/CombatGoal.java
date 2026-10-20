package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.mob.LdMob;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The one AI goal of a data-driven mob in a fight: holds still while an ability or stagger runs, otherwise tries to
 * start an ability and, failing that, moves according to the behaviour's movement style. Targeting itself stays
 * with the threat system; the ability timeline is ticked by {@link MobCombat}.
 */
public class CombatGoal extends Goal {
    private static final int REPATH_TICKS = 10;
    /** Degrees per tick the mob turns to face its target while standing. */
    private static final float TURN_RATE = 10.0F;

    private final LdMob mob;
    private int repath;

    public CombatGoal(LdMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        MobCombat combat = mob.combat();
        return combat.behaviour() != null && !combat.returning() && (combat.busy() || combat.engaged());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        MobCombat combat = mob.combat();
        if (combat.busy()) {
            mob.getNavigation().stop();
            return;
        }
        if (combat.tryStartAbility()) {
            mob.getNavigation().stop();
            return;
        }
        MobBehaviour.Movement movement = combat.movement();
        LivingEntity follow = CombatTargets.pick(mob, movement.follow());
        if (follow == null) {
            mob.getNavigation().stop();
            return;
        }
        mob.getLookControl().setLookAt(follow, 30.0F, 30.0F);
        double distance = CombatTargets.distance(mob, follow);
        switch (movement.style()) {
            case STANDOFF -> {
                if (distance > movement.distance() + 0.3) {
                    approach(follow, movement.speed());
                } else {
                    hold(follow);
                }
            }
            case KITE -> {
                if (distance < movement.distance() - 1.5) {
                    retreat(follow, movement);
                } else if (distance > movement.distance() + 1.5) {
                    approach(follow, movement.speed());
                } else {
                    hold(follow);
                }
            }
            case STATIONARY -> hold(follow);
        }
    }

    private void approach(LivingEntity follow, double speed) {
        if (--repath <= 0 || mob.getNavigation().isDone()) {
            repath = REPATH_TICKS;
            mob.getNavigation().moveTo(follow, speed);
        }
    }

    private void retreat(LivingEntity follow, MobBehaviour.Movement movement) {
        if (--repath <= 0 || mob.getNavigation().isDone()) {
            repath = REPATH_TICKS;
            Vec3 away = mob.position().subtract(follow.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 1.0e-4) {
                away = new Vec3(1.0, 0.0, 0.0);
            }
            Vec3 spot = follow.position().add(away.normalize().scale(movement.distance()));
            mob.getNavigation().moveTo(spot.x, spot.y, spot.z, movement.speed());
        }
    }

    /** Stand still and square up to the target, so frontal abilities start facing it. */
    private void hold(LivingEntity follow) {
        mob.getNavigation().stop();
        repath = 0;
        double dx = follow.getX() - mob.getX();
        double dz = follow.getZ() - mob.getZ();
        if (dx * dx + dz * dz > 1.0e-4) {
            float yaw = Mth.approachDegrees(mob.getYRot(), (float) Math.toDegrees(Math.atan2(-dx, dz)), TURN_RATE);
            mob.setYRot(yaw);
            mob.yBodyRot = yaw;
        }
    }
}
