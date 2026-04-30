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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class UnderGateAttackAbility extends AbilityDefinition {
    private static final net.minecraft.resources.ResourceLocation UNDERGROUND_SPEED_MOD_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mobs_ld", "vecna_underground_speed");

    private boolean firstCast = true;
    private AttackZoneDisplay trackerDisplay;
    private AttackZoneDisplay surfaceDisplay;
    private boolean undergroundStarted = false;
    private Vec3 surfaceOrigin;
    private float surfaceYaw;

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
        return null;
    }

    @Override
    public int windupTicks() {
        return 40;
    }

    @Override
    public int damagePersistTicks() {
        return 40;
    }

    @Override
    public AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.DEFAULT, 0xFF1A0033, 0xFF6600CC);
    }

    @Override
    public void onWindupStart(ServerLevel world, BaseBossMob boss) {
        undergroundStarted = false;
        surfaceOrigin = null;
        surfaceYaw = 0.0F;
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
        AttributeInstance speedAttr = vecna.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(UNDERGROUND_SPEED_MOD_ID);
            speedAttr.addTransientModifier(new AttributeModifier(
                    UNDERGROUND_SPEED_MOD_ID,
                    0.5D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }
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
        AttributeInstance speedAttr = vecna.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(UNDERGROUND_SPEED_MOD_ID);
        }
        vecna.setDamageImmune(false);
        vecna.setIsUnderground(false);
        vecna.setUndergroundTarget(null);
        vecna.getNavigation().stop();

        if (trackerDisplay != null) {
            trackerDisplay.remove();
            trackerDisplay = null;
        }
        vecna.setTrackerDisplay(null);

        surfaceOrigin = vecna.position();
        surfaceYaw = vecna.getYRot();
        surfaceDisplay = AttackZoneDisplay.spawn(
                world,
                surfaceOrigin,
                surfaceYaw,
                new AttackZone.Circle(8.0F),
                displayConfig(),
                damagePersistTicks()
        );
    }

    @Override
    public void onPersistTick(ServerLevel world, BaseBossMob boss, int persistTimer) {
        if (surfaceDisplay != null) {
            surfaceDisplay.update(persistTimer, Math.max(1, damagePersistTicks()));
            if (persistTimer <= 2) {
                surfaceDisplay.setBrightRed();
            }
        }
    }

    @Override
    public void onEnd(ServerLevel world, BaseBossMob boss) {
        if (boss instanceof VecnaTheSecond vecna) {
            vecna.removeEffect(MobEffects.INVISIBILITY);
            AttributeInstance speedAttr = vecna.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.removeModifier(UNDERGROUND_SPEED_MOD_ID);
            }
            vecna.setDamageImmune(false);
            vecna.setIsUnderground(false);
            vecna.setUndergroundTarget(null);
            vecna.setTrackerDisplay(null);
        }
        if (trackerDisplay != null) {
            trackerDisplay.remove();
            trackerDisplay = null;
        }
        if (surfaceDisplay != null) {
            surfaceDisplay.remove();
            surfaceDisplay = null;
        }
        if (surfaceOrigin != null) {
            float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
            boss.applyZoneDamage(new AttackZone.Circle(8.0F), surfaceOrigin, surfaceYaw, damage);
            List<ServerPlayer> hit = world.getEntitiesOfClass(ServerPlayer.class, new AABB(surfaceOrigin, surfaceOrigin).inflate(8.0));
            for (ServerPlayer player : hit) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
        }
        surfaceOrigin = null;
        surfaceYaw = 0.0F;
        undergroundStarted = false;
    }
}
