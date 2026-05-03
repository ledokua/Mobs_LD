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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class UnderGroundWhipMadnessAbility extends AbilityDefinition {
    private static final AttackZone DAMAGE_ZONE = new AttackZone.Circle(1.6F);
    private final List<AttackZoneDisplay> displays = new ArrayList<>();
    private final List<Vec3> lockedOrigins = new ArrayList<>();

    @Override
    public String id() {
        return "whip_madness";
    }

    @Override
    public int priority() {
        return 40;
    }

    @Override
    public boolean isPassive() {
        return true;
    }

    @Override
    public TriggerCondition trigger() {
        return new TriggerCondition.OnTimer(0);
    }

    @Override
    public int cooldownTicks() {
        return 12;
    }

    @Override
    public int windupTicks() {
        return 7;
    }

    @Override
    public AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.GROW, 0xFF1A5C1A, 0xFF00FF44);
    }

    @Override
    public boolean canUse(ServerLevel world, BaseBossMob boss) {
        if (!(boss instanceof VecnaTheSecond vecna) || vecna.isUnderground() || !vecna.isWhipMadnessActive()) {
            return false;
        }
        double followRange = boss.getAttributeValue(Attributes.FOLLOW_RANGE);
        return !world.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(followRange)).isEmpty();
    }

    @Override
    public void onWindupStart(ServerLevel world, BaseBossMob boss) {
        clearDisplays();
        lockedOrigins.clear();
        double followRange = boss.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB range = boss.getBoundingBox().inflate(followRange);
        for (ServerPlayer player : world.getEntitiesOfClass(ServerPlayer.class, range)) {
            if (!player.isAlive() || player.isRemoved()) {
                continue;
            }
            Vec3 origin = boss.snapToGround(resolveTargetOrigin(player));
            lockedOrigins.add(origin);
            displays.add(AttackZoneDisplay.spawn(
                    world,
                    origin,
                    boss.getYRot(),
                    DAMAGE_ZONE,
                    displayConfig(),
                    windupTicks()
            ));
        }
    }

    @Override
    public void onWindupTick(ServerLevel world, BaseBossMob boss, int windupTimer) {
        for (AttackZoneDisplay display : displays) {
            display.update(windupTimer, Math.max(1, windupTicks()));
            if (windupTimer <= 2) {
                display.setBrightRed();
            }
        }
    }

    @Override
    public void onActivate(ServerLevel world, BaseBossMob boss, Vec3 zoneOrigin, float yawDegrees) {
        float damage = (float) (boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * damageScale());
        for (Vec3 origin : lockedOrigins) {
            boss.applyZoneDamage(DAMAGE_ZONE, origin, yawDegrees, damage);
        }
    }

    @Override
    public void onEnd(ServerLevel world, BaseBossMob boss) {
        clearDisplays();
        lockedOrigins.clear();
    }

    private void clearDisplays() {
        for (AttackZoneDisplay display : displays) {
            display.remove();
        }
        displays.clear();
    }
}
