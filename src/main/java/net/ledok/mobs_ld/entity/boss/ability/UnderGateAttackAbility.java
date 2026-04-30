package net.ledok.mobs_ld.entity.boss.ability;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.boss.AbilityDefinition;
import net.ledok.mobs_ld.entity.boss.BaseBossMob;
import net.ledok.mobs_ld.entity.boss.TriggerCondition;
import net.ledok.mobs_ld.entity.boss.mob.VecnaTheSecond;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class UnderGateAttackAbility extends AbilityDefinition {
    private boolean firstCast = true;
    private AttackZoneDisplay trackerDisplay;
    private boolean undergroundStarted = false;

    @Override
    public String id() {
        return "under_gate";
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
        if (firstCast) {
            return new TriggerCondition.OnPhaseEntry();
        }
        return new TriggerCondition.OnTimer(600);
    }

    @Override
    public int cooldownTicks() {
        return 600;
    }

    @Override
    public AttackZone zone() {
        return new AttackZone.CircleTarget(100.0F, 8.0F);
    }

    @Override
    public int windupTicks() {
        return 40;
    }

    @Override
    public AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.DEFAULT, 0xFF1A0033, 0xFF6600CC);
    }

    @Override
    public void onWindupStart(ServerLevel world, BaseBossMob boss) {
        undergroundStarted = false;
    }

    @Override
    public void onWindupTick(ServerLevel world, BaseBossMob boss, int windupTimer) {
        if (!(boss instanceof VecnaTheSecond vecna) || undergroundStarted || windupTimer != 20) {
            return;
        }
        undergroundStarted = true;

        ServerPlayer target = world.getEntitiesOfClass(
                        ServerPlayer.class,
                        boss.getBoundingBox().inflate(boss.getAttributeValue(Attributes.FOLLOW_RANGE))
                ).stream()
                .min(Comparator.comparingDouble(ServerPlayer::getHealth))
                .orElse(vecna.getTarget() instanceof ServerPlayer p ? p : null);

        vecna.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 2400, 0, false, false));
        vecna.setDamageImmune(true);
        vecna.setIsUnderground(true);
        vecna.setUndergroundTarget(target);

        trackerDisplay = AttackZoneDisplay.spawn(
                world,
                vecna.position().add(0.0, 0.05, 0.0),
                vecna.getYRot(),
                new AttackZone.Circle(0.75F),
                new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.DEFAULT, 0xFF000000, 0xFF111111),
                1
        );
        trackerDisplay.setAlwaysRender(true);
        vecna.setTrackerDisplay(trackerDisplay);
    }

    @Override
    public void onActivate(ServerLevel world, BaseBossMob boss, Vec3 zoneOrigin, float yaw) {
        if (!(boss instanceof VecnaTheSecond vecna)) {
            return;
        }
        firstCast = false;

        vecna.removeEffect(MobEffects.INVISIBILITY);
        vecna.setDamageImmune(false);
        vecna.setIsUnderground(false);
        vecna.setUndergroundTarget(null);
        vecna.getNavigation().stop();

        if (trackerDisplay != null) {
            trackerDisplay.remove();
            trackerDisplay = null;
        }
        vecna.setTrackerDisplay(null);

        float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
        boss.applyZoneDamage(zone(), zoneOrigin, yaw, damage);

        List<ServerPlayer> hit = world.getEntitiesOfClass(ServerPlayer.class, new AABB(zoneOrigin, zoneOrigin).inflate(8.0));
        for (ServerPlayer player : hit) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
        }
    }

    @Override
    public void onEnd(ServerLevel world, BaseBossMob boss) {
        if (boss instanceof VecnaTheSecond vecna) {
            vecna.removeEffect(MobEffects.INVISIBILITY);
            vecna.setDamageImmune(false);
            vecna.setIsUnderground(false);
            vecna.setUndergroundTarget(null);
            vecna.setTrackerDisplay(null);
        }
        if (trackerDisplay != null) {
            trackerDisplay.remove();
            trackerDisplay = null;
        }
        undergroundStarted = false;
    }
}
