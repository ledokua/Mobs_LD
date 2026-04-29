package net.ledok.mobs_ld.entity.attack;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class AttackZoneDisplay {
    private static final DustParticleOptions DIM_RED = new DustParticleOptions(new Vector3f(0.6F, 0.0F, 0.0F), 1.0F);
    private static final DustParticleOptions BRIGHT_RED = new DustParticleOptions(new Vector3f(1.0F, 0.0F, 0.0F), 1.2F);

    private final ServerLevel world;
    private final Vec3 origin;
    private final float yawDegrees;
    private final AttackZone zone;

    private AttackZoneDisplay(ServerLevel world, Vec3 origin, float yawDegrees, AttackZone zone) {
        this.world = world;
        this.origin = origin;
        this.yawDegrees = yawDegrees;
        this.zone = zone;
    }

    public static AttackZoneDisplay spawn(ServerLevel world, Vec3 origin, float yawDegrees, AttackZone zone) {
        AttackZoneDisplay display = new AttackZoneDisplay(world, origin, yawDegrees, zone);
        display.drawDimRed();
        return display;
    }

    public void drawDimRed() {
        draw(DIM_RED);
    }

    public void setBrightRed() {
        draw(BRIGHT_RED);
    }

    public void remove() {
        // Particle telegraph has no persistent entity to remove.
    }

    private void draw(DustParticleOptions particle) {
        float reach = zone.maxForwardReach();
        int points = Math.max(12, (int) (reach * 16));
        double yawRad = Math.toRadians(yawDegrees);
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        switch (zone) {
            case AttackZone.Rectangle r -> drawRectangleOutline(particle, forward, right, r, points);
            case AttackZone.Cone c -> drawConeOutline(particle, forward, c, points);
            case AttackZone.Circle c -> drawCircleOutline(particle, c, points);
        }
    }

    private void drawRectangleOutline(
            DustParticleOptions particle,
            Vec3 forward,
            Vec3 right,
            AttackZone.Rectangle rectangle,
            int points
    ) {
        Vec3 center = origin.add(forward.scale(rectangle.offsetForward() + rectangle.length() * 0.5));
        Vec3 halfForward = forward.scale(rectangle.length() * 0.5);
        Vec3 halfRight = right.scale(rectangle.width() * 0.5);

        Vec3 frontLeft = center.add(halfForward).subtract(halfRight);
        Vec3 frontRight = center.add(halfForward).add(halfRight);
        Vec3 backLeft = center.subtract(halfForward).subtract(halfRight);
        Vec3 backRight = center.subtract(halfForward).add(halfRight);

        int edgePoints = Math.max(6, points / 4);
        drawLine(particle, backLeft, frontLeft, edgePoints);
        drawLine(particle, backRight, frontRight, edgePoints);
        drawLine(particle, backLeft, backRight, edgePoints);
        drawLine(particle, frontLeft, frontRight, edgePoints);
    }

    private void drawConeOutline(DustParticleOptions particle, Vec3 forward, AttackZone.Cone cone, int points) {
        double halfAngle = Math.toRadians(cone.angleDegrees() * 0.5);
        Vec3 leftDir = rotateY(forward, -halfAngle).normalize();
        Vec3 rightDir = rotateY(forward, halfAngle).normalize();

        int sidePoints = Math.max(6, points / 3);
        drawLine(particle, origin, origin.add(leftDir.scale(cone.maxDistance())), sidePoints);
        drawLine(particle, origin, origin.add(rightDir.scale(cone.maxDistance())), sidePoints);

        int arcPoints = Math.max(10, points);
        for (int i = 0; i <= arcPoints; i++) {
            double t = (double) i / arcPoints;
            double angle = -halfAngle + (halfAngle * 2.0) * t;
            Vec3 arcDir = rotateY(forward, angle).normalize();
            spawnParticle(particle, origin.add(arcDir.scale(cone.maxDistance())));
        }
    }

    private void drawCircleOutline(DustParticleOptions particle, AttackZone.Circle circle, int points) {
        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            double angle = (Math.PI * 2.0) * t;
            Vec3 pos = origin.add(Math.cos(angle) * circle.radius(), 0.0, Math.sin(angle) * circle.radius());
            spawnParticle(particle, pos);
        }
    }

    private void drawLine(DustParticleOptions particle, Vec3 start, Vec3 end, int points) {
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            Vec3 pos = start.lerp(end, t);
            spawnParticle(particle, pos);
        }
    }

    private Vec3 rotateY(Vec3 vec, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(
                vec.x * cos - vec.z * sin,
                0.0,
                vec.x * sin + vec.z * cos
        );
    }

    private void spawnParticle(DustParticleOptions particle, Vec3 pos) {
        world.sendParticles(particle, pos.x, origin.y + 0.05, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
