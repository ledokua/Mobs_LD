package net.ledok.mobs_ld.client.renderer;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.client.model.SandGolemGuardianModel;
import net.ledok.mobs_ld.entity.boss.SandGolemGuardian;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SandGolemGuardianRenderer extends MobRenderer<SandGolemGuardian, SandGolemGuardianModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "textures/entity/sand_golem_guardian.png");

    public SandGolemGuardianRenderer(EntityRendererProvider.Context context) {
        super(context, new SandGolemGuardianModel(context.bakeLayer(SandGolemGuardianModel.LAYER_LOCATION)), 1.2F);
    }

    @Override
    public ResourceLocation getTextureLocation(SandGolemGuardian entity) {
        return TEXTURE;
    }
}
