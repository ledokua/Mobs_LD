package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads {@code data/<namespace>/mob_behaviour/<path>.json} on every datapack (re)load; the file id is the entity
 * type it drives: one of this mod's data-driven mobs, or any other mob (e.g. {@code minecraft:zombie}). A broken file
 * is reported and skipped (that mob keeps no behaviour) instead of failing the reload.
 *
 * <p>Mobs pick up a reload through {@link #generation()}: one int compare per tick tells a mob whether to look its
 * behaviour up again, so the many mobs without one pay nothing more.
 */
public final class MobBehaviours extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "mob_behaviour");

    /**
     * Vanilla mobs the engine cannot drive, refused at reload: the dragon's AI never reaches {@code Mob.serverAiStep},
     * and the rest are run by a Brain that ignores goals (it would fight the engine's movement).
     */
    private static final Set<EntityType<?>> REFUSED_TYPES = Set.of(EntityType.ENDER_DRAGON, EntityType.WARDEN,
            EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.BREEZE,
            EntityType.ALLAY, EntityType.AXOLOTL, EntityType.FROG, EntityType.TADPOLE, EntityType.GOAT,
            EntityType.CAMEL, EntityType.SNIFFER, EntityType.ARMADILLO, EntityType.VILLAGER);
    /** The same mobs by class, checked when a mob would attach: also catches other mods' subclasses of them. */
    private static final List<Class<?>> REFUSED_CLASSES = List.of(EnderDragon.class, Warden.class,
            AbstractPiglin.class, Hoglin.class, Zoglin.class, Breeze.class, Allay.class, Axolotl.class, Frog.class,
            Tadpole.class, Goat.class, Camel.class, Sniffer.class, Armadillo.class, Villager.class);
    private static final Set<EntityType<?>> WARNED = ConcurrentHashMap.newKeySet();

    private static volatile Map<EntityType<?>, MobBehaviour> byType = Map.of();
    /** Bumped by every reload; 0 = nothing loaded yet. */
    private static volatile int generation;

    private MobBehaviours() {
        super(new Gson(), "mob_behaviour");
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new MobBehaviours());
    }

    public static MobBehaviour get(EntityType<?> type) {
        return byType.get(type);
    }

    /** Changes whenever the loaded behaviours do. */
    public static int generation() {
        return generation;
    }

    /**
     * Whether the engine may attach to {@code mob}; false (with one warning per entity type) for mob classes whose
     * AI cannot be driven by goals.
     */
    public static boolean canHost(Mob mob) {
        for (Class<?> refused : REFUSED_CLASSES) {
            if (refused.isInstance(mob)) {
                if (WARNED.add(mob.getType())) {
                    LOGGER.warn("Mob behaviour for {} is ignored: {} is not driven by goals, so the ability engine "
                            + "cannot run it", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), refused.getSimpleName());
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, MobBehaviour> parsed = new LinkedHashMap<>();
        files.forEach((id, json) -> MobBehaviour.CODEC.parse(JsonOps.INSTANCE, json)
                .ifSuccess(behaviour -> parsed.put(id, behaviour))
                .ifError(error -> LOGGER.error("Invalid mob behaviour {}: {}", id, error.message())));
        Map<EntityType<?>, MobBehaviour> result = new IdentityHashMap<>();
        List<ResourceLocation> used = new ArrayList<>();
        parsed.forEach((id, behaviour) -> {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null) {
                LOGGER.warn("Mob behaviour {} names no registered entity type; it will never be used", id);
            } else if (REFUSED_TYPES.contains(type)) {
                LOGGER.warn("Mob behaviour {} is ignored: that mob is not driven by goals, so the ability engine "
                        + "cannot run it", id);
            } else if (!DefaultAttributes.hasSupplier(type) || type == EntityType.PLAYER || type == EntityType.ARMOR_STAND) {
                LOGGER.warn("Mob behaviour {} is ignored: that entity is not a living mob", id);
            } else {
                result.put(type, behaviour);
                used.add(id);
            }
        });
        byType = Collections.unmodifiableMap(result);
        generation++;
        LOGGER.info("Loaded {} mob behaviour(s): {}", used.size(), used);
    }
}
