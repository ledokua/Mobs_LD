package net.ledok.mobs_ld.mob.host;

import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * What the ability engine needs to know about one vanilla (or other mod's) entity type it runs on: when the fight
 * may start, where the clip and texture cues of a behaviour go (vanilla models have no GeckoLib action layer), and
 * which boss bar to use. Registered per type in {@link HostAdapters}; a type without one gets {@link #DEFAULT},
 * which needs nothing from the host. Data-driven {@link net.ledok.mobs_ld.mob.LdMob}s always use the default.
 *
 * <p>All calls are server side, from the mob's own AI tick unless noted. Every hook added for Brain hosts
 * ({@link #acceptsBrain} to {@link #setBurrowed}) is a no-op by default, so goal-based hosts (the Wither) and LdMobs
 * never see them.
 */
public interface HostAdapter {
    /** Runs the engine on the mob as it is, with no cues and its own boss bar if the behaviour asks for one. */
    HostAdapter DEFAULT = new HostAdapter() {
    };

    /** Whether the engine may tick this mob now (e.g. not while a summoning charge plays out). */
    default boolean ready(Mob mob) {
        return true;
    }

    /** Once, when the engine attaches to the mob (after its combat goals are in place). */
    default void install(Mob mob) {
    }

    /** Every AI tick of a hosted mob, right after the engine's own tick (also while not {@link #ready}). */
    default void tick(Mob mob) {
    }

    /** A behaviour started an action clip (an ability, a stagger, a transition); {@code speed} is attack speed. */
    default void onAction(Mob mob, String clip, double speed) {
    }

    /** The action clip ended (ability over, phase change, reset). */
    default void onStopAction(Mob mob) {
    }

    /**
     * An ability is starting (picked, chained, a phase transition, a command), possibly cutting off one that never got
     * its {@link #onStopAction}: clear what the previous action left on the body (a held pose). Its own clip follows
     * through {@link #onAction} on the run's first tick.
     */
    default void onAbilityStart(Mob mob) {
    }

    /** A phase switched the texture variant ("phase2"; empty = base); also while a saved mob loads back in. */
    default void onVariant(Mob mob, String variant) {
    }

    /**
     * The host's own boss bar, which the engine then styles (the behaviour's {@code boss.bar} colour and overlay)
     * instead of showing a second one; null = the engine makes its own bar if the behaviour asks for one.
     */
    default ServerBossEvent bossBar(Mob mob) {
        return null;
    }

    /**
     * The look of {@link #bossBar} where the behaviour sets none: each part neither the current phase's {@code bar} nor
     * {@code boss.bar} sets goes back to this (after a reset to the first phase, or once the behaviour is gone). Null =
     * leave the bar as it is.
     */
    default MobBehaviour.BossBar defaultBarStyle() {
        return null;
    }

    /**
     * Whether the mob keeps its altitude while an event steers it only sideways (a dash, a chase): true for a flyer
     * whose move control lets it sink while the engine holds it, so those events give it no vertical speed. False
     * keeps the body's own vertical speed (gravity, jumps).
     */
    default boolean holdsAltitude() {
        return false;
    }

    /**
     * How wide the mob's rendered body is, in blocks: a {@code dash} with {@code width: body} draws and hits a lane this
     * wide. By default its hitbox width (which follows {@code generic.scale}).
     */
    default double bodyWidth(Mob mob) {
        return mob.getBbWidth();
    }

    /**
     * The {@code host.type} this adapter reads its options from (see {@link HostSpec}); null = it takes none, and a
     * typed host block on its entity type is ignored with a warning at reload.
     */
    default ResourceLocation hostType() {
        return null;
    }

    /**
     * Goal priority of the engine's CombatGoal on this type (lower runs first). 0 by default: some vanilla attack
     * goals sit at 1 (iron golem, polar bear melee), and a goal only takes over from a strictly higher number.
     */
    default int goalPriority() {
        return 0;
    }

    /**
     * Where {@code head}'s shots, sight lines and particles start; empty = the host has no such head. Every host has
     * a {@link Head#CENTER}: by default its eyes.
     */
    default Optional<Vec3> muzzle(Mob mob, Head head) {
        return head == Head.CENTER ? Optional.of(mob.getEyePosition()) : Optional.empty();
    }

    /**
     * Turns {@code head} toward {@code target} (null = let it go back to the host's own aiming). Called every tick a
     * {@code look} event or a target slot holds the head. By default the center head is the mob's own head, turned
     * with its look control; hosts without other heads ignore them.
     */
    default void aimHead(Mob mob, Head head, LivingEntity target) {
        if (head == Head.CENTER && target != null) {
            mob.getLookControl().setLookAt(target, 60.0F, 60.0F);
        }
    }

    /**
     * The head a target slot shows on (e.g. the Wither's {@code right_head} slot is its right head); empty = the slot
     * has no visual, it only feeds abilities ({@code target: slot:<name>}).
     */
    default Optional<Head> slotHead(String slot) {
        return Optional.empty();
    }

    // --- Brain hosts (the Warden): all no-ops by default ---

    /**
     * Whether this adapter drives a mob whose AI is a Brain rather than goals (the Warden). Such entity types are
     * refused by {@link net.ledok.mobs_ld.mob.behaviour.MobBehaviours} unless their adapter says true here; the Ender
     * Dragon (whose AI never reaches the mob tick) stays refused regardless.
     */
    default boolean acceptsBrain() {
        return false;
    }

    /**
     * After {@code Mob.setTarget} ran on a mob that has a combat (any caller: threat targeting, the engine's own drops
     * and resets, taunts, Spell Engine aggro; the behaviour's target filter already passed). A Brain host whose
     * {@code getTarget()} reads a memory writes the target there; null = the target was dropped.
     */
    default void onTargetSet(Mob mob, LivingEntity target) {
    }

    /**
     * Whether the host's Brain must only keep its memories and sensors fresh this tick, starting and ticking no
     * behaviour (the engine owns the body). Read by the host's own mixin, before the engine's tick.
     */
    default boolean holdsBrain(Mob mob) {
        return false;
    }

    /** The engine took the body (an ability, a stagger or the walk home began). */
    default void onClaimBody(Mob mob) {
    }

    /** The engine gave the body back (nothing runs any more); the host's own AI resumes. */
    default void onReleaseBody(Mob mob) {
    }

    /**
     * The fight was reset ({@code MobCombat.reset}), or the behaviour went away and the mob turns back to its own AI:
     * the host clears whatever fight state of its own would bring the fight straight back (a Warden's anger).
     */
    default void onReset(Mob mob) {
    }

    /** A {@code burrow} event put the mob under the ground (true) or brought it back up (false). */
    default void setBurrowed(Mob mob, boolean burrowed) {
    }
}
