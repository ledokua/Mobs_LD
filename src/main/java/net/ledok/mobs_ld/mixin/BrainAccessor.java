package net.ledok.mobs_ld.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The two halves of {@code Brain.tick} a held Brain still runs (see WardenHostMixin): memory expiry and the sensors,
 * without starting or ticking any behaviour. An accessor only: no Brain code is changed, so villagers and every other
 * Brain mob pay nothing.
 */
@Mixin(Brain.class)
public interface BrainAccessor {
    @Invoker("forgetOutdatedMemories")
    void mobs_ld$forgetOutdatedMemories();

    @Invoker("tickSensors")
    void mobs_ld$tickSensors(ServerLevel level, LivingEntity entity);
}
