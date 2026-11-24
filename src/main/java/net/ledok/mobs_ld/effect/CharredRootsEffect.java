package net.ledok.mobs_ld.effect;

import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Emberroot's smoldering snare debuff: charred embers and tangled roots drag at the target's stride and crack
 * armour plating while releasing wisps of woodsmoke. Ships with a custom knotted-root HUD icon.
 */
public class CharredRootsEffect extends BossStatusEffect {
    public CharredRootsEffect() {
        super(MobEffectCategory.HARMFUL, 0xB83E18, ParticleTypes.SMOKE, ParticleTypes.SMOKE);
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "effect.charred_roots.movement_speed"),
                -0.20,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        addAttributeModifier(
                Attributes.ARMOR,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "effect.charred_roots.armor"),
                -2.0,
                AttributeModifier.Operation.ADD_VALUE
        );
    }
}
