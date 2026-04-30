package net.ledok.mobs_ld.entity.boss.mob;

import net.ledok.mobs_ld.entity.boss.*;
import net.ledok.mobs_ld.entity.boss.ability.UnderGateWhipAbility;
import net.ledok.mobs_ld.entity.boss.ability.UnderGateWhipPhase2Ability;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VecnaTheSecond extends BaseBossMob {
    public VecnaTheSecond(EntityType<? extends VecnaTheSecond> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected Map<String, AbilityDefinition> defineAbilities() {
        return Map.of(
                "whip", new UnderGateWhipAbility(),
                "whip_phase2", new UnderGateWhipPhase2Ability()
        );
    }

    @Override
    protected List<BossPhase> definePhases() {
        return List.of(
                new BossPhase(1.0F, new ArrayList<>(List.of("whip")), MovementType.FREE, true, DamageProfile.NONE),
                new BossPhase(0.75F, new ArrayList<>(List.of("whip_phase2")), MovementType.FREE, true, DamageProfile.NONE)
        );
    }

    @Override
    protected EnrageConfig defineEnrage() {
        return null;
    }

    @Override
    protected double idealAttackDistance() {
        return 5.0;
    }

    @Override
    protected Component getBossBarName() {
        return Component.literal("Vecna The Second");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 350.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }
}
