package net.ledok.mobs_ld.registry;

import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/** Damage types defined in {@code data/mobs_ld/damage_type/}. */
public final class ModDamageTypes {
    /**
     * The blast a summoned Wither lets off when its charge ends. An explosion in every vanilla tag, but its own type
     * so it can stay out of per-boss damage multipliers ({@code #loot_stage:unscaled}): it is a one-off, telegraphed
     * set piece, not the boss's damage output.
     */
    public static final ResourceKey<DamageType> WITHER_SPAWN_BLAST = key("wither_spawn_blast");
    /**
     * The hosted Warden's sonic attacks (Circle, Lance, Pulse, ...; its physical swings stay
     * {@code minecraft:mob_attack}). Unlike vanilla's {@code minecraft:sonic_boom}, armour and Protection reduce it;
     * shields do not block it ({@code #minecraft:bypasses_shield}); Spell Engine evasion can dodge it
     * ({@code #spell_engine:evadable}, a tag file that is harmless without Spell Engine); it is not
     * {@code #loot_stage:unscaled}, so the Warden's {@code damage_dealt} scales it. Difficulty-scaled only when a
     * living non-player deals it, and every engine hit names the Warden as the attacker.
     */
    public static final ResourceKey<DamageType> WARDEN_SONIC = key("warden_sonic");
    /**
     * A summoner's share of a killed {@code minion_link} add, credited to whoever killed the add. A share of health,
     * so armour, Protection, Resistance, shields and the hurt cooldown leave it whole; no knockback; outside per-mob
     * damage multipliers ({@code #loot_stage:unscaled}). The summoner's own {@code damage_taken} rules still apply.
     */
    public static final ResourceKey<DamageType> MINION_LINK = key("minion_link");
    /**
     * The ticks of Amethyst Splinters (Gorefist's cleaver wound), caused by the mob that applied it: armour reduces
     * it (it is in no bypass tag), and the mob's {@code loot_stage:damage_dealt} scales it like its hits.
     */
    public static final ResourceKey<DamageType> AMETHYST_SPLINTERS = key("amethyst_splinters");
    /**
     * Midra's Frenzied Flame sorcery and magma eruptions (beam, burst, flame rain, ground wave, plunge fissures; his
     * physical greatsword strikes stay {@code minecraft:mob_attack}). Armour and Protection reduce it; shields do not
     * block it ({@code #minecraft:bypasses_shield}); Spell Engine evasion can dodge it
     * ({@code #spell_engine:evadable}); deliberately outside {@code #minecraft:is_fire} so Fire Resistance potions
     * do not trivialize the boss, while {@code "effects": "burning"} keeps fiery hurt feedback.
     */
    public static final ResourceKey<DamageType> FRENZIED_FLAME = key("frenzied_flame");
    /**
     * Amber Brute's crystalline earth eruptions and shattering amber shards (spires, geode traps, shard sprays,
     * resonance rings; his physical claw swipes and heavy fist slams stay {@code minecraft:mob_attack}). Armour and
     * Protection reduce it; ground eruptions bypass shields ({@code #minecraft:bypasses_shield}); Spell Engine evasion
     * can dodge it ({@code #spell_engine:evadable}); scales normally with {@code damage_dealt}.
     */
    public static final ResourceKey<DamageType> AMBER_SHATTER = key("amber_shatter");
    /**
     * Emberroot's molten furnace blasts, erupting charred roots, and split-ribcage cinder waves (his physical claw
     * swipes, shoulder barges, hammer slams, and boulder rolls stay {@code minecraft:mob_attack}). Armour and
     * Protection reduce it; shields do not block it ({@code #minecraft:bypasses_shield}); Spell Engine evasion can
     * dodge it ({@code #spell_engine:evadable}); deliberately outside {@code #minecraft:is_fire} so Fire Resistance
     * potions do not trivialize the boss, while {@code "effects": "burning"} keeps fiery hurt feedback.
     */
    public static final ResourceKey<DamageType> EMBER_BLIGHT = key("ember_blight");
    /**
     * Nameless King's storm gales, sky-called golden lightning bolts, and tempest shockwaves (his physical
     * Dragonslayer Swordspear swings stay {@code minecraft:mob_attack}). Armour and Protection reduce it; shields do
     * not block it ({@code #minecraft:bypasses_shield}); Spell Engine evasion can dodge it
     * ({@code #spell_engine:evadable}); scales normally with {@code damage_dealt}.
     */
    public static final ResourceKey<DamageType> STORM_LIGHTNING = key("storm_lightning");

    private ModDamageTypes() {
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, path));
    }
}
