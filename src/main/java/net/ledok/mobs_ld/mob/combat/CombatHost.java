package net.ledok.mobs_ld.mob.combat;

import net.minecraft.world.entity.Entity;

/**
 * A mob that can run the ability engine. Every {@code Mob} implements this through MobCombatHostMixin: a vanilla
 * (or other mod's) mob gets its {@link MobCombat} lazily, once a datapack gives its entity type a behaviour, and its
 * visual cues go to the type's {@link net.ledok.mobs_ld.mob.host.HostAdapter}. {@link net.ledok.mobs_ld.mob.LdMob}
 * implements it itself: its combat exists from construction and its cues drive the GeckoLib action layer.
 *
 * <p>Members are prefixed {@code mobs_ld$} because the mixin adds them to every mob class in the game.
 */
public interface CombatHost {
    /** The mob's fight state; null while it has never been given a behaviour (it then runs only vanilla AI). */
    MobCombat mobs_ld$combat();

    /** The fight state, attaching the engine first if it has none yet (goals, persistence). */
    MobCombat mobs_ld$combatOrCreate();

    /** Starts an action clip ({@code speed} times faster): the action layer on LdMob, an adapter cue elsewhere. */
    void mobs_ld$playAction(String clip, double speed);

    /** Ends the current action clip. */
    void mobs_ld$stopAction();

    /** Switches the texture variant ("phase2"; empty = base): LdMob textures, an adapter cue elsewhere. */
    void mobs_ld$setVariant(String variant);

    /** The fight state of {@code entity}, or null if it is not a mob or has never been given a behaviour. */
    static MobCombat of(Entity entity) {
        return entity instanceof CombatHost host ? host.mobs_ld$combat() : null;
    }
}
