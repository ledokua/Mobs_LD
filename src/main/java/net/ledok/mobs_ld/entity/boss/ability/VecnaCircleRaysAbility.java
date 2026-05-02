package net.ledok.mobs_ld.entity.boss.ability;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.boss.AbilityDefinition;
import net.ledok.mobs_ld.entity.boss.BaseBossMob;
import net.ledok.mobs_ld.entity.boss.TriggerCondition;
import net.ledok.mobs_ld.entity.boss.mob.VecnaTheSecond;
import net.minecraft.server.level.ServerLevel;

public class VecnaCircleRaysAbility extends AbilityDefinition {
    private final int rayCount;
    private final int cooldown;

    public VecnaCircleRaysAbility(int rayCount, int cooldown) {
        int normalizedRayCount = Math.max(2, rayCount);
        this.rayCount = normalizedRayCount % 2 == 0 ? normalizedRayCount : normalizedRayCount + 1;
        this.cooldown = Math.max(0, cooldown);
    }

    @Override
    public String id() {
        return "rays_" + rayCount;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean isPassive() {
        return false;
    }

    @Override
    public TriggerCondition trigger() {
        return new TriggerCondition.OnTimer(cooldown);
    }

    @Override
    public int cooldownTicks() {
        return cooldown;
    }

    @Override
    public int initialCooldown() {
        return cooldown;
    }

    @Override
    public int windupTicks() {
        return 15;
    }

    @Override
    public int damagePersistTicks() {
        return 5;
    }

    @Override
    public double damageScale() {
        return 1.5;
    }

    @Override
    public boolean canMoveWhilePersisting() {
        return false;
    }

    @Override
    public AttackZone zone() {
        return new AttackZone.CircleRays(rayCount, 20.0F, 0.5F);
    }

    @Override
    public AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(
                AttackDisplayConfig.AnimationStyle.SWEEP,
                0xFF1A0033,
                0xFF6600CC
        );
    }

    @Override
    public boolean canUse(ServerLevel world, BaseBossMob boss) {
        return boss instanceof VecnaTheSecond vecna && !vecna.isUnderground();
    }
}
