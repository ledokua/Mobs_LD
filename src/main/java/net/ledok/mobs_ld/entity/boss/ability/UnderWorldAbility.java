package net.ledok.mobs_ld.entity.boss.ability;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.boss.AbilityDefinition;
import net.ledok.mobs_ld.entity.boss.BaseBossMob;
import net.ledok.mobs_ld.entity.boss.TriggerCondition;
import net.ledok.mobs_ld.entity.boss.mob.VecnaTheSecond;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

public class UnderWorldAbility extends AbilityDefinition {
    private static final ResourceLocation UNDERGROUND_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("mobs_ld", "vecna_underground_speed");

    private int undergroundTimer = 0;
    private Vec3 interceptTarget = Vec3.ZERO;
    private Vec3 lastTargetSamplePos = null;
    private Vec3 sampledTargetMove = Vec3.ZERO;

    @Override
    public String id() {
        return "under_world";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public boolean isPassive() {
        return false;
    }

    @Override
    public TriggerCondition trigger() {
        return new TriggerCondition.OnTimer(600);
    }

    @Override
    public int cooldownTicks() {
        return 600;
    }

    @Override
    public boolean canUse(ServerLevel world, BaseBossMob boss) {
        if (!(boss instanceof VecnaTheSecond vecna)) {
            return false;
        }
        return !vecna.isUnderground()
                && vecna.getGlobalAttackLockout() <= 0
                && selectLowestHpPlayer(world, vecna) != null;
    }

    @Override
    public AttackZone zone() {
        return null;
    }

    @Override
    public int windupTicks() {
        return 20;
    }

    @Override
    public int damagePersistTicks() {
        return 600;
    }

    @Override
    public void onWindupStart(ServerLevel world, BaseBossMob boss) {
        boss.getNavigation().stop();
        boss.setWindingUp(true);
    }

    @Override
    public void onWindupTick(ServerLevel world, BaseBossMob boss, int windupTimer) {
        if (windupTimer % 2 != 0) {
            return;
        }
        world.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                boss.getX(), boss.getY() + 0.1, boss.getZ(),
                3, 0.3, 0.0, 0.3, 0.01
        );
    }

