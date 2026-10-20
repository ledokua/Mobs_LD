package net.ledok.mobs_ld.client.renderer;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.client.model.BallModel;
import net.ledok.mobs_ld.entity.mob.Ball;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class BallRenderer extends MobRenderer<Ball, BallModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "textures/entity/ball.png");

    public BallRenderer(EntityRendererProvider.Context context) {
        super(context, new BallModel(context.bakeLayer(BallModel.LAYER_LOCATION)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(Ball entity) {
        return TEXTURE;
    }
}
