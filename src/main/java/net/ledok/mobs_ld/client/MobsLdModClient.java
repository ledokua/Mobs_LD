package net.ledok.mobs_ld.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.ledok.mobs_ld.client.model.BallModel;
import net.ledok.mobs_ld.client.model.MummyModel;
import net.ledok.mobs_ld.client.model.SandGolemGuardianModel;
import net.ledok.mobs_ld.client.model.ShamanCustomModel;
import net.ledok.mobs_ld.client.renderer.AttackZoneVisualRenderer;
import net.ledok.mobs_ld.client.renderer.BallRenderer;
import net.ledok.mobs_ld.client.renderer.DungeonMobRenderer;
import net.ledok.mobs_ld.client.renderer.MummyRenderer;
import net.ledok.mobs_ld.client.renderer.SandGolemGuardianRenderer;
import net.ledok.mobs_ld.client.renderer.ShamanRenderer;
import net.ledok.mobs_ld.registry.ModEntities;
import net.minecraft.client.renderer.entity.SkeletonRenderer;

public class MobsLdModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(ShamanCustomModel.LAYER_LOCATION, ShamanCustomModel::createBodyLayer);
        EntityModelLayerRegistry.registerModelLayer(SandGolemGuardianModel.LAYER_LOCATION, SandGolemGuardianModel::createBodyLayer);
        EntityModelLayerRegistry.registerModelLayer(MummyModel.LAYER_LOCATION, MummyModel::createBodyLayer);
        EntityModelLayerRegistry.registerModelLayer(BallModel.LAYER_LOCATION, BallModel::createBodyLayer);

        EntityRendererRegistry.register(ModEntities.CRYPT_GUARD, ctx -> new DungeonMobRenderer<>(ctx, "crypt_guard"));
        EntityRendererRegistry.register(ModEntities.SLAB_GUARD, ctx -> new DungeonMobRenderer<>(ctx, "slab_guard"));
        EntityRendererRegistry.register(ModEntities.SPINNER_MOB, ctx -> new DungeonMobRenderer<>(ctx, "spinner_mob"));
        EntityRendererRegistry.register(ModEntities.FANG_MOB, ctx -> new DungeonMobRenderer<>(ctx, "fang_mob"));
        EntityRendererRegistry.register(ModEntities.CURSE_SHAMAN, ShamanRenderer::new);
        EntityRendererRegistry.register(ModEntities.SAND_GOLEM_GUARDIAN, SandGolemGuardianRenderer::new);
        EntityRendererRegistry.register(ModEntities.MUMMY, MummyRenderer::new);
        EntityRendererRegistry.register(ModEntities.BALL, BallRenderer::new);
        // Reuse the vanilla skeleton renderer (model + texture) as-is; recolour later via a subclass override.
        EntityRendererRegistry.register(ModEntities.DUNGEON_SKELETON, SkeletonRenderer::new);
        EntityRendererRegistry.register(ModEntities.ATTACK_ZONE_VISUAL, AttackZoneVisualRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> ScreenShake.tick());
        // A shake mid-decay must not report intensity into the next world.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ScreenShake.reset());
    }
}
