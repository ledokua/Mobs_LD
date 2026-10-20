package net.ledok.mobs_ld.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.ledok.mobs_ld.mob.behaviour.event.CloudEvent;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Clouds left by data-driven mobs ({@link CloudEvent}) only affect players and their summons: a vanilla cloud
 * poisons everything inside it, including the boss and its own adds standing in their pool.
 */
@Mixin(AreaEffectCloud.class)
public class AreaEffectCloudMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<LivingEntity> mobs_ld$onlyHostileVictims(List<LivingEntity> victims) {
        AreaEffectCloud self = (AreaEffectCloud) (Object) this;
        if (!self.getTags().contains(CloudEvent.HOSTILE_TAG)) {
            return victims;
        }
        return victims.stream()
                .filter(victim -> victim instanceof Player
                        || (victim instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player))
                .toList();
    }
}
