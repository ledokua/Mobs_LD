package net.ledok.mobs_ld.entity.attack;

import net.ledok.mobs_ld.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class AttackZoneDisplay {
    private final AttackZoneVisualEntity preview;
    private final AttackZoneVisualEntity fill;

    private AttackZoneDisplay(AttackZoneVisualEntity preview, AttackZoneVisualEntity fill) {
        this.preview = preview;
        this.fill = fill;
    }

    public static AttackZoneDisplay spawn(
            ServerLevel world,
            Vec3 origin,
            float yawDegrees,
            AttackZone zone,
            AttackDisplayConfig config,
            int windupTicks
    ) {
        AttackZoneVisualEntity previewEntity = ModEntities.ATTACK_ZONE_VISUAL.create(world);
        AttackZoneVisualEntity fillEntity = ModEntities.ATTACK_ZONE_VISUAL.create(world);
        if (previewEntity == null || fillEntity == null) {
            throw new IllegalStateException("Failed to create attack zone visual entity");
        }
        previewEntity.setPos(origin.x, origin.y + 0.16, origin.z);
        previewEntity.configure(zone, yawDegrees, config, windupTicks, true);
        world.addFreshEntity(previewEntity);

        fillEntity.setPos(origin.x, origin.y + 0.18, origin.z);
        fillEntity.configure(zone, yawDegrees, config, windupTicks, false);
        world.addFreshEntity(fillEntity);

        return new AttackZoneDisplay(previewEntity, fillEntity);
    }

    public void update(int windupTimer, int totalWindupTicks) {
        fill.setWindupTimer(windupTimer);
    }

    public void setBrightRed() {
        preview.setForceInvoke(true);
        fill.setForceInvoke(true);
    }

    public void updatePosition(Vec3 pos) {
        preview.setPos(pos.x, pos.y + 0.16, pos.z);
        fill.setPos(pos.x, pos.y + 0.18, pos.z);
    }

    public void followEntity(LivingEntity entity) {
        preview.followEntity(entity);
        fill.followEntity(entity);
    }

    public void setAlwaysRender(boolean alwaysRender) {
        preview.setAlwaysRender(alwaysRender);
        fill.setAlwaysRender(alwaysRender);
    }

    public void remove() {
        preview.discard();
        fill.discard();
    }
}
