package net.ledok.mobs_ld.registry;

import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

/**
 * Test potions for the threat system. Obtain via give commands (see the feature notes); these exist mainly so
 * the effects can be triggered in-game without the spell engine.
 *   Drinkable Taunt:   /give @p minecraft:potion[potion_contents={potion:"mobs_ld:taunt"}]
 *   Throwable Provoke: /give @p minecraft:splash_potion[potion_contents={potion:"mobs_ld:provoke"}]
 */
public final class ModPotions {
    /** Drinkable: applies the duration Taunt (10s) to the drinker — a self-cast hard taunt. */
    public static final Holder<Potion> TAUNT = register(
            "taunt",
            new Potion("taunt", new MobEffectInstance(ModEffects.TAUNT, 200, 0))
    );

    /** Use as a splash potion: instant Provoke on whoever it splashes (throw it at your own feet). */
    public static final Holder<Potion> PROVOKE = register(
            "provoke",
            new Potion("provoke", new MobEffectInstance(ModEffects.PROVOKE, 1, 0))
    );

    private static Holder<Potion> register(String name, Potion potion) {
        return Registry.registerForHolder(
                BuiltInRegistries.POTION,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, name),
                potion
        );
    }

    private ModPotions() {
    }

    /** Forces class-load so the static fields register. */
    public static void register() {
    }
}
