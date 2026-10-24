package net.ledok.mobs_ld.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.ledok.mobs_ld.entity.threat.ThreatTargetGoal;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviours;
import net.ledok.mobs_ld.mob.combat.CombatGoal;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.ledok.mobs_ld.mob.host.HostAdapter;
import net.ledok.mobs_ld.mob.host.HostAdapters;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the ability engine run on any goal-based mob (see {@link CombatHost}). A mob gets its {@link MobCombat} on
 * its first AI tick after a datapack gives its entity type a behaviour; until then it carries one null field and
 * an int, and each tick costs one int compare. LdMob overrides the {@link CombatHost} members (its combat exists
 * from construction, its cues drive the GeckoLib action layer) but shares every hook below, so each engine hook
 * exists exactly once.
 */
@Mixin(Mob.class)
public class MobCombatHostMixin implements CombatHost {
    @Shadow
    @Final
    protected GoalSelector goalSelector;
    @Shadow
    @Final
    protected GoalSelector targetSelector;

    @Unique
    private MobCombat mobs_ld$hosted;
    /** {@link MobBehaviours#generation()} this mob last looked its behaviour up at; 0 = never. */
    @Unique
    private int mobs_ld$behaviourGeneration;

    @Unique
    private Mob mobs_ld$self() {
        return (Mob) (Object) this;
    }

    // --- CombatHost (vanilla and other mods' mobs) ---

    @Override
    public MobCombat mobs_ld$combat() {
        return mobs_ld$hosted;
    }

    @Override
    public MobCombat mobs_ld$combatOrCreate() {
        if (mobs_ld$hosted == null) {
            Mob self = mobs_ld$self();
            mobs_ld$hosted = new MobCombat(self);
            HostAdapter adapter = mobs_ld$hosted.adapter();
            // Never duplicated: a mob may already carry the threat goal (MobThreatMixin) or have been attached
            // before. Called outside goal iteration (after the selectors ticked, from a load or a command).
            if (goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof CombatGoal)) {
                goalSelector.addGoal(adapter.goalPriority(), new CombatGoal(self));
            }
            if (targetSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof ThreatTargetGoal)) {
                targetSelector.addGoal(0, new ThreatTargetGoal(self));
            }
            adapter.install(self);
        }
        return mobs_ld$hosted;
    }

    @Override
    public void mobs_ld$playAction(String clip, double speed) {
        Mob self = mobs_ld$self();
        HostAdapters.get(self.getType()).onAction(self, clip, speed);
    }

    @Override
    public void mobs_ld$stopAction() {
        Mob self = mobs_ld$self();
        HostAdapters.get(self.getType()).onStopAction(self);
    }

    @Override
    public void mobs_ld$setVariant(String variant) {
        Mob self = mobs_ld$self();
        HostAdapters.get(self.getType()).onVariant(self, variant);
    }

    // --- Hooks (every mob, LdMob included) ---

    /**
     * The engine tick: after the mob's own {@code customServerAiStep}, so it runs inside the boss tick (summons are
     * then Arenas_LD minions), and before the move and look controls, so engine velocity and facing win this tick.
     * Covers every subclass, since {@code serverAiStep} is final; not reached with NoAI, as for vanilla AI.
     */
    @Inject(method = "serverAiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;customServerAiStep()V", shift = At.Shift.AFTER))
    private void mobs_ld$tickCombat(CallbackInfo ci) {
        int generation = MobBehaviours.generation();
        if (generation != mobs_ld$behaviourGeneration) {
            mobs_ld$behaviourGeneration = generation;
            mobs_ld$resolve();
        }
        MobCombat combat = mobs_ld$combat();
        if (combat == null) {
            return;
        }
        Mob self = mobs_ld$self();
        if (self.level().isClientSide()) {
            return; // a mob a client steers runs its AI there too; the fight is server-side only
        }
        HostAdapter adapter = combat.adapter();
        if (adapter.ready(self)) {
            combat.tick();
        }
        if (!self.isRemoved()) {
            adapter.tick(self);
        }
    }

    /**
     * After a datapack reload (or on the first tick): attaches the engine if the type now has a behaviour. Removing
     * a behaviour leaves the combat in place but inert, so its goals stand down and it can resume after a reload.
     */
    @Unique
    private void mobs_ld$resolve() {
        Mob self = mobs_ld$self();
        if (mobs_ld$combat() == null && !self.level().isClientSide() && MobBehaviours.get(self.getType()) != null
                && MobBehaviours.canHost(self)) {
            mobs_ld$combatOrCreate();
        }
    }

    /**
     * Bosses never despawn while their type's behaviour has a boss block: their fight, home and adds would vanish
     * with them. Decided by type, so it holds from the first despawn check (before any AI tick) and lapses if the
     * behaviour goes; the mob's saved PersistenceRequired flag is left alone. Wrapped at the call sites, because many
     * subclasses override removeWhenFarAway without calling super.
     */
    @ModifyExpressionValue(method = "checkDespawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;removeWhenFarAway(D)Z"))
    private boolean mobs_ld$keepBosses(boolean despawn) {
        if (!despawn) {
            return false;
        }
        Mob self = mobs_ld$self();
        MobBehaviour behaviour = MobBehaviours.get(self.getType());
        return behaviour == null || behaviour.boss().isEmpty() || !MobBehaviours.canHost(self);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void mobs_ld$saveCombat(CompoundTag tag, CallbackInfo ci) {
        MobCombat combat = mobs_ld$combat();
        if (combat != null) {
            combat.save(tag);
        }
    }

    /** A saved vanilla host attaches at once, so its fight state and adds come back with it. */
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mobs_ld$loadCombat(CompoundTag tag, CallbackInfo ci) {
        Mob self = mobs_ld$self();
        MobCombat combat = mobs_ld$combat();
        if (combat == null) {
            if (self.level().isClientSide() || !MobCombat.hasSavedState(tag)) {
                return;
            }
            if (MobBehaviours.get(self.getType()) == null || !MobBehaviours.canHost(self)) {
                MobCombat.stripEnrage(self); // its type lost the behaviour while it was saved enraged
                return;
            }
            combat = mobs_ld$combatOrCreate();
        }
        combat.load(tag);
    }
}
