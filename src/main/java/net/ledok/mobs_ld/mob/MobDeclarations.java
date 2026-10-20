package net.ledok.mobs_ld.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads every {@code mobs_ld/entities/*.json} in the mod jar at startup and registers one {@link LdMob} entity type
 * per file, with its default attributes and optional spawn egg. All data-driven mobs share the {@link LdMob} class;
 * this registry maps each type back to its declaration.
 */
public final class MobDeclarations {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIRECTORY = MobsLdMod.MOD_ID + "/entities";

    private static final Map<ResourceLocation, MobDeclaration> BY_ID = new LinkedHashMap<>();
    private static final Map<EntityType<?>, MobDeclaration> BY_TYPE = new IdentityHashMap<>();
    private static final Map<ResourceLocation, EntityType<LdMob>> TYPES = new LinkedHashMap<>();

    private MobDeclarations() {
    }

    public static void register() {
        for (MobDeclaration declaration : load()) {
            EntityType<LdMob> type = registerType(declaration);
            BY_ID.put(declaration.id(), declaration);
            BY_TYPE.put(type, declaration);
            TYPES.put(declaration.id(), type);
            FabricDefaultAttributeRegistry.register(type, attributes(declaration));
            declaration.spawnEgg().ifPresent(egg -> registerSpawnEgg(declaration.id(), type, egg));
        }
        LOGGER.info("Registered {} data-driven mob type(s): {}", TYPES.size(), TYPES.keySet());
    }

    /** Declaration behind an {@link LdMob} entity type. Safe during the entity constructor. */
    public static MobDeclaration of(EntityType<?> type) {
        MobDeclaration declaration = BY_TYPE.get(type);
        if (declaration == null) {
            throw new IllegalStateException("No mob declaration for entity type " + BuiltInRegistries.ENTITY_TYPE.getKey(type));
        }
        return declaration;
    }

    public static Collection<MobDeclaration> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static Map<ResourceLocation, EntityType<LdMob>> types() {
        return Collections.unmodifiableMap(TYPES);
    }

    private static List<MobDeclaration> load() {
        Path dir = FabricLoader.getInstance().getModContainer(MobsLdMod.MOD_ID)
                .flatMap(container -> container.findPath(DIRECTORY))
                .orElse(null);
        List<MobDeclaration> result = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return result;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString();
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID,
                        name.substring(0, name.length() - ".json".length()));
                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    // A broken declaration must fail loudly: silently skipping it would desync the entity registry
                    // between a server and clients running a different build.
                    result.add(MobDeclaration.codec(id).parse(JsonOps.INSTANCE, json).getOrThrow(
                            error -> new IllegalStateException("Invalid mob declaration " + id + ": " + error)));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read mob declarations from " + dir, e);
        }
        return result;
    }

    private static EntityType<LdMob> registerType(MobDeclaration declaration) {
        EntityType.Builder<LdMob> builder = EntityType.Builder.of(LdMob::new, declaration.category())
                .sized(declaration.width(), declaration.height())
                .clientTrackingRange(declaration.trackingRange());
        declaration.eyeHeight().ifPresent(builder::eyeHeight);
        if (declaration.fireImmune()) {
            builder.fireImmune();
        }
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, declaration.id(), builder.build());
    }

    private static AttributeSupplier.Builder attributes(MobDeclaration declaration) {
        AttributeSupplier.Builder builder = Monster.createMonsterAttributes()
                .add(Attributes.FOLLOW_RANGE, 32.0);
        declaration.attributes().forEach((id, value) -> {
            Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(id).orElseThrow(
                    () -> new IllegalStateException("Mob " + declaration.id() + " sets unknown attribute " + id));
            builder.add(attribute, value);
        });
        return builder;
    }

    private static void registerSpawnEgg(ResourceLocation id, EntityType<LdMob> type, MobDeclaration.SpawnEgg egg) {
        Item item = Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg"),
                new SpawnEggItem(type, egg.primaryColor(), egg.secondaryColor(), new Item.Properties()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(item));
    }
}
