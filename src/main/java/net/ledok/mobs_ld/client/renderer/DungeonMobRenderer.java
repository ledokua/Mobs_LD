package net.ledok.mobs_ld.client.renderer;

import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

public class DungeonMobRenderer<T extends Mob> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
    private final ResourceLocation texture;

    public DungeonMobRenderer(EntityRendererProvider.Context context, String textureName) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        this.texture = ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "textures/entity/" + textureName + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }
}
