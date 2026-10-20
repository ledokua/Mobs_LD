package net.ledok.mobs_ld.registry;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.entity.attack.AttackZoneVisualEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<AttackZoneVisualEntity> ATTACK_ZONE_VISUAL = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "attack_zone_visual"),
            EntityType.Builder.of(AttackZoneVisualEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(64)
                    .build()
    );

    private ModEntities() {
    }

    /** Forces class-load so the static fields register. */
    public static void register() {
    }
}
