package net.ledok.mobs_ld.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Rebuilds a mob's navigation: a PathNavigation sizes its path search (follow range x 16 visited nodes) once, when it
 * is built in the mob's constructor, so a host that raises the follow range afterwards (the Warden) needs a new one.
 * {@code createNavigation} is the mob's own (the Warden's door-passing, floating ground navigation).
 */
@Mixin(Mob.class)
public interface MobNavigationAccessor {
    @Accessor("navigation")
    void mobs_ld$setNavigation(PathNavigation navigation);

    @Invoker("createNavigation")
    PathNavigation mobs_ld$createNavigation(Level level);
}
