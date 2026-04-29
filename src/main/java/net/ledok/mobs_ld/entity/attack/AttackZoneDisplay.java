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
        display.draw(DIM_RED);
        return display;
    }

    public void setBrightRed() {
        draw(BRIGHT_RED);
    }

    public void remove() {
        // Particle telegraph has no persistent entity to remove.
    }

    private void draw(DustParticleOptions particle) {
        float reach = zone.maxForwardReach();
        int points = Math.max(8, (int) (reach * 12));
        double yawRad = Math.toRadians(yawDegrees);
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);

        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            Vec3 pos = switch (zone) {
                case AttackZone.Rectangle r -> origin
                        .add(forward.scale(r.offsetForward() + r.length() * t))
                        .add(right.scale((i % 2 == 0 ? -1 : 1) * r.width() * 0.5));
                case AttackZone.Cone c -> {
                    double dist = c.maxDistance() * t;
                    double halfAngle = Math.toRadians(c.angleDegrees() / 2.0);
                    double angle = (i % 2 == 0 ? -halfAngle : halfAngle);
                    Vec3 dir = new Vec3(
                            forward.x * Math.cos(angle) - forward.z * Math.sin(angle),
                            0.0,
                            forward.x * Math.sin(angle) + forward.z * Math.cos(angle)
                    );
                    yield origin.add(dir.normalize().scale(dist));
                }
                case AttackZone.Circle c -> {
                    double angle = (Math.PI * 2.0) * t;
                    yield origin.add(Math.cos(angle) * c.radius(), 0.0, Math.sin(angle) * c.radius());
                }
            };
            world.sendParticles(particle, pos.x, origin.y + 0.05, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
