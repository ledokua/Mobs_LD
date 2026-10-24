package net.ledok.mobs_ld.mob.host;

import net.minecraft.resources.ResourceLocation;

/**
 * The adapter-specific half of a behaviour's {@code host} block: settings only one entity type's
 * {@link HostAdapter} understands (e.g. the Wither's hover height). Each kind is registered under its
 * {@code host.type} id in {@link HostSpec}.
 */
public interface HostOptions {
    /** The {@code host.type} these options were read for, e.g. {@code mobs_ld:wither}. */
    ResourceLocation type();
}
