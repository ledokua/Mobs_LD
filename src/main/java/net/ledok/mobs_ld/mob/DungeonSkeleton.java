package net.ledok.mobs_ld.mob;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** A dungeon archer with vanilla skeleton visuals and the Mobs_LD ability engine as its only attack AI. */
public final class DungeonSkeleton extends AbstractSkeleton {
    public DungeonSkeleton(EntityType<? extends DungeonSkeleton> type, Level level) {
        super(type, level);
        if (!level.isClientSide()) {
            setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
        }
        setDropChance(EquipmentSlot.MAINHAND, 0.0F); // its loot table is what it drops
    }

    @Override
    protected void registerGoals() {
        DungeonMobGoals.register(this, goalSelector, targetSelector);
    }

    @Override
    public void reassessWeaponGoal() {
        // Equipment changes never add the vanilla bow or melee goal to this archer.
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.SKELETON_STEP;
    }
}
