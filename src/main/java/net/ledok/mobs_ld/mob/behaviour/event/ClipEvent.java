package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.CombatHost;

/**
 * Plays an action clip at {@code at}, in the middle of the ability's timeline, as the ability's own {@code clip}
 * does at its start: the GeckoLib action layer on an LdMob (restarted if it is the same clip), a host cue on a vanilla
 * host (the Warden's {@code attack}, {@code sonic_boom}, ...; see its adapter). Played at the ability's speed. It
 * replaces the ability's clip; the ability's end stops it as usual.
 */
public record ClipEvent(double at, String clip) implements AbilityEvent.Instant {
    public static final MapCodec<ClipEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(ClipEvent::at),
            Codec.STRING.fieldOf("clip").forGetter(ClipEvent::clip)
    ).apply(i, ClipEvent::new));

    @Override
    public String type() {
        return "clip";
    }

    @Override
    public void fire(AbilityRun run) {
        run.combat().trace(run.name() + ": clip " + clip);
        ((CombatHost) run.mob()).mobs_ld$playAction(clip, run.speed());
    }
}
