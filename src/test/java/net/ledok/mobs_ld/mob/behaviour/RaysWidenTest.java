package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitEvent;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Warden's Sonic Boom Circle geometry (design 2.2.1): {@code rays} lanes that widen with distance, the second
 * wave turned by {@code hit.rotate}, and the particles drawn along the lanes. Hit tests run through
 * {@link AbilityRun#inside}, the test every zone uses.
 */
class RaysWidenTest {
    /** The design's Circle table: distance from the Warden -> lane width. */
    private static final double[][] TABLE = {
            {2, 3.1}, {4, 3.7}, {6, 4.3}, {8, 4.9}, {12, 6.1}, {16, 7.3}, {20, 8.5}, {24, 9.7}, {26, 10.3}};
    private static final Shape.Rays CIRCLE = new Shape.Rays(8, 26.0, 2.5, 0.3);
    /** A single line (two opposite rays), to test a lane's edges without its neighbours. */
    private static final Shape.Rays ONE_LANE = new Shape.Rays(2, 26.0, 2.5, 0.3);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Whether a zone laid along facing {@code zoneYaw} hits the point {@code distance} blocks from its origin in the
     * direction of yaw {@code pointYaw}.
     */
    private static boolean hitsAt(Shape shape, float zoneYaw, double distance, double pointYaw) {
        double angle = Math.toRadians(pointYaw);
        // World offset of a facing: forward(yaw) = (-sin, cos).
        return AbilityRun.inside(shape, -Math.sin(angle) * distance, Math.cos(angle) * distance, zoneYaw);
    }

    /** A point {@code degrees} clockwise (as yaw grows) from the zone's own facing. */
    private static boolean hits(Shape shape, float yaw, double distance, double degrees) {
        return hitsAt(shape, yaw, distance, yaw + degrees);
    }

    @Test
    void laneWidthFollowsTheTable() {
        for (double[] row : TABLE) {
            assertEquals(row[1], CIRCLE.widthAt(row[0]), 1.0e-9, "width at " + row[0]);
        }
    }

    /** At every distance of the table: inside up to half the lane's width there, outside just past it. */
    @Test
    void laneEdgesWidenWithDistance() {
        for (double[] row : TABLE) {
            double d = row[0];
            double half = row[1] / 2.0;
            for (double side : new double[]{-1.0, 1.0}) {
                assertTrue(ONE_LANE.contains(d, side * (half - 0.05)), "inside at " + d);
                assertFalse(ONE_LANE.contains(d, side * (half + 0.05)), "outside at " + d);
                assertTrue(ONE_LANE.contains(-d, side * (half - 0.05)), "the opposite ray too, at " + d);
            }
            assertTrue(ONE_LANE.contains(d, 0.0));
        }
        assertFalse(ONE_LANE.contains(26.1, 0.0), "lanes stop at their length");
        // Without widen a lane keeps its width (Gorefist-era rays are unchanged).
        Shape.Rays straight = new Shape.Rays(2, 26.0, 2.5);
        assertTrue(straight.contains(20.0, 1.2));
        assertFalse(straight.contains(20.0, 1.3));
        assertEquals(0.0, straight.widen());
    }

    /**
     * Wave 1 has free gaps from 6 blocks out (their middles 22.5 degrees off a lane), which wave 2, turned 22.5
     * degrees, fills; nowhere on the floor the lanes reach is safe from both waves.
     */
    @Test
    void twoWavesLeaveNoSafeSpot() {
        float yaw = 37.0F;
        float second = yaw + 22.5F;
        for (double[] row : TABLE) {
            // The last row is the lanes' very end: test just inside it, clear of rounding at the tip.
            double d = Math.min(row[0], 25.99);
            boolean freeGap = 2.0 * d * Math.sin(Math.toRadians(22.5)) > row[1];
            assertEquals(!freeGap, hits(CIRCLE, yaw, d, 22.5), "wave 1 at a gap's middle, " + d + " blocks");
            assertTrue(hits(CIRCLE, second, d, 22.5 - 22.5), "wave 2 runs down the gap, " + d + " blocks");
            for (double degrees = 0.0; degrees < 360.0; degrees += 0.5) {
                assertTrue(hitsAt(CIRCLE, yaw, d, yaw + degrees) || hitsAt(CIRCLE, second, d, yaw + degrees),
                        "safe spot at " + d + " blocks, " + degrees + " degrees");
            }
        }
        assertFalse(hits(CIRCLE, yaw, 6.0, 22.5), "a 0.3-block free gap at 6");
        assertTrue(hits(CIRCLE, yaw, 4.0, 22.5), "no free gap at 4");
        assertFalse(hits(CIRCLE, yaw, 27.0, 0.0) || hitsAt(CIRCLE, second, 27.0, yaw), "outside past the lanes' end");
    }

    @Test
    void rotateTurnsTheZoneFromTheLockedFacing() {
        HitEvent wave2 = hit("{\"type\": \"hit\", \"at\": 3.4, \"rotate\": 22.5, \"turn_body\": true, "
                + "\"shape\": {\"type\": \"rays\", \"count\": 8, \"length\": 26, \"width\": 2.5, \"widen\": 0.3}}");
        HitEvent wave3 = hit("{\"type\": \"hit\", \"at\": 5.1, \"rotate\": 0, \"turn_body\": true, "
                + "\"shape\": {\"type\": \"rays\", \"count\": 8, \"length\": 26, \"width\": 2.5}}");
        HitEvent plain = hit("{\"type\": \"hit\", \"at\": 1.0, \"shape\": {\"type\": \"circle\", \"radius\": 2}}");
        assertEquals(0.3, ((Shape.Rays) wave2.shape()).widen());
        assertTrue(wave2.turnBody());
        // Locked at 10; the body has since turned to 32.5 for wave 2.
        assertEquals(32.5F, wave2.zoneYaw(10.0F, 10.0F), 1.0e-4F);
        assertEquals(10.0F, wave3.zoneYaw(32.5F, 10.0F), 1.0e-4F, "wave 3 goes back to the wave-1 angle");
        assertEquals(-167.5F, wave2.zoneYaw(170.0F, 170.0F), 1.0e-4F, "wrapped");
        assertEquals(32.5F, plain.zoneYaw(32.5F, 10.0F), "without rotate: the current facing, as before");
        assertFalse(plain.turnBody());
        assertTrue(plain.rotate().isEmpty());
        // The rotated zone's hit test: a point on a wave-2 lane is not on a wave-1 lane at 12 blocks.
        assertTrue(hits(CIRCLE, wave2.zoneYaw(10.0F, 10.0F), 12.0, 0.0));
        assertFalse(hits(CIRCLE, 10.0F, 12.0, 22.5));
    }

    @Test
    void particlesRunAlongEveryLane() {
        List<double[]> points = CIRCLE.centreLine(2.0);
        assertEquals(8 * 13, points.size(), "8 rays, one point every 2 blocks up to 26");
        for (double[] point : points) {
            assertTrue(CIRCLE.contains(point[0], point[1]));
            double distance = Math.hypot(point[0], point[1]);
            assertTrue(distance >= 2.0 - 1.0e-9 && distance <= 26.0 + 1.0e-9);
        }
        assertEquals(40, new Shape.Rectangle(2.5, 40.0, 0.0).centreLine(1.0).size());
        List<double[]> rim = new Shape.Circle(6.0).centreLine(2.0);
        assertEquals(19, rim.size());
        rim.forEach(point -> assertEquals(6.0, Math.hypot(point[0], point[1]), 1.0e-9));
    }

    private static HitEvent hit(String json) {
        AbilityEvent event = AbilityEvent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        return (HitEvent) event;
    }
}
