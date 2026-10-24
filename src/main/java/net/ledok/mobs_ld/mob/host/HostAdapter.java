package net.ledok.mobs_ld.mob.host;

import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Mob;

/**
 * What the ability engine needs to know about one vanilla (or other mod's) entity type it runs on: when the fight
 * may start, where the clip and texture cues of a behaviour go (vanilla models have no GeckoLib action layer), and
 * which boss bar to use. Registered per type in {@link HostAdapters}; a type without one gets {@link #DEFAULT},
 * which needs nothing from the host. Data-driven {@link net.ledok.mobs_ld.mob.LdMob}s always use the default.
 *
 * <p>All calls are server side, from the mob's own AI tick unless noted.
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
}
