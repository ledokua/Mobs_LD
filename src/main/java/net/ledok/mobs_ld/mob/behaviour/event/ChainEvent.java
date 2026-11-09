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
 *
 * @param tauntWins a taunt lock on the mob (the latest live taunt or provoke) beats the kept aim: the follow-up
 *                  turns on the taunter (the Warden's Deep Charge after a Hunting Sniff). Off by default: the kept
 *                  aim stays, as chains always worked
 * @param ifReady   only if {@code to} could start on its own now (its phase allows it, its cooldown and initial
 *                  cooldown are over, {@code skip_when_full} finds room); else this run just plays on to its end. Off
 *                  by default: a chain ignores the follow-up's cooldown, as chains always did
 */
public record ChainEvent(double at, String to, boolean tauntWins, boolean ifReady) implements AbilityEvent.Instant {
    public static final MapCodec<ChainEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(ChainEvent::at),
            Codec.STRING.fieldOf("to").forGetter(ChainEvent::to),
            Codec.BOOL.optionalFieldOf("taunt_wins", false).forGetter(ChainEvent::tauntWins),
            Codec.BOOL.optionalFieldOf("if_ready", false).forGetter(ChainEvent::ifReady)
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
        if (ifReady && !run.combat().readyToChain(to)) {
            run.combat().trace(run.name() + ": " + to + " not ready, no chain");
            return;
        }
        run.chain(to, null, tauntWins);
    }
}
