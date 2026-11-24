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
 * Nameless King's Storm Static debuff: crackling sunlight-lightning static clings to the target, disrupting weapon
 * swing cadence ({@code attack_speed}) and discharging periodic non-lethal static shocks with crisp electric sparks.
 * Displays a custom 18x18 storm-lightning HUD icon.
 */
public class StormStaticEffect extends BossStatusEffect {
    private static final int TICK_INTERVAL = 35;

    public StormStaticEffect() {
        super(MobEffectCategory.HARMFUL, 0x7CC8FF, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.ELECTRIC_SPARK);
        addAttributeModifier(
                Attributes.ATTACK_SPEED,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "effect.storm_static.attack_speed"),
                -0.14,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "effect.storm_static.movement_speed"),
                -0.05,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = Math.max(20, TICK_INTERVAL - Math.min(amplifier, 2) * 5);
        return duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() && entity.getHealth() > 1.0F) {
            float damage = Math.min(1.0F + 0.5F * amplifier, entity.getHealth() - 1.0F);
            if (damage > 0.0F) {
                DamageSource source = entity.level().registryAccess()
                        .registry(Registries.DAMAGE_TYPE)
                        .flatMap(reg -> reg.getHolder(ModDamageTypes.STORM_LIGHTNING))
                        .map(DamageSource::new)
                        .orElseGet(() -> entity.damageSources().lightningBolt());
                entity.hurt(source, damage);
            }
        }
        return true;
    }
}
