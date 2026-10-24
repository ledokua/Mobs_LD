package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.host.Head;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Turns heads onto someone from {@code at} to {@code until} (a tell: all three Wither heads snapping onto the player
 * a rush is aimed at). While it runs it overrides those heads' target slots, and the side heads hold their own
 * skull fire; afterwards the slots take the heads back. On a host with one head, {@code center} turns its head.
 *
 * @param heads  which heads turn
 * @param target whom they look at, read every tick
 */
public record LookEvent(double at, double until, Head.Selector heads, EventTarget target) implements AbilityEvent {
    public static final MapCodec<LookEvent> CODEC = RecordCodecBuilder.<LookEvent>mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(LookEvent::at),
            Codec.DOUBLE.fieldOf("until").forGetter(LookEvent::until),
            Head.Selector.CODEC.optionalFieldOf("heads", Head.Selector.ALL).forGetter(LookEvent::heads),
            EventTarget.CODEC.optionalFieldOf("target", EventTarget.AIM).forGetter(LookEvent::target)
    ).apply(i, LookEvent::new)).validate(LookEvent::validate);

    private static DataResult<LookEvent> validate(LookEvent event) {
        return event.until < event.at
                ? DataResult.error(() -> "A look event ends (until " + event.until + ") before it starts (at " + event.at + ")")
                : DataResult.success(event);
    }

    @Override
    public String type() {
        return "look";
    }

    @Override
    public double begin() {
        return at;
    }

    @Override
    public double end() {
        return until;
    }

    @Override
    public List<String> slots() {
        return target.slots();
    }

    @Override
    public Instance start(AbilityRun run) {
        return new Instance() {
            @Override
            public void tick(AbilityRun run) {
                LivingEntity entity = target.resolve(run);
                if (entity != null) {
                    run.combat().slots().look(heads.heads(), entity);
                }
            }
        };
    }
}
