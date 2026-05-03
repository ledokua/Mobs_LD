package net.ledok.mobs_ld.entity.boss.ability;

import net.ledok.mobs_ld.entity.boss.AbilityDefinition;
import net.ledok.mobs_ld.entity.boss.BaseBossMob;
import net.ledok.mobs_ld.entity.boss.TriggerCondition;
import net.ledok.mobs_ld.entity.boss.mob.VecnaTheSecond;
import net.minecraft.server.level.ServerLevel;

public class VecnaWhipMadnessStateAbility extends AbilityDefinition {
    private final String id;
    private final int cooldownTicks;
    private final int initialCooldownTicks;
    private final int durationTicks;

    public VecnaWhipMadnessStateAbility(String id, int cooldownTicks, int initialCooldownTicks, int durationTicks) {
        this.id = id;
        this.cooldownTicks = Math.max(0, cooldownTicks);
        this.initialCooldownTicks = Math.max(0, initialCooldownTicks);
        this.durationTicks = Math.max(1, durationTicks);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public boolean isPassive() {
        return false;
    }

    @Override
    public TriggerCondition trigger() {
        return new TriggerCondition.OnTimer(0);
    }

    @Override
    public int cooldownTicks() {
        return cooldownTicks;
    }

    @Override
    public int initialCooldown() {
        return initialCooldownTicks;
    }

    @Override
    public boolean startsCooldownOnActivate() {
        return false;
    }

    @Override
    public boolean canUse(ServerLevel world, BaseBossMob boss) {
        if (!(boss instanceof VecnaTheSecond vecna)) {
            return false;
        }
        return !vecna.isUnderground()
                && !vecna.isWhipMadnessActive()
                && boss.getTarget() != null
                && boss.getTarget().isAlive()
                && !boss.getTarget().isRemoved();
    }

    @Override
    public void onActivate(ServerLevel world, BaseBossMob boss, net.minecraft.world.phys.Vec3 zoneOrigin, float yawDegrees) {
        if (boss instanceof VecnaTheSecond vecna) {
            vecna.startWhipMadnessState(id(), durationTicks, boss.resolvedCooldown(this));
        }
    }
}
