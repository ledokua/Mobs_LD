package net.ledok.mobs_ld.entity.attack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class AttackZoneVisualEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_ZONE_KIND =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_PARAM_A =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PARAM_B =
            SynchedEntityData.defineId(AttackZoneVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PARAM_C =
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
        builder.define(DATA_YAW_DEGREES, 0.0F);
        builder.define(DATA_STYLE, AttackDisplayConfig.AnimationStyle.DEFAULT.ordinal());
        builder.define(DATA_PREPARE_COLOR, 0xFF8B0000);
        builder.define(DATA_INVOKE_COLOR, 0xFFFF0000);
        builder.define(DATA_PREVIEW_COLOR, 0x408B0000);
        builder.define(DATA_WINDUP_TIMER, 0);
        builder.define(DATA_TOTAL_WINDUP, 1);
        builder.define(DATA_FORCE_INVOKE, false);
        builder.define(DATA_IS_PREVIEW, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
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
            case AttackZone.Ring r -> {
                entityData.set(DATA_ZONE_KIND, 4);
                entityData.set(DATA_PARAM_A, r.radius());
                entityData.set(DATA_PARAM_B, (float) r.rectanglesPerSide());
                entityData.set(DATA_PARAM_C, r.rectangleWidth());
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
}
