package net.ledok.mobs_ld.mob.damage;

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
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads attack profiles from {@code data/<ns>/mobs_ld/attack_profiles/*.json} (any file name; the {@code entity} field
 * names the mob type). With no files nothing changes. A later file for the same type replaces an earlier one, with a
 * warning.
 */
public final class AttackProfiles extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "attack_profiles");

    private static volatile Map<EntityType<?>, AttackProfile> byType = Map.of();
    /** Profiles with a {@code no_attacker} attack, the only ones a hit without a dealer needs to look at. */
    private static volatile List<Map.Entry<EntityType<?>, AttackProfile>> withNoAttacker = List.of();

    private AttackProfiles() {
        super(new Gson(), "mobs_ld/attack_profiles");
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new AttackProfiles());
    }

    public static AttackProfile get(EntityType<?> type) {
        return byType.isEmpty() ? null : byType.get(type);
    }

    public static List<Map.Entry<EntityType<?>, AttackProfile>> withNoAttacker() {
        return withNoAttacker;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<EntityType<?>, AttackProfile> result = new IdentityHashMap<>();
        files.forEach((file, json) -> {
            try {
                AttackProfile.CODEC.parse(JsonOps.INSTANCE, json)
                        .ifError(error -> LOGGER.error("Invalid attack profile {}: {}", file, error.message()))
                        .ifSuccess(profile -> {
                            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(profile.entity()).orElse(null);
                            if (type == null) {
                                LOGGER.warn("Attack profile {} names no registered entity type {}; skipped", file,
                                        profile.entity());
                            } else if (result.put(type, profile) != null) {
                                LOGGER.warn("Attack profile {} replaces an earlier profile for {}", file, profile.entity());
                            }
                        });
            } catch (RuntimeException e) {
                // One file must never fail the whole datapack reload.
                LOGGER.error("Invalid attack profile {}: {}", file, e.toString());
            }
        });
        List<Map.Entry<EntityType<?>, AttackProfile>> noAttacker = new ArrayList<>();
        result.forEach((type, profile) -> {
            if (profile.attacks().stream().anyMatch(attack -> attack.match().noAttacker())) {
                noAttacker.add(Map.entry(type, profile));
            }
        });
        byType = result;
        withNoAttacker = List.copyOf(noAttacker);
        if (!result.isEmpty()) {
            LOGGER.info("Loaded {} attack profile(s)", result.size());
        }
    }
}
