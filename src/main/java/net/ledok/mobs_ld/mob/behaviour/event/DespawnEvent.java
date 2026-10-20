package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.combat.AbilityRun;

/** Removes the mob in a puff of smoke: no death, no drops, no {@code on_death} (e.g. a bomb that has gone off). */
public record DespawnEvent(double at) implements AbilityEvent.Instant {
    public static final MapCodec<DespawnEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(DespawnEvent::at)
    ).apply(i, DespawnEvent::new));

    @Override
    public String type() {
        return "despawn";
    }

    @Override
    public void fire(AbilityRun run) {
        run.mob().despawnWithPoof();
    }
}
