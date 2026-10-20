package net.ledok.mobs_ld.client.renderer;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.client.model.ShamanCustomModel;
import net.ledok.mobs_ld.entity.mob.CurseShaman;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class ShamanRenderer extends MobRenderer<CurseShaman, ShamanCustomModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "textures/entity/curse_shaman.png");

    public ShamanRenderer(EntityRendererProvider.Context context) {
        super(context, new ShamanCustomModel(context.bakeLayer(ShamanCustomModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(CurseShaman entity) {
        return TEXTURE;
    }
}
