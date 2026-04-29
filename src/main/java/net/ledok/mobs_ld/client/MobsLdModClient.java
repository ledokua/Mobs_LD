package net.ledok.mobs_ld.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.ledok.mobs_ld.client.renderer.CryptGuardRenderer;
import net.ledok.mobs_ld.registry.ModEntities;

public class MobsLdModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.CRYPT_GUARD, CryptGuardRenderer::new);
    }
}
