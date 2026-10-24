package net.ledok.mobs_ld.mob.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A dash's lane: contact against its drawn edges, the swept test, strip segmentation and the side push. */
class DashLaneTest {
    /** The Soul Rush: a 3-block body (scale 1), hitbox 0.9 deep, 1.4 blocks per tick down a 28-block lane. */
    private static final double WIDTH = 3.0;
    private static final double HALF_DEPTH = 0.45;
    private static final double SPEED = 1.4;
    private static final DashLane LANE = new DashLane(WIDTH, 28.0);

    @Test
    void laneEdgesAreTheDrawnStrip() {
        // A centre exactly on the edge is on the lane, both sides; the old contact box reached 0.45 further out.
        assertTrue(LANE.contains(10.0, 1.5));
        assertTrue(LANE.contains(10.0, -1.5));
        assertFalse(LANE.contains(10.0, 1.501));
        assertFalse(LANE.contains(10.0, -1.501));
        assertFalse(LANE.contains(10.0, 1.95), "the old box (width/2 + half the hitbox) would have hit here");
        // Start and end of the lane.
        assertTrue(LANE.contains(0.0, 0.0));
        assertTrue(LANE.contains(28.0, 0.0));
        assertFalse(LANE.contains(-0.01, 0.0));
        assertFalse(LANE.contains(28.01, 0.0));
    }

    @Test
    void contactNeedsTheCentreOnTheLane() {
        // The body passes right by: on the edge is hit, a step further out is not, on either side.
        assertTrue(LANE.contact(9.0, 10.4, HALF_DEPTH, 10.0, 10.0, 1.5));
        assertTrue(LANE.contact(9.0, 10.4, HALF_DEPTH, 10.0, 10.0, -1.5));
        assertFalse(LANE.contact(9.0, 10.4, HALF_DEPTH, 10.0, 10.0, 1.6));
        assertFalse(LANE.contact(9.0, 10.4, HALF_DEPTH, 10.0, 10.0, -1.6));
        // Not reached yet / already past.
        assertFalse(LANE.contact(5.0, 6.4, HALF_DEPTH, 10.0, 10.0, 0.0));
        assertFalse(LANE.contact(12.0, 13.4, HALF_DEPTH, 10.0, 10.0, 0.0));
    }

    /** Body centre positions tick by tick: standing at the start on the first tick, then {@code speed} per tick. */
    private static double[] bodyPath(double speed, double length) {
        int ticks = (int) Math.ceil(length / speed) + 1;
        double[] path = new double[ticks + 1];
        for (int t = 1; t <= ticks; t++) {
            path[t] = Math.min(length, path[t - 1] + (t == 1 ? 0.0 : speed));
        }
        return path;
    }

    @Test
    void sweptTestDoesNotTunnelAtRushSpeed() {
        double[] path = bodyPath(SPEED, LANE.length());
        int naiveMisses = 0;
        for (int step = 0; step <= 2800; step++) {
            double p = step / 100.0;
            int hits = 0;
            boolean naive = false;
            for (int t = 1; t < path.length; t++) {
                if (LANE.contact(path[t - 1], path[t], HALF_DEPTH, p, p, 0.0)) {
                    hits++;
                }
                naive |= Math.abs(p - path[t]) <= HALF_DEPTH;
            }
            assertTrue(hits >= 1, "a still victim at " + p + " was skipped");
            if (!naive) {
                naiveMisses++;
            }
        }
        // 1.4 blocks a tick is more than the 0.9-block body: only testing where the body is now would miss people.
        assertTrue(naiveMisses > 0);
    }

