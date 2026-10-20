package net.ledok.mobs_ld.client.mob;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ledok.mobs_ld.mob.LdMob;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.joml.Vector4f;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.util.RenderUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Dev tool behind {@code /mobs_ldc bake <mob>}: plays every clip of a mob through GeckoLib's own animation
 * processor (no rendering) and records where each bone's pivot is, tick by tick, relative to the mob - forward, up,
 * right in blocks. The server needs this for {@code trace} events (weapon hitboxes that follow the swing), because
 * GeckoLib animations only ever run on the client.
 *
 * <p>Output: {@code <game dir>/mobs_ld_bake/<namespace>/<mob>.json}; copy it to
 * {@code data/<namespace>/hit_tracks/<mob>.json}. Re-bake whenever the model or animations change.
 */
public final class HitTrackBaker {
    /** Arbitrary positive start time; GeckoLib only cares about differences. */
    private static final double START_TICK = 1000.0;

    private HitTrackBaker() {
    }

    public static Path bake(Minecraft mc, EntityType<LdMob> type, ResourceLocation id) throws IOException {
        LdMobModel model = new LdMobModel(id); // own processor and clock, so the live renderer is untouched
        BakedGeoModel baked = model.getBakedModel(model.modelResource());
        BakedAnimations animations = GeckoLibCache.getBakedAnimations().get(model.animationResource());
        if (animations == null) {
            throw new IOException("No animation file at " + model.animationResource());
        }
        List<GeoBone> bones = allBones(baked);
        AnimationProcessor<LdMob> processor = model.getAnimationProcessor();

        JsonObject clips = new JsonObject();
        for (Map.Entry<String, Animation> entry : animations.animations().entrySet()) {
            String full = entry.getKey();
            String clip = full.substring(full.lastIndexOf('.') + 1);
            int length = (int) Math.ceil(entry.getValue().length());

            LdMob dummy = type.create(mc.level);
            if (dummy == null) {
                throw new IOException("Could not create a " + id);
            }
            dummy.playAction(clip);
            AnimatableInstanceCache cache = dummy.getAnimatableInstanceCache();
            AnimatableManager<LdMob> manager = cache.getManagerForId(dummy.getId());

            List<JsonArray> tracks = new ArrayList<>();
            bones.forEach(bone -> tracks.add(new JsonArray()));
            for (int tick = 0; tick <= length; tick++) {
                AnimationState<LdMob> state = new AnimationState<>(dummy, 0, 0, 0, false);
                processor.preAnimationSetup(state, START_TICK + tick);
                processor.tickAnimation(dummy, model, manager, START_TICK + tick, state, false);
                for (int b = 0; b < bones.size(); b++) {
                    float[] local = pivot(bones.get(b));
                    JsonArray sample = new JsonArray();
                    for (float value : local) {
                        sample.add(Math.round(value * 1000.0) / 1000.0);
                    }
                    tracks.get(b).add(sample);
                }
            }
            JsonObject boneJson = new JsonObject();
            for (int b = 0; b < bones.size(); b++) {
                boneJson.add(bones.get(b).getName(), tracks.get(b));
            }
            JsonObject clipJson = new JsonObject();
            clipJson.addProperty("length", length);
            clipJson.add("bones", boneJson);
            clips.add(clip, clipJson);
            dummy.discard();
        }

        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("rate", 20);
        root.addProperty("note", "Baked by /mobs_ldc bake from the GeckoLib animations: per tick, each bone pivot as "
                + "[forward, up, right] blocks from the mob's feet. Re-bake after changing the model or animations.");
        root.add("clips", clips);

        Path out = mc.gameDirectory.toPath().resolve("mobs_ld_bake").resolve(id.getNamespace()).resolve(id.getPath() + ".json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        return out;
    }

    /**
     * The bone's pivot in mob space, composed exactly as GeckoLib's renderer composes it; GeckoLib model space is
     * converted to the mob's frame (the renderer turns the model 180 degrees, so -Z is forward and +X its right).
     */
    private static float[] pivot(GeoBone bone) {
        Deque<GeoBone> chain = new ArrayDeque<>();
        for (GeoBone at = bone; at != null; at = at.getParent()) {
            chain.addFirst(at);
        }
        PoseStack pose = new PoseStack();
        for (GeoBone link : chain) {
            RenderUtil.translateMatrixToBone(pose, link);
            RenderUtil.translateToPivotPoint(pose, link);
            RenderUtil.rotateMatrixAroundBone(pose, link);
            RenderUtil.scaleMatrixForBone(pose, link);
            if (link != bone) {
                RenderUtil.translateAwayFromPivotPoint(pose, link);
            }
        }
        Vector4f origin = pose.last().pose().transform(new Vector4f(0.0F, 0.0F, 0.0F, 1.0F));
        return new float[]{-origin.z, origin.y, origin.x};
    }

    private static List<GeoBone> allBones(BakedGeoModel model) {
        List<GeoBone> result = new ArrayList<>();
        Deque<GeoBone> open = new ArrayDeque<>(model.topLevelBones());
        while (!open.isEmpty()) {
            GeoBone bone = open.removeFirst();
            result.add(bone);
            open.addAll(bone.getChildBones());
        }
        return result;
    }
}
