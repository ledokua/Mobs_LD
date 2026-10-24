package net.ledok.mobs_ld.mob.combat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Entity;

/**
 * Death and removal of mobs that run the ability engine, from Fabric's events (registered once from the mod
 * initializer). The tick, damage, save and viewer hooks live in mixins (MobCombatHostMixin, LivingEntityCombatMixin,
 * EntityCombatViewersMixin).
 */
public final class CombatEvents {
    private CombatEvents() {
    }

    public static void register() {
        // Fired once per real death (from LivingEntity.die), before the body starts falling.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            MobCombat combat = CombatHost.of(entity);
            if (combat != null) {
                combat.onDeath();
            }
        });
        // Every way out of the level. Destroyed (killed, discarded): clean up at once. Otherwise (chunk unload, which
        // fires this while the level streams over the chunk's entities, before any removal; dimension change) touch
        // no other entity now.
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            MobCombat combat = CombatHost.of(entity);
            if (combat == null) {
                return;
            }
            Entity.RemovalReason reason = entity.getRemovalReason();
            if (reason != null && reason.shouldDestroy()) {
                combat.onRemoved();
            } else {
                combat.onUnloaded();
            }
        });
    }
}
