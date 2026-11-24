package net.ledok.mobs_ld.entity.attack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class AttackZoneVisualEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_ZONE_KIND =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_PARAM_A =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PARAM_B =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PARAM_C =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.FLOAT);
    /** The part of the wind-up a SWEEP fill runs in (fractions; 0..1 = all of it), for strips drawn in segments. */
    private static final EntityDataAccessor<Float> DATA_SWEEP_FROM =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SWEEP_TO =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_YAW_DEGREES =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_STYLE =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PREPARE_COLOR =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_INVOKE_COLOR =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PREVIEW_COLOR =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WINDUP_TIMER =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TOTAL_WINDUP =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FORCE_INVOKE =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_PREVIEW =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ALWAYS_RENDER =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * A fourth shape parameter: the widening of {@code rays} lanes (blocks per block); 0 for every other shape.
     * Defined last, so the synced ids of every earlier field stay what they were.
     */
    private static final EntityDataAccessor<Float> DATA_PARAM_D =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.FLOAT);
    /**
     * How far above and below the zone's origin (its floor) a victim's feet may be and still be hit, so the client
     * lays the telegraph only on floors inside that band (0 = unknown: a default band). Defined after PARAM_D.
     */
    private static final EntityDataAccessor<Float> DATA_BAND_UP =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BAND_DOWN =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.FLOAT);
    private UUID trackedEntityId;
    /** Client only: the floor heights the renderer sampled under this zone (see the client's TerrainDrape). */
    public transient Object clientDrape;

    public AttackZoneVisualEntity(EntityType<? extends AttackZoneVisualEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ZONE_KIND, 0);
        builder.define(DATA_PARAM_A, 0.0F);
        builder.define(DATA_PARAM_B, 0.0F);
        builder.define(DATA_PARAM_C, 0.0F);
        builder.define(DATA_SWEEP_FROM, 0.0F);
        builder.define(DATA_SWEEP_TO, 1.0F);
        builder.define(DATA_YAW_DEGREES, 0.0F);
        builder.define(DATA_STYLE, AttackDisplayConfig.AnimationStyle.DEFAULT.ordinal());
        builder.define(DATA_PREPARE_COLOR, 0xFF8B0000);
        builder.define(DATA_INVOKE_COLOR, 0xFFFF0000);
        builder.define(DATA_PREVIEW_COLOR, 0x408B0000);
        builder.define(DATA_WINDUP_TIMER, 0);
        builder.define(DATA_TOTAL_WINDUP, 1);
        builder.define(DATA_FORCE_INVOKE, false);
        builder.define(DATA_IS_PREVIEW, false);
        builder.define(DATA_ALWAYS_RENDER, false);
        builder.define(DATA_PARAM_D, 0.0F);
        builder.define(DATA_BAND_UP, 0.0F);
        builder.define(DATA_BAND_DOWN, 0.0F);
    }

    /** The hit's height band around the origin's floor (blocks above / below), for laying the telegraph on terrain. */
    public void setBand(float up, float down) {
        entityData.set(DATA_BAND_UP, up);
        entityData.set(DATA_BAND_DOWN, down);
    }

    public float getBandUp() {
        return entityData.get(DATA_BAND_UP);
    }

    public float getBandDown() {
        return entityData.get(DATA_BAND_DOWN);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("tracked_entity")) {
            trackedEntityId = tag.getUUID("tracked_entity");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (trackedEntityId != null) {
            tag.putUUID("tracked_entity", trackedEntityId);
        }
    }

    /** Telegraphs only live for an ability's wind-up; never write one to disk where it would outlive its owner. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    public void configure(AttackZone zone, float yawDegrees, AttackDisplayConfig config, int totalWindupTicks, boolean preview) {
        entityData.set(DATA_SWEEP_FROM, 0.0F);
        entityData.set(DATA_SWEEP_TO, 1.0F);
        entityData.set(DATA_PARAM_D, 0.0F);
        switch (zone) {
            case AttackZone.Rectangle r -> {
                entityData.set(DATA_ZONE_KIND, 0);
                entityData.set(DATA_PARAM_A, r.width());
                entityData.set(DATA_PARAM_B, r.length());
                entityData.set(DATA_PARAM_C, r.offsetForward());
            }
            case AttackZone.Cone c -> {
                entityData.set(DATA_ZONE_KIND, 1);
                entityData.set(DATA_PARAM_A, c.maxDistance());
                entityData.set(DATA_PARAM_B, c.angleDegrees());
                entityData.set(DATA_PARAM_C, 0.0F);
            }
            case AttackZone.Circle c -> {
                entityData.set(DATA_ZONE_KIND, 2);
                entityData.set(DATA_PARAM_A, c.radius());
                entityData.set(DATA_PARAM_B, 0.0F);
                entityData.set(DATA_PARAM_C, 0.0F);
            }
            case AttackZone.CircleTarget c -> {
                entityData.set(DATA_ZONE_KIND, 3);
                entityData.set(DATA_PARAM_A, c.radius());
                entityData.set(DATA_PARAM_B, 0.0F);
                entityData.set(DATA_PARAM_C, 0.0F);
            }
            case AttackZone.CircleRays r -> {
                entityData.set(DATA_ZONE_KIND, 4);
                entityData.set(DATA_PARAM_A, (float) r.rayCount());
                entityData.set(DATA_PARAM_B, r.length());
                entityData.set(DATA_PARAM_C, r.width());
                entityData.set(DATA_PARAM_D, r.widen());
            }
            case AttackZone.Ring ring -> {
                // Kind 6: a client from before rings draws nothing for it (its renderer ignores unknown kinds).
                entityData.set(DATA_ZONE_KIND, 6);
                entityData.set(DATA_PARAM_A, ring.inner());
                entityData.set(DATA_PARAM_B, ring.outer());
                entityData.set(DATA_PARAM_C, 0.0F);
            }
            case AttackZone.Strip strip -> {
                entityData.set(DATA_ZONE_KIND, 5);
                entityData.set(DATA_PARAM_A, strip.width());
                entityData.set(DATA_PARAM_B, strip.length());
                entityData.set(DATA_PARAM_C, strip.headLength());
                entityData.set(DATA_SWEEP_FROM, strip.sweepFrom());
                entityData.set(DATA_SWEEP_TO, strip.sweepTo());
            }
        }
        entityData.set(DATA_YAW_DEGREES, yawDegrees);
        entityData.set(DATA_STYLE, config.animationStyle().ordinal());
        entityData.set(DATA_PREPARE_COLOR, config.prepareColor());
        entityData.set(DATA_INVOKE_COLOR, config.invokeColor());
        entityData.set(DATA_PREVIEW_COLOR, config.resolvedPreviewColor());
        entityData.set(DATA_TOTAL_WINDUP, Math.max(1, totalWindupTicks));
        entityData.set(DATA_WINDUP_TIMER, totalWindupTicks);
        entityData.set(DATA_FORCE_INVOKE, false);
        entityData.set(DATA_IS_PREVIEW, preview);
    }

    public void setWindupTimer(int windupTimer) {
        entityData.set(DATA_WINDUP_TIMER, windupTimer);
    }

    public void setForceInvoke(boolean forceInvoke) {
        entityData.set(DATA_FORCE_INVOKE, forceInvoke);
    }

    public int getZoneKind() {
        return entityData.get(DATA_ZONE_KIND);
    }

    public float getParamA() {
        return entityData.get(DATA_PARAM_A);
    }

    public float getParamB() {
        return entityData.get(DATA_PARAM_B);
    }

    public float getParamC() {
        return entityData.get(DATA_PARAM_C);
    }

    public float getParamD() {
        return entityData.get(DATA_PARAM_D);
    }

    public float getSweepFrom() {
        return entityData.get(DATA_SWEEP_FROM);
    }

    public float getSweepTo() {
        return entityData.get(DATA_SWEEP_TO);
    }

    public float getYawDegrees() {
        return entityData.get(DATA_YAW_DEGREES);
    }

    public int getStyle() {
        return entityData.get(DATA_STYLE);
    }

    public int getPrepareColor() {
        return entityData.get(DATA_PREPARE_COLOR);
    }

    public int getInvokeColor() {
        return entityData.get(DATA_INVOKE_COLOR);
    }

    public int getWindupTimer() {
        return entityData.get(DATA_WINDUP_TIMER);
    }

    public int getTotalWindup() {
        return entityData.get(DATA_TOTAL_WINDUP);
    }

    public boolean isForceInvoke() {
        return entityData.get(DATA_FORCE_INVOKE);
    }

    public int getPreviewColor() {
        return entityData.get(DATA_PREVIEW_COLOR);
    }

    public boolean isPreview() {
        return entityData.get(DATA_IS_PREVIEW);
    }

    public void setAlwaysRender(boolean alwaysRender) {
        entityData.set(DATA_ALWAYS_RENDER, alwaysRender);
    }

    public boolean isAlwaysRender() {
        return entityData.get(DATA_ALWAYS_RENDER);
    }

    public void followEntity(LivingEntity entity) {
        trackedEntityId = entity.getUUID();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world) || trackedEntityId == null) {
            return;
        }
        Entity tracked = world.getEntity(trackedEntityId);
        if (tracked != null) {
            setPos(tracked.getX(), tracked.getY() + 0.16, tracked.getZ());
        }
    }
}
