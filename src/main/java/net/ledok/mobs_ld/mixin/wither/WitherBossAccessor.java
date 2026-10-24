package net.ledok.mobs_ld.mixin.wither;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The Wither's private parts the host adapter uses: its own boss bar, the block-breaking countdown armed by a hit,
 * and the head positions skulls fire from (head 0 = middle, 1 and 2 = the side heads).
 */
@Mixin(WitherBoss.class)
public interface WitherBossAccessor {
    @Accessor("bossEvent")
    ServerBossEvent mobs_ld$bossEvent();

    @Accessor("destroyBlocksTick")
    int mobs_ld$destroyBlocksTick();

    @Accessor("destroyBlocksTick")
    void mobs_ld$setDestroyBlocksTick(int ticks);

    @Invoker("getHeadX")
    double mobs_ld$headX(int head);

    @Invoker("getHeadY")
    double mobs_ld$headY(int head);

    @Invoker("getHeadZ")
    double mobs_ld$headZ(int head);
}
