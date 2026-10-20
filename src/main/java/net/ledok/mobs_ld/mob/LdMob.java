package net.ledok.mobs_ld.mob;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
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
 */
public class LdMob extends Monster implements GeoEntity {
    private static final EntityDataAccessor<String> DATA_ACTION =
            SynchedEntityData.defineId(LdMob.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_ACTION_SEQ =
            SynchedEntityData.defineId(LdMob.class, EntityDataSerializers.INT);

    private static final String IDLE = "idle";
    private static final String WALK = "walk";
    private static final String DEATH = "death";

    /** Shared, immutable animation recipes; GeckoLib compares them by content, so caching just saves garbage. */
    private static final Map<String, RawAnimation> LOOPS = new ConcurrentHashMap<>();
    private static final Map<String, RawAnimation> ONCE = new ConcurrentHashMap<>();
    private static final Map<String, RawAnimation> HOLD = new ConcurrentHashMap<>();

    private final MobDeclaration declaration;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** Client: last action sequence the action controller started, so a repeated clip restarts. */
    private int clientActionSeq = -1;
    /** Client: whether the base layer is on its walk loop (walk playback is speed-matched). */
    private boolean walking;
    /** Client: world positions of marker bones ({@code hit_/fx_/proj_}) from the last rendered frame. */
    private final Map<String, Vec3> clientMarkerPositions = new HashMap<>();

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
        // Placeholder until the data-driven combat goal lands; the threat goal is added by MobThreatMixin.
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, "");
        builder.define(DATA_ACTION_SEQ, 0);
    }

    // --- Actions (server) ---

    /** Starts {@code clip} on the action layer for every watching client, restarting it if already playing. */
    public void playAction(String clip) {
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

    // --- Death ---

    @Override
    protected void tickDeath() {
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
        controllers.add(withEffects(new AnimationController<>(this, "action", 3, this::actionState)));
    }

    private static AnimationController<LdMob> withEffects(AnimationController<LdMob> controller) {
        return controller
                .setSoundKeyframeHandler(event -> MobClientHooks.get().onSound(event))
                .setParticleKeyframeHandler(event -> MobClientHooks.get().onParticle(event))
                .setCustomInstructionKeyframeHandler(event -> MobClientHooks.get().onInstruction(event));
    }

    private PlayState baseState(AnimationState<LdMob> state) {
        MobClientHooks hooks = MobClientHooks.get();
        String walk = state.isMoving() ? hooks.animation(this, WALK) : null;
        String clip = walk != null ? walk : hooks.animation(this, IDLE);
        walking = walk != null;
        if (clip == null) {
            return PlayState.STOP;
        }
        return state.setAndContinue(LOOPS.computeIfAbsent(clip, name -> RawAnimation.begin().thenLoop(name)));
    }

    private PlayState actionState(AnimationState<LdMob> state) {
        MobClientHooks hooks = MobClientHooks.get();
        if (isDeadOrDying()) {
            String death = hooks.animation(this, DEATH);
            if (death == null) {
                return PlayState.STOP;
            }
            return state.setAndContinue(HOLD.computeIfAbsent(death, name -> RawAnimation.begin().thenPlayAndHold(name)));
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
        return state.setAndContinue(ONCE.computeIfAbsent(clip, name -> RawAnimation.begin().thenPlay(name)));
    }

    /** Client: last rendered world position of a marker bone, if it has been rendered yet. */
    public Vec3 markerPosition(String bone) {
        return clientMarkerPositions.get(bone);
    }

    public void setMarkerPosition(String bone, Vec3 position) {
        clientMarkerPositions.put(bone, position);
    }
}
