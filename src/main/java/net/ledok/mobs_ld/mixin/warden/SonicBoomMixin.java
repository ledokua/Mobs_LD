package net.ledok.mobs_ld.mixin.warden;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.ledok.mobs_ld.mob.host.warden.WardenAdapter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A hosted Warden's vanilla sonic boom (untelegraphed, through walls) never starts; its sonic attacks come from the
 * behaviour. Belt and braces with the SONIC_BOOM_COOLDOWN muzzle, which other vanilla code overwrites.
 */
@Mixin(SonicBoom.class)
public class SonicBoomMixin {
    @ModifyReturnValue(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/warden/Warden;)Z",
            at = @At("RETURN"))
    private boolean mobs_ld$noVanillaBoom(boolean start, ServerLevel level, Warden warden) {
        return start && WardenAdapter.hosted(warden) == null;
    }
}
