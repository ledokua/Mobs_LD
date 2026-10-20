package net.ledok.mobs_ld.registry;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.effect.ProvokeEffect;
import net.ledok.mobs_ld.effect.TauntEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class ModEffects {
    /** Instant provoke: snap to the top of nearby threat tables with a short override window. */
    public static final Holder<MobEffect> PROVOKE = register(
            "provoke",
            new ProvokeEffect(MobEffectCategory.BENEFICIAL, 0xC81E1E)
    );

    /** Duration taunt: hard-lock nearby mobs onto the caster for the effect's duration. */
    public static final Holder<MobEffect> TAUNT = register(
            "taunt",
            new TauntEffect(MobEffectCategory.BENEFICIAL, 0xE0641E)
    );

    private static Holder<MobEffect> register(String name, MobEffect effect) {
        return Registry.registerForHolder(
                BuiltInRegistries.MOB_EFFECT,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, name),
                effect
        );
    }

    private ModEffects() {
    }

    /** Forces class-load so the static fields register. */
    public static void register() {
    }
}
