package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The one AI goal of a mob in a behaviour fight: holds still while an ability or stagger runs, otherwise tries to
 * start an ability and, failing that, moves according to the behaviour's movement style. Targeting itself stays
 * with the threat system; the ability timeline is ticked by {@link MobCombat}. While a boss walks home after a reset
 * it only holds the movement and look flags, so no vanilla goal takes over the path {@link MobCombat} set.
 *
 * <p>With {@code movement.style: vanilla} it only runs while an ability, a stagger or the walk home does: between
 * abilities the host's own goals move and attack, and {@link MobCombat} picks the next ability itself.
 *
 * <p>The combat is looked up on every use, never held: LdMob adds this goal from inside its constructor, before its
 * combat exists, and a vanilla mob only gets a combat once its type has a behaviour.
 */
public class CombatGoal extends Goal {
    private static final int REPATH_TICKS = 10;
    /** Degrees per tick the mob turns to face its target while standing. */
    private static final float TURN_RATE = 10.0F;

    private final Mob mob;
    private int repath;

    public CombatGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        MobCombat combat = CombatHost.of(mob);
        if (combat == null || combat.behaviour() == null) {
            return false;
        }
        return combat.returning() || combat.busy()
                || (combat.engaged() && combat.movement().style() != MobBehaviour.Movement.Style.VANILLA);
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
        MobCombat combat = CombatHost.of(mob);
        if (combat == null || combat.returning()) {
            return;
        }
        if (combat.busy()) {
            mob.getNavigation().stop();
            return;
        }
        MobBehaviour.Movement movement = combat.movement();
        if (movement.style() == MobBehaviour.Movement.Style.VANILLA) {
            return; // an ability just ended; the goal stops at the next goal update and the host's own AI resumes
        }
        if (combat.tryStartAbility()) {
            mob.getNavigation().stop();
            return;
        }
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
                    approach(follow, movement);
                } else {
                    hold(follow, movement);
                }
            }
            case KITE -> {
                if (distance < movement.distance() - 1.5) {
                    retreat(follow, movement);
                } else if (distance > movement.distance() + 1.5) {
                    approach(follow, movement);
                } else {
                    hold(follow, movement);
                }
            }
            case STATIONARY -> hold(follow, movement);
            case VANILLA -> {
                // returned above
            }
        }
    }

    private void approach(LivingEntity follow, MobBehaviour.Movement movement) {
        if (--repath <= 0 || mob.getNavigation().isDone()) {
            repath = REPATH_TICKS;
            if (movement.hoverHeight().isPresent()) {
                mob.getNavigation().moveTo(follow.getX(), follow.getY() + movement.hoverHeight().get(), follow.getZ(),
                        movement.speed());
            } else {
                mob.getNavigation().moveTo(follow, movement.speed());
            }
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
            double targetY = follow.getY() + movement.hoverHeight().orElse(0.0);
            mob.getNavigation().moveTo(spot.x, targetY, spot.z, movement.speed());
        }
    }

    /** Stand still and square up to the target, so frontal abilities start facing it. */
    private void hold(LivingEntity follow, MobBehaviour.Movement movement) {
        mob.getNavigation().stop();
        repath = 0;
        double dx = follow.getX() - mob.getX();
        double dz = follow.getZ() - mob.getZ();
        if (dx * dx + dz * dz > 1.0e-4) {
            float yaw = Mth.approachDegrees(mob.getYRot(), (float) Math.toDegrees(Math.atan2(-dx, dz)), TURN_RATE);
            mob.setYRot(yaw);
            mob.yBodyRot = yaw;
        }
        if (movement.hoverHeight().isPresent()) {
            double dy = follow.getY() + movement.hoverHeight().get() - mob.getY();
            if (Math.abs(dy) > 0.25) {
                Vec3 velocity = mob.getDeltaMovement();
                mob.setDeltaMovement(velocity.x, Mth.clamp(dy * 0.16, -0.22, 0.22), velocity.z);
            }
        }
    }
}
