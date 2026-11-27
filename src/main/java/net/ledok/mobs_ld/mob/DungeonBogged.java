package net.ledok.mobs_ld.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Bogged;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** A Bogged with the arena archer's combat-only goals and the vanilla Bogged's visuals and sounds. */
public final class DungeonBogged extends Bogged {
    public DungeonBogged(EntityType<? extends DungeonBogged> type, Level level) {
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
        // Equipment changes never add native attacks.
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }
}