    /**
     * Runs a dash past one victim the way DashEvent does: each tick the sweep checks the body's position, and the
     * victim is only looked at while inside the search area (from behind the body to {@link DashLane.Sweep#LOOKAHEAD}
     * blocks past its front and coming step). The victim starts at {@code startAt} and moves {@code velocity} blocks
     * per tick along the lane once the dash runs. With {@code staleBefore} the victim's previous position is taken to
     * be its current one, as vanilla's xo/zo are for a player ticked earlier in the same server tick.
     */
    private static boolean dashMeets(double startAt, double velocity, boolean staleBefore) {
        double[] path = bodyPath(SPEED, LANE.length());
        DashLane.Sweep<String> sweep = new DashLane.Sweep<>(LANE, HALF_DEPTH);
        double victim = startAt;
        for (int t = 1; t < path.length; t++) {
            if (t > 1) {
                victim += velocity;
            }
            sweep.check(path[t]);
            double step = Math.min(SPEED, LANE.length() - path[t]);
            double behind = Math.min(sweep.bodyBefore(), sweep.bodyNow()) - HALF_DEPTH - 1.5;
            double ahead = Math.max(sweep.bodyBefore(), sweep.bodyNow()) + HALF_DEPTH + step + DashLane.Sweep.LOOKAHEAD;
            if (victim < behind || victim > ahead) {
                continue;
            }
            boolean met = staleBefore
                    ? LANE.contact(sweep.bodyBefore(), sweep.bodyNow(), HALF_DEPTH, victim, victim, 0.2)
                    : sweep.meets("victim", victim, 0.2);
            if (met) {
                return true;
            }
        }
        return false;
    }

    @Test
    void victimsMovingThroughTheBodyAreStillMet() {
        // Rolling (1.2 b/t) or running (0.3 b/t) head-on into the rush, or fleeing ahead of it at sprint speed.
        for (double velocity : new double[]{-1.2, -0.3, 0.28}) {
            for (double startAt = 3.0; startAt < 20.0; startAt += 0.37) {
                assertTrue(dashMeets(startAt, velocity, false), "victim from " + startAt + " moving " + velocity + " was skipped");
            }
        }
    }

    @Test
    void headOnRollNeedsTheRememberedPosition() {
        // Taking the previous position from xo/zo (already the current one for a player ticked earlier) skips some
        // head-on rolls: body and victim close 2.6 blocks a tick against a 0.9-block body.
        int skipped = 0;
        for (double startAt = 3.0; startAt < 20.0; startAt += 0.37) {
            if (!dashMeets(startAt, -1.2, true)) {
                skipped++;
            }
        }
        assertTrue(skipped > 0);
    }

    @Test
    void sweepForgetsVictimsThatLeftTheArea() {
        DashLane.Sweep<String> sweep = new DashLane.Sweep<>(LANE, HALF_DEPTH);
        sweep.check(5.0);
        assertFalse(sweep.meets("v", 20.0, 0.0)); // far ahead
        sweep.check(6.4); // the victim is not looked at this tick (it stepped out of the search area)
        sweep.check(7.8);
        // Back behind the body: an old position ahead must not make it look as if it passed through.
        assertFalse(sweep.meets("v", 2.0, 0.0));
        // A victim first seen this tick counts as having stood still: right under the body it is met.
        sweep.check(9.2);
        assertTrue(sweep.meets("w", 9.0, 0.0));
    }

    @Test
    void steppingOutOfTheLaneDodges() {
        // A victim that left the lane by the time the body passes is not hit, even if it stood on it before.
        assertFalse(LANE.contact(9.0, 10.4, HALF_DEPTH, 10.0, 10.0, 1.8));
        // Two blocks out of the lane's centre line clears the Soul Rush's 3-block body.
        assertFalse(LANE.contact(9.0, 10.4, HALF_DEPTH, 10.0, 10.0, 2.0));
    }

    @Test
    void flatStripIsCutIntoEqualSegments() {
        double[] floors = new double[28];
        java.util.Arrays.fill(floors, 64.0);
        List<DashLane.Segment> segments = DashLane.segments(28.0, DashLane.MAX_SEGMENT, floors);
        assertEquals(5, segments.size());
        double at = 0.0;
        for (DashLane.Segment segment : segments) {
            assertEquals(at, segment.start(), 1.0e-9);
            assertEquals(5.6, segment.length(), 1.0e-9);
            assertTrue(segment.length() <= DashLane.MAX_SEGMENT);
            assertEquals(64.0, segment.floor());
            at = segment.end();
        }
        assertEquals(28.0, at, 1.0e-9);
        // A 17-block lane (12 to the target + 5): 3 segments; exactly 6 blocks: one.
        assertEquals(3, DashLane.segments(17.0, 6.0, new double[17]).size());
        assertEquals(1, DashLane.segments(6.0, 6.0, new double[6]).size());
    }

