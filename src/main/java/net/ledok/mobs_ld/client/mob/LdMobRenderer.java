package net.ledok.mobs_ld.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.ledok.mobs_ld.client.renderer.SummonerWardRenderer;
import net.ledok.mobs_ld.mob.LdMob;
import net.ledok.mobs_ld.mob.MobClientHooks;
import net.ledok.mobs_ld.mob.MobDeclaration;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Renders any data-driven mob from its GeckoLib assets; one instance per entity type. */
public class LdMobRenderer extends GeoEntityRenderer<LdMob> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final LdMobModel ldModel;
    private boolean warnedMissingModel;

    public LdMobRenderer(EntityRendererProvider.Context context, MobDeclaration declaration) {
        this(context, declaration, new LdMobModel(declaration.id()));
    }

    private LdMobRenderer(EntityRendererProvider.Context context, MobDeclaration declaration, LdMobModel model) {
        super(context, model);
        this.ldModel = model;
        this.shadowRadius = declaration.render().shadowRadius().orElse(declaration.width() * 0.5F);
        if (declaration.render().scale() != 1.0F) {
            withScale(declaration.render().scale());
        }
        // Renderers are rebuilt on every resource reload, so checking for the optional glowmask here stays current.
        ResourceLocation glowmask = declaration.id().withPath("textures/entity/" + declaration.id().getPath() + "_glowmask.png");
        if (context.getResourceManager().getResource(glowmask).isPresent()) {
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    @Override
    public void render(LdMob mob, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        // GeckoLib throws on a missing model file; a mob whose art has not landed yet should just be invisible.
        if (!GeckoLibCache.getBakedModels().containsKey(ldModel.modelResource())) {
            if (!warnedMissingModel) {
                warnedMissingModel = true;
                LOGGER.warn("No model at {} for {}; rendering nothing", ldModel.modelResource(), mob.getType());
            }
            return;
        }
        super.render(mob, yaw, partialTick, poseStack, buffers, light);
        // Bones are shared by every mob of this type, so read the marker positions right after this mob's pass.
        for (GeoBone marker : ldModel.markers()) {
            Vector3d world = marker.getWorldPosition();
            mob.setMarkerPosition(marker.getName(), new Vec3(world.x, world.y, world.z));
        }
        // Particle keyframes fired while this frame was animated; spawn them now, at this frame's bone positions.
        mob.runClientEffects();
        SummonerWardRenderer.render(mob, partialTick, poseStack, buffers);
    }

    @Override
    protected float getDeathMaxRotation(LdMob mob) {
        // A mob with a death clip falls the way it was animated, not with the vanilla sideways flop.
        return MobClientHooks.get().animation(mob, "death") != null ? 0.0F : super.getDeathMaxRotation(mob);
    }
}
