package net.ledok.mobs_ld.mob.combat;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.mobs_ld.network.AttackPreviewPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Opt-in combat geometry and outcome reporting for nearby operators only. */
public final class AttackPreview {
    private static final double WATCH_DISTANCE_SQUARED = 64.0 * 64.0;
    private static final UUID CLEAR_ID = new UUID(0, 0);
    private static final Set<UUID> ENABLED = new HashSet<>();

    private AttackPreview() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(AttackPreviewPayload.TYPE, AttackPreviewPayload.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ENABLED.remove(handler.player.getUUID()));
    }

    public static boolean available(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, AttackPreviewPayload.TYPE);
    }

    /** Only the requesting operator's preview changes. */
    public static boolean toggle(ServerPlayer player) {
        if (ENABLED.remove(player.getUUID())) {
            if (available(player)) {
                ServerPlayNetworking.send(player, new AttackPreviewPayload(CLEAR_ID, AttackPreviewPayload.Stage.CLEAR,
                        0, List.of(), List.of()));
            }
            return false;
        }
        ENABLED.add(player.getUUID());
        return true;
    }

    public static Locked lock(AbilityRun run, List<AbilityRun.HitZone> zones, double up, double down, double at) {
        if (ENABLED.isEmpty() || zones.isEmpty()) {
            return null;
        }
        ServerLevel level = run.level();
        List<UUID> watching = level.players().stream()
                .filter(player -> permitted(player) && player.distanceToSqr(run.mob()) <= WATCH_DISTANCE_SQUARED)
                .map(ServerPlayer::getUUID).toList();
        if (watching.isEmpty()) {
            return null;
        }

        UUID id = UUID.randomUUID();
        String label = run.mob().getName().getString() + " / " + run.name();
        int ticks = Math.max(0, (int) Math.ceil((at - run.seconds()) * 20.0 / run.speed() - 1.0e-6));
        List<AttackPreviewPayload.Zone> geometry = zones.stream().limit(AttackPreviewPayload.MAX_ZONES)
                .map(zone -> new AttackPreviewPayload.Zone(zone.origin(), zone.yaw(), up, down, zone.shape())).toList();
        Locked locked = new Locked(level, id, watching, label);
        locked.send(new AttackPreviewPayload(id, AttackPreviewPayload.Stage.LOCKED, ticks, geometry, List.of()),
                String.format(Locale.ROOT, "Locked %s · %d zone(s) · feet −%.2f/+%.2f blocks · expected impact tick %d",
                        zones.getFirst().shape().type(), zones.size(), down, up, level.getGameTime() + ticks));
        return locked;
    }

    private static boolean permitted(ServerPlayer player) {
        return ENABLED.contains(player.getUUID()) && player.hasPermissions(2) && available(player);
    }

    /** A victim that actually accepted the hit, with its footprint at the moment of impact. */
    public record Struck(String name, AABB box) {
    }

    public record Locked(ServerLevel level, UUID id, List<UUID> watching, String label) {
        public void impact(List<Struck> victims) {
            String names = victims.stream().limit(12).map(Struck::name).collect(Collectors.joining(", "));
            if (victims.size() > 12) {
                names += " (+" + (victims.size() - 12) + " more)";
            }
            List<AABB> hitboxes = victims.stream().limit(AttackPreviewPayload.MAX_HITBOXES).map(Struck::box).toList();
            send(new AttackPreviewPayload(id, AttackPreviewPayload.Stage.IMPACT, 0, List.of(), hitboxes),
                    "Impact tick " + level.getGameTime() + " · hit " + victims.size()
                            + (victims.isEmpty() ? "" : " · " + names));
        }

        public void cancel() {
            send(new AttackPreviewPayload(id, AttackPreviewPayload.Stage.CANCELLED, 0, List.of(), List.of()),
                    "Cancelled before impact");
        }

        private void send(AttackPreviewPayload payload, String text) {
            for (UUID watcher : watching) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(watcher);
                if (player != null && player.serverLevel() == level && permitted(player)) {
                    ServerPlayNetworking.send(player, payload);
                    player.sendSystemMessage(Component.literal("[Attack preview] " + label + " · " + text));
                }
            }
        }
    }
}
