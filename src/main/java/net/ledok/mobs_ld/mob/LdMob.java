package net.ledok.mobs_ld.mob;

import net.ledok.mobs_ld.mob.combat.CombatGoal;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one entity class behind every data-driven mob. Its type-specific facts come from its {@link MobDeclaration};
 * its look comes from the GeckoLib assets named after its entity id (docs/models.md).
 *
 * <p>Animation is two GeckoLib controllers: {@code base} loops idle/walk, and {@code action} plays whatever the
 * server last started through {@link #playAction} (an ability clip, a transition) plus the death clip. The action
 * controller runs second, so it overrides every bone its clip keys. The server only syncs <i>which</i> clip and a
 * sequence number; the client never needs animation timing from the server.
 *
 * <p>Its fight runs on the shared ability engine hooks (MobCombatHostMixin and friends), like any vanilla host; the
 * {@link CombatHost} members here only differ in that the combat exists from construction and its cues drive the
 * action layer and texture variant.
 */
public class LdMob extends Monster implements GeoEntity, CombatHost {
    private static final EntityDataAccessor<String> DATA_ACTION =
            SynchedEntityData.defineId(LdMob.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_ACTION_SEQ =
            SynchedEntityData.defineId(LdMob.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ACTION_SPEED =
            SynchedEntityData.defineId(LdMob.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_WARD_TARGET =
            SynchedEntityData.defineId(LdMob.class, EntityDataSerializers.INT);
    /** Texture variant suffix ("phase2" -> {@code <mob>_phase2.png}); empty = base texture. */
    private static final EntityDataAccessor<String> DATA_TEXTURE =
            SynchedEntityData.defineId(LdMob.class, EntityDataSerializers.STRING);

    private static final String IDLE = "idle";
    private static final String WALK = "walk";
    /** Fallback base loop for small, fast mobs whose movement clip is a run. */
    private static final String RUN = "run";
    private static final String HURT = "hurt";
    private static final String DEATH = "death";

    /** Shared, immutable animation recipes; GeckoLib compares them by content, so caching just saves garbage. */
    private static final Map<String, RawAnimation> LOOPS = new ConcurrentHashMap<>();
    /** Plays a clip with the loop mode authored in its animation file (loop, hold on last frame, or once). */
    private static final Map<String, RawAnimation> AS_AUTHORED = new ConcurrentHashMap<>();

    private final MobDeclaration declaration;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final MobCombat combat = new MobCombat(this);

    /** Client: last action sequence the action controller started, so a repeated clip restarts. */
    private int clientActionSeq = -1;
    /** Client: whether the base layer is on its walk loop (walk playback is speed-matched). */
    private boolean walking;
    /** Client: a hurt flinch is playing for the current hurt flash. */
    private boolean clientHurtPlaying;
    /** Client: world positions of marker bones ({@code hit_/fx_/proj_}) from the last rendered frame. */
    private final Map<String, Vec3> clientMarkerPositions = new HashMap<>();
    /** Client: keyframe effects waiting for the current frame's marker positions (see LdMobRenderer). */
    private final List<Runnable> clientEffects = new ArrayList<>();

    public LdMob(EntityType<? extends LdMob> type, Level level) {
        super(type, level);
        this.declaration = MobDeclarations.of(type);
        if (declaration.body() == MobDeclaration.Body.FLYING) {
            this.moveControl = new FlyingMoveControl(this, 20, true);
            this.setNoGravity(true);
        }
    }

    public MobDeclaration declaration() {
        return declaration;
    }

    @Override
    public boolean isPushable() {
        return declaration.pushable() && super.isPushable();
    }

    public MobCombat combat() {
        return combat;
    }

    @Override
    public MobCombat mobs_ld$combat() {
        return combat;
    }

    @Override
    public MobCombat mobs_ld$combatOrCreate() {
        return combat;
    }

    @Override
    public void mobs_ld$playAction(String clip, double speed) {
        playAction(clip, speed);
    }

    @Override
    public void mobs_ld$stopAction() {
        stopAction();
    }

    @Override
    public void mobs_ld$setVariant(String variant) {
        setTextureVariant(variant);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        // Called from the Mob constructor, before our fields are set — read the declaration from the type.
        if (MobDeclarations.of(getType()).body() == MobDeclaration.Body.FLYING) {
            FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
            navigation.setCanOpenDoors(false);
            navigation.setCanFloat(true);
            return navigation;
        }
        return super.createNavigation(level);
    }

    @Override
    protected void registerGoals() {
        // Runs from the Mob constructor: goals must only reach combat() lazily, once the fields exist.
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CombatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        // Initial aggro; once anyone builds threat, the ThreatTargetGoal added by MobThreatMixin takes over. Players
        // the engine gave up on (unreachable, or anyone while walking home) are skipped by TargetingConditionsMixin.
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Leaves without dying: a puff of smoke, no drops, no {@code on_death}. */
    public void despawnWithPoof() {
        MobCombat.despawnWithPoof(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, "");
        builder.define(DATA_ACTION_SEQ, 0);
        builder.define(DATA_ACTION_SPEED, 1.0F);
        builder.define(DATA_WARD_TARGET, -1);
        builder.define(DATA_TEXTURE, "");
    }

    // --- Actions (server) ---

    /** Starts {@code clip} on the action layer for every watching client, restarting it if already playing. */
    public void playAction(String clip) {
        playAction(clip, 1.0);
    }

    /** As {@link #playAction(String)}, played {@code speed} times faster (phase attack speed). */
    public void playAction(String clip, double speed) {
        entityData.set(DATA_ACTION_SPEED, (float) speed);
        entityData.set(DATA_ACTION, clip);
        entityData.set(DATA_ACTION_SEQ, entityData.get(DATA_ACTION_SEQ) + 1);
    }

    /** Clears the action layer so idle/walk show through again. */
    public void stopAction() {
        entityData.set(DATA_ACTION, "");
    }

    public String currentAction() {
        return entityData.get(DATA_ACTION);
    }

    /** The protected entity's ID, synced only when the ward connects or breaks. */
    public int wardTargetId() {
        return entityData.get(DATA_WARD_TARGET);
    }

    public void setWardTargetId(int id) {
        entityData.set(DATA_WARD_TARGET, id);
    }

    public void setTextureVariant(String variant) {
        entityData.set(DATA_TEXTURE, variant);
    }

    public String textureVariant() {
        return entityData.get(DATA_TEXTURE);
    }

    // --- Death ---

    @Override
    protected void tickDeath() {
        // The on_death timeline (e.g. a bursting add) is ticked just before this by LivingEntityCombatMixin.
        // Vanilla removes the body after 20 ticks; data-driven mobs hold it for their death clip.
        ++deathTime;
        if (deathTime >= declaration.deathTicks() && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60); // vanilla death poof particles
            remove(RemovalReason.KILLED);
        }
    }

    // --- GeckoLib ---

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(withEffects(new AnimationController<>(this, "base", 5, this::baseState)
                .setAnimationSpeedHandler(mob -> mob.walking ? MobClientHooks.get().walkSpeed(mob) : 1.0)));
        controllers.add(new AnimationController<>(this, "hurt", 1, this::hurtState));
        // No blend-in on the action layer: GeckoLib eases bones into a new clip over the transition but fires its
        // keyframe events on the clip's own clock, so with a blend every effect lands ahead of the pose (and the
        // visible swing trails the server's damage). Ability clips start from their authored first frame.
        controllers.add(withEffects(new AnimationController<>(this, "action", 0, this::actionState)
                .setAnimationSpeedHandler(mob -> mob.isDeadOrDying() ? 1.0 : (double) mob.entityData.get(DATA_ACTION_SPEED))));
    }

    private static AnimationController<LdMob> withEffects(AnimationController<LdMob> controller) {
        return controller
                .setSoundKeyframeHandler(event -> MobClientHooks.get().onSound(event))
                .setParticleKeyframeHandler(event -> MobClientHooks.get().onParticle(event))
                .setCustomInstructionKeyframeHandler(event -> MobClientHooks.get().onInstruction(event));
    }

    private PlayState baseState(AnimationState<LdMob> state) {
        MobClientHooks hooks = MobClientHooks.get();
        String walk = state.isMoving() ? movementClip(hooks) : null;
        String clip = walk != null ? walk : hooks.animation(this, IDLE);
        walking = walk != null;
        if (clip == null) {
            return PlayState.STOP;
        }
        return state.setAndContinue(LOOPS.computeIfAbsent(clip, name -> RawAnimation.begin().thenLoop(name)));
    }

    /** The walk loop, or the run loop for mobs that only have one. */
    public String movementClip(MobClientHooks hooks) {
        String walk = hooks.animation(this, WALK);
        return walk != null ? walk : hooks.animation(this, RUN);
    }

    /** One flinch per hurt flash; sits under the action layer, so an ability or death always wins. */
    private PlayState hurtState(AnimationState<LdMob> state) {
        if (hurtTime <= 0 || isDeadOrDying()) {
            clientHurtPlaying = false;
            return PlayState.STOP;
        }
        String hurt = MobClientHooks.get().animation(this, HURT);
        if (hurt == null) {
            return PlayState.STOP;
        }
        if (!clientHurtPlaying) {
            clientHurtPlaying = true;
            state.getController().forceAnimationReset();
        }
        return state.setAndContinue(authored(hurt));
    }

    private PlayState actionState(AnimationState<LdMob> state) {
        MobClientHooks hooks = MobClientHooks.get();
        if (isDeadOrDying()) {
            String death = hooks.animation(this, DEATH);
            if (death == null) {
                return PlayState.STOP;
            }
            return state.setAndContinue(authored(death));
        }
        String action = currentAction();
        String clip = action.isEmpty() ? null : hooks.animation(this, action);
        if (clip == null) {
            clientActionSeq = entityData.get(DATA_ACTION_SEQ);
            return PlayState.STOP;
        }
        int seq = entityData.get(DATA_ACTION_SEQ);
        if (seq != clientActionSeq) {
            clientActionSeq = seq;
            state.getController().forceAnimationReset();
        }
        return state.setAndContinue(authored(clip));
    }

    private static RawAnimation authored(String clip) {
        return AS_AUTHORED.computeIfAbsent(clip, name -> RawAnimation.begin().then(name, Animation.LoopType.DEFAULT));
    }

    /** Client: last rendered world position of a marker bone, if it has been rendered yet. */
    public Vec3 markerPosition(String bone) {
        return clientMarkerPositions.get(bone);
    }

    public void setMarkerPosition(String bone, Vec3 position) {
        clientMarkerPositions.put(bone, position);
    }

    /** Client: run {@code effect} once this frame has been drawn and marker positions are current. */
    public void queueClientEffect(Runnable effect) {
        clientEffects.add(effect);
    }

    public void runClientEffects() {
        if (!clientEffects.isEmpty()) {
            List<Runnable> due = List.copyOf(clientEffects);
            clientEffects.clear();
            due.forEach(Runnable::run);
        }
    }
}
