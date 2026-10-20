package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.ledok.mobs_ld.mob.combat.AbilityRun;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One beat on an ability's timeline. An event becomes active at {@link #begin()} (e.g. when its telegraph appears)
 * and finishes at {@link #end()} (e.g. the impact). Types are registered by name, so other code can add more before
 * datapacks load; the built-ins are below.
 */
public interface AbilityEvent {
    Map<String, MapCodec<? extends AbilityEvent>> TYPES = new LinkedHashMap<>(Map.of(
            "hit", HitEvent.CODEC,
            "dash", DashEvent.CODEC,
            "summon", SummonEvent.CODEC,
            "cloud", CloudEvent.CODEC,
            "effect", EffectEvent.CODEC,
            "despawn", DespawnEvent.CODEC,
            "chase", ChaseEvent.CODEC,
            "trace", TraceEvent.CODEC
    ));

    Codec<AbilityEvent> CODEC = Codec.STRING.dispatch("type", AbilityEvent::type, type -> {
        MapCodec<? extends AbilityEvent> codec = TYPES.get(type);
        if (codec == null) {
            throw new IllegalArgumentException("Unknown ability event type '" + type + "' (expected one of " + TYPES.keySet() + ")");
        }
        return codec;
    });

    String type();

    /** Seconds into the ability when the event starts (telegraph appears, dash begins). */
    double begin();

    /** Seconds into the ability when the event completes (impact). Never before {@link #begin()}. */
    double end();

    /**
     * Strike line (blocks to the mob's right) to aim before this beat; the run starts turning a little ahead of
     * {@link #begin()} so the body is facing right when the beat lands. Empty = keep the current facing.
     */
    default Optional<Double> aimSide() {
        return Optional.empty();
    }

    /** Abilities this event can hand over to (validated against the behaviour's ability names). */
    default List<String> chains() {
        return List.of();
    }

    /** Creates the per-run state; called once at {@link #begin()}. */
    Instance start(AbilityRun run);

    interface Instance {
        /** Every tick between begin and end. */
        default void tick(AbilityRun run) {
        }

        /** At {@link AbilityEvent#end()}. */
        default void finish(AbilityRun run) {
        }

        /** The ability was interrupted (phase change, stagger, reset) before {@link AbilityEvent#end()}. */
        default void cancel(AbilityRun run) {
        }
    }

    /** For events that just happen at one moment. */
    interface Instant extends AbilityEvent {
        double at();

        void fire(AbilityRun run);

        @Override
        default double begin() {
            return at();
        }

        @Override
        default double end() {
            return at();
        }

        @Override
        default Instance start(AbilityRun run) {
            return new Instance() {
                @Override
                public void finish(AbilityRun run) {
                    fire(run);
                }
            };
        }
    }
}
