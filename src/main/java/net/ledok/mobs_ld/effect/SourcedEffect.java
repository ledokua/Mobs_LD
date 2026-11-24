package net.ledok.mobs_ld.effect;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A damaging effect that remembers who put it on each victim (the mob whose hit applied it), so its ticks can name
 * that mob as the attacker: kill credit, threat and the attacker's damage scaling ({@code loot_stage:damage_dealt})
 * work as for its hits. Fed by LivingEntityEffectThreatMixin whenever the effect is added or refreshed with a source;
 * server thread only, and nothing is kept for victims or sources that are gone.
 */
public interface SourcedEffect {
    Map<LivingEntity, WeakReference<Entity>> sources();

    default void noteSource(LivingEntity victim, Entity source) {
        if (source != null && source != victim) {
            sources().put(victim, new WeakReference<>(source));
        }
    }

    /** Who last applied it to {@code victim}, while they are still around; null otherwise. */
    default Entity sourceOf(LivingEntity victim) {
        WeakReference<Entity> reference = sources().get(victim);
        Entity source = reference == null ? null : reference.get();
        return source != null && !source.isRemoved() ? source : null;
    }

    static Map<LivingEntity, WeakReference<Entity>> newSources() {
        return new WeakHashMap<>();
    }
}
