package net.ledok.mobs_ld.mixin.warden;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.ledok.mobs_ld.mob.host.warden.WardenAdapter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.warden.Digging;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The hard dig lock: a hosted Warden, or any Warden of an Arenas_LD run, never digs away (vanilla discards a Warden
 * that dug in, which loses a raid). The dig cooldown the adapter keeps set already stops the DIG activity; this is the
 * guard behind it. Both hooks only run when vanilla is about to dig, so they cost nothing per tick.
 */
@Mixin(Digging.class)
public class DiggingMixin {
    /**
     * Never starts: this also blocks the "in water or lava: discard at once" branch of {@code start}. Being asked at
     * all means the DIG activity is selected (its dig cooldown is missing), so the cooldown is set too: otherwise it
     * would stand frozen in DIG, which also drops its target.
     */
    @ModifyReturnValue(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/warden/Warden;)Z",
            at = @At("RETURN"))
    private boolean mobs_ld$neverDig(boolean start, ServerLevel level, Warden warden) {
        return !WardenAdapter.refuseDig(warden) && start;
    }

    /** If a dig ever ran anyway, its end does not discard the Warden: it stands back up, with a warning in the log. */
    @WrapWithCondition(method = "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/warden/Warden;J)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/warden/Warden;remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"))
    private boolean mobs_ld$neverDiscard(Warden warden, Entity.RemovalReason reason) {
        return !WardenAdapter.blockDigDiscard(warden);
    }
}
