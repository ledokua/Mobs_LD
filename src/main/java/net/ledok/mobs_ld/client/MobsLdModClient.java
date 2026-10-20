package net.ledok.mobs_ld.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.ledok.mobs_ld.client.renderer.AttackZoneVisualRenderer;
import net.ledok.mobs_ld.registry.ModEntities;

public class MobsLdModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.ATTACK_ZONE_VISUAL, AttackZoneVisualRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> ScreenShake.tick());
        // A shake mid-decay must not report intensity into the next world.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ScreenShake.reset());
    }
}
