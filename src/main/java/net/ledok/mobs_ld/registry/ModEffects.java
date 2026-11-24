package net.ledok.mobs_ld.registry;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.effect.AmberMireEffect;
import net.ledok.mobs_ld.effect.CharredRootsEffect;
import net.ledok.mobs_ld.effect.FrenziedBurnEffect;
import net.ledok.mobs_ld.effect.ProvokeEffect;
import net.ledok.mobs_ld.effect.StormStaticEffect;
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

    /** Midra's custom boss debuff: golden Frenzied Flame scorch with custom burning-eye HUD icon. */
    public static final Holder<MobEffect> FRENZIED_BURN = register(
            "frenzied_burn",
            new FrenziedBurnEffect()
    );

    /** Amber Brute's custom boss debuff: sticky golden tree resin with custom amber-crystal HUD icon. */
    public static final Holder<MobEffect> AMBER_MIRE = register(
            "amber_mire",
            new AmberMireEffect()
    );

    /** Emberroot's custom boss debuff: smoldering tangled roots with custom charred-root HUD icon. */
    public static final Holder<MobEffect> CHARRED_ROOTS = register(
            "charred_roots",
            new CharredRootsEffect()
    );

    /** Nameless King's custom boss debuff: crackling storm static with custom lightning HUD icon. */
    public static final Holder<MobEffect> STORM_STATIC = register(
            "storm_static",
            new StormStaticEffect()
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
