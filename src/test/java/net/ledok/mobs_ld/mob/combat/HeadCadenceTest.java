package net.ledok.mobs_ld.mob.combat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Wither heads' skull timing: exact intervals, pauses, holds, and side heads kept apart. */
class HeadCadenceTest {
    private static final int CENTER = 0;
    private static final int RIGHT = 1;
    private static final int LEFT = 2;

    /** Runs {@code ticks} running ticks; each head fires as soon as it may, with its next interval from {@code next}. */
    private static List<List<Long>> run(HeadCadence cadence, int ticks, int gap, java.util.function.IntUnaryOperator next) {
        List<List<Long>> shots = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (long tick = 1; tick <= ticks; tick++) {
            cadence.advance();
            for (int head = 0; head < 3; head++) {
                if (cadence.ready(head, gap)) {
                    shots.get(head).add(tick);
                    cadence.fired(head, next.applyAsInt(head));
                }
            }
        }
        return shots;
    }

    @Test
    void firesExactlyEveryInterval() {
        HeadCadence cadence = new HeadCadence(3);
        cadence.start(new int[]{80, 0, 0}); // every 4.0 s: the first shot one interval into the fight
        List<Long> center = run(cadence, 400, 0, head -> 80).get(CENTER);
        assertEquals(List.of(80L, 160L, 240L, 320L, 400L), center.subList(0, 5));
        // 3.0 s in the last phase: 60 ticks apart, not 61.
        cadence.start(new int[]{60, 0, 0});
        center = run(cadence, 240, 0, head -> 60).get(CENTER);
        assertEquals(List.of(60L, 120L, 180L, 240L), center.subList(0, 4));
    }

    @Test
    void rolledIntervalsAreKeptExactly() {
        Random random = new Random(11);
        List<Integer> rolled = new ArrayList<>();
        HeadCadence cadence = new HeadCadence(3);
        cadence.start(new int[]{0, 90, 0});
        List<Long> shots = run(cadence, 2000, 0, head -> {
            if (head != RIGHT) {
                return 100000; // the other heads are not under test
            }
            int interval = 80 + random.nextInt(21); // [4, 5] s
            rolled.add(interval);
            return interval;
        }).get(RIGHT);
        for (int i = 1; i < shots.size(); i++) {
            assertEquals((long) rolled.get(i - 1), shots.get(i) - shots.get(i - 1), "shot " + i);
        }
        assertEquals(90L, shots.get(0));
    }

    @Test
    void pausesFreezeTheTimersAndHoldsRetry() {
        HeadCadence cadence = new HeadCadence(3);
        cadence.start(new int[]{80, 0, 0});
        for (int tick = 0; tick < 79; tick++) {
            cadence.advance();
        }
        assertFalse(cadence.ready(CENTER, 0));
        // An ability runs for a while: no advance() calls, nothing changes.
        assertFalse(cadence.ready(CENTER, 0));
        cadence.advance();
        assertTrue(cadence.ready(CENTER, 0));
        // No sight line: hold 4 ticks, then ready again (no full interval lost).
        cadence.hold(CENTER, 4);
        for (int tick = 0; tick < 3; tick++) {
            cadence.advance();
            assertFalse(cadence.ready(CENTER, 0));
        }
        cadence.advance();
        assertTrue(cadence.ready(CENTER, 0));
        cadence.stop();
        assertFalse(cadence.ready(CENTER, 0));
    }

    @Test
    void sideHeadsStayApart() {
        HeadCadence cadence = new HeadCadence(3).pair(RIGHT, LEFT);
        // Same cadence for both side heads ([4, 5] s, gap = half of 4.5 s): the left one starts half an interval later.
        cadence.start(new int[]{80, 84, 90});
        Random random = new Random(5);
        int gap = 45;
        List<List<Long>> shots = run(cadence, 4000, gap, head -> 80 + random.nextInt(21));
        assertEquals(84L, shots.get(RIGHT).get(0));
        assertEquals(84L + 45L, shots.get(LEFT).get(0));
        List<long[]> sides = new ArrayList<>();
        shots.get(RIGHT).forEach(tick -> sides.add(new long[]{tick, RIGHT}));
        shots.get(LEFT).forEach(tick -> sides.add(new long[]{tick, LEFT}));
        sides.sort((a, b) -> Long.compare(a[0], b[0]));
        for (int i = 1; i < sides.size(); i++) {
            if (sides.get(i)[1] != sides.get(i - 1)[1]) {
                assertTrue(sides.get(i)[0] - sides.get(i - 1)[0] >= gap,
                        "side skulls " + sides.get(i - 1)[0] + " and " + sides.get(i)[0] + " too close");
            }
        }
        // Each side head still fires about every 4-5 s (the gap only nudges, it does not starve either head).
        assertTrue(shots.get(RIGHT).size() >= 4000 / 110, "right fired " + shots.get(RIGHT).size());
        assertTrue(shots.get(LEFT).size() >= 4000 / 110, "left fired " + shots.get(LEFT).size());
        // The middle head runs on its own and may fire together with either side head.
        assertEquals(80L, shots.get(CENTER).get(0));
    }
}
