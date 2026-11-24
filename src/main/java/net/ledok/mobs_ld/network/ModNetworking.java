package net.ledok.mobs_ld.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Registers Mobs_LD's S2C payloads and broadcasts visual packets to players within range who support the channel.
 */
public final class ModNetworking {
    /** Maximum range (blocks) at which clients receive 3D spell VFX packets. */
    private static final double VFX_BROADCAST_RADIUS = 80.0;
    private static final double VFX_BROADCAST_RADIUS_SQR = VFX_BROADCAST_RADIUS * VFX_BROADCAST_RADIUS;

    private ModNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(SpellVfxPayload.TYPE, SpellVfxPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SpellVfxCancelPayload.TYPE, SpellVfxCancelPayload.STREAM_CODEC);
    }

    /** Sends a one-shot spell VFX spawn packet to nearby players whose client understands it. */
    public static void sendVfx(ServerLevel level, Vec3 origin, SpellVfxPayload payload) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(origin) <= VFX_BROADCAST_RADIUS_SQR
                    && ServerPlayNetworking.canSend(player, SpellVfxPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    /** Tells nearby clients to abort a windup VFX whose ability was interrupted before impact. */
    public static void sendVfxCancel(ServerLevel level, Vec3 origin, int vfxId) {
        SpellVfxCancelPayload payload = new SpellVfxCancelPayload(vfxId);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(origin) <= VFX_BROADCAST_RADIUS_SQR
                    && ServerPlayNetworking.canSend(player, SpellVfxCancelPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
