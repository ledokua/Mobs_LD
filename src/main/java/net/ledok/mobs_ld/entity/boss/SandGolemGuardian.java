package net.ledok.mobs_ld.entity.boss;

import net.ledok.mobs_ld.client.ScreenShake;
import net.ledok.mobs_ld.entity.BaseDungeonMob;
import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class SandGolemGuardian extends BaseDungeonMob {
    private static final EntityDataAccessor<Boolean> ATTACKING =
            SynchedEntityData.defineId(SandGolemGuardian.class, EntityDataSerializers.BOOLEAN);

    /** Full length of the attack_pound clip, in ticks (~1.2s). */
    private static final int ATTACK_DURATION_TICKS = 24;

    /** Tick within attack_pound when the hands hit the ground — aligned with the telegraph's damage frame. */
    private static final int ANIM_IMPACT_TICK = 10;

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState attackAnimationState = new AnimationState();

    /** Counts down the attack_pound clip (clears the synced flag at 0). */
    private int attackTimer = 0;
    /** Delays the swing animation so its impact frame lands on the telegraph's damage frame; -1 = inactive. */
    private int smashAnimDelay = -1;

    /** Client-only: tracks the synced attack flag's rising edge so the slam screen-shake fires once per swing. */
    private boolean clientAttackingPrev = false;
    /** Client-only: ticks until the hands hit the ground (and the camera shakes); -1 = inactive. */
    private int slamShakeDelay = -1;

    private final ServerBossEvent bossEvent =
            new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);

    public SandGolemGuardian(EntityType<? extends SandGolemGuardian> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.xpReward = 50;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return BaseDungeonMob.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 16.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 10.0)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    // --- Telegraphed attack configuration (drives TelegraphedAttackGoal from BaseDungeonMob) ---

    @Override
    protected AttackZone attackZone() {
        // Circle that drops where the hands land — a fixed distance in front of the golem (see zoneForwardOffset).
        return new AttackZone.Circle(3.0F);
    }

    @Override
    protected double zoneForwardOffset() {
        return 3.5;
    }

    @Override
    protected double lateralStandoffOffset() {
        // 0 = stand directly facing the player between attacks. A non-zero sideways offset makes the
        // standing spot rotate around the player as the golem strafes toward it, which turns into an
        // endless orbit instead of closing to attack range. Keep at 0 unless holdPosition is reworked.
        return 0.0;
    }

    @Override
    protected boolean lockFacingDuringWindup() {
        return true;
    }

    @Override
    protected int windupTicks() {
        return 10;
    }

    @Override
    protected int attackCooldownTicks() {
        return 50;
    }

    @Override
    protected int damagePersistTicks() {
        return 0;
    }

    @Override
    protected double idealAttackDistance() {
        return 3.5;
    }

    @Override
    protected AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(AttackDisplayConfig.AnimationStyle.GROW, 0xFFB8860B, 0xFFFFC04D);
    }

    /** The telegraph goal flips this on at wind-up start; we schedule the swing so its slam lands on impact. */
    @Override
    public void setWindingUp(boolean windingUp) {
        super.setWindingUp(windingUp);
        if (this.level().isClientSide) {
            return;
        }
        if (windingUp) {
            this.smashAnimDelay = Math.max(0, getWindupTicks() - ANIM_IMPACT_TICK);
        } else {
            this.smashAnimDelay = -1; // swing was interrupted before it began
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACKING, false);
    }

    public boolean isAttacking() {
        return this.entityData.get(ATTACKING);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            setupAnimationStates();
            tickSlamShake();
        } else {
            tickAttackAnimation();
        }
    }

    /** Fires the camera shake on the frame the hands actually strike the ground (impact tick of the swing clip). */
    private void tickSlamShake() {
        boolean attacking = isAttacking();
        if (attacking && !this.clientAttackingPrev) {
            this.slamShakeDelay = ANIM_IMPACT_TICK;
        }
        this.clientAttackingPrev = attacking;
        if (this.slamShakeDelay >= 0 && this.slamShakeDelay-- == 0) {
            ScreenShake.shakeFromSlam(this.getX(), this.getY(), this.getZ());
        }
    }

    private void tickAttackAnimation() {
        if (this.smashAnimDelay >= 0 && this.smashAnimDelay-- == 0) {
            this.attackTimer = ATTACK_DURATION_TICKS;
            this.entityData.set(ATTACKING, true);
        }
        if (this.attackTimer > 0 && --this.attackTimer == 0) {
            this.entityData.set(ATTACKING, false);
        }
    }

    private void setupAnimationStates() {
        this.idleAnimationState.startIfStopped(this.tickCount);
        if (isAttacking()) {
            this.attackAnimationState.startIfStopped(this.tickCount);
        } else {
            this.attackAnimationState.stop();
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
