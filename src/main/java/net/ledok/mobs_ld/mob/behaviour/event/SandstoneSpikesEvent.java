package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.network.SandstoneSpikesPayload;
import net.minecraft.server.level.ServerPlayer;

/** A cosmetic spike row that follows a rectangular hit's locked lane at the same impact beat. */
public record SandstoneSpikesEvent(double at) implements AbilityEvent.Instant {
    public static final String ID = "sandstone_spikes";
    public static final MapCodec<SandstoneSpikesEvent> CODEC =
            Codec.DOUBLE.fieldOf("at").xmap(SandstoneSpikesEvent::new, SandstoneSpikesEvent::at);

    @Override
    public String type() {
        return ID;
    }

    public boolean matches(AbilityEvent event) {
        return event instanceof HitEvent hit && hit.shape() instanceof Shape.Rectangle
                && FxEvent.sameMoment(hit.at(), at);
    }

    @Override
    public void fire(AbilityRun run) {
        // Zero-windup hits can lock their lanes on this same tick, after this event in the JSON.
        run.afterEvents(() -> {
            for (AbilityEvent event : run.ability().events()) {
                if (!matches(event)) {
                    continue;
                }
                HitEvent hit = (HitEvent) event;
                Shape.Rectangle shape = (Shape.Rectangle) hit.shape();
                float up = hit.height().orElse(run.combat().reachUp()).floatValue();
                float down = hit.depth().orElse(run.combat().reachDown()).floatValue();
                for (AbilityRun.HitZone zone : run.lockedZones(hit)) {
                    SandstoneSpikesPayload payload = new SandstoneSpikesPayload(zone.origin(), zone.yaw(),
                            (float) shape.width(), (float) shape.length(), (float) shape.offset(), up, down);
                    for (ServerPlayer player : PlayerLookup.tracking(run.mob())) {
                        if (ServerPlayNetworking.canSend(player, SandstoneSpikesPayload.TYPE)) {
                            ServerPlayNetworking.send(player, payload);
                        }
                    }
                }
            }
        });
    }
}
