package net.ledok.mobs_ld.mob.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Who one target slot (a secondary target, e.g. a Wither side head; see {@link TargetSlots}) aims at, and when it
 * picks again. Pure logic without game classes, so the rules are unit-tested: TargetSlots gathers the facts about each
 * candidate and the slot's timing, this decides.
 *
 * <p>Rules (players only, unless noted):
 * <ul>
 *   <li>{@link Rule#LOWEST_THREAT}: the player with the least threat on the mob's table (0 counts); a tie goes to
 *       someone other than the tank (the mob's main target), then at random.</li>
 *   <li>{@link Rule#SPREAD}: a player no other slot (and not the tank) is on, avoiding its own previous pick when there
 *       is another choice; else the player with the fewest others on them, ties away from its previous pick (so with
 *       two players it alternates); only if no player qualifies, a pet or summon that has threat on the table.</li>
 * </ul>
 */
public final class SlotPicker {
    /** Threats closer than this are a tie. */
    private static final double TIE = 1.0e-6;

    private SlotPicker() {
    }

    public enum Rule {
        LOWEST_THREAT("lowest_threat"),
        SPREAD("spread");

        private final String name;

        Rule(String name) {
            this.name = name;
        }

        public String serializedName() {
            return name;
        }

        /** The rule written as {@code name}, or null. */
        public static Rule byName(String name) {
            for (Rule rule : values()) {
                if (rule.name.equals(name)) {
                    return rule;
                }
            }
            return null;
        }
    }

    /**
     * What the picker knows about one possible target.
     *
     * @param player     a player (anything else is only a spread slot's last resort)
     * @param threat     its threat on the mob's table (0 = none yet)
     * @param targetable alive, attackable by the mob, not a spectator or in creative
     * @param hidden     stealthed (e.g. Rogues' vanish): never picked
     * @param distance   blocks from the mob
     * @param visible    in line of sight of the slot's head
     */
    public record Candidate<T>(T id, boolean player, double threat, boolean targetable, boolean hidden,
                               double distance, boolean visible) {
    }

    /** Whether {@code candidate} may hold a slot with this range and sight rule. */
    public static boolean eligible(Candidate<?> candidate, double range, boolean needSight) {
        return candidate.targetable() && !candidate.hidden() && candidate.distance() <= range
                && (!needSight || candidate.visible());
    }

    /**
     * Picks a slot's target; null when nobody qualifies.
     *
     * @param tank     the mob's main target (its threat top or taunt lock), or null
     * @param headsOn  how many others (the tank's head and the other slots, not this one) are on each id
     * @param previous this slot's previous pick, or null
     * @param random   {@code bound -> [0, bound)}
     */
    public static <T> T pick(Rule rule, List<Candidate<T>> candidates, double range, boolean needSight, T tank,
                             Map<T, Integer> headsOn, T previous, IntUnaryOperator random) {
        List<Candidate<T>> eligible = new ArrayList<>();
        for (Candidate<T> candidate : candidates) {
            if (eligible(candidate, range, needSight)) {
                eligible.add(candidate);
            }
        }
        return switch (rule) {
            case LOWEST_THREAT -> lowestThreat(eligible, tank, random);
            case SPREAD -> spread(eligible, headsOn, previous, random);
        };
    }

    /** The lowest-threat player among already eligible candidates; ties prefer anyone but the tank, then random. */
    public static <T> T lowestThreat(List<Candidate<T>> eligible, T tank, IntUnaryOperator random) {
        List<Candidate<T>> players = players(eligible);
        if (players.isEmpty()) {
            return null;
        }
        double lowest = Double.POSITIVE_INFINITY;
        for (Candidate<T> player : players) {
            lowest = Math.min(lowest, player.threat());
        }
        List<T> pool = new ArrayList<>();
        for (Candidate<T> player : players) {
            if (player.threat() <= lowest + TIE) {
                pool.add(player.id());
            }
        }
        if (pool.size() > 1 && tank != null) {
            pool.remove(tank);
        }
        return choose(pool, random);
    }

    private static <T> T spread(List<Candidate<T>> eligible, Map<T, Integer> headsOn, T previous, IntUnaryOperator random) {
        List<Candidate<T>> players = players(eligible);
        if (!players.isEmpty()) {
            List<T> free = new ArrayList<>();
            for (Candidate<T> player : players) {
                if (headsOn.getOrDefault(player.id(), 0) <= 0) {
                    free.add(player.id());
                }
            }
            if (!free.isEmpty()) {
                return choose(avoid(free, previous), random);
            }
            return choose(avoid(fewest(players, headsOn), previous), random);
        }
        List<Candidate<T>> pets = new ArrayList<>();
        for (Candidate<T> candidate : eligible) {
            if (candidate.threat() > 0.0) {
                pets.add(candidate);
            }
        }
        return pets.isEmpty() ? null : choose(avoid(fewest(pets, headsOn), previous), random);
    }

    private static <T> List<Candidate<T>> players(List<Candidate<T>> candidates) {
        List<Candidate<T>> players = new ArrayList<>();
        for (Candidate<T> candidate : candidates) {
            if (candidate.player()) {
                players.add(candidate);
            }
        }
        return players;
    }

    private static <T> List<T> fewest(List<Candidate<T>> candidates, Map<T, Integer> headsOn) {
        int least = Integer.MAX_VALUE;
        for (Candidate<T> candidate : candidates) {
            least = Math.min(least, headsOn.getOrDefault(candidate.id(), 0));
        }
        List<T> pool = new ArrayList<>();
        for (Candidate<T> candidate : candidates) {
            if (headsOn.getOrDefault(candidate.id(), 0) == least) {
                pool.add(candidate.id());
            }
        }
        return pool;
    }

    /** {@code pool} without {@code previous}, unless that would leave nobody. */
    private static <T> List<T> avoid(List<T> pool, T previous) {
        if (previous != null && pool.size() > 1) {
            pool.remove(previous);
        }
        return pool;
    }

    private static <T> T choose(List<T> pool, IntUnaryOperator random) {
        if (pool.isEmpty()) {
            return null;
        }
        return pool.size() == 1 ? pool.get(0) : pool.get(Math.floorMod(random.applyAsInt(pool.size()), pool.size()));
    }

    /** How a slot's current target stands this tick. */
    public enum Status {
        /** The slot has no target. */
        NONE,
        /** Dead, gone from the level, out of range, stealthed or no longer attackable: re-pick at once. */
        GONE,
        /** A spread slot's last resort (a pet or summon) holds it while a player qualifies again: re-pick at once. */
        FALLBACK,
        /** Still valid but out of the head's line of sight: re-pick once that has lasted {@code lost_after}. */
        HIDDEN,
        /** Valid and in sight. */
        SEEN
    }

    /**
     * When one slot picks: at once when it starts, while it has no target (retrying a little later after a pick that
     * found nobody), when its target is gone or only a last resort, or once the target has been out of sight for
     * {@code lost_after}; otherwise on its schedule. Times are server ticks.
     *
     * <p>Schedule: every {@code repick} (rolled per pick). A slot with an {@code offset} that follows a lead slot
     * instead re-picks {@code offset} after each pick of its lead, whatever made either of them pick (their timer, a
     * lost target), so the two never switch together for the whole fight; only while one of them holds nobody does it
     * fall back to its own {@code repick}, its first scheduled re-pick then delayed by the offset.
     */
    public static final class Timer {
        private boolean started;
        private boolean offsetPending = true;
        private boolean holding;
        private long nextPickAt;
        private long lastPickAt;
        private long foundAt;
        private long lostSince = -1;

        /** {@link #due(long, Status, long, Timer, long)} for a slot that follows no lead. */
        public boolean due(long now, Status status, long lostAfterTicks) {
            return due(now, status, lostAfterTicks, null, 0L);
        }

        /**
         * Whether the slot must pick now (also tracks how long the target has been out of sight); {@code lead} is the
         * timer of the slot it follows ({@code offsetTicks} behind), or null.
         */
        public boolean due(long now, Status status, long lostAfterTicks, Timer lead, long offsetTicks) {
            if (!started) {
                return true;
            }
            switch (status) {
                case GONE, FALLBACK -> {
                    return true;
                }
                case HIDDEN -> {
                    if (lostSince < 0) {
                        lostSince = now;
                    }
                    if (now - lostSince >= lostAfterTicks) {
                        return true;
                    }
                }
                default -> lostSince = -1;
            }
            return now >= scheduledAt(lead, offsetTicks);
        }

        /**
         * When the next scheduled pick is: {@code offsetTicks} after the lead's latest pick (or its next one, if this
         * slot already picked since), while both hold a target; else this slot's own schedule.
         */
        public long scheduledAt(Timer lead, long offsetTicks) {
            if (lead == null || !lead.started || !lead.holding || !started || !holding) {
                return nextPickAt;
            }
            long anchor = lead.foundAt + offsetTicks;
            return lastPickAt >= anchor ? lead.nextPickAt + offsetTicks : anchor;
        }

        /**
         * A pick was made at {@code now}: {@code found} = it has a target. Its own next scheduled pick is
         * {@code repickTicks} away (plus {@code offsetTicks} for the first one since the slot started), or
         * {@code retryTicks} when it found nobody.
         */
        public void picked(long now, boolean found, long repickTicks, long offsetTicks, long retryTicks) {
            started = true;
            lostSince = -1;
            lastPickAt = now;
            holding = found;
            if (!found) {
                nextPickAt = now + retryTicks;
                return;
            }
            foundAt = now;
            nextPickAt = now + repickTicks + (offsetPending ? offsetTicks : 0L);
            offsetPending = false;
        }

        /** Back to the start (fight over, reset): the next tick picks at once and the offset applies again. */
        public void reset() {
            started = false;
            offsetPending = true;
            holding = false;
            nextPickAt = 0L;
            lastPickAt = 0L;
            foundAt = 0L;
            lostSince = -1;
        }

        /** Ticks until the next scheduled pick (0 = now); see {@link #scheduledAt}. */
        public long ticksUntilPick(long now, Timer lead, long offsetTicks) {
            return started ? Math.max(0L, scheduledAt(lead, offsetTicks) - now) : 0L;
        }

        /** Ticks the target has been out of sight, or -1 while it is in sight. */
        public long ticksHidden(long now) {
            return lostSince < 0 ? -1L : now - lostSince;
        }
    }
}
