package net.ledok.mobs_ld.entity.boss.ai;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.boss.AbilityDefinition;
import net.ledok.mobs_ld.entity.boss.BaseBossMob;
import net.ledok.mobs_ld.entity.boss.TriggerCondition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class BossAbilityGoal extends Goal {
    private static final int BRIGHT_WINDOW_TICKS = 2;

    private final BaseBossMob boss;

    private int windupTimer = -1;
    private int damageTimer = -1;
    private boolean activated = false;
    private Vec3 lockedOrigin = Vec3.ZERO;
    private Vec3 lockedTargetPos = Vec3.ZERO;
    private float lockedYaw = 0.0F;
    private final List<AttackZoneDisplay> displays = new ArrayList<>();

    public BossAbilityGoal(BaseBossMob boss) {
        this.boss = boss;
    }

    @Override
    public boolean canUse() {
        if (!(boss.level() instanceof ServerLevel)) {
            return false;
        }
        if (boss.getGlobalAttackLockout() > 0) {
            return false;
        }
        if (boss.getAttackCooldown() > 0) {
            return false;
        }
        AbilityDefinition next = selectNextAbility((ServerLevel) boss.level());
        if (next == null) {
            return false;
        }
        boss.setActiveAbility(next);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return boss.getActiveAbility() != null && (windupTimer >= 0 || damageTimer >= 0);
    }

    @Override
    public void start() {
        AbilityDefinition ability = boss.getActiveAbility();
        if (ability == null || !(boss.level() instanceof ServerLevel world)) {
            return;
        }

        windupTimer = Math.max(0, ability.windupTicks());
        damageTimer = -1;
        activated = false;
        lockedOrigin = boss.position();
        if (ability.zone() instanceof AttackZone.CircleTarget && boss.getTarget() != null) {
            lockedTargetPos = boss.getTarget().position();
        } else {
            lockedTargetPos = Vec3.ZERO;
        }
        if (boss.getTarget() != null) {
            Vec3 toTarget = boss.getTarget().position().subtract(boss.position());
            lockedYaw = toTarget.lengthSqr() > 1.0e-6
                    ? (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z))
                    : boss.getYRot();
        } else {
            lockedYaw = boss.getYRot();
        }

        boss.setWindingUp(true);
        if (ability.grantsImmunityDuringWindup()) {
            boss.setDamageImmune(true);
        }
        ability.onWindupStart(world, boss);

        displays.clear();
        spawnDisplays(world, ability);
    }

    @Override
    public void tick() {
        AbilityDefinition ability = boss.getActiveAbility();
        if (ability == null || !(boss.level() instanceof ServerLevel world)) {
            return;
        }

        if (windupTimer >= 0) {
            windupTimer--;
            ability.onWindupTick(world, boss, windupTimer);
            for (AttackZoneDisplay display : displays) {
                display.update(windupTimer, Math.max(1, ability.windupTicks()));
                if (windupTimer <= BRIGHT_WINDOW_TICKS) {
                    display.setBrightRed();
                }
            }

            if (windupTimer <= 0) {
                boss.setDamageImmune(false);
                damageTimer = Math.max(0, ability.damagePersistTicks());
                if (damageTimer == 0) {
                    activateAbility(ability, world);
                    endAbility(ability, world);
                    return;
                }
                windupTimer = -1;
            }
        } else if (damageTimer >= 0) {
            if (!activated) {
                activateAbility(ability, world);
            }
            ability.onPersistTick(world, boss, damageTimer);
            damageTimer--;
            if (damageTimer < 0) {
                endAbility(ability, world);
            }
        }
    }

    @Override
    public void stop() {
        cancelAbility();
    }

    private void activateAbility(AbilityDefinition ability, ServerLevel world) {
        if (activated) {
            return;
        }
        activated = true;
        boss.consumePhaseEntryAbility(ability.id());
        Vec3 origin = getZoneOrigin(ability);
        ability.onActivate(world, boss, origin, lockedYaw);
        if (ability.trigger() instanceof TriggerCondition.AtHpThreshold) {
            boss.markThresholdAbilityUsed(ability.id());
        }
    }

    private void endAbility(AbilityDefinition ability, ServerLevel world) {
        for (AttackZoneDisplay display : displays) {
            display.remove();
        }
        displays.clear();

        ability.onEnd(world, boss);
        boss.setCooldown(ability.id(), boss.resolvedCooldown(ability));
        boss.setActiveAbility(null);
        boss.setWindingUp(false);
        if (!ability.keepDamageImmuneAfterEnd()) {
            boss.setDamageImmune(false);
        }
        boss.setAttackCooldown(2);

        windupTimer = -1;
        damageTimer = -1;
        activated = false;
    }

    private void cancelAbility() {
        AbilityDefinition ability = boss.getActiveAbility();
        for (AttackZoneDisplay display : displays) {
            display.remove();
        }
        displays.clear();
        windupTimer = -1;
        damageTimer = -1;
        activated = false;

        if (ability != null && boss.level() instanceof ServerLevel world) {
            ability.onEnd(world, boss);
        }
        boss.cancelActiveAbility();
    }

    public void forceActivate() {
        if (boss.getActiveAbility() != null) {
            windupTimer = 0;
        }
    }

    public void endPersistEarly() {
        if (damageTimer >= 0) {
            damageTimer = 0;
        }
    }

    private AbilityDefinition selectNextAbility(ServerLevel world) {
        return boss.getActivePhase().abilityIds().stream()
                .map(boss.getAbilities()::get)
                .filter(Objects::nonNull)
                .filter(a -> !a.isPassive())
                .filter(a -> boss.getCooldown(a.id()) <= 0)
                .filter(boss::canTrigger)
                .filter(a -> a.canUse(world, boss))
                .max(Comparator.comparingInt(AbilityDefinition::priority))
                .orElse(null);
    }

    private Vec3 getZoneOrigin(AbilityDefinition ability) {
        if (ability.zone() instanceof AttackZone.CircleTarget) {
            return lockedTargetPos;
        }
        return lockedOrigin;
    }

    private void spawnDisplays(ServerLevel world, AbilityDefinition ability) {
        AttackZone zone = ability.zone();
        if (zone == null) {
            return;
        }
        AttackDisplayConfig cfg = ability.displayConfig() != null
                ? ability.displayConfig() : AttackDisplayConfig.DEFAULT;

        if (zone instanceof AttackZone.Ring ring) {
            int segments = Math.max(1, ring.rectanglesPerSide());
            float sideLength = ring.radius() * 2.0F;
            float segmentLength = sideLength / segments;
            float half = ring.radius();
            float y = (float) lockedOrigin.y;

            for (int i = 0; i < segments; i++) {
                float t0 = -half + (i + 0.5F) * segmentLength;
                addRingSegmentDisplay(world, cfg, y, t0, -half, 0.0F, ring.rectangleWidth(), segmentLength);
                addRingSegmentDisplay(world, cfg, y, t0, half, 180.0F, ring.rectangleWidth(), segmentLength);
                addRingSegmentDisplay(world, cfg, y, -half, t0, 90.0F, ring.rectangleWidth(), segmentLength);
                addRingSegmentDisplay(world, cfg, y, half, t0, -90.0F, ring.rectangleWidth(), segmentLength);
            }
        } else {
            Vec3 displayOrigin = getZoneOrigin(ability);
            AttackZoneDisplay display = AttackZoneDisplay.spawn(
                    world, displayOrigin, lockedYaw, zone, cfg, Math.max(1, ability.windupTicks())
            );
            displays.add(display);
        }
    }

    private void addRingSegmentDisplay(
            ServerLevel world,
            AttackDisplayConfig cfg,
            float y,
            float localX,
            float localZ,
            float yaw,
            float width,
            float length
    ) {
        Vec3 origin = new Vec3(lockedOrigin.x + localX, y, lockedOrigin.z + localZ);
        AttackZone segmentZone = new AttackZone.Rectangle(width, length, -length * 0.5F);
        AttackZoneDisplay display = AttackZoneDisplay.spawn(
                world, origin, yaw, segmentZone, cfg, Math.max(1, boss.getActiveAbility().windupTicks())
        );
        displays.add(display);
    }
}
