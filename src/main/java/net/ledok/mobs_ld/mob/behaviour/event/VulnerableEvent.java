package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.combat.AbilityRun;

/**
 * A window from {@code at} to {@code until} in which the mob takes {@code multiplier} times the damage (the Warden's
 * open chest after a Circle or a Lance). Stacks with the phase's {@code damage_taken} and with other windows open at
 * the same time; it closes early if the ability is cut off (phase change, stagger, reset). The ability runs until
 * {@code until}, like every event.
 */
public record VulnerableEvent(double at, double until, double multiplier) implements AbilityEvent {
    public static final MapCodec<VulnerableEvent> CODEC = RecordCodecBuilder.<VulnerableEvent>mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(VulnerableEvent::at),
            Codec.DOUBLE.fieldOf("until").forGetter(VulnerableEvent::until),
            Codec.doubleRange(0.0, 100.0).fieldOf("multiplier").forGetter(VulnerableEvent::multiplier)
    ).apply(i, VulnerableEvent::new)).validate(event -> event.until() < event.at()
            ? DataResult.error(() -> "A vulnerable window ends (until " + event.until() + ") before it starts (at "
            + event.at() + ")")
            : DataResult.success(event));

    @Override
    public String type() {
        return "vulnerable";
    }

    @Override
    public double begin() {
        return at;
    }

    @Override
    public double end() {
        return until;
    }

    /** Nothing to do per tick: the run reads the open windows when the mob is hurt. */
    @Override
    public Instance start(AbilityRun run) {
        run.combat().trace(String.format("%s: vulnerable x%.2f until %.1f s", run.name(), multiplier, until));
        return new Instance() {
        };
    }
}
