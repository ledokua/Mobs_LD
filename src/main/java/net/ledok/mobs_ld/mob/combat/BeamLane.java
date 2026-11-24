package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** One forward lane of a locked rectangle or radial attack. */
public record BeamLane(Vec3 origin, float yaw, double length, double width, double widen) {
    /** Returns the resolved lanes only if at least one was intercepted. */
    public static List<BeamLane> clip(AbilityRun run, AbilityRun.HitZone zone, double height, double depth,
                                      Map<Entity, Vec3> impacts) {
        Vec3 origin = zone.origin();
        double reach = zone.shape().reach();
        AABB bounds = new AABB(origin.x - reach, origin.y - depth, origin.z - reach,
                origin.x + reach, origin.y + height, origin.z + reach).inflate(1.0e-6);
        List<Entity> barriers = PaladinBarriers.between(run, bounds);
        if (barriers.isEmpty()) {
            return List.of();
        }
        List<BeamLane> lanes = fromZone(zone);
        List<BeamLane> clipped = new ArrayList<>(lanes.size());
        boolean intercepted = false;
        for (BeamLane lane : lanes) {
            double length = lane.length();
            Entity blocker = null;
            for (Entity barrier : barriers) {
                double contact = lane.firstContact(barrier.getBoundingBox());
                if (contact < length) {
                    length = contact;
                    blocker = barrier;
                }
            }
            if (blocker == null) {
                clipped.add(lane);
                continue;
            }
            intercepted = true;
            clipped.add(new BeamLane(lane.origin(), lane.yaw(), length, lane.width(), lane.widen()));
            Vec3 tip = lane.origin().add(AbilityRun.forward(lane.yaw()).scale(length)).add(0, run.mob().getEyeHeight(), 0);
            impacts.putIfAbsent(blocker, PaladinBarriers.closestPoint(blocker.getBoundingBox(), tip));
        }
        return intercepted ? List.copyOf(clipped) : List.of();
    }

    static List<BeamLane> fromZone(AbilityRun.HitZone zone) {
        if (zone.shape() instanceof Shape.Rectangle rect) {
            Vec3 origin = zone.origin().add(AbilityRun.forward(zone.yaw()).scale(rect.offset()));
            return List.of(new BeamLane(origin, zone.yaw(), rect.length(), rect.width(), 0.0));
        }
        if (zone.shape() instanceof Shape.Rays rays) {
            int count = rays.rayCount();
            List<BeamLane> lanes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lanes.add(new BeamLane(zone.origin(), zone.yaw() + i * (360.0F / count),
                        rays.length(), rays.width(), rays.widen()));
            }
            return lanes;
        }
        return List.of();
    }

    public boolean contains(Vec3 point) {
        Vec3 delta = point.subtract(origin);
        double along = delta.dot(AbilityRun.forward(yaw));
        double across = delta.dot(AbilityRun.right(yaw));
        return length > 0.0 && along >= -1.0e-9 && along <= length
                && Math.abs(across) <= (width + widen * along) / 2.0 + 1.0e-9;
    }

    /** Nearest point of the barrier's footprint inside the lane's trapezoid, in forward coordinates. */
    double firstContact(AABB box) {
        Vec3 forward = AbilityRun.forward(yaw);
        Vec3 right = AbilityRun.right(yaw);
        List<Point> footprint = List.of(
                project(box.minX, box.minZ, forward, right),
                project(box.maxX, box.minZ, forward, right),
                project(box.maxX, box.maxZ, forward, right),
                project(box.minX, box.maxZ, forward, right));
        double halfWidth = width / 2.0;
        double spread = widen / 2.0;
        footprint = clipEdge(footprint, Point::along);
        footprint = clipEdge(footprint, point -> halfWidth + point.along() * spread - point.across());
        footprint = clipEdge(footprint, point -> halfWidth + point.along() * spread + point.across());
        // The caller compares this with the full lane length.
        return footprint.stream().mapToDouble(Point::along).min().orElse(Double.POSITIVE_INFINITY);
    }

    private Point project(double x, double z, Vec3 forward, Vec3 right) {
        double dx = x - origin.x;
        double dz = z - origin.z;
        return new Point(dx * forward.x + dz * forward.z, dx * right.x + dz * right.z);
    }

    /** Clip a convex polygon against a half-plane with nonnegative signed distance on its inside. */
    private static List<Point> clipEdge(List<Point> polygon, ToDoubleFunction<Point> distance) {
        if (polygon.isEmpty()) {
            return polygon;
        }
        List<Point> result = new ArrayList<>(polygon.size() + 1);
        Point previous = polygon.getLast();
        double before = distance.applyAsDouble(previous);
        for (Point point : polygon) {
            double after = distance.applyAsDouble(point);
            if ((before < 0.0) != (after < 0.0)) {
                double t = before / (before - after);
                result.add(new Point(previous.along() + t * (point.along() - previous.along()),
                        previous.across() + t * (point.across() - previous.across())));
            }
            if (after >= 0.0) {
                result.add(point);
            }
            previous = point;
            before = after;
        }
        return result;
    }

    private record Point(double along, double across) {
    }
}
