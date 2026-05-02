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
        return new TriggerCondition.OnTimer(15);
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
        return 7;
    }

    @Override
    public AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.GROW, 0xFF8B0000, 0xFFFF0000);
    }

    @Override
    public boolean canUse(ServerLevel world, BaseBossMob boss) {
        return boss instanceof VecnaTheSecond vecna && !vecna.isUnderground();
    }

    @Override
    public Vec3 resolveTargetOrigin(LivingEntity target) {
        Vec3 velocity = target.getDeltaMovement();
        if (velocity.horizontalDistanceSqr() > 0.0001) {
            Vec3 direction = new Vec3(velocity.x, 0.0, velocity.z).normalize();
            return target.position().add(direction.scale(5));
        }
        return target.position();
    }
}
