package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.registry.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Adds of a summon with a {@code minion_link} (see SummonEvent.MinionLink). Each carries the tag
 * {@code mobs_ld_linked_to:<summoner uuid>} (set before it enters the level, so it survives saves); its summoner keeps
 * the link it was raised under ({@link MobCombat#trackLinkedSummon}). When such an add dies (a real death, from
 * Fabric's AFTER_DEATH; a discarded add never gets there) its summoner takes the link's {@code death_damage} as
 * {@code mobs_ld:minion_link} damage credited to the add's killer, and once no linked add is left it takes the
 * {@code all_dead} multiplier until it summons linked adds again.
 */
public final class MinionLinks {
    public static final String LINK_TAG_PREFIX = "mobs_ld_linked_to:";
    /** Particles on an exposed summoner this often (ticks). */
    static final int FX_INTERVAL = 10;
    static final String EXPOSED_KEY = "mobs_ld.minion_link.exposed";
    private static final String EXPOSED_FALLBACK = "All minions are dead: %s takes %s%% damage!";

    private MinionLinks() {
    }

    /** Links {@code add} to {@code summoner} (call before adding it to the level). */
    public static void mark(Entity add, Entity summoner) {
        add.addTag(LINK_TAG_PREFIX + summoner.getUUID());
    }

    /** The summoner {@code entity} is linked to, or null if it is not a linked add. */
    static UUID summoner(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(LINK_TAG_PREFIX)) {
                try {
                    return UUID.fromString(tag.substring(LINK_TAG_PREFIX.length()));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** {@code entity} died: if it is a linked add, its summoner (when loaded and still counting it) hears of it. */
    static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        UUID id = summoner(entity);
        Entity owner = id == null ? null : level.getEntity(id);
        MobCombat combat = owner == null ? null : CombatHost.of(owner);
        if (combat != null) {
            combat.onLinkedSummonDeath(entity, source);
        }
    }

    /**
     * Who gets the credit for killing {@code add}: whoever the killing blow names, else whoever last hurt it; never
     * the summoner itself or one of its own adds (a stray skull, a minion's swing): then nobody.
     */
    static LivingEntity killer(LivingEntity add, DamageSource source, Mob summoner, Predicate<Entity> ownAdd) {
        LivingEntity killer = source.getEntity() instanceof LivingEntity living ? living : add.getKillCredit();
        if (killer == null || killer == add || killer == summoner || ownAdd.test(killer)) {
            return null;
        }
        return killer;
    }

    /** The summoner's share of a killed add, credited to {@code killer} (may be null). */
    static DamageSource damage(ServerLevel level, LivingEntity killer) {
        return level.damageSources().source(ModDamageTypes.MINION_LINK, killer, killer);
    }

    /** "+50" for a multiplier of 1.5. */
    static String percent(double multiplier) {
        long percent = Math.round((multiplier - 1.0) * 100.0);
        return percent >= 0 ? "+" + percent : Long.toString(percent);
    }

    /** All linked adds are dead: tells the fighters, a shield-break sound and a burst of sparks on the summoner. */
    static void announce(Mob summoner, ServerLevel level, double multiplier) {
        Component text = Component.translatableWithFallback(EXPOSED_KEY, EXPOSED_FALLBACK, summoner.getDisplayName(),
                percent(multiplier));
        for (Player player : Fighters.of(summoner)) {
            player.displayClientMessage(text, true);
        }
        level.playSound(null, summoner.getX(), summoner.getY(), summoner.getZ(), SoundEvents.SHIELD_BREAK,
                SoundSource.HOSTILE, 2.0F, 0.6F);
        sparks(summoner, level, 40);
    }

    /** While exposed: a few sparks every {@value #FX_INTERVAL} ticks. */
    static void tickFx(Mob summoner, ServerLevel level) {
        sparks(summoner, level, 6);
    }

    private static void sparks(Mob summoner, ServerLevel level, int count) {
        double width = summoner.getBbWidth() * 0.6;
        double height = summoner.getBbHeight();
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, summoner.getX(), summoner.getY() + height * 0.5,
                summoner.getZ(), count, width, height * 0.4, width, 0.15);
    }
}
