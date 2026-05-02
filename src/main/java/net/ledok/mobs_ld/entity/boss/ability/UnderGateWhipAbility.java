package net.ledok.mobs_ld.entity.boss.ability;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.boss.AbilityDefinition;
import net.ledok.mobs_ld.entity.boss.BaseBossMob;
import net.ledok.mobs_ld.entity.boss.TriggerCondition;
import net.ledok.mobs_ld.entity.boss.mob.VecnaTheSecond;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class UnderGateWhipAbility extends AbilityDefinition {
    @Override
    public String id() {
        return "whip";
    }

    @Override
    public int priority() {
        return 10;
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
        return 15;
    }

    @Override
    public AttackZone zone() {
        return new AttackZone.CircleTarget(20.0F, 1.25F);
    }

    @Override
    public int windupTicks() {
        return 15;
    }

    @Override
    public AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.GROW, 0xFF8B0000, 0xFFFF0000);
    }

    @Override
    public boolean canUse(ServerLevel world, BaseBossMob boss) {
        if (!(boss instanceof VecnaTheSecond vecna) || vecna.isUnderground() || vecna.isWhipMadnessActive()) {
            return false;
        }
        return boss.getTarget() != null && boss.getTarget().isAlive() && !boss.getTarget().isRemoved();
    }

    @Override
    public Vec3 resolveTargetOrigin(LivingEntity target) {
        Vec3 known = target.getKnownMovement();
        Vec3 horizontalKnown = new Vec3(known.x, 0.0, known.z);
        if (horizontalKnown.lengthSqr() > 0.0001) {
            return target.position().add(horizontalKnown.normalize().scale(0.4));
        }

        Vec3 delta = target.getDeltaMovement();
        Vec3 horizontalDelta = new Vec3(delta.x, 0.0, delta.z);
        if (horizontalDelta.lengthSqr() > 0.0001) {
            return target.position().add(horizontalDelta.normalize().scale(0.4));
        }
        return target.position();
    }
}
