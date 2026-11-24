package net.ledok.mobs_ld.network;

import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** One locked slam lane whose sandstone spikes are rendered by watching clients. */
public record SandstoneSpikesPayload(Vec3 origin, float yaw, float width, float length, float offset, float up, float down)
        implements CustomPacketPayload {
    public static final Type<SandstoneSpikesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "sandstone_spikes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SandstoneSpikesPayload> CODEC =
            StreamCodec.of(SandstoneSpikesPayload::write, SandstoneSpikesPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SandstoneSpikesPayload payload) {
        buf.writeDouble(payload.origin.x);
        buf.writeDouble(payload.origin.y);
        buf.writeDouble(payload.origin.z);
        buf.writeFloat(payload.yaw);
        buf.writeFloat(payload.width);
        buf.writeFloat(payload.length);
        buf.writeFloat(payload.offset);
        buf.writeFloat(payload.up);
        buf.writeFloat(payload.down);
    }

    private static SandstoneSpikesPayload read(RegistryFriendlyByteBuf buf) {
        return new SandstoneSpikesPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public Type<SandstoneSpikesPayload> type() {
        return TYPE;
    }
}
