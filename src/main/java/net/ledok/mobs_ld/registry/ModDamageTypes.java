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

    private ModDamageTypes() {
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, path));
    }
}
