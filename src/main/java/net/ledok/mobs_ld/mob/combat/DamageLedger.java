package net.ledok.mobs_ld.mob.combat;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Damage dealt to one mob over the last {@value #WINDOW_TICKS} ticks, per attacker, for the {@code top_damage} target
 * rule (the Warden's Hunting Sniff marks whoever hurts it most). Times are game ticks; pure, so it is unit-tested.
 */
public final class DamageLedger {
    /** How far back damage counts: 10 s. */
    public static final long WINDOW_TICKS = 200L;

    private final ArrayDeque<Hit> hits = new ArrayDeque<>();

    /** {@code attacker} dealt {@code amount} at {@code now}. */
    public void record(UUID attacker, double amount, long now) {
        if (!(amount > 0.0)) {
            return;
        }
        prune(now);
        hits.addLast(new Hit(attacker, amount, now));
    }

    /** Damage {@code attacker} dealt within the window ending at {@code now}. */
    public double dealtBy(UUID attacker, long now) {
        prune(now);
        double total = 0.0;
        for (Hit hit : hits) {
            if (hit.attacker().equals(attacker)) {
                total += hit.amount();
            }
        }
        return total;
    }

    /**
     * The attacker with the most damage within the window ending at {@code now} among those {@code eligible} accepts;
     * null if none of them dealt any. A tie goes to whoever reached that total first.
     */
    public UUID top(long now, Predicate<UUID> eligible) {
        prune(now);
        Map<UUID, Double> totals = new HashMap<>();
        UUID best = null;
        double bestTotal = 0.0;
        for (Hit hit : hits) {
            double total = totals.merge(hit.attacker(), hit.amount(), Double::sum);
            if (total > bestTotal && eligible.test(hit.attacker())) {
                best = hit.attacker();
                bestTotal = total;
            }
        }
        return best;
    }

    public void clear() {
        hits.clear();
    }

    private void prune(long now) {
        while (!hits.isEmpty() && now - hits.peekFirst().tick() > WINDOW_TICKS) {
            hits.removeFirst();
        }
    }

    private record Hit(UUID attacker, double amount, long tick) {
    }
}
