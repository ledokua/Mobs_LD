package net.ledok.mobs_ld.mixin.warden;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ledok.mobs_ld.mixin.BrainAccessor;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviours;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.ledok.mobs_ld.mob.host.warden.WardenAdapter;
import net.ledok.mobs_ld.mob.host.warden.WardenHostOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies {@link WardenAdapter} to a hosted Warden. Every hook checks "hosted" first, which for a world Warden (never
 * attached) is one null field read, so it stays fully vanilla; the dig seed also covers unhosted Wardens of an
 * Arenas_LD run. {@code customServerAiStep} is never cancelled (its darkness, anger decay and activity update keep
 * running); only calls inside it are wrapped. Full descriptors everywhere: {@code increaseAngerAt} is overloaded.
 */
@Mixin(Warden.class)
public class WardenHostMixin {
    /**
     * {@link MobBehaviours#generation()} the run tag was last read at; it is also re-read once a second (one tag lookup),
     * so a Warden tagged after it spawned is dig-locked and hosted within a second, without a reload.
     */
    @Unique
    private int mobs_ld$tagGeneration = -1;
    @Unique
    private boolean mobs_ld$runTagged;

    @Unique
    private Warden mobs_ld$self() {
        return (Warden) (Object) this;
    }

    /**
     * Before its Brain ticks (on the first tick too, before the engine has attached): dig cooldown for hosted and
     * run-tagged Wardens, the attack muzzles for hosted ones; a Warden that got the run tag after its first tick is
     * attached here. Not cancelling.
     */
    @Inject(method = "customServerAiStep()V", at = @At("HEAD"))
    private void mobs_ld$seed(CallbackInfo ci) {
        Warden self = mobs_ld$self();
        int generation = MobBehaviours.generation();
        if (generation != mobs_ld$tagGeneration || self.tickCount % 20 == 0) {
            boolean wasTagged = mobs_ld$runTagged;
            mobs_ld$tagGeneration = generation;
            mobs_ld$runTagged = MobCombat.inArenasRun(self);
            if (mobs_ld$runTagged && !wasTagged) {
                WardenAdapter.attachIfHosted(self);
            }
        }
        WardenAdapter.seed(self, mobs_ld$runTagged);
    }

    /**
     * The hold: while the engine owns the body, the Brain only expires its memories and runs its sensors, so its
     * cooldowns keep counting and its senses stay fresh, but no behaviour starts or ticks (a running one would stop
     * at once when ticks resume after a skip, and Digging.stop discards the Warden: so never skip the whole tick).
     * The core Swim behaviour is the one thing it still does, so it does not sink.
     */
    @WrapOperation(method = "customServerAiStep()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void mobs_ld$holdBrain(Brain<?> brain, ServerLevel level, LivingEntity entity, Operation<Void> original) {
        Warden self = mobs_ld$self();
        MobCombat combat = CombatHost.of(self);
        if (combat != null && combat.adapter().holdsBrain(self)) {
            BrainAccessor held = (BrainAccessor) brain;
            held.mobs_ld$forgetOutdatedMemories();
            held.mobs_ld$tickSensors(level, entity);
            WardenAdapter.swimWhileHeld(self); // its core Swim does not run either
        } else {
            original.call(brain, level, entity);
        }
    }

    /** The Darkness pulse every 6 s within 20 blocks: only with {@code darkness_pulse: vanilla} while hosted. */
    @WrapWithCondition(method = "customServerAiStep()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/warden/Warden;applyDarknessAround(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;I)V"))
    private boolean mobs_ld$darknessPulse(ServerLevel level, Vec3 position, Entity source, int radius) {
        MobCombat combat = WardenAdapter.hosted(mobs_ld$self());
        return combat == null || WardenAdapter.options(combat).darknessPulse() == WardenHostOptions.DarknessPulse.VANILLA;
    }

    /**
     * The one predicate behind what it hears, whom it grows angry at, roars at and keeps as its target, and whom
     * vanilla's boom may hit: while hosted it also follows the engine's target rules ({@code engage_on}, players given
     * up on as unreachable, nobody while walking home), so the Brain cannot pick or keep a target the engine refuses.
     */
    @ModifyReturnValue(method = "canTargetEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"))
    private boolean mobs_ld$engineTargetRules(boolean valid, Entity entity) {
        if (!valid) {
            return false;
        }
        MobCombat combat = WardenAdapter.hosted(mobs_ld$self());
        return combat == null || (entity instanceof LivingEntity living && combat.mayTarget(living)
                && !combat.refusesTarget(living));
    }

    /**
     * When hit while it has no target, vanilla takes the attacker straight as its target (no roar), without asking
     * {@code canTargetEntity}; while hosted only a target that passes it (with the engine's rules above) is taken.
     * The method's only {@code setAttackTarget} call.
     */
    @WrapWithCondition(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/warden/Warden;setAttackTarget(Lnet/minecraft/world/entity/LivingEntity;)V"))
    private boolean mobs_ld$retaliateByEngineRules(Warden self, LivingEntity attacker) {
        return WardenAdapter.hosted(self) == null || self.canTargetEntity(attacker);
    }

    /**
     * Vanilla drops a non-player target the moment a player makes it angry ("go roar at the player"); while hosted
     * threat decides the target, so that switch is skipped. The method's only {@code eraseMemory} call.
     */
    @WrapWithCondition(method = "increaseAngerAt(Lnet/minecraft/world/entity/Entity;IZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;eraseMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;)V"))
    private boolean mobs_ld$keepThreatTarget(Brain<?> brain, MemoryModuleType<?> memory) {
        return WardenAdapter.hosted(mobs_ld$self()) == null;
    }

    /**
     * A blocked hit disables the shield when the attacker's {@code canDisableShield()} is true, and the Warden's is:
     * engine swings would disable shields too. Only with {@code disable_shields: true} while hosted.
     */
    @ModifyReturnValue(method = "canDisableShield()Z", at = @At("RETURN"))
    private boolean mobs_ld$disableShields(boolean disables) {
        if (!disables) {
            return false;
        }
        MobCombat combat = WardenAdapter.hosted(mobs_ld$self());
        return combat == null || WardenAdapter.options(combat).disableShields();
    }

    // TODO(M4): wrap isDiggingOrEmerging() inside isInvulnerableTo, so a hosted Warden is invulnerable only while the
    // engine's burrow has it under the ground (and during the emerge on spawn). Until then: vanilla.
}
