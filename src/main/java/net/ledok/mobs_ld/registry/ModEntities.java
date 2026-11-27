package net.ledok.mobs_ld.registry;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.entity.attack.AttackZoneVisualEntity;
import net.ledok.mobs_ld.mob.DungeonBogged;
import net.ledok.mobs_ld.mob.DungeonCreeper;
import net.ledok.mobs_ld.mob.DungeonHusk;
import net.ledok.mobs_ld.mob.DungeonSkeleton;
import net.ledok.mobs_ld.mob.DungeonSpider;
import net.ledok.mobs_ld.mob.DungeonZombie;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
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

    public static final EntityType<DungeonSkeleton> DUNGEON_SKELETON =
            registerDungeonMob("dungeon_skeleton", DungeonSkeleton::new, 0.6F, 1.99F, 1.74F);
    public static final EntityType<DungeonBogged> DUNGEON_BOGGED =
            registerDungeonMob("dungeon_bogged", DungeonBogged::new, 0.6F, 1.99F, 1.74F);
    public static final EntityType<DungeonZombie> DUNGEON_ZOMBIE =
            registerDungeonMob("dungeon_zombie", DungeonZombie::new, 0.6F, 1.95F, 1.74F);
    public static final EntityType<DungeonHusk> DUNGEON_HUSK =
            registerDungeonMob("dungeon_husk", DungeonHusk::new, 0.6F, 1.95F, 1.74F);
    public static final EntityType<DungeonCreeper> DUNGEON_CREEPER =
            registerDungeonMob("dungeon_creeper", DungeonCreeper::new, 0.6F, 1.7F, 1.445F);
    public static final EntityType<DungeonSpider> DUNGEON_SPIDER =
            registerDungeonMob("dungeon_spider", DungeonSpider::new, 1.4F, 0.9F, 0.65F);

    private ModEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(DUNGEON_SKELETON, DungeonSkeleton.createAttributes());
        FabricDefaultAttributeRegistry.register(DUNGEON_BOGGED, DungeonBogged.createAttributes());
        FabricDefaultAttributeRegistry.register(DUNGEON_ZOMBIE, DungeonZombie.createAttributes());
        FabricDefaultAttributeRegistry.register(DUNGEON_HUSK, DungeonHusk.createAttributes());
        FabricDefaultAttributeRegistry.register(DUNGEON_CREEPER, DungeonCreeper.createAttributes());
        FabricDefaultAttributeRegistry.register(DUNGEON_SPIDER, DungeonSpider.createAttributes());
        registerSpawnEgg("dungeon_skeleton", DUNGEON_SKELETON, 0xBEAF97, 0x55515A);
        registerSpawnEgg("dungeon_bogged", DUNGEON_BOGGED, 0x768653, 0x3E5146);
        registerSpawnEgg("dungeon_zombie", DUNGEON_ZOMBIE, 0x00AFAF, 0x799C65);
        registerSpawnEgg("dungeon_husk", DUNGEON_HUSK, 0x797061, 0xE6CC94);
        registerSpawnEgg("dungeon_creeper", DUNGEON_CREEPER, 0x0DA70B, 0x000000);
        registerSpawnEgg("dungeon_spider", DUNGEON_SPIDER, 0x342D27, 0xA80E0E);
    }

    private static <T extends Mob> EntityType<T> registerDungeonMob(String name, EntityType.EntityFactory<T> factory,
                                                                  float width, float height, float eyes) {
        return Registry.register(BuiltInRegistries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, name),
                EntityType.Builder.of(factory, MobCategory.MONSTER).sized(width, height).eyeHeight(eyes)
                        .clientTrackingRange(8).build());
    }

    private static void registerSpawnEgg(String name, EntityType<? extends Mob> type, int primary, int secondary) {
        Item egg = Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, name + "_spawn_egg"),
                new SpawnEggItem(type, primary, secondary, new Item.Properties()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(egg));
    }
}
