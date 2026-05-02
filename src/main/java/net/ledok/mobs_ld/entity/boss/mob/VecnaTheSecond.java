package net.ledok.mobs_ld.entity.boss.mob;

import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.boss.*;
import net.ledok.mobs_ld.entity.boss.ability.UnderGateAttackAbility;
import net.ledok.mobs_ld.entity.boss.ability.UnderWorldAbility;
import net.ledok.mobs_ld.entity.boss.ability.UnderGateWhipAbility;
import net.ledok.mobs_ld.entity.boss.ability.UnderGateWhipPhase2Ability;
import net.ledok.mobs_ld.entity.boss.ability.UnderGroundWhipMadnessAbility;
import net.ledok.mobs_ld.entity.boss.ability.VecnaCircleRaysAbility;
import net.ledok.mobs_ld.entity.boss.ability.VecnaWhipMadnessStateAbility;
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
    private static final int UNDERGROUND_MAX_TICKS = 600;
    private boolean underground = false;
    private boolean readyToSurface = false;
    private int globalAttackLockout = 0;
    private int whipMadnessTicks = 0;
    private int damageImmuneTicks = 0;
    private int undergroundImmunityBudget = 0;
    private String pendingWhipMadnessCooldownId = null;
    private int pendingWhipMadnessCooldownTicks = 0;
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
                "whip_madness", new UnderGroundWhipMadnessAbility(),
                "whip_madness_state_p1", new VecnaWhipMadnessStateAbility("whip_madness_state_p1", 1200, 200, 300),
                "whip_madness_state_p2", new VecnaWhipMadnessStateAbility("whip_madness_state_p2", 700, 400, 600),
                "under_world", new UnderWorldAbility(),
                "under_gate_attack", new UnderGateAttackAbility(),
                "rays_16", new VecnaCircleRaysAbility(16, 300),
                "rays_24", new VecnaCircleRaysAbility(24, 200)
        );
    }

    @Override
    protected List<BossPhase> definePhases() {
        return List.of(
                new BossPhase(1.0F, new ArrayList<>(List.of(
                        "whip",
                        "whip_madness",
                        "whip_madness_state_p1",
                        "rays_16"
                )), MovementType.FREE, true, DamageProfile.NONE),
                new BossPhase(
                        0.75F,
                        new ArrayList<>(List.of(
                                "whip_phase2",
                                "whip_madness",
                                "whip_madness_state_p2",
                                "under_world",
                                "under_gate_attack",
                                "rays_24"
                        )),
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
        if (whipMadnessTicks > 0) {
            whipMadnessTicks--;
            if (whipMadnessTicks <= 0 && pendingWhipMadnessCooldownId != null) {
                setCooldown(pendingWhipMadnessCooldownId, pendingWhipMadnessCooldownTicks);
                pendingWhipMadnessCooldownId = null;
                pendingWhipMadnessCooldownTicks = 0;
            }
        }
        if (damageImmuneTicks > 0) {
            damageImmuneTicks--;
        }
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (damageImmuneTicks > 0) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public int getGlobalAttackLockout() {
        return globalAttackLockout;
    }

    @Override
    public boolean isMovementLocked() {
        return underground || isWhipMadnessActive();
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

    public boolean isWhipMadnessActive() {
        return whipMadnessTicks > 0;
    }

    public void setWhipMadnessTicks(int whipMadnessTicks) {
        this.whipMadnessTicks = Math.max(0, whipMadnessTicks);
        if (this.whipMadnessTicks > 0) {
            addDamageImmuneTicks(this.whipMadnessTicks);
        }
    }

    public void startWhipMadnessState(String cooldownAbilityId, int durationTicks, int cooldownAfterEndTicks) {
        pendingWhipMadnessCooldownId = cooldownAbilityId;
        pendingWhipMadnessCooldownTicks = Math.max(0, cooldownAfterEndTicks);
        setWhipMadnessTicks(durationTicks);
    }

    public void addDamageImmuneTicks(int ticks) {
        damageImmuneTicks = Math.max(damageImmuneTicks, Math.max(0, ticks));
    }

    public void consumeDamageImmuneTicks(int ticks) {
        damageImmuneTicks = Math.max(0, damageImmuneTicks - Math.max(0, ticks));
    }

    public void startUndergroundImmunityWindow(int emergeTicks) {
        undergroundImmunityBudget = UNDERGROUND_MAX_TICKS;
        addDamageImmuneTicks(UNDERGROUND_MAX_TICKS + Math.max(0, emergeTicks));
    }

    public void tickUndergroundImmunityBudget() {
        if (undergroundImmunityBudget > 0) {
            undergroundImmunityBudget--;
        }
    }

    public void startEmergingImmunityPhase() {
        if (undergroundImmunityBudget > 0) {
            consumeDamageImmuneTicks(undergroundImmunityBudget);
            undergroundImmunityBudget = 0;
        }
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

    @Override
    public boolean isPushable() {
        return !underground && super.isPushable();
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
        if (!underground) {
            super.doPush(entity);
        }
    }

    @Override
    public void push(double x, double y, double z) {
        if (!underground) {
            super.push(x, y, z);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 550.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }
}
