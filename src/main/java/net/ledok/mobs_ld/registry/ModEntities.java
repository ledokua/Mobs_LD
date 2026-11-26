package net.ledok.mobs_ld.registry;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.entity.attack.AttackZoneVisualEntity;
import net.ledok.mobs_ld.mob.DungeonSkeleton;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public final class ModEntities {
    public static final EntityType<AttackZoneVisualEntity> ATTACK_ZONE_VISUAL = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "attack_zone_visual"),
            EntityType.Builder.of(AttackZoneVisualEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(64)
                    .build()
    );

    public static final EntityType<DungeonSkeleton> DUNGEON_SKELETON = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "dungeon_skeleton"),
            EntityType.Builder.of(DungeonSkeleton::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(8)
                    .build()
    );

    private ModEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(DUNGEON_SKELETON, DungeonSkeleton.createAttributes());
        Item egg = Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "dungeon_skeleton_spawn_egg"),
                new SpawnEggItem(DUNGEON_SKELETON, 0xBEAF97, 0x55515A, new Item.Properties()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(egg));
    }
}
