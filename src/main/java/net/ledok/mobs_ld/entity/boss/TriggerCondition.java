package net.ledok.mobs_ld.entity.boss;

public sealed interface TriggerCondition permits
        TriggerCondition.OnTimer,
        TriggerCondition.OnPhaseEntry,
        TriggerCondition.AtHpThreshold,
        TriggerCondition.RandomFromPool {

    record OnTimer(int intervalTicks) implements TriggerCondition {
    }

    record OnPhaseEntry() implements TriggerCondition {
    }

    record AtHpThreshold(float threshold) implements TriggerCondition {
    }

    record RandomFromPool(int minCooldown, int maxCooldown) implements TriggerCondition {
    }
}
