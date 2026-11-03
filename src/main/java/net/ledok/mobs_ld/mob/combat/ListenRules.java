package net.ledok.mobs_ld.mob.combat;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Set;

/**
 * What gives a player away during a {@code listen} window (the Warden's Listening Silence): the triggers, a per-player
 * tracker fed one sample per tick, and which damage counts as an action, all on plain values so the rules are
 * unit-tested. Damage and instant item uses are reported by events instead (see {@link ListenWindows}).
 */
public final class ListenRules {
    /**
     * After a player was hurt or pushed (a hit's knockback or knock-up, any shove), jumps and moves are not held
     * against them for this many ticks, and where they stand is taken again as they land.
     */
    public static final int PUSH_GRACE_TICKS = 10;

    private ListenRules() {
    }

    /** One way to give oneself away. */
    public enum Trigger implements StringRepresentable {
        /**
         * Damaging the mob or one of its adds by an action during the window: a melee hit, or a projectile (or other
         * damaging entity) that came into being after the window opened. Damage over time, effects, thorns and
         * projectiles already in flight when it opened do not count.
         */
        DAMAGE("damage"),
        /**
         * Starting to use an item (draw a bow or crossbow, raise a shield, eat), using an instant item (throw an ender
         * pearl, a snowball, a splash potion: the stack shrinks or goes on cooldown), or starting a Spell Engine cast
         * with a cast time or a channel.
         */
        USE_ITEM("use_item"),
        /** Sprinting at any moment of the window. */
        SPRINT("sprint"),
        /**
         * A jump (the server's jump statistic goes up). The server also counts a hit's knock-up as a jump, so not
         * within {@value #PUSH_GRACE_TICKS} ticks of being hurt or pushed.
         */
        JUMP("jump"),
        /**
         * Moving more than {@code move_tolerance} blocks (horizontally) from where it stood when the window opened, or
         * where it landed after being hurt or pushed (not counted during the {@value #PUSH_GRACE_TICKS} ticks after).
         */
        MOVE("move");

        public static final Codec<Trigger> CODEC = StringRepresentable.fromEnum(Trigger::values);
        public static final Set<Trigger> ALL = Set.of(values());
        private final String name;

        Trigger(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * A player as the listener sees them on one tick.
     *
     * @param using  using an item or casting a spell
     * @param jumps  the player's jump statistic
     * @param pushed hurt or shoved recently (hurt time left, or a velocity the server set this tick)
     */
    public record Sample(double x, double z, boolean using, boolean sprinting, int jumps, boolean pushed) {
    }

    /** One watched player over a window: where it stands still, and its last sample. */
    public static final class Tracker {
        private double anchorX;
        private double anchorZ;
        private Sample previous;
        /** Jumps and moves are ignored before this tick (a push's grace). */
        private long graceUntil = Long.MIN_VALUE;

        /** Starts watching at {@code tick}, with the player as it is when the window opens. */
        public Tracker(Sample start, long tick) {
            anchorX = start.x();
            anchorZ = start.z();
            previous = start;
            if (start.pushed()) {
                graceUntil = tick + PUSH_GRACE_TICKS;
            }
        }

        /**
         * The trigger the player set off with sample {@code now} at {@code tick}, or null. Checked in the order of
         * {@link Trigger} (damage is never found here). While a push's grace runs, jumps and moves are ignored and the
         * spot it stands still on follows the player, so it is where they land.
         */
        public Trigger observe(Set<Trigger> triggers, Sample now, long tick, double moveTolerance) {
            if (now.pushed()) {
                graceUntil = tick + PUSH_GRACE_TICKS;
            }
            boolean shoved = tick < graceUntil;
            Trigger found = null;
            if (triggers.contains(Trigger.USE_ITEM) && now.using() && !previous.using()) {
                found = Trigger.USE_ITEM;
            } else if (triggers.contains(Trigger.SPRINT) && now.sprinting()) {
                found = Trigger.SPRINT;
            } else if (!shoved && triggers.contains(Trigger.JUMP) && now.jumps() > previous.jumps()) {
                found = Trigger.JUMP;
            } else if (!shoved && triggers.contains(Trigger.MOVE)
                    && Math.hypot(now.x() - anchorX, now.z() - anchorZ) > moveTolerance) {
                found = Trigger.MOVE;
            }
            if (shoved) {
                anchorX = now.x();
                anchorZ = now.z();
            }
            previous = now;
            return found;
        }
    }

    /**
     * Whether a damage counts as the player acting during the window: dealt by the player itself (melee, a spell they
     * strike with), except thorns; or by an entity of theirs (a projectile) that is younger than the window
     * ({@code directAge} ticks against {@code windowAge}). No direct entity (effects, fire, damage over time) never
     * counts.
     */
    public static boolean countsAsAction(boolean hasDirect, boolean directIsAttacker, boolean thorns, int directAge,
                                         long windowAge) {
        if (!hasDirect || thorns) {
            return false;
        }
        return directIsAttacker || directAge < windowAge;
    }
}
