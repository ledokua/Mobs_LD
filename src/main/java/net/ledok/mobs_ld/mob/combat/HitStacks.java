package net.ledok.mobs_ld.mob.combat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Per-victim stacks one mob puts on players with its hits ({@code hit.stacks}, the Warden's Sculk Brand): each stack
 * of an id makes this mob's later hits on that victim {@code per_stack} stronger ({@code 1 + per_stack x stacks}),
 * up to {@code max} stacks; every new stack restarts the id's {@code duration}, and when it runs out all of that id's
 * stacks go. Several ids multiply. Kept on the mob's combat, not as a mob effect, so milk and cleanses cannot remove
 * it. Times are game ticks; pure, so it is unit-tested.
 */
public final class HitStacks {
    private final Map<UUID, Map<String, Entry>> byVictim = new HashMap<>();

    /**
     * One more stack of {@code id} on {@code victim} (at most {@code max}), all of them lasting until
     * {@code now + durationTicks}; returns the stacks it now has.
     */
    public int add(UUID victim, String id, double perStack, int max, long durationTicks, long now) {
        Map<String, Entry> stacks = byVictim.computeIfAbsent(victim, key -> new HashMap<>());
        Entry old = stacks.get(id);
        int count = old == null || now >= old.expiresAt() ? 1 : Math.min(Math.max(1, max), old.count() + 1);
        stacks.put(id, new Entry(count, perStack, now + Math.max(1L, durationTicks)));
        return count;
    }

    /** Stacks of {@code id} {@code victim} has at {@code now} (0 once they ran out). */
    public int count(UUID victim, String id, long now) {
        Map<String, Entry> stacks = byVictim.get(victim);
        Entry entry = stacks == null ? null : stacks.get(id);
        return entry == null || now >= entry.expiresAt() ? 0 : entry.count();
    }

    /** What this mob's hits on {@code victim} are multiplied by at {@code now}; 1 without stacks. Drops run-out ones. */
    public double multiplier(UUID victim, long now) {
        if (byVictim.isEmpty()) {
            return 1.0;
        }
        Map<String, Entry> stacks = byVictim.get(victim);
        if (stacks == null) {
            return 1.0;
        }
        double multiplier = 1.0;
        for (Iterator<Entry> it = stacks.values().iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (now >= entry.expiresAt()) {
                it.remove();
            } else {
                multiplier *= 1.0 + entry.perStack() * entry.count();
            }
        }
        if (stacks.isEmpty()) {
            byVictim.remove(victim);
        }
        return multiplier;
    }

    /** Drops every stack of the victims {@code gone} accepts (dead, logged out, or no longer in the mob's level). */
    public void removeVictims(Predicate<UUID> gone) {
        byVictim.keySet().removeIf(gone);
    }

    public boolean isEmpty() {
        return byVictim.isEmpty();
    }

    /** Every stack on everyone (the mob reset or died). */
    public void clear() {
        byVictim.clear();
    }

    private record Entry(int count, double perStack, long expiresAt) {
    }
}
