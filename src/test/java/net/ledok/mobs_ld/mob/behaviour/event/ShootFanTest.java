package net.ledok.mobs_ld.mob.behaviour.event;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShootFanTest {
    @Test
    void threeLanesMatchTheirTelegraphHeadingsAndPreservePitch() {
        float yaw = 53;
        Vec3 aimed = Vec3.directionFromRotation(12, yaw);
        List<ShootEvent.Lane> lanes = new ShootEvent.Fan(3, 22).lanes(aimed, yaw);

        assertEquals(List.of(42.0F, 53.0F, 64.0F), lanes.stream().map(ShootEvent.Lane::yaw).toList());
        assertEquals(0, aimed.distanceTo(lanes.get(1).direction()), 1.0e-6);
        for (ShootEvent.Lane lane : lanes) {
            Vec3 direction = lane.direction();
            assertEquals(lane.yaw(), Math.toDegrees(Math.atan2(-direction.x, direction.z)), 0.01);
            assertEquals(aimed.y, direction.y, 1.0e-6);
        }
    }
}
