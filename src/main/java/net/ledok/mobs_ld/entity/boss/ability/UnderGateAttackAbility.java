package net.ledok.mobs_ld.entity.boss.ability;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.boss.AbilityDefinition;
import net.ledok.mobs_ld.entity.boss.BaseBossMob;
import net.ledok.mobs_ld.entity.boss.TriggerCondition;
import net.ledok.mobs_ld.entity.boss.mob.VecnaTheSecond;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

public class UnderGateAttackAbility extends AbilityDefinition {
    private static final ResourceLocation UNDERGROUND_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("mobs_ld", "vecna_underground_speed");

    @Override
    public String id() {
        return "under_gate_attack";
    }

    @Override
    public int priority() {
        return 100;
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
        return vecna.isUnderground()
                && vecna.isReadyToSurface()
                && vecna.getGlobalAttackLockout() <= 0;
    }

    @Override
    public AttackZone zone() {
        return new AttackZone.Circle(12.0F);
    }

    @Override
    public int windupTicks() {
        return 30;
    }

    @Override
    public int damagePersistTicks() {
        return 0;
    }

    @Override
    public AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.GROW, 0xFF1A0033, 0xFF6600CC);
    }

    @Override
    public boolean grantsImmunityDuringWindup() {
        return true;
    }

    @Override
    public void onWindupStart(ServerLevel world, BaseBossMob boss) {
        if (boss instanceof VecnaTheSecond vecna) {
            vecna.setReadyToSurface(false);
            vecna.getNavigation().stop();
            vecna.setDamageImmune(true);
        }
    }

    @Override
    public void onActivate(ServerLevel world, BaseBossMob boss, net.minecraft.world.phys.Vec3 zoneOrigin, float yawDegrees) {
        if (!(boss instanceof VecnaTheSecond vecna)) {
            return;
        }

        vecna.removeEffect(MobEffects.INVISIBILITY);
        AttributeInstance speed = vecna.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(UNDERGROUND_SPEED_ID);
        }
        vecna.setIsUnderground(false);
        vecna.setDamageImmune(false);
        vecna.setUndergroundTarget(null);

        AttackZoneDisplay tracker = vecna.getTrackerDisplay();
        if (tracker != null) {
            tracker.remove();
            vecna.setTrackerDisplay(null);
        }

        AABB damageBox = new AABB(vecna.blockPosition()).inflate(12.0);
        float damage = (float) vecna.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (ServerPlayer player : world.getEntitiesOfClass(ServerPlayer.class, damageBox)) {
            player.hurt(world.damageSources().mobAttack(vecna), damage);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        }

        vecna.setGlobalAttackLockout(10);
    }

    @Override
    public void onEnd(ServerLevel world, BaseBossMob boss) {
        if (boss instanceof VecnaTheSecond vecna) {
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

            vecna.setCooldown("under_world", 600);
            vecna.setCooldown("under_gate_attack", 600);
        }
    }
}
