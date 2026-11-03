package net.ledok.mobs_ld.mob.combat;

/**
 * An ability's own clock: it advances {@code speed} ticks per server tick, so a phase's {@code attack_speed} of 1.2
 * runs the whole timeline (telegraphs, hits, clip events) 1.2 times faster; an ability with {@code fixed_speed} runs
 * at 1 (see {@link AbilityChoice#speed}).
 */
final class AbilityClock {
    private final double speed;
    private double ticks;

    AbilityClock(double speed) {
        this.speed = speed;
    }

    /** One server tick passed. */
    void advance() {
        ticks += speed;
    }

    /** Seconds on the ability's timeline. */
    double seconds() {
        return ticks / 20.0;
    }

    double speed() {
        return speed;
    }
}
