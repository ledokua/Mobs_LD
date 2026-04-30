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
        undergroundTimer = 600;
        recalculateIntercept(world, vecna);
    }

    @Override
    public void onPersistTick(ServerLevel world, BaseBossMob boss, int persistTimer) {
        if (!(boss instanceof VecnaTheSecond vecna)) {
            return;
        }

        undergroundTimer--;
        if (undergroundTimer % 10 == 0) {
            recalculateIntercept(world, vecna);
        }

        AttackZoneDisplay tracker = vecna.getTrackerDisplay();
        if (tracker != null) {
            tracker.updatePosition(vecna.position().add(0.0, 0.05, 0.0));
        }

        double dx = interceptTarget.x - vecna.getX();
        double dz = interceptTarget.z - vecna.getZ();
        if (dx * dx + dz * dz <= 0.01) {
            triggerSurface(vecna);
            return;
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
        }

        Vec3 playerVel = target.getDeltaMovement();
        double vecnaSpeed = Math.max(0.01, vecna.getAttributeValue(Attributes.MOVEMENT_SPEED));
        Vec3 toPlayer = target.position().subtract(vecna.position());
        double travelTime = toPlayer.length() / vecnaSpeed;

        Vec3 targetPos;
        if (playerVel.lengthSqr() < 0.001) {
            targetPos = target.position();
        } else {
            targetPos = target.position().add(playerVel.scale(travelTime + 10.0));
        }

        interceptTarget = new Vec3(targetPos.x, vecna.getY(), targetPos.z);
        vecna.getNavigation().moveTo(interceptTarget.x, vecna.getY(), interceptTarget.z, 1.0D);
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
}
