package net.ledok.mobs_ld.entity.boss.ability;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.boss.AbilityDefinition;
import net.ledok.mobs_ld.entity.boss.TriggerCondition;

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
        return 13;
    }

    @Override
    public AttackZone zone() {
        return new AttackZone.CircleTarget(20.0F, 1.25F);
    }

    @Override
    public int windupTicks() {
        return 5;
    }

    @Override
    public AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.GROW, 0xFF8B0000, 0xFFFF0000);
    }
}
