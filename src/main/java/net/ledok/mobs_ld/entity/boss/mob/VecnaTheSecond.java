package net.ledok.mobs_ld.entity.boss.mob;

import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.boss.*;
import net.ledok.mobs_ld.entity.boss.ability.UnderGateAttackAbility;
import net.ledok.mobs_ld.entity.boss.ability.UnderWorldAbility;
import net.ledok.mobs_ld.entity.boss.ability.UnderGateWhipAbility;
import net.ledok.mobs_ld.entity.boss.ability.UnderGateWhipPhase2Ability;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VecnaTheSecond extends BaseBossMob {
    private boolean underground = false;
    private boolean readyToSurface = false;
    private int globalAttackLockout = 0;
    private AttackZoneDisplay trackerDisplay;
    private ServerPlayer undergroundTarget;

    public VecnaTheSecond(EntityType<? extends VecnaTheSecond> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected Map<String, AbilityDefinition> defineAbilities() {
        return Map.of(
                "whip", new UnderGateWhipAbility(),
                "whip_phase2", new UnderGateWhipPhase2Ability(),
                "under_world", new UnderWorldAbility(),
                "under_gate_attack", new UnderGateAttackAbility()
        );
    }

    @Override
    protected List<BossPhase> definePhases() {
        return List.of(
                new BossPhase(1.0F, new ArrayList<>(List.of("whip")), MovementType.FREE, true, DamageProfile.NONE),
                new BossPhase(
                        0.75F,
                        new ArrayList<>(List.of("whip_phase2", "under_world", "under_gate_attack")),
                        MovementType.FREE,
                        true,
                        DamageProfile.NONE
                )
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
        if (globalAttackLockout > 0) {
            globalAttackLockout--;
        }
    }

    @Override
    public int getGlobalAttackLockout() {
        return globalAttackLockout;
    }

    @Override
    public boolean isMovementLocked() {
        return underground;
    }

    public boolean isUnderground() {
        return underground;
    }

    public void setIsUnderground(boolean underground) {
        this.underground = underground;
    }

    public boolean isReadyToSurface() {
        return readyToSurface;
    }

    public void setReadyToSurface(boolean readyToSurface) {
        this.readyToSurface = readyToSurface;
    }

    public void setGlobalAttackLockout(int globalAttackLockout) {
        this.globalAttackLockout = Math.max(0, globalAttackLockout);
    }

    public AttackZoneDisplay getTrackerDisplay() {
        return trackerDisplay;
    }

    public void setTrackerDisplay(AttackZoneDisplay trackerDisplay) {
        this.trackerDisplay = trackerDisplay;
    }

    public ServerPlayer getUndergroundTarget() {
        return undergroundTarget;
    }

    public void setUndergroundTarget(ServerPlayer undergroundTarget) {
        this.undergroundTarget = undergroundTarget;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 350.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }
}
