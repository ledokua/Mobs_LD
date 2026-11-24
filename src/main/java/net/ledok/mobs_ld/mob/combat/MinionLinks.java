package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.registry.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

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
    /** Particles along active barrier or siphon tethers this often (ticks). */
    static final int TETHER_INTERVAL = 8;
    static final String EXPOSED_KEY = "mobs_ld.minion_link.exposed";
    private static final String EXPOSED_FALLBACK = "All minions are dead: %s takes %s%% damage!";
    static final String SHIELD_BLOCKED_KEY = "mobs_ld.shield.blocked_by_adds";
    private static final String SHIELD_BLOCKED_FALLBACK = "%s is shielded — destroy its summoned pillars first!";

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

    /** A hit bounced off an add-driven invulnerability barrier: chime, deflect sparks, and a hint to the attacker. */
    static void announceShieldBlocked(Mob boss, ServerLevel level, DamageSource source) {
        level.playSound(null, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.8F, 0.72F);
        double width = boss.getBbWidth() * 0.65;
        double height = boss.getBbHeight();
        level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + height * 0.5, boss.getZ(),
                12, width, height * 0.35, width, 0.08);
        if (source.getEntity() instanceof Player player) {
            Component text = Component.translatableWithFallback(SHIELD_BLOCKED_KEY, SHIELD_BLOCKED_FALLBACK,
                    boss.getDisplayName());
            player.displayClientMessage(text, true);
        }
    }

    /** Sends a clear, lightweight particle arc from a pillar/pyre into the boss it is shielding or healing. */
    static void drawTether(ServerLevel level, Entity from, Mob to, SimpleParticleType particle) {
        Vec3 start = from.position().add(0.0, from.getBbHeight() * 0.72, 0.0);
        Vec3 end = to.position().add(0.0, to.getBbHeight() * 0.54, 0.0);
        // Beacon shimmer atop the active pillar/pyre so players can spot it immediately across the arena.
        level.sendParticles(particle, from.getX(), from.getY() + from.getBbHeight() + 0.35, from.getZ(),
                2, 0.14, 0.16, 0.14, 0.015);
        int steps = 9;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (steps + 1);
            double x = start.x + (end.x - start.x) * t;
            double y = start.y + (end.y - start.y) * t + Math.sin(t * Math.PI) * 0.85;
            double z = start.z + (end.z - start.z) * t;
            level.sendParticles(particle, x, y, z, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /** Subtle shimmering ward ring around a boss currently shielded by live pillars. */
    static void tickShieldAura(Mob boss, ServerLevel level) {
        double radius = Math.max(1.2, boss.getBbWidth() * 0.75);
        double y = boss.getY() + boss.getBbHeight() * 0.5;
        double phase = (level.getGameTime() % 40) * (Math.PI * 2.0 / 40.0);
        for (int i = 0; i < 4; i++) {
            double angle = phase + i * (Math.PI * 0.5);
            level.sendParticles(ParticleTypes.END_ROD,
                    boss.getX() + Math.cos(angle) * radius, y, boss.getZ() + Math.sin(angle) * radius,
                    1, 0.04, 0.15, 0.04, 0.01);
        }
    }

    private static void sparks(Mob summoner, ServerLevel level, int count) {
        double width = summoner.getBbWidth() * 0.6;
        double height = summoner.getBbHeight();
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, summoner.getX(), summoner.getY() + height * 0.5,
                summoner.getZ(), count, width, height * 0.4, width, 0.15);
    }
}
