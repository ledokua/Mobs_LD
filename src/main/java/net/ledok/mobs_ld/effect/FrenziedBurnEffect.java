package net.ledok.mobs_ld.effect;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.registry.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Midra's Frenzied Burn debuff: golden madness flame scorches the target, dulling weapon strikes and movement while
 * dealing a light, non-lethal periodic scorch pulse. Replaces generic vanilla debuff icons with a custom burning
 * Frenzied Eye HUD icon and small flame particles.
 */
public class FrenziedBurnEffect extends BossStatusEffect {
    private static final int TICK_INTERVAL = 40;

    public FrenziedBurnEffect() {
        super(MobEffectCategory.HARMFUL, 0xF27D16, ParticleTypes.SMALL_FLAME, ParticleTypes.SMALL_FLAME);
        addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "effect.frenzied_burn.attack_damage"),
                -0.10,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "effect.frenzied_burn.movement_speed"),
                -0.06,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = Math.max(20, TICK_INTERVAL >> Math.min(amplifier, 1));
        return duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() && entity.getHealth() > 1.0F) {
            float damage = Math.min(1.0F + 0.5F * amplifier, entity.getHealth() - 1.0F);
            if (damage > 0.0F) {
                DamageSource source = entity.level().registryAccess()
                        .registry(Registries.DAMAGE_TYPE)
                        .flatMap(reg -> reg.getHolder(ModDamageTypes.FRENZIED_FLAME))
                        .map(DamageSource::new)
                        .orElseGet(() -> entity.damageSources().onFire());
                entity.hurt(source, damage);
            }
        }
        return true;
    }
}
