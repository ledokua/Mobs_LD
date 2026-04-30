package net.ledok.mobs_ld.entity.boss;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Map;

public record DamageProfile(Map<DamageCategory, Float> resistances) {
    public static final DamageProfile NONE = new DamageProfile(Map.of());

    public enum DamageCategory {
        FIRE, PROJECTILE, MAGIC, EXPLOSION, MELEE, FALL, ALL
    }

    public float getMultiplierFor(DamageSource source) {
        float all = resistances.getOrDefault(DamageCategory.ALL, 1.0F);
        DamageCategory category = resolveCategory(source);
        return resistances.getOrDefault(category, all);
    }

    private static DamageCategory resolveCategory(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return DamageCategory.FIRE;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return DamageCategory.PROJECTILE;
        }
        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            return DamageCategory.MAGIC;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return DamageCategory.EXPLOSION;
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return DamageCategory.FALL;
        }
        return DamageCategory.MELEE;
    }
}
