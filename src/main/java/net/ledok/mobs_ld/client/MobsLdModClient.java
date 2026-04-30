package net.ledok.mobs_ld.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.ledok.mobs_ld.client.renderer.AttackZoneVisualRenderer;
import net.ledok.mobs_ld.client.renderer.DungeonMobRenderer;
import net.ledok.mobs_ld.registry.ModEntities;

public class MobsLdModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.CRYPT_GUARD, ctx -> new DungeonMobRenderer<>(ctx, "crypt_guard"));
        EntityRendererRegistry.register(ModEntities.SLAB_GUARD, ctx -> new DungeonMobRenderer<>(ctx, "slab_guard"));
        EntityRendererRegistry.register(ModEntities.SPINNER_MOB, ctx -> new DungeonMobRenderer<>(ctx, "spinner_mob"));
        EntityRendererRegistry.register(ModEntities.FANG_MOB, ctx -> new DungeonMobRenderer<>(ctx, "fang_mob"));
        EntityRendererRegistry.register(ModEntities.CURSE_SHAMAN, ctx -> new DungeonMobRenderer<>(ctx, "curse_shaman"));
        EntityRendererRegistry.register(ModEntities.VECNA_THE_SECOND, ctx -> new DungeonMobRenderer<>(ctx, "vecna_the_second"));
        EntityRendererRegistry.register(ModEntities.ATTACK_ZONE_VISUAL, AttackZoneVisualRenderer::new);
    }
}
