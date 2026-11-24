package net.ledok.mobs_ld.network;

import net.ledok.mobs_ld.MobsLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Compact one-shot S2C packet spawning a client-side procedural 3D spell effect.
 * The server spawns no visual entities and runs no tick loops for VFX: one packet starts the full windup,
 * impact, and fadeout sequence on nearby clients.
 */
public record SpellVfxPayload(
        int id,
        int sourceEntityId,
        SpellVfxKind kind,
        double x,
        double y,
        double z,
        float yaw,
        int primaryColor,
        int secondaryColor,
        float windupSeconds,
        float durationSeconds,
        float radius,
        float length,
        float width,
        float height,
        float angle,
        float tilt,
        byte count,
        byte flags
) implements CustomPacketPayload {
    public static final int FLAG_REVERSE = 1;
    public static final int FLAG_PARTICLES = 1 << 1;
    public static final int FLAG_WINDUP_ONLY = 1 << 2;
    public static final int FLAG_LOCKED_BEAM = 1 << 3;

    public static final Type<SpellVfxPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "spell_vfx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellVfxPayload> STREAM_CODEC =
            StreamCodec.of(SpellVfxPayload::write, SpellVfxPayload::read);

    public static byte packFlags(boolean reverse, boolean particles) {
        int bits = 0;
        if (reverse) {
            bits |= FLAG_REVERSE;
        }
        if (particles) {
            bits |= FLAG_PARTICLES;
        }
        return (byte) bits;
    }

    public boolean reverse() {
        return (flags & FLAG_REVERSE) != 0;
    }

    public boolean particles() {
        return (flags & FLAG_PARTICLES) != 0;
    }

    public boolean windupOnly() {
        return (flags & FLAG_WINDUP_ONLY) != 0;
    }

    public boolean lockedBeam() {
        return (flags & FLAG_LOCKED_BEAM) != 0;
    }

    public int countUnsigned() {
        return Math.max(1, count & 0xFF);
    }

    private static void write(RegistryFriendlyByteBuf buf, SpellVfxPayload payload) {
        buf.writeVarInt(payload.id);
        buf.writeVarInt(payload.sourceEntityId);
        buf.writeByte(payload.kind.ordinal());
        buf.writeDouble(payload.x);
        buf.writeDouble(payload.y);
        buf.writeDouble(payload.z);
        buf.writeFloat(payload.yaw);
        buf.writeInt(payload.primaryColor);
        buf.writeInt(payload.secondaryColor);
        buf.writeFloat(payload.windupSeconds);
        buf.writeFloat(payload.durationSeconds);
        buf.writeFloat(payload.radius);
        buf.writeFloat(payload.length);
        buf.writeFloat(payload.width);
        buf.writeFloat(payload.height);
        buf.writeFloat(payload.angle);
        buf.writeFloat(payload.tilt);
        buf.writeByte(payload.count);
        buf.writeByte(payload.flags);
    }

    private static SpellVfxPayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int sourceEntityId = buf.readVarInt();
        SpellVfxKind kind = SpellVfxKind.byId(buf.readByte() & 0xFF);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float yaw = buf.readFloat();
        int primaryColor = buf.readInt();
        int secondaryColor = buf.readInt();
        float windupSeconds = buf.readFloat();
        float durationSeconds = buf.readFloat();
        float radius = buf.readFloat();
        float length = buf.readFloat();
        float width = buf.readFloat();
        float height = buf.readFloat();
        float angle = buf.readFloat();
        float tilt = buf.readFloat();
        byte count = buf.readByte();
        byte flags = buf.readByte();
        return new SpellVfxPayload(id, sourceEntityId, kind, x, y, z, yaw, primaryColor, secondaryColor,
                windupSeconds, durationSeconds, radius, length, width, height, angle, tilt, count, flags);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
