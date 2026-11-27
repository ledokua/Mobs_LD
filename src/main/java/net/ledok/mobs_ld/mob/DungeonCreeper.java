package net.ledok.mobs_ld.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

public final class DungeonCreeper extends Creeper {
    public DungeonCreeper(EntityType<? extends DungeonCreeper> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        DungeonMobGoals.register(this, goalSelector, targetSelector);
    }
}
