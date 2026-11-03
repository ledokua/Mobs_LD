package net.ledok.mobs_ld.mob.host.warden;

import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.warden.Warden;

import java.util.Locale;

/**
 * The vanilla Warden's clips as behaviour cues: an ability's {@code clip}, a {@code clip} event or a stagger names one
 * and the server triggers the vanilla animation the client already has (no new animation, no client code). Entity
 * events restart their clip each time they are sent; pose clips run while the pose holds, and the pose goes back to
 * STANDING when the clip's length is up or the action stops.
 *
 * <table>
 *   <tr><th>Cue</th><th>Server trigger</th><th>Clip</th></tr>
 *   <tr><td>{@code attack}</td><td>entity event 4</td><td>0.33 s impact flash; also ends a roar clip</td></tr>
 *   <tr><td>{@code sonic_boom}</td><td>entity event 62</td><td>3.0 s, chest burst at 1.7 s</td></tr>
 *   <tr><td>{@code tendrils}</td><td>entity event 61</td><td>0.5 s tendril twitch and glow</td></tr>
 *   <tr><td>{@code roar}</td><td>pose ROARING, STANDING after 84 ticks</td><td>4.2 s</td></tr>
 *   <tr><td>{@code sniff}</td><td>pose SNIFFING, STANDING after 84 ticks</td><td>4.16 s</td></tr>
 * </table>
 * Other names do nothing ({@code dig} and {@code emerge} come with the engine's burrow). The client plays every clip at
 * speed 1 whatever the phase's attack speed, so abilities timed to a clip set {@code fixed_speed}.
 */
public final class WardenCues {
    /** Ticks a pose clip holds before the pose goes back to STANDING (WardenAi's ROAR and SNIFFING durations). */
    private static final int POSE_CLIP_TICKS = 84;

    private WardenCues() {
    }

    /** One cue: an entity event, or a pose held for its clip's length. */
    private enum Cue {
        ATTACK((byte) 4, null),
        SONIC_BOOM((byte) 62, null),
        TENDRILS((byte) 61, null),
        ROAR((byte) 0, Pose.ROARING),
        SNIFF((byte) 0, Pose.SNIFFING);

        private final byte event;
        private final Pose pose;

        Cue(byte event, Pose pose) {
            this.event = event;
            this.pose = pose;
        }

        static Cue byName(String name) {
            for (Cue cue : values()) {
                if (cue.name().toLowerCase(Locale.ROOT).equals(name)) {
                    return cue;
                }
            }
            return null;
        }
    }

    /** Per-Warden pose timers (kept as the combat's host state). */
    static final class State {
        /** A pose cue waiting one tick in STANDING, because the Warden already held that pose (see {@link #play}). */
        private Pose pending;
        /** The pose a cue set, and the game time it goes back to STANDING; null = none running. */
        private Pose held;
        private long releaseAt;
    }

    private static State state(MobCombat combat) {
        return combat.hostState(State.class, State::new);
    }

    /** Plays cue {@code clip} on {@code warden}; unknown names do nothing. */
    static void play(Warden warden, MobCombat combat, String clip) {
        Cue cue = Cue.byName(clip);
        if (cue == null) {
            return;
        }
        State state = state(combat);
        if (warden.isDeadOrDying()) {
            // An on_death clip: never a pose over DYING (the death animation and its tilt need it).
            if (cue.pose == null) {
                warden.level().broadcastEntityEvent(warden, cue.event);
            }
            return;
        }
        if (cue.pose == null) {
            warden.level().broadcastEntityEvent(warden, cue.event);
            if (cue == Cue.ATTACK && warden.hasPose(Pose.ROARING)) {
                // Event 4 ends the roar clip on the client; the pose follows, so the next roar starts cleanly.
                warden.setPose(Pose.STANDING);
                state.held = null;
            }
            return;
        }
        state.pending = null;
        if (warden.hasPose(cue.pose)) {
            // The client starts a pose clip when the synced pose changes: one tick in another pose first (synced
            // data goes out once per tick), then the pose again on the next tick.
            warden.setPose(Pose.STANDING);
            state.held = null;
            state.pending = cue.pose;
            return;
        }
        hold(warden, state, cue.pose);
    }

    private static void hold(Warden warden, State state, Pose pose) {
        warden.setPose(pose);
        state.held = pose;
        state.releaseAt = warden.level().getGameTime() + POSE_CLIP_TICKS;
    }

    /** The action stopped: a roar or sniff the engine is holding (or any ROARING / SNIFFING pose) goes to STANDING. */
    static void stop(Warden warden, MobCombat combat) {
        State state = state(combat);
        state.pending = null;
        state.held = null;
        if (!warden.isDeadOrDying() && (warden.hasPose(Pose.ROARING) || warden.hasPose(Pose.SNIFFING))) {
            warden.setPose(Pose.STANDING);
        }
    }

    /** Every tick of a hosted Warden: a pending pose is set, and a pose clip that has run its length is ended. */
    static void tick(Warden warden, MobCombat combat) {
        State state = state(combat);
        if (warden.isDeadOrDying()) {
            state.pending = null;
            state.held = null;
            return;
        }
        if (state.pending != null) {
            Pose pose = state.pending;
            state.pending = null;
            hold(warden, state, pose);
            return;
        }
        if (state.held != null && warden.level().getGameTime() >= state.releaseAt) {
            if (warden.hasPose(state.held)) {
                warden.setPose(Pose.STANDING);
            }
            state.held = null;
        }
    }

    /** {@code /mobs_ld brain}: the pose cue running now, if any. */
    static String describe(MobCombat combat) {
        State state = state(combat);
        if (state.pending != null) {
            return "cue " + state.pending.name().toLowerCase(Locale.ROOT) + " starts next tick";
        }
        return state.held == null ? "no pose cue" : "cue " + state.held.name().toLowerCase(Locale.ROOT) + " holds";
    }
}
