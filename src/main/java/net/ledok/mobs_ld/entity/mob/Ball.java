package net.ledok.mobs_ld.entity.mob;

import net.ledok.mobs_ld.entity.ai.BallBurstGoal;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class Ball extends Monster {
    private static final EntityDataAccessor<Boolean> BURSTING =
            SynchedEntityData.defineId(Ball.class, EntityDataSerializers.BOOLEAN);

    /** Full length of the plate_burst clip, in ticks (2.5s). The burst goal runs for exactly this long. */
    public static final int BURST_DURATION_TICKS = 50;
    /** Telegraph fill time: the warning grows over ticks 0..20, then the plates fling out and damage begins. */
    public static final int BURST_WINDUP_TICKS = 20;
    /** Elapsed tick when the damage zone stops being active. The zone persists across ticks 20..30. */
    public static final int BURST_DAMAGE_END_TICK = 30;
    /** Elapsed tick when the plates begin flinging outward (1.0s into the clip) — per-plate sweep start. */
    public static final int BURST_PLATE_OUT_START_TICK = 20;
    /** Elapsed tick when the plates reach full extension (1.29s into the clip) — per-plate sweep peak. */
    public static final int BURST_PLATE_PEAK_TICK = 26;

    /** Which flavour of the plate-burst AOE to run. Flip {@link #burstMode()} (a method-body change) to compare. */
    public enum BurstMode {
        /** A single telegraphed circle around the ball, using the shared AttackZoneDisplay warning. */
        TELEGRAPHED_CIRCLE,
        /** Each of the 14 plates is its own moving hitbox that hurts whatever it sweeps through. */
        PER_PLATE_HITBOX
    }

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState burstAnimationState = new AnimationState();

    /** Server-side cooldown gating the next plate burst. */
    private int burstCooldown = 0;
    /** Client-only: tracks the synced bursting flag's edge so each burst restarts the clip from frame 0. */
    private boolean clientBurstingPrev = false;

    public Ball(EntityType<? extends Ball> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new BallBurstGoal(this));
        // The "roll" attack: chase the player and deal contact damage when it reaches them. The roll animation
        // plays automatically while it moves (driven by limbSwingAmount in the model).
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        // The threat-targeting goal (priority 0) is installed for every mob by ThreatEvents on entity load.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // --- Plate-burst tuning (read by BallBurstGoal). Override-style methods so they HotSwap cleanly. ---

    /** The active burst mode. Change the returned value and HotSwap to switch flavours live. */
    public BurstMode burstMode() {
        return BurstMode.TELEGRAPHED_CIRCLE;
    }

    /** Radius of the circle-mode AOE, in blocks (also the broad search radius for per-plate mode). */
    public float burstRadius() {
        return 3.0F;
    }

    /** Damage dealt by the plate burst (both modes). */
    public float burstDamage() {
        return 6.0F;
    }

    /** Cooldown between bursts, in ticks. */
    public int burstCooldownTicks() {
        return 100;
    }

    /** How close the target must be (blocks) for the ball to commit to a burst. */
    public double burstTriggerRange() {
        return 1;
    }

    /** Horizontal radius, in blocks, of each individual plate's hitbox in PER_PLATE_HITBOX mode. */
    public double plateHitRadius() {
        return 0.8;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BURSTING, false);
    }

    public boolean isBursting() {
        return this.entityData.get(BURSTING);
    }

    public void setBursting(boolean bursting) {
        this.entityData.set(BURSTING, bursting);
    }

    public int getBurstCooldown() {
        return this.burstCooldown;
    }

    public void setBurstCooldown(int burstCooldown) {
        this.burstCooldown = burstCooldown;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.burstCooldown > 0) {
            this.burstCooldown--;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.idleAnimationState.startIfStopped(this.tickCount);
            boolean bursting = isBursting();
            // Restart the clip from frame 0 on each rising edge so every burst animates (startIfStopped would
            // leave the state "started" on the frozen last frame and skip the replay).
            if (bursting && !this.clientBurstingPrev) {
                this.burstAnimationState.start(this.tickCount);
            } else if (!bursting && this.clientBurstingPrev) {
                this.burstAnimationState.stop();
            }
            this.clientBurstingPrev = bursting;
        }
    }
}
