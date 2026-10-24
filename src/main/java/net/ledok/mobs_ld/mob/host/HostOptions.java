package net.ledok.mobs_ld.mob.host;

import net.ledok.mobs_ld.mob.behaviour.SkullSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * The adapter-specific half of a behaviour's {@code host} block: settings only one entity type's
 * {@link HostAdapter} understands (e.g. the Wither's hover height). Each kind is registered under its
 * {@code host.type} id in {@link HostSpec}.
 */
public interface HostOptions {
    /** The {@code host.type} these options were read for, e.g. {@code mobs_ld:wither}. */
    ResourceLocation type();

    /**
     * The skulls this host fires by name ({@code host.skulls}), read by its heads' own cadence and by
     * {@code projectile} events; empty = it has none (its own projectiles, if any, stay vanilla).
     */
    default Map<String, SkullSpec> skulls() {
        return Map.of();
    }
}