    @Test
    void stripFollowsSlopesAndSkipsTheVoid() {
        // A slope rising a block every 3 blocks: one segment per terrace, each on its own floor.
        double[] slope = {64, 64, 64, 65, 65, 65, 66, 66, 66};
        List<DashLane.Segment> terraces = DashLane.segments(9.0, 6.0, slope);
        assertEquals(3, terraces.size());
        assertEquals(64.0, terraces.get(0).floor());
        assertEquals(66.0, terraces.get(2).floor());
        assertEquals(3.0, terraces.get(1).start(), 1.0e-9);
        // A slab step stays in one segment, laid at the higher floor (never buried).
        List<DashLane.Segment> slab = DashLane.segments(4.0, 6.0, new double[]{64, 64.5, 64.5, 64});
        assertEquals(1, slab.size());
        assertEquals(64.5, slab.get(0).floor());
        // A gap over the void (no floor within the ground scan) is left out.
        List<DashLane.Segment> gap = DashLane.segments(8.0, 6.0, new double[]{64, 64, Double.NaN, Double.NaN, Double.NaN, 64, 64, 64});
        assertEquals(2, gap.size());
        assertEquals(2.0, gap.get(0).end(), 1.0e-9);
        assertEquals(5.0, gap.get(1).start(), 1.0e-9);
        assertEquals(8.0, gap.get(1).end(), 1.0e-9);
        // A lane ending part-way through a block: the last sample is the middle of the short piece.
        assertEquals(11, DashLane.samples(10.5));
        assertEquals(10.25, DashLane.sampleAt(10, 10.5), 1.0e-9);
        assertEquals(10.5, DashLane.segments(10.5, 6.0, new double[11]).getLast().end(), 1.0e-9);
    }

    @Test
    void arrowheadScalesWithTheLaneAndHasItsOwnFloor() {
        assertEquals(2.25, DashLane.headLength(3.0), 1.0e-9);
        assertEquals(6.75, DashLane.headLength(9.0), 1.0e-9);
        // Its own samples past the lane's end: the highest floor, even if the lane's last block has none.
        assertEquals(65.0, DashLane.headFloor(new double[]{64.0, 65.0, Double.NaN}));
        assertEquals(63.0, DashLane.headFloor(new double[]{Double.NaN, 63.0, Double.NaN}));
        assertTrue(Double.isNaN(DashLane.headFloor(new double[]{Double.NaN, Double.NaN, Double.NaN})));
        assertEquals(3, DashLane.samples(DashLane.headLength(3.0)));
    }

    @Test
    void sidePushGoesOutOfTheLaneOnTheVictimsSide() {
        assertEquals(1, DashLane.sideSign(0.5));
        assertEquals(-1, DashLane.sideSign(-0.5));
        assertEquals(1, DashLane.sideSign(0.0));
        // Facing south (+z), the right is west (-x): a victim west of the centre line is shoved further west.
        float yaw = 0.0F;
        Vec3 forward = AbilityRun.forward(yaw);
        Vec3 right = AbilityRun.right(yaw);
        assertEquals(1.0, forward.z, 1.0e-9);
        Vec3 victim = new Vec3(-1.0, 0.0, 5.0);
        double across = victim.x * right.x + victim.z * right.z;
        assertEquals(1.0, across, 1.0e-9);
        Vec3 push = right.scale(DashLane.sideSign(across));
        assertTrue(push.x < 0.0, "pushed west, away from the centre line");
        assertEquals(0.0, push.z, 1.0e-9);
        Vec3 east = new Vec3(1.0, 0.0, 5.0);
        Vec3 eastPush = right.scale(DashLane.sideSign(east.x * right.x + east.z * right.z));
        assertTrue(eastPush.x > 0.0, "pushed east");
    }
}
