package net.ledok.mobs_ld.mob;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

public final class DungeonSpider extends Spider {
    public DungeonSpider(EntityType<? extends DungeonSpider> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        DungeonMobGoals.register(this, goalSelector, targetSelector);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                   MobSpawnType reason, @Nullable SpawnGroupData group) {
        // Empty spider group data: no permanent potion effect. Spider's 1% skeleton jockey is removed again, so only
        // the Mob-level spawn hooks (other mods' scaling) remain.
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, new SpiderEffectsGroupData());
        for (Entity rider : List.copyOf(getPassengers())) {
            rider.stopRiding();
            rider.discard();
        }
        return result;
    }
}
