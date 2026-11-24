package net.ledok.mobs_ld.network;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** A private snapshot of one rectangular, cone, circular, ring or rays hit event. */
public record AttackPreviewPayload(UUID id, Stage stage, int windupTicks, List<Zone> zones, List<AABB> hitboxes)
        implements CustomPacketPayload {
    public static final int MAX_ZONES = 64;
    public static final int MAX_HITBOXES = 64;
    public static final Type<AttackPreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "attack_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AttackPreviewPayload> CODEC =
            StreamCodec.of(AttackPreviewPayload::write, AttackPreviewPayload::read);

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Zone>> ZONES =
            StreamCodec.<RegistryFriendlyByteBuf, Zone>of(Zone::write, Zone::read)
                    .apply(ByteBufCodecs.list(MAX_ZONES));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<AABB>> HITBOXES =
            StreamCodec.<RegistryFriendlyByteBuf, AABB>of(AttackPreviewPayload::writeBox, AttackPreviewPayload::readBox)
                    .apply(ByteBufCodecs.list(MAX_HITBOXES));

    public enum Stage {
        LOCKED, IMPACT, CANCELLED, CLEAR
    }

    public record Zone(Vec3 origin, float yaw, double up, double down, Shape shape) {
        private static final StreamCodec<io.netty.buffer.ByteBuf, Shape> SHAPE = ByteBufCodecs.fromCodec(Shape.CODEC);

        private static void write(RegistryFriendlyByteBuf buf, Zone zone) {
            buf.writeDouble(zone.origin.x);
            buf.writeDouble(zone.origin.y);
            buf.writeDouble(zone.origin.z);
            buf.writeFloat(zone.yaw);
            buf.writeDouble(zone.up);
            buf.writeDouble(zone.down);
            SHAPE.encode(buf, zone.shape);
        }

        private static Zone read(RegistryFriendlyByteBuf buf) {
            return new Zone(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(),
                    buf.readDouble(), buf.readDouble(), SHAPE.decode(buf));
        }
    }

    @Override
    public Type<AttackPreviewPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, AttackPreviewPayload payload) {
        buf.writeUUID(payload.id);
        buf.writeEnum(payload.stage);
        buf.writeVarInt(payload.windupTicks);
        ZONES.encode(buf, payload.zones);
        HITBOXES.encode(buf, payload.hitboxes);
    }

    private static AttackPreviewPayload read(RegistryFriendlyByteBuf buf) {
        return new AttackPreviewPayload(buf.readUUID(), buf.readEnum(Stage.class), buf.readVarInt(), ZONES.decode(buf),
                HITBOXES.decode(buf));
    }

    private static void writeBox(RegistryFriendlyByteBuf buf, AABB box) {
        buf.writeDouble(box.minX);
        buf.writeDouble(box.minY);
        buf.writeDouble(box.minZ);
        buf.writeDouble(box.maxX);
        buf.writeDouble(box.maxY);
        buf.writeDouble(box.maxZ);
    }

    private static AABB readBox(RegistryFriendlyByteBuf buf) {
        return new AABB(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
