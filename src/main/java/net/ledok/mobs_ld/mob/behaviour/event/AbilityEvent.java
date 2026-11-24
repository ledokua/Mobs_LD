package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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
    Map<String, MapCodec<? extends AbilityEvent>> TYPES = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("hit", HitEvent.CODEC),
            Map.entry("dash", DashEvent.CODEC),
            Map.entry("summon", SummonEvent.CODEC),
            Map.entry("cloud", CloudEvent.CODEC),
            Map.entry("effect", EffectEvent.CODEC),
            Map.entry("despawn", DespawnEvent.CODEC),
            Map.entry("chase", ChaseEvent.CODEC),
            Map.entry("trace", TraceEvent.CODEC),
            Map.entry("hover", HoverEvent.CODEC),
            Map.entry("look", LookEvent.CODEC),
            Map.entry("fx", FxEvent.CODEC),
            Map.entry(SandstoneSpikesEvent.ID, SandstoneSpikesEvent.CODEC),
            Map.entry("projectile", ProjectileEvent.CODEC),
            Map.entry("shoot", ShootEvent.CODEC),
            Map.entry("telegraph", TelegraphEvent.CODEC),
            Map.entry("chain", ChainEvent.CODEC),
            Map.entry("clip", ClipEvent.CODEC),
            Map.entry("vulnerable", VulnerableEvent.CODEC),
            Map.entry("listen", ListenEvent.CODEC)
    ));

    /** An unknown {@code type} is a decoding error (the file is reported and skipped), never an exception. */
    Codec<AbilityEvent> CODEC = Codec.STRING.partialDispatch("type", event -> DataResult.success(event.type()), type -> {
        MapCodec<? extends AbilityEvent> codec = TYPES.get(type);
        return codec != null ? DataResult.success(codec)
                : DataResult.error(() -> "Unknown ability event type '" + type + "' (expected one of " + TYPES.keySet() + ")");
    });

    String type();

    /** Seconds into the ability when the event starts (telegraph appears, dash begins). */
    double begin();

    /** Seconds into the ability when the event completes (impact). Never before {@link #begin()}. */
    double end();

    /**
     * Strike line (blocks to the mob's right) the body must already face when this beat begins; the run starts
     * turning {@link AbilityRun#AIM_LEAD} seconds ahead of {@link #begin()}. Empty = the event aims itself (or keeps
     * the current facing).
     */
    default Optional<Double> preAim() {
        return Optional.empty();
    }

    /** Abilities this event can hand over to (validated against the behaviour's ability names). */
    default List<String> chains() {
        return List.of();
    }

    /** Target slots this event aims at (validated against the behaviour's {@code target_slots}). */
    default List<String> slots() {
        return List.of();
    }

    /** Skull specs this event fires (validated against the host block's {@code skulls}). */
    default List<String> skulls() {
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
