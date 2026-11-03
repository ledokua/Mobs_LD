package net.ledok.mobs_ld.mob.combat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code listen} windows open right now (normally none), so a player damaging a listening mob or one of its adds,
 * or using an item, is reported to it from the game's events (each pays one empty-list check otherwise). Server
 * thread only. A window is removed when it ends, when its run is cut off, and whenever its mob dies, resets, unloads
 * or is removed ({@link #closeFor}), so the list never keeps a mob or a level alive.
 */
public final class ListenWindows {
    private static final List<Window> OPEN = new ArrayList<>();

    private ListenWindows() {
    }

    /** An open window: its mob, whether it still listens, and what to do with what a player did. */
    public interface Window {
        Mob mob();

        boolean listening();

        /** Game time it opened. */
        long openedAt();

        void damagedBy(Player player);

        /** {@code player} used {@code stack}: decided on the run's next tick, once the use has happened. */
        void usedItem(Player player, ItemStack stack);
    }

    public static void open(Window window) {
        OPEN.removeIf(open -> !open.listening() || open.mob().isRemoved()); // safety net; closeFor normally did it
        OPEN.add(window);
    }

    public static void close(Window window) {
        OPEN.remove(window);
    }

    /** Drops every window of {@code mob} (it died, reset, unloaded or was removed). */
    public static void closeFor(Mob mob) {
        if (!OPEN.isEmpty()) {
            OPEN.removeIf(window -> window.mob() == mob);
        }
    }

    /** The server stopped: nothing may outlive its levels. */
    public static void clear() {
        OPEN.clear();
    }

    /**
     * {@code victim} took damage from {@code source}: tells every window listening on it (or on its summoner), if a
     * player dealt it by an action during the window (see {@link ListenRules#countsAsAction}).
     */
    public static void onDamage(LivingEntity victim, DamageSource source) {
        if (OPEN.isEmpty() || !(source.getEntity() instanceof Player player)) {
            return;
        }
        OPEN.removeIf(window -> !window.listening() || window.mob().isRemoved());
        Entity direct = source.getDirectEntity();
        long now = victim.level().getGameTime();
        for (Window window : List.copyOf(OPEN)) {
            Mob mob = window.mob();
            MobCombat combat = CombatHost.of(mob);
            if ((victim == mob || (combat != null && combat.ownsSummon(victim)))
                    && ListenRules.countsAsAction(direct != null, direct == player, source.is(DamageTypes.THORNS),
                    direct == null ? 0 : direct.tickCount, now - window.openedAt())) {
                window.damagedBy(player);
            }
        }
    }

    /** {@code player} uses the item in {@code hand} (Fabric's use callback, before the use happens). */
    public static void onUseItem(Player player, InteractionHand hand) {
        if (OPEN.isEmpty()) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return;
        }
        for (Window window : List.copyOf(OPEN)) {
            if (window.listening() && !window.mob().isRemoved()) {
                window.usedItem(player, stack);
            }
        }
    }
}
