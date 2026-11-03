package net.ledok.mobs_ld.mixin.warden;

import net.minecraft.world.entity.monster.warden.AngerManagement;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The Warden's anger table (package-private, not final), replaced by an empty one on a reset: AngerManagement has no
 * clear-all, and leftover anger would make it roar straight back at the players.
 */
@Mixin(Warden.class)
public interface WardenAccessor {
    @Accessor("angerManagement")
    void mobs_ld$setAngerManagement(AngerManagement angerManagement);
}
