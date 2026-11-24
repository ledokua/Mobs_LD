package net.ledok.mobs_ld.entity.attack;

import net.ledok.mobs_ld.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class AttackZoneDisplay {
    /** How far above the zone's floor the preview and the fill entities sit (the fill on top, so they never z-fight). */
    public static final double PREVIEW_LIFT = 0.16;
    public static final double FILL_LIFT = 0.18;

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
            int windupTicks,
            float bandUp,
            float bandDown
    ) {
        AttackZoneVisualEntity previewEntity = ModEntities.ATTACK_ZONE_VISUAL.create(world);
        AttackZoneVisualEntity fillEntity = ModEntities.ATTACK_ZONE_VISUAL.create(world);
        if (previewEntity == null || fillEntity == null) {
            throw new IllegalStateException("Failed to create attack zone visual entity");
        }
        previewEntity.setPos(origin.x, origin.y + PREVIEW_LIFT, origin.z);
        previewEntity.configure(zone, yawDegrees, config, windupTicks, true);
        previewEntity.setBand(bandUp, bandDown);
        world.addFreshEntity(previewEntity);

        fillEntity.setPos(origin.x, origin.y + FILL_LIFT, origin.z);
        fillEntity.configure(zone, yawDegrees, config, windupTicks, false);
        fillEntity.setBand(bandUp, bandDown);
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
        preview.setPos(pos.x, pos.y + PREVIEW_LIFT, pos.z);
        fill.setPos(pos.x, pos.y + FILL_LIFT, pos.z);
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
