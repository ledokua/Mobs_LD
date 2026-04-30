package net.ledok.mobs_ld.entity.boss.mob;

import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.boss.*;
import net.ledok.mobs_ld.entity.boss.ability.UnderGateAttackAbility;
import net.ledok.mobs_ld.entity.boss.ability.UnderGateWhipAbility;
import net.ledok.mobs_ld.entity.boss.ability.UnderGateWhipPhase2Ability;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VecnaTheSecond extends BaseBossMob {
    private boolean underground = false;
    private ServerPlayer undergroundTarget;
    private AttackZoneDisplay trackerDisplay;
    private int undergroundTimer = -1;

    public VecnaTheSecond(EntityType<? extends VecnaTheSecond> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected Map<String, AbilityDefinition> defineAbilities() {
        return Map.of(
                "whip", new UnderGateWhipAbility(),
                "whip_phase2", new UnderGateWhipPhase2Ability(),
                "under_gate", new UnderGateAttackAbility()
        );
    }

    @Override
    protected List<BossPhase> definePhases() {
        return List.of(
                new BossPhase(1.0F, new ArrayList<>(List.of("whip")), MovementType.FREE, true, DamageProfile.NONE),
                new BossPhase(0.75F, new ArrayList<>(List.of("whip_phase2", "under_gate")), MovementType.FREE, true, DamageProfile.NONE)
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

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (underground) {
            if (undergroundTarget == null || !undergroundTarget.isAlive() || undergroundTarget.isRemoved()) {
                undergroundTarget = selectLowestHpTarget();
                if (undergroundTarget == null) {
                    forceActivateCurrentAbility();
                    return;
                }
            }

            getNavigation().moveTo(
                    undergroundTarget.getX(),
                    undergroundTarget.getY(),
                    undergroundTarget.getZ(),
                    1.2D
            );
            if (trackerDisplay != null) {
                trackerDisplay.updatePosition(position().add(0, 0.05, 0));
            }
            if (distanceToSqr(undergroundTarget) <= 1.0) {
                forceActivateCurrentAbility();
            }
        }
        if (underground && undergroundTimer >= 0) {
            undergroundTimer--;
            if (undergroundTimer <= 0) {
                forceActivateCurrentAbility();
            }
        }
    }

    private ServerPlayer selectLowestHpTarget() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        double followRange = getAttributeValue(Attributes.FOLLOW_RANGE);
        return serverLevel.getEntitiesOfClass(
                        ServerPlayer.class,
                        getBoundingBox().inflate(followRange)
                ).stream()
                .filter(player -> player.isAlive() && !player.isRemoved())
                .min(java.util.Comparator.comparingDouble(ServerPlayer::getHealth))
                .orElse(null);
    }

    public boolean isUnderground() {
        return underground;
    }

    public void setIsUnderground(boolean underground) {
        this.underground = underground;
        if (!underground) {
            undergroundTimer = -1;
        }
    }

    public ServerPlayer getUndergroundTarget() {
        return undergroundTarget;
    }

    public void setUndergroundTarget(ServerPlayer undergroundTarget) {
        this.undergroundTarget = undergroundTarget;
        if (undergroundTarget != null) {
            undergroundTimer = 600;
        }
    }

    public AttackZoneDisplay getTrackerDisplay() {
        return trackerDisplay;
    }

    public void setTrackerDisplay(AttackZoneDisplay trackerDisplay) {
        this.trackerDisplay = trackerDisplay;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 350.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }
}
