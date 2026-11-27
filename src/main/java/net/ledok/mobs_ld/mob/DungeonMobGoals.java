package net.ledok.mobs_ld.mob;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

final class DungeonMobGoals {
    private DungeonMobGoals() {
    }

    static void register(PathfinderMob mob, GoalSelector goals, GoalSelector targets) {
        goals.addGoal(0, new FloatGoal(mob));
        targets.addGoal(1, new HurtByTargetGoal(mob));
        targets.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
        // MobCombatHostMixin adds the data-driven combat goal.
    }
}
