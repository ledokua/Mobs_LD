package net.ledok.mobs_ld.entity.threat;

import net.ledok.mobs_ld.config.ThreatConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Per-mob aggro ledger: how much threat each player has built up against this mob. The mob targets the
 * highest-threat valid holder (see {@link net.ledok.mobs_ld.entity.ai.ThreatTargetGoal}). Damage adds
 * threat; the provoke/taunt effects rewrite it and stamp per-mob targeting controls.
 */
public class ThreatTable {
    // Lazily allocated: an idle mob that has never taken player damage carries only this null reference, which
    // matters because every mob in the world now owns a ThreatTable (see MobThreatMixin).
    private Map<UUID, Double> threat;
    // Targeting control stamps on THIS mob (see ThreatEffects). Taunt and provoke get separate slots so a taunt's
    // per-tick refresh never overwrites a newer provoke window. Also lazily allocated.
    private Map<UUID, Control> tauntControls;
    private Map<UUID, Control> provokeControls;

    /** A control stamp: application order (higher = more recent) and the game-time it lapses at. */
    public record Control(long sequence, long expiry) {
    }

    /** Add threat (ignored if non-positive). */
    public void add(UUID id, double amount) {
        if (amount <= 0.0) {
            return;
        }
        if (threat == null) {
            threat = new HashMap<>();
        }
        threat.merge(id, amount, (a, b) -> clamp(a + b));
    }

    /** Set a holder's threat outright (used by the taunt effects). Clamped to [0, config maxThreat]. */
    public void set(UUID id, double amount) {
        if (threat == null) {
            threat = new HashMap<>();
        }
        threat.put(id, clamp(amount));
    }

    /** Keep threat finite and within [0, configured maxThreat]; maps NaN to 0 defensively. */
    private static double clamp(double amount) {
        if (Double.isNaN(amount) || amount <= 0.0) {
            return 0.0;
        }
        return Math.min(amount, ThreatConfig.get().maxThreat);
    }

    public double get(UUID id) {
        return threat == null ? 0.0 : threat.getOrDefault(id, 0.0);
    }

    public boolean isEmpty() {
        return threat == null || threat.isEmpty();
    }

    /** The UUIDs currently holding threat. Read-only view for iteration; never null. */
    public Set<UUID> holders() {
        return threat == null ? Collections.emptySet() : threat.keySet();
    }

    /** Highest threat held by anyone, or 0 if the table is empty. */
    public double topThreat() {
        if (threat == null) {
            return 0.0;
        }
        double max = 0.0;
        for (double v : threat.values()) {
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    /** Highest threat held by anyone other than {@code exclude}, or 0 if nobody else is on the table. */
    public double topThreatExcluding(UUID exclude) {
        return topThreatExcluding(uuid -> uuid.equals(exclude));
    }

    /** Highest threat held by anyone the {@code excluded} predicate rejects, or 0 if nobody qualifies. */
    public double topThreatExcluding(Predicate<UUID> excluded) {
        if (threat == null) {
            return 0.0;
        }
        double max = 0.0;
        for (Map.Entry<UUID, Double> e : threat.entrySet()) {
            if (!excluded.test(e.getKey()) && e.getValue() > max) {
                max = e.getValue();
            }
        }
        return max;
    }

    /** UUID of the highest-threat holder that passes {@code valid}, or null if none qualifies. */
    public UUID topHolder(Predicate<UUID> valid) {
        if (threat == null) {
            return null;
        }
        UUID top = null;
        double max = Double.NEGATIVE_INFINITY;
        for (Map.Entry<UUID, Double> e : threat.entrySet()) {
            if (e.getValue() > max && valid.test(e.getKey())) {
                max = e.getValue();
                top = e.getKey();
            }
        }
        return top;
    }

    /** Bleed a flat amount off every holder; entries that hit zero are dropped. */
    public void decay(double amount) {
        if (amount <= 0.0 || threat == null) {
            return;
        }
        threat.entrySet().removeIf(e -> {
            double v = e.getValue() - amount;
            if (v <= 0.0) {
                return true;
            }
            e.setValue(v);
            return false;
        });
    }

    /** Drop every holder for which {@code invalid} is true (e.g. disconnected players). */
    public void prune(Predicate<UUID> invalid) {
        if (threat != null) {
            threat.keySet().removeIf(invalid);
        }
        if (tauntControls != null) {
            tauntControls.keySet().removeIf(invalid);
        }
        if (provokeControls != null) {
            provokeControls.keySet().removeIf(invalid);
        }
    }

    /** Stamp a taunt control for {@code id} on this mob, lapsing at {@code expiry} unless refreshed. */
    public void controlTaunt(UUID id, long sequence, long expiry) {
        if (tauntControls == null) {
            tauntControls = new HashMap<>();
        }
        tauntControls.put(id, new Control(sequence, expiry));
    }

    /** Stamp a provoke control for {@code id} on this mob, lapsing at {@code expiry}. */
    public void controlProvoke(UUID id, long sequence, long expiry) {
        if (provokeControls == null) {
            provokeControls = new HashMap<>();
        }
        provokeControls.put(id, new Control(sequence, expiry));
    }

    /** Most recent unexpired control sequence held by {@code id}, or {@link Long#MIN_VALUE}. Prunes lapsed stamps. */
    public long controlSequence(UUID id, long now) {
        return Math.max(liveSequence(tauntControls, id, now), liveSequence(provokeControls, id, now));
    }

    /** True if {@code id} holds any unexpired control on this mob. */
    public boolean isControlling(UUID id, long now) {
        return controlSequence(id, now) != Long.MIN_VALUE;
    }

    /** Players holding a control stamp (possibly lapsed). Snapshot copy, safe to iterate while pruning. */
    public Set<UUID> controllers() {
        Set<UUID> ids = new HashSet<>();
        if (tauntControls != null) {
            ids.addAll(tauntControls.keySet());
        }
        if (provokeControls != null) {
            ids.addAll(provokeControls.keySet());
        }
        return ids;
    }

    private static long liveSequence(Map<UUID, Control> controls, UUID id, long now) {
        if (controls == null) {
            return Long.MIN_VALUE;
        }
        Control control = controls.get(id);
        if (control == null) {
            return Long.MIN_VALUE;
        }
        if (now >= control.expiry()) {
            controls.remove(id);
            return Long.MIN_VALUE;
        }
        return control.sequence();
    }

    /** Wipe everything {@code id} holds on this mob: threat and any control stamps (the "fade" / aggro clear). */
    public void remove(UUID id) {
        if (threat != null) {
            threat.remove(id);
        }
        if (tauntControls != null) {
            tauntControls.remove(id);
        }
        if (provokeControls != null) {
            provokeControls.remove(id);
        }
    }

    public void clear() {
        if (threat != null) {
            threat.clear();
        }
        if (tauntControls != null) {
            tauntControls.clear();
        }
        if (provokeControls != null) {
            provokeControls.clear();
        }
    }
}
