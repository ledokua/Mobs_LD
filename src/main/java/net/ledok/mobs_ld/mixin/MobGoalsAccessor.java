package net.ledok.mobs_ld.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Host adapters swap a vanilla mob's own goals when the engine attaches (see HostAdapter#install). */
@Mixin(Mob.class)
public interface MobGoalsAccessor {
    @Accessor("targetSelector")
    GoalSelector mobs_ld$targetSelector();
}
