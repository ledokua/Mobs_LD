package net.ledok.mobs_ld.client.mob;

import net.ledok.mobs_ld.mob.LdMob;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic GeckoLib model for every data-driven mob: files are found by entity id under the contract's paths
 * (docs/models.md), the {@code head} bone gets look-at on top of animations, and marker bones are tracked so their
 * world positions can anchor particles.
 */
public class LdMobModel extends GeoModel<LdMob> {
    private static final String[] MARKER_PREFIXES = {"hit_", "fx_", "proj_"};

    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animations;

    /** Baked model the marker list was built from; a resource reload bakes a new one. */
    private BakedGeoModel markerSource;
    private List<GeoBone> markers = List.of();

    public LdMobModel(ResourceLocation id) {
        this.model = id.withPath("geo/entity/" + id.getPath() + ".geo.json");
        this.texture = id.withPath("textures/entity/" + id.getPath() + ".png");
        this.animations = id.withPath("animations/entity/" + id.getPath() + ".animation.json");
    }

    public ResourceLocation modelResource() {
        return model;
    }

    public ResourceLocation animationResource() {
        return animations;
    }

    @Override
    public ResourceLocation getModelResource(LdMob mob) {
        return model;
    }

    @Override
    public ResourceLocation getTextureResource(LdMob mob) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationResource(LdMob mob) {
        return animations;
    }

    @Override
    public void setCustomAnimations(LdMob mob, long instanceId, AnimationState<LdMob> state) {
        if (!mob.isDeadOrDying()) {
            GeoBone head = getAnimationProcessor().getBone("head");
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head != null && data != null) {
                // Added to whatever the animation set this frame (GeckoLib rewrites every bone each frame). Clamped
                // like vanilla look control, so a body/head yaw mismatch never twists the head round.
                float maxYaw = mob.getMaxHeadYRot();
                float maxPitch = mob.getMaxHeadXRot();
                head.setRotX(head.getRotX() + Mth.clamp(data.headPitch(), -maxPitch, maxPitch) * Mth.DEG_TO_RAD);
                head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -maxYaw, maxYaw) * Mth.DEG_TO_RAD);
            }
        }
        for (GeoBone marker : markers()) {
            marker.setTrackingMatrices(true);
        }
    }

    /** Marker bones ({@code hit_/fx_/proj_}) of the current model. */
    public List<GeoBone> markers() {
        BakedGeoModel baked = GeckoLibCache.getBakedModels().get(model);
        if (baked != markerSource) {
            List<GeoBone> found = new ArrayList<>();
            for (GeoBone bone : getAnimationProcessor().getRegisteredBones()) {
                for (String prefix : MARKER_PREFIXES) {
                    if (bone.getName().startsWith(prefix)) {
                        found.add(bone);
                        break;
                    }
                }
            }
            markers = List.copyOf(found);
            markerSource = baked;
        }
        return markers;
    }
}
