package net.ledok.mobs_ld.entity.boss;

import java.util.List;

public record EnrageConfig(
        float hpLossThreshold,
        int timeWindowTicks,
        float damageMultiplier,
        float speedMultiplier,
        float cooldownMultiplier,
        List<String> unlockedAbilityIds
) {
}
