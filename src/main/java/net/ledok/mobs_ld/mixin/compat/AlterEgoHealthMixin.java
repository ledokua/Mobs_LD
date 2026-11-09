package net.ledok.mobs_ld.mixin.compat;

import net.ledok.mobs_ld.config.ThreatConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Archers Expansion's Alter Ego copies have their caster's current health ({@link ThreatConfig#decoyCopiesOwnerHealth}):
 * they hold aggro as decoys now, and with Alter Ego's own 20 health a boss swept them away in one hit. Nothing happens
 * without Archers Expansion ({@code @Pseudo}, {@code require = 0}).
 */
@Pseudo
@Mixin(targets = "com.archers_expansion.entity.AlterEgoEntity", remap = false)
public abstract class AlterEgoHealthMixin {

    @Shadow(remap = false)
    public abstract LivingEntity getOwner();

    @Inject(method = "onSpawnedBySpell", at = @At("TAIL"), remap = false, require = 0)
    private void mobs_ld$ownersHealth(CallbackInfo ci) {
        LivingEntity owner = getOwner();
        if (!ThreatConfig.get().decoyCopiesOwnerHealth || owner == null || !((Object) this instanceof LivingEntity decoy)) {
            return;
        }
        float health = Math.max(1.0F, owner.getHealth());
        AttributeInstance maxHealth = decoy.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
        }
        decoy.setHealth(health);
    }
}
