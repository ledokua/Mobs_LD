package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.combat.AbilityRun;

import java.util.List;

/**
 * Hands over to another ability at {@code at}: the run ends there (events still active are cut off, as by a chase's
 * hand-over) and {@code to} starts at once, skipping the global cooldown and its range. The follow-up keeps this
 * run's aim if its own {@code target} is {@code current} (the default), else it picks its aim again by its rule
 * (e.g. {@code slot:left_head}), keeping this run's aim when that finds nobody. A stagger or phase change before
 * {@code at} ends the run without it.
 */
public record ChainEvent(double at, String to) implements AbilityEvent.Instant {
    public static final MapCodec<ChainEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(ChainEvent::at),
            Codec.STRING.fieldOf("to").forGetter(ChainEvent::to)
    ).apply(i, ChainEvent::new));

    @Override
    public String type() {
        return "chain";
    }

    @Override
    public List<String> chains() {
        return List.of(to);
    }

    @Override
    public void fire(AbilityRun run) {
        run.chain(to);
    }
}
