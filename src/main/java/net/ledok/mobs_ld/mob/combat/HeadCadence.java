package net.ledok.mobs_ld.mob.combat;

import java.util.Arrays;

/**
 * Shot timing of a host's heads (the Wither's three), pure and unit-tested. Each head fires once per interval (the
 * caller rolls one per shot), counted in running ticks only: while the caller pauses it (an ability, a stagger) no
 * timer moves, so the heads do not all fire the moment the pause ends. A head that cannot shoot when its time comes
 * (no target, out of sight) holds a few ticks and tries again.
 *
 * <p>Two heads can be paired (the Wither's side heads): the second one's first shot comes half its interval after
 * the first one's, and neither fires within a gap of the other's last shot, so on one player their skulls land apart
 * instead of the second one vanishing in the first one's hurt immunity.
 *
 * <p>Use per running tick: {@link #advance()}, then for each head {@link #ready} -> fire -> {@link #fired} (or
 * {@link #hold}).
 */
public final class HeadCadence {
    private final int[] cooldown;
    private final int[] hold;
    private final long[] firedAt;
    private int first = -1;
    private int second = -1;
    private long clock;
    private boolean running;

    public HeadCadence(int heads) {
        cooldown = new int[heads];
        hold = new int[heads];
        firedAt = new long[heads];
    }

    /** Keeps {@code second} apart from {@code first} (see the class notes). */
    public HeadCadence pair(int first, int second) {
        this.first = first;
        this.second = second;
        return this;
    }

    public boolean running() {
        return running;
    }

    /**
     * Starts the timers (the fight began): head {@code i} fires {@code intervals[i]} running ticks from now; the paired
     * second head half its own interval after the first.
     */
    public void start(int[] intervals) {
        for (int head = 0; head < cooldown.length; head++) {
            cooldown[head] = Math.max(0, intervals[head]);
        }
        if (first >= 0) {
            cooldown[second] = cooldown[first] + Math.max(0, intervals[second]) / 2;
        }
        Arrays.fill(hold, 0);
        Arrays.fill(firedAt, Long.MIN_VALUE);
        clock = 0L;
        running = true;
    }

    /** The fight is over: the next {@link #start} begins again. */
    public void stop() {
        running = false;
    }

    /** One running tick passes (not called while paused). */
    public void advance() {
        clock++;
        for (int head = 0; head < cooldown.length; head++) {
            if (cooldown[head] > 0) {
                cooldown[head]--;
            }
            if (hold[head] > 0) {
                hold[head]--;
            }
        }
    }

    /**
     * Whether {@code head} may fire this tick: its interval has passed, it is not holding, and (if paired) the other
     * paired head fired at least {@code gapTicks} running ticks ago.
     */
    public boolean ready(int head, int gapTicks) {
        if (!running || cooldown[head] > 0 || hold[head] > 0) {
            return false;
        }
        int partner = head == first ? second : head == second ? first : -1;
        return partner < 0 || firedAt[partner] == Long.MIN_VALUE || clock - firedAt[partner] >= gapTicks;
    }

    /** {@code head} could not shoot (no target, out of sight): it tries again after {@code ticks} running ticks. */
    public void hold(int head, int ticks) {
        hold[head] = Math.max(hold[head], ticks);
    }

    /** {@code head} fired this tick; its next shot comes {@code nextInterval} running ticks later. */
    public void fired(int head, int nextInterval) {
        cooldown[head] = Math.max(1, nextInterval);
        firedAt[head] = clock;
    }
}
