package net.ledok.mobs_ld.mob.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Skull flight: Mobs_LD's launch (constant speed) against vanilla's (accelerating from 0.1). */
class ProjectileFlightTest {
    private static final double VANILLA_ACCELERATION = 0.1;

    @Test
    void launchedSkullsHoldTheirSpeed() {
        for (double inertia : new double[]{ProjectileFlight.INERTIA, ProjectileFlight.DANGEROUS_INERTIA}) {
            for (double speed : new double[]{0.5, 1.6, 2.4}) {
                double acceleration = ProjectileFlight.accelerationPower(speed, inertia);
                for (int ticks : new int[]{1, 5, 20, 60}) {
                    assertEquals(speed * ticks, ProjectileFlight.distanceAfter(speed, acceleration, inertia, ticks), 1.0e-6,
                            "speed " + speed + ", inertia " + inertia + ", " + ticks + " ticks");
                }
            }
        }
    }

    @Test
    void flightTimeIsDistanceOverSpeed() {
        for (double inertia : new double[]{ProjectileFlight.INERTIA, ProjectileFlight.DANGEROUS_INERTIA}) {
            double acceleration = ProjectileFlight.accelerationPower(1.6, inertia);
            // ceil(d / 1.6): the hit lands on the tick whose move reaches the target.
            assertEquals(7, ProjectileFlight.ticksToReach(10.0, 1.6, acceleration, inertia, 200));
            assertEquals(13, ProjectileFlight.ticksToReach(20.0, 1.6, acceleration, inertia, 200));
            assertEquals(19, ProjectileFlight.ticksToReach(30.0, 1.6, acceleration, inertia, 200));
        }
    }

    @Test
    void vanillaSkullsAccelerate() {
        // Vanilla launches at 0.1 and accelerates by 0.1: 10 / 20 / 30 blocks in 16 / 24 / 31 ticks, blue ones far slower.
        assertEquals(16, ProjectileFlight.ticksToReach(10.0, 0.1, VANILLA_ACCELERATION, ProjectileFlight.INERTIA, 500));
        assertEquals(24, ProjectileFlight.ticksToReach(20.0, 0.1, VANILLA_ACCELERATION, ProjectileFlight.INERTIA, 500));
        assertEquals(31, ProjectileFlight.ticksToReach(30.0, 0.1, VANILLA_ACCELERATION, ProjectileFlight.INERTIA, 500));
        assertEquals(40, ProjectileFlight.ticksToReach(10.0, 0.1, VANILLA_ACCELERATION, ProjectileFlight.DANGEROUS_INERTIA, 500));
        assertEquals(114, ProjectileFlight.ticksToReach(30.0, 0.1, VANILLA_ACCELERATION, ProjectileFlight.DANGEROUS_INERTIA, 500));
        // The old "speed / 19" shortcut only held for normal skulls: 1.9 cruise = 0.1 x 0.95 / 0.05.
        assertEquals(1.9, VANILLA_ACCELERATION * ProjectileFlight.INERTIA / (1.0 - ProjectileFlight.INERTIA), 1.0e-9);
        assertEquals(VANILLA_ACCELERATION, ProjectileFlight.accelerationPower(1.9, ProjectileFlight.INERTIA), 1.0e-9);
    }

    @Test
    void outOfReachReportsNoHit() {
        assertEquals(-1, ProjectileFlight.ticksToReach(100.0, 1.0, ProjectileFlight.accelerationPower(1.0, 0.95), 0.95, 50));
    }
}
