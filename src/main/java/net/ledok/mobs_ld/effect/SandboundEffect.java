package net.ledok.mobs_ld.effect;

import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Briefly slows a player caught by the Colossus's ground slam. */
public final class SandboundEffect extends BossStatusEffect {
    public SandboundEffect() {
        super(MobEffectCategory.HARMFUL, 0xC49B64, ParticleTypes.DUST_PLUME, ParticleTypes.DUST_PLUME);
        addAttributeModifier(Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "effect.sandbound.movement_speed"),
                -0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
