package net.ledok.mobs_ld.mob.combat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Damage, death and removal of mobs that run the ability engine, from Fabric's events (registered once from the mod
 * initializer). The tick, damage scaling, save and viewer hooks live in mixins (MobCombatHostMixin,
 * LivingEntityCombatMixin, EntityCombatViewersMixin).
 */
public final class CombatEvents {
    private CombatEvents() {
    }

    public static void register() {
        // A mob that hit a players-only boss may fight it back for a while (boss.engage_on: players); damage dealt to
        // it counts for top_damage for 10 s; a listening mob hears whoever damaged it or its adds.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            MobCombat combat = CombatHost.of(entity);
            if (combat != null && source.getEntity() instanceof LivingEntity attacker && attacker != entity) {
                if (!(attacker instanceof Player)) {
                    combat.noteHurtBy(attacker);
                }
                combat.noteDamage(attacker, damageTaken);
            }
            ListenWindows.onDamage(entity, source);
        });
        // A listening mob hears instant item uses (a pearl, a snowball, a splash potion); nothing to do without one.
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide()) {
                ListenWindows.onUseItem(player, hand);
            }
            return InteractionResultHolder.pass(ItemStack.EMPTY);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ListenWindows.clear());
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
