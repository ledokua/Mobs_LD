package net.ledok.mobs_ld.client.renderer;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.client.model.MummyModel;
import net.ledok.mobs_ld.entity.mob.Mummy;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class MummyRenderer extends MobRenderer<Mummy, MummyModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "textures/entity/mummy.png");

    public MummyRenderer(EntityRendererProvider.Context context) {
        super(context, new MummyModel(context.bakeLayer(MummyModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(Mummy entity) {
        return TEXTURE;
    }
}
