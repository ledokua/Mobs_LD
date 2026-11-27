package net.ledok.mobs_ld.mob;

import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

public final class DungeonZombie extends Zombie {
    public DungeonZombie(EntityType<? extends DungeonZombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        DungeonMobGoals.register(this, goalSelector, targetSelector);
    }

    @Override
    protected boolean supportsBreakDoorGoal() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected void handleAttributes(float difficulty) {
        // Keep the configured stats when spawned, including zero chance of vanilla reinforcements.
    }

    /**
     * Spawns as an adult with no jockey (fixed group data), no random weapon (no default equipment) and nothing picked
     * up later; a Halloween pumpkin is taken off again. Mob-level spawn hooks of other mods still run.
     */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                   MobSpawnType reason, @Nullable SpawnGroupData group) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, new ZombieGroupData(false, false));
        setCanPickUpLoot(false);
        if (getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN) || getItemBySlot(EquipmentSlot.HEAD).is(Items.JACK_O_LANTERN)) {
            setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        return result;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // No random sword or shovel: the dungeon mob fights with its abilities.
    }
}
