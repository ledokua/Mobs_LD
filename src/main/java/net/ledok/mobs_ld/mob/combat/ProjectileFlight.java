package net.ledok.mobs_ld.mob.combat;

/**
 * Flight of vanilla accelerating projectiles (AbstractHurtingProjectile: wither skulls, fireballs). Each tick such a
 * projectile first moves by its velocity, then sets {@code velocity = (velocity + direction * accelerationPower) *
 * inertia}, so it settles at {@code accelerationPower * inertia / (1 - inertia)} blocks per tick whatever it was
 * launched with. Launching at {@code speed} with {@link #accelerationPower} for that speed makes it fly at exactly
 * that speed from the first tick: flight time = distance / speed. Pure maths, unit-tested.
 */
public final class ProjectileFlight {
    /** WitherSkull's inertia in air (vanilla 0.95); a dangerous (blue) skull uses 0.73. */
    public static final double INERTIA = 0.95;
    public static final double DANGEROUS_INERTIA = 0.73;

    private ProjectileFlight() {
    }

    /** The acceleration that holds a projectile at {@code speed} blocks per tick against {@code inertia}. */
    public static double accelerationPower(double speed, double inertia) {
        return speed * (1.0 - inertia) / inertia;
    }

    /** Blocks flown in a straight line after {@code ticks} ticks, launched at {@code launchSpeed} (vanilla tick order). */
    public static double distanceAfter(double launchSpeed, double accelerationPower, double inertia, int ticks) {
        double travelled = 0.0;
        double speed = launchSpeed;
        for (int tick = 0; tick < ticks; tick++) {
            travelled += speed;
            speed = (speed + accelerationPower) * inertia;
        }
        return travelled;
    }

    /**
     * Ticks until the projectile has covered {@code distance} (it hits on the tick its move reaches the target), or
     * -1 if it does not within {@code maxTicks}.
     */
    public static int ticksToReach(double distance, double launchSpeed, double accelerationPower, double inertia,
                                   int maxTicks) {
        double travelled = 0.0;
        double speed = launchSpeed;
        for (int tick = 1; tick <= maxTicks; tick++) {
            travelled += speed;
            if (travelled >= distance) {
                return tick;
            }
            speed = (speed + accelerationPower) * inertia;
        }
        return -1;
    }
}
