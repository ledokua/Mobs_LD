package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrierCollisionTest {
    @Test
    void dashStopsAtFirstBodyContactEvenWhenOneTickWouldCrossTheBarrier() {
        AABB body = new AABB(-1, 0, -1, 1, 3, 1);
        AABB barrier = new AABB(-2, 0, 4, 2, 4, 6);
        assertEquals(0.3, PaladinBarriers.sweptFraction(body, new Vec3(0, 0, 10), barrier), 1.0e-9);
        assertEquals(0.0, PaladinBarriers.sweptFraction(body.move(0, 0, 5), new Vec3(0, 0, 1), barrier));
        assertEquals(Double.POSITIVE_INFINITY, PaladinBarriers.sweptFraction(body, new Vec3(12, 0, 10), barrier));
        assertEquals(Double.POSITIVE_INFINITY, PaladinBarriers.sweptFraction(body.move(0, 5, 0),
                new Vec3(0, 0, 10), barrier));
        assertEquals(Double.POSITIVE_INFINITY, PaladinBarriers.sweptFraction(body.move(0, 0, 3),
                new Vec3(0, 0, -1), barrier));
    }

    @Test
    void aCrossfireBarrierClipsOneRayWithoutTakingAwayTheOppositeOrAdjacentRays() {
        var zone = new AbilityRun.HitZone(new Shape.Rays(6, 24, 2.6, 0.08), Vec3.ZERO, 0);
        var lanes = BeamLane.fromZone(zone);
        AABB barrier = new AABB(-4, 0, 8, 4, 4, 16);
        assertEquals(6, lanes.size());
        BeamLane blocked = lanes.getFirst();
        assertEquals(8, blocked.firstContact(barrier), 1.0e-9);
        for (BeamLane lane : lanes.subList(1, 6)) {
            assertTrue(lane.firstContact(barrier) >= lane.length());
        }
        var shorter = new BeamLane(blocked.origin(), blocked.yaw(), 8, blocked.width(), blocked.widen());
        assertTrue(shorter.contains(new Vec3(0, 0, 7)));
        assertFalse(shorter.contains(new Vec3(0, 0, 9)));
        assertFalse(new BeamLane(Vec3.ZERO, 0, 0, 2.6, 0.08).contains(Vec3.ZERO));
        assertTrue(lanes.get(3).contains(new Vec3(0, 0, -9)));
    }

    @Test
    void aGlancingBarrierOnlyIntersectsOnceTheLaneWidensIntoItsFootprint() {
        AABB barrier = new AABB(1.8, 0, 8, 4, 4, 16);
        assertEquals(15.0, new BeamLane(Vec3.ZERO, 0, 20, 3, 0.04).firstContact(barrier), 1.0e-9);
        assertEquals(Double.POSITIVE_INFINITY, new BeamLane(Vec3.ZERO, 0, 20, 3, 0).firstContact(barrier));
    }

    @Test
    void rectangleStartsAtItsOffsetAndFollowsItsLockedYaw() {
        var zone = new AbilityRun.HitZone(new Shape.Rectangle(2, 20, 3), new Vec3(2, 0, 0), 90);
        BeamLane lane = BeamLane.fromZone(zone).getFirst();
        assertEquals(-1, lane.origin().x, 1.0e-9);
        assertEquals(6, lane.firstContact(new AABB(-12, 0, -1, -7, 4, 1)), 1.0e-9);
        assertEquals(Double.POSITIVE_INFINITY, lane.firstContact(new AABB(0, 0, -1, 1, 4, 1)));
    }
}
