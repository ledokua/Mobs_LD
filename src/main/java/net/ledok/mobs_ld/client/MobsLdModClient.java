package net.ledok.mobs_ld.client;

import net.fabricmc.api.ClientModInitializer;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.ledok.mobs_ld.client.mob.HitTrackBaker;
import net.ledok.mobs_ld.mob.LdMob;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.ledok.mobs_ld.client.mob.LdMobClientHooks;
import net.ledok.mobs_ld.client.mob.LdMobRenderer;
import net.ledok.mobs_ld.client.renderer.AttackPreviewRenderer;
import net.ledok.mobs_ld.client.renderer.AttackZoneVisualRenderer;
import net.ledok.mobs_ld.client.renderer.SandstoneSpikesRenderer;
import net.ledok.mobs_ld.mob.MobClientHooks;
import net.ledok.mobs_ld.mob.MobDeclaration;
import net.ledok.mobs_ld.mob.MobDeclarations;
import net.ledok.mobs_ld.registry.ModEntities;

import java.nio.file.Path;

public class MobsLdModClient implements ClientModInitializer {
    /** {@code /mobs_ldc bake <mob>}: write the mob's hit tracks (see {@link HitTrackBaker}). */
    private static int bake(FabricClientCommandSource source, String mob) {
        ResourceLocation id = mob.contains(":") ? ResourceLocation.parse(mob) : ResourceLocation.fromNamespaceAndPath("mobs_ld", mob);
        EntityType<LdMob> type = MobDeclarations.types().get(id);
        if (type == null) {
            source.sendError(Component.literal("Unknown data-driven mob " + id));
            return 0;
        }
        try {
            Path out = HitTrackBaker.bake(Minecraft.getInstance(), type, id);
            source.sendFeedback(Component.literal("Baked hit tracks for " + id + " -> " + out));
            return 1;
        } catch (Exception e) {
            source.sendError(Component.literal("Bake failed: " + e));
            return 0;
        }
    }

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.ATTACK_ZONE_VISUAL, AttackZoneVisualRenderer::new);
        AttackPreviewRenderer.register();
        SandstoneSpikesRenderer.register();
        MobClientHooks.install(new LdMobClientHooks());
        for (MobDeclaration declaration : MobDeclarations.all()) {
            EntityRendererRegistry.register(MobDeclarations.types().get(declaration.id()),
                    context -> new LdMobRenderer(context, declaration));
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("mobs_ldc")
                        .then(ClientCommandManager.literal("bake")
                                .then(ClientCommandManager.argument("mob", StringArgumentType.word())
                                        .executes(ctx -> bake(ctx.getSource(), StringArgumentType.getString(ctx, "mob")))))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> ScreenShake.tick());
        // A shake mid-decay must not report intensity into the next world.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ScreenShake.reset());
    }
}
