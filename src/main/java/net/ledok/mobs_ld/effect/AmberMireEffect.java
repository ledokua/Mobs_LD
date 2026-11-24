package net.ledok.mobs_ld.effect;

import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Amber Brute's sticky tree-resin debuff: thick golden amber coats boots and weapons, dragging down both footwork
 * and swing cadence while dripping amber droplets. Replaces generic vanilla Slowness with a custom faceted amber
 * crystal HUD icon and honey-resin particles.
 */
public class AmberMireEffect extends BossStatusEffect {
    public AmberMireEffect() {
        super(MobEffectCategory.HARMFUL, 0xD4921A, ParticleTypes.FALLING_HONEY, ParticleTypes.LANDING_HONEY);
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "effect.amber_mire.movement_speed"),
                -0.16,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        addAttributeModifier(
                Attributes.ATTACK_SPEED,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "effect.amber_mire.attack_speed"),
                -0.08,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }
}
