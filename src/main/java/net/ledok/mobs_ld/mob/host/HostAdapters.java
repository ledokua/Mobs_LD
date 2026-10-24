package net.ledok.mobs_ld.mob.host;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Host adapters by entity type. Filled during mod initialisation, read from mob ticks; an entity type without an
 * adapter runs the engine through {@link HostAdapter#DEFAULT}.
 */
public final class HostAdapters {
    private static final Map<EntityType<?>, HostAdapter> BY_TYPE = new IdentityHashMap<>();

    private HostAdapters() {
    }

    /** Registers the adapter for {@code type}; call from a mod initializer, before any world loads. */
    public static void register(EntityType<?> type, HostAdapter adapter) {
        if (BY_TYPE.putIfAbsent(type, adapter) != null) {
            throw new IllegalStateException("Host adapter for " + BuiltInRegistries.ENTITY_TYPE.getKey(type) + " registered twice");
        }
    }

    public static HostAdapter get(EntityType<?> type) {
        return BY_TYPE.getOrDefault(type, HostAdapter.DEFAULT);
    }
}