    @Override
    public void onActivate(ServerLevel world, BaseBossMob boss, Vec3 zoneOrigin, float yawDegrees) {
        if (!(boss instanceof VecnaTheSecond vecna)) {
            return;
        }

        vecna.setIsUnderground(true);
        vecna.setDamageImmune(true);
        vecna.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));

        AttributeInstance speed = vecna.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(UNDERGROUND_SPEED_ID);
            speed.addTransientModifier(new AttributeModifier(
                    UNDERGROUND_SPEED_ID,
                    0.5,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }

        AttackZoneDisplay tracker = AttackZoneDisplay.spawn(
                world,
                vecna.position().add(0.0, 0.05, 0.0),
                0.0F,
                new AttackZone.Circle(0.75F),
                new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.DEFAULT, 0xFF000000, 0xFF111111),
                1
        );
        tracker.setAlwaysRender(true);
        vecna.setTrackerDisplay(tracker);

        vecna.setUndergroundTarget(selectLowestHpPlayer(world, vecna));
        lastTargetSamplePos = null;
        sampledTargetMove = Vec3.ZERO;
        undergroundTimer = 600;
        recalculateIntercept(world, vecna);
    }

    @Override
    public void onPersistTick(ServerLevel world, BaseBossMob boss, int persistTimer) {
        if (!(boss instanceof VecnaTheSecond vecna)) {
            return;
        }

        undergroundTimer--;
        recalculateIntercept(world, vecna);
        vecna.getNavigation().stop();

        Vec3 toIntercept = interceptTarget.subtract(vecna.position());
        Vec3 flatToIntercept = new Vec3(toIntercept.x, 0.0, toIntercept.z);
        if (flatToIntercept.lengthSqr() > 1.0e-6) {
            ServerPlayer target = vecna.getUndergroundTarget();
            double targetSpeed = 0.0;
            if (target != null) {
                Vec3 tv = target.getDeltaMovement();
                targetSpeed = Math.sqrt(tv.x * tv.x + tv.z * tv.z);
            }
            double baseSpeed = vecna.getAttributeValue(Attributes.MOVEMENT_SPEED) * 3.5;
            double stepPerTick = Math.max(baseSpeed, targetSpeed + 0.35);
            stepPerTick = Math.max(0.40, Math.min(1.20, stepPerTick));
            Vec3 step = flatToIntercept.normalize().scale(Math.min(stepPerTick, flatToIntercept.length()));
            vecna.setPos(vecna.getX() + step.x, vecna.getY(), vecna.getZ() + step.z);
        }

        AttackZoneDisplay tracker = vecna.getTrackerDisplay();
        if (tracker != null) {
            tracker.updatePosition(vecna.position().add(0.0, 0.05, 0.0));
        }

        ServerPlayer target = vecna.getUndergroundTarget();
        if (target != null && target.isAlive() && !target.isRemoved()) {
            Vec3 leadDir = computeLeadDirection(target, vecna, sampledTargetMove);
            double dxIntercept = interceptTarget.x - vecna.getX();
            double dzIntercept = interceptTarget.z - vecna.getZ();
            double distToInterceptSqr = dxIntercept * dxIntercept + dzIntercept * dzIntercept;

            Vec3 fromTargetToVecna = new Vec3(vecna.getX() - target.getX(), 0.0, vecna.getZ() - target.getZ());
            double aheadDistance = fromTargetToVecna.dot(leadDir);

            Vec3 frontOffset = new Vec3(interceptTarget.x - target.getX(), 0.0, interceptTarget.z - target.getZ());
            double interceptFrontDistance = frontOffset.length();
            double requiredAheadDistance = Math.max(0.15, Math.min(2.0, interceptFrontDistance * 0.7));
            double arrivalRadius = Math.max(0.35, interceptFrontDistance * 0.8);

            if (distToInterceptSqr <= arrivalRadius * arrivalRadius && aheadDistance >= requiredAheadDistance) {
                triggerSurface(vecna);
                return;
            }
        }

        if (undergroundTimer <= 0) {
            triggerSurface(vecna);
        }
    }

    @Override
    public boolean keepDamageImmuneAfterEnd() {
        return true;
    }

    @Override
    public void onEnd(ServerLevel world, BaseBossMob boss) {
        if (!(boss instanceof VecnaTheSecond vecna)) {
            return;
        }
        if (vecna.isReadyToSurface() && vecna.getHealth() > 0.0F) {
            return;
        }

        AttributeInstance speed = vecna.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(UNDERGROUND_SPEED_ID);
        }
        vecna.removeEffect(MobEffects.INVISIBILITY);
        vecna.setDamageImmune(false);
        vecna.setIsUnderground(false);
        vecna.setReadyToSurface(false);
        vecna.setUndergroundTarget(null);
        lastTargetSamplePos = null;
        sampledTargetMove = Vec3.ZERO;

        AttackZoneDisplay tracker = vecna.getTrackerDisplay();
        if (tracker != null) {
            tracker.remove();
            vecna.setTrackerDisplay(null);
        }
    }

    private void recalculateIntercept(ServerLevel world, VecnaTheSecond vecna) {
        ServerPlayer target = vecna.getUndergroundTarget();
        if (target == null || !target.isAlive() || target.isRemoved()) {
            target = selectLowestHpPlayer(world, vecna);
            vecna.setUndergroundTarget(target);
            if (target == null) {
                triggerSurface(vecna);
                return;
            }
            lastTargetSamplePos = null;
            sampledTargetMove = Vec3.ZERO;
        }

        Vec3 moveVector = sampleTargetMovement(target);
        Vec3 horizontalVel = new Vec3(moveVector.x, 0.0, moveVector.z);
        Vec3 leadDir = computeLeadDirection(target, vecna, horizontalVel);
        double vecnaSpeed = Math.max(0.01, vecna.getAttributeValue(Attributes.MOVEMENT_SPEED));
        double distToPlayer = target.position().distanceTo(vecna.position());
        double predictionTicks = Math.min(12.0, Math.max(2.0, (distToPlayer / vecnaSpeed) * 0.35));

        Vec3 predictedBase = horizontalVel.lengthSqr() < 1.0e-4
                ? target.position()
                : target.position().add(horizontalVel.scale(predictionTicks));

        Vec3 desired = predictedBase.add(leadDir.scale(3.0));
        interceptTarget = new Vec3(desired.x, vecna.getY(), desired.z);
    }

    private void triggerSurface(VecnaTheSecond vecna) {
        vecna.setReadyToSurface(true);
        vecna.getNavigation().stop();
        if (vecna.getBossAbilityGoal() != null) {
            vecna.getBossAbilityGoal().endPersistEarly();
        }
    }

    private ServerPlayer selectLowestHpPlayer(ServerLevel world, VecnaTheSecond vecna) {
        double followRange = vecna.getAttributeValue(Attributes.FOLLOW_RANGE);
        return world.getEntitiesOfClass(
                        ServerPlayer.class,
                        vecna.getBoundingBox().inflate(followRange)
                ).stream()
                .filter(player -> player.isAlive() && !player.isRemoved())
                .min(Comparator.comparingDouble(ServerPlayer::getHealth))
                .orElse(null);
    }

    private Vec3 sampleTargetMovement(ServerPlayer target) {
        Vec3 currentPos = target.position();
        Vec3 sampledDelta = Vec3.ZERO;
        if (lastTargetSamplePos != null) {
            sampledDelta = new Vec3(
                    currentPos.x - lastTargetSamplePos.x,
                    0.0,
                    currentPos.z - lastTargetSamplePos.z
            );
        }
        lastTargetSamplePos = currentPos;

        Vec3 deltaMovement = target.getDeltaMovement();
        Vec3 flatDeltaMovement = new Vec3(deltaMovement.x, 0.0, deltaMovement.z);
        Vec3 chosen = sampledDelta.lengthSqr() >= flatDeltaMovement.lengthSqr() * 0.5
                ? sampledDelta
                : flatDeltaMovement;

        if (chosen.lengthSqr() <= 1.0e-5) {
            chosen = sampledTargetMove.scale(0.6);
        }
        sampledTargetMove = chosen;
        return chosen;
    }

    private Vec3 computeLeadDirection(ServerPlayer target, VecnaTheSecond vecna, Vec3 flatVelocity) {
        if (flatVelocity.lengthSqr() > 1.0e-4) {
            return flatVelocity.normalize();
        }

        Vec3 look = target.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
        if (flatLook.lengthSqr() > 1.0e-4) {
            return flatLook.normalize();
        }

        Vec3 toTarget = target.position().subtract(vecna.position());
        Vec3 flatToTarget = new Vec3(toTarget.x, 0.0, toTarget.z);
        if (flatToTarget.lengthSqr() > 1.0e-6) {
            return flatToTarget.normalize();
        }
        return new Vec3(0.0, 0.0, 1.0);
    }
}
