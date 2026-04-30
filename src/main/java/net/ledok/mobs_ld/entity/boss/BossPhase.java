package net.ledok.mobs_ld.entity.boss;

import java.util.List;

public record BossPhase(
        float hpThreshold,
        List<String> abilityIds,
        MovementType movementType,
        boolean canRotate,
        DamageProfile damageProfile
) {
}
