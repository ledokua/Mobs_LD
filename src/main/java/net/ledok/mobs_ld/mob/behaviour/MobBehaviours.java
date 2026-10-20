package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.mob.MobDeclarations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@code data/<namespace>/mob_behaviour/<path>.json} on every datapack (re)load; the file id is the entity
 * type it drives. A broken file is reported and skipped (that mob keeps no behaviour) instead of failing the reload.
 */
public final class MobBehaviours extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "mob_behaviour");

    private static volatile Map<ResourceLocation, MobBehaviour> loaded = Map.of();

    private MobBehaviours() {
        super(new Gson(), "mob_behaviour");
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new MobBehaviours());
    }

    public static MobBehaviour get(EntityType<?> type) {
        return loaded.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, MobBehaviour> result = new HashMap<>();
        files.forEach((id, json) -> MobBehaviour.CODEC.parse(JsonOps.INSTANCE, json)
                .ifSuccess(behaviour -> result.put(id, behaviour))
                .ifError(error -> LOGGER.error("Invalid mob behaviour {}: {}", id, error.message())));
        for (ResourceLocation id : result.keySet()) {
            if (!MobDeclarations.types().containsKey(id)) {
                LOGGER.warn("Mob behaviour {} has no matching data-driven entity type; it will never be used", id);
            }
        }
        loaded = Map.copyOf(result);
        LOGGER.info("Loaded {} mob behaviour(s): {}", result.size(), result.keySet());
    }
}
