package net.ledok.mobs_ld.network;

import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tiny S2C notification telling nearby clients to fade out a windup {@link SpellVfxPayload} whose ability was
 * interrupted before impact (stagger, phase transition, death, or boss reset).
 */
public record SpellVfxCancelPayload(int id) implements CustomPacketPayload {
    public static final Type<SpellVfxCancelPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "spell_vfx_cancel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellVfxCancelPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeVarInt(payload.id),
                    buf -> new SpellVfxCancelPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
