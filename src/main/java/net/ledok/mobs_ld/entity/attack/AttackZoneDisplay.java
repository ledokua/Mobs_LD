package net.ledok.mobs_ld.entity.attack;

import net.ledok.mobs_ld.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class AttackZoneDisplay {
    private final AttackZoneVisualEntity entity;

    private AttackZoneDisplay(AttackZoneVisualEntity entity) {
        this.entity = entity;
    }

    public static AttackZoneDisplay spawn(
            ServerLevel world,
            Vec3 origin,
            float yawDegrees,
            AttackZone zone,
            AttackDisplayConfig config,
            int windupTicks
    ) {
        AttackZoneVisualEntity visual = ModEntities.ATTACK_ZONE_VISUAL.create(world);
        if (visual == null) {
            throw new IllegalStateException("Failed to create attack zone visual entity");
        }
        visual.setPos(origin.x, origin.y + 0.02, origin.z);
        visual.configure(zone, yawDegrees, config, windupTicks);
        world.addFreshEntity(visual);
        return new AttackZoneDisplay(visual);
    }

    public void update(int windupTimer, int totalWindupTicks) {
        entity.setWindupTimer(windupTimer);
    }

    public void setBrightRed() {
        entity.setForceInvoke(true);
    }

    public void remove() {
        entity.discard();
    }
}
