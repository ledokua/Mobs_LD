package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Server copy of where a mob's bones are during each animation clip, baked on a client by {@code /mobs_ldc bake}
 * ({@code data/<namespace>/hit_tracks/<mob>.json}). {@code trace} events read it to sweep weapon hitboxes along
 * the actual swing.
 */
public final class HitTracks extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "hit_tracks");

    private static volatile Map<ResourceLocation, Map<String, Clip>> loaded = Map.of();

    private HitTracks() {
        super(new Gson(), "hit_tracks");
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new HitTracks());
    }

    /**
     * Bone pivot at {@code seconds} into {@code clip}, as (forward, up, right) blocks from the mob's feet, linearly
     * interpolated between baked ticks; null if the mob, clip or bone was never baked.
     */
    public static Vec3 at(ResourceLocation mob, String clip, String bone, double seconds) {
        Map<String, Clip> clips = loaded.get(mob);
        Clip baked = clips == null ? null : clips.get(clip);
        float[] track = baked == null ? null : baked.bones().get(bone);
        if (track == null) {
            return null;
        }
        int samples = track.length / 3;
        double tick = Math.max(0.0, Math.min(seconds * 20.0, samples - 1));
        int a = (int) Math.floor(tick);
        int b = Math.min(a + 1, samples - 1);
        double f = tick - a;
        return new Vec3(lerp(track[a * 3], track[b * 3], f), lerp(track[a * 3 + 1], track[b * 3 + 1], f),
                lerp(track[a * 3 + 2], track[b * 3 + 2], f));
    }

    public static boolean has(ResourceLocation mob, String clip) {
        Map<String, Clip> clips = loaded.get(mob);
        return clips != null && clips.containsKey(clip);
    }

    private static double lerp(float from, float to, double f) {
        return from + (to - from) * f;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Map<String, Clip>> result = new HashMap<>();
        files.forEach((id, json) -> {
            try {
                Map<String, Clip> clips = new HashMap<>();
                for (Map.Entry<String, JsonElement> clip : json.getAsJsonObject().getAsJsonObject("clips").entrySet()) {
                    Map<String, float[]> bones = new HashMap<>();
                    JsonObject boneJson = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
                    for (Map.Entry<String, JsonElement> bone : boneJson.entrySet()) {
                        JsonArray samples = bone.getValue().getAsJsonArray();
                        float[] flat = new float[samples.size() * 3];
                        for (int i = 0; i < samples.size(); i++) {
                            JsonArray xyz = samples.get(i).getAsJsonArray();
                            for (int k = 0; k < 3; k++) {
                                flat[i * 3 + k] = xyz.get(k).getAsFloat();
                            }
                        }
                        bones.put(bone.getKey(), flat);
                    }
                    clips.put(clip.getKey(), new Clip(Map.copyOf(bones)));
                }
                result.put(id, Map.copyOf(clips));
            } catch (RuntimeException e) {
                LOGGER.error("Invalid hit track {}: {}", id, e.toString());
            }
        });
        loaded = Map.copyOf(result);
        LOGGER.info("Loaded hit tracks for {}", result.keySet());
    }

    private record Clip(Map<String, float[]> bones) {
    }
}
