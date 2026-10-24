package net.ledok.mobs_ld.mixin;

import net.ledok.mobs_ld.mob.combat.DeathDrops;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The catch-all behind a {@code loot: false} summon's drop rules (LivingEntityCombatMixin): an item the add drops of
 * its own ({@code spawnAtLocation}, which every entity's drops go through) is held back only while the add's own
 * {@code die} runs, i.e. a death drop a subclass override or another mod's death hook adds past the skipped loot step.
 * Other entities' drops, and anything the add drops at another time (a lead coming off later), are never touched.
 */
@Mixin(Entity.class)
public class EntityDropsMixin {
    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void mobs_ld$noDeathDrops(ItemStack stack, float offsetY, CallbackInfoReturnable<ItemEntity> cir) {
        if ((Object) this instanceof DeathDrops dying && dying.mobs_ld$blocksDeathDrops()) {
            cir.setReturnValue(null);
        }
    }
}
