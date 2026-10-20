package net.ledok.mobs_ld.entity.threat;

import net.ledok.mobs_ld.config.ThreatConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Shared logic behind taunt/provoke, whether it comes from our effects or from Spell Engine's AGGRO impact.
 * Two concerns:
 * <ul>
 *   <li><b>Threat numbers</b> — a taunt/provoke raises the caster above the top damage-dealer, a value that
 *       survives after the control ends.</li>
 *   <li><b>Targeting control</b> — each affected mob carries per-player control stamps in its own
 *       {@link ThreatTable}, and targets the most recent unexpired one. A taunt stamp is refreshed every tick
 *       while the caster holds the effect and stays in range; a provoke stamp lasts its override window.
 *       Stamps take a fresh monotonic sequence, so "most recent wins": taunt &rarr; provoke overrides &rarr;
 *       provoke window lapses &rarr; the still-active taunt reclaims. A taunt keeps its sequence while it is kept
 *       alive (re-applied before it lapses); only a taunt started after a gap out-ranks a live provoke.</li>
 * </ul>
 * Because stamps live on the mob, a provoke can target one mob ({@link #provokeMob}) or an area
 * ({@link #applyProvoke}), and {@link #fadeMob} can drop a player from one mob.
 */
public final class ThreatEffects {
    /** Taunt stamps lapse this long after the last per-tick refresh — i.e. once the effect ends or the mob leaves range. */
    private static final long TAUNT_GRACE_TICKS = 5L;

    /** Each player's current taunt: its control sequence and the game-time it last ticked. */
    private static final Map<UUID, TauntMark> TAUNT = new HashMap<>();
    /** Monotonic application order shared by both control types, so their recency is directly comparable. */
    private static long controlSequence = 0L;

    private record TauntMark(long sequence, long lastTick) {
    }

    private ThreatEffects() {
    }

    /** Taunt strength for an effect amplifier. Amplifier 0 (level 1) -> 1 + tauntStep*1, etc. */
    public static double tauntMultiplier(int amplifier) {
        return 1.0 + ThreatConfig.get().tauntMultiplierStep * (amplifier + 1);
    }

    /** Provoke strength for an effect amplifier — stronger than taunt so it out-threats a locked taunter. */
    public static double provokeMultiplier(int amplifier) {
        return 1.0 + ThreatConfig.get().provokeMultiplierStep * (amplifier + 1);
    }

    /**
     * Records that {@code entity} just applied a duration taunt. Vanilla calls this on every {@code addEffect},
     * refreshes included, so a taunt that is still ticking keeps its sequence: a channel re-applying it each tick
     * must not out-rank a newer provoke on every refresh. Only a taunt starting after a gap takes a fresh sequence.
     */
    public static void markTaunt(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel server) || !(entity instanceof Player player)) {
            return;
        }
        long now = server.getGameTime();
        TauntMark mark = TAUNT.get(player.getUUID());
        if (mark == null || now - mark.lastTick() > TAUNT_GRACE_TICKS) {
            TAUNT.put(player.getUUID(), new TauntMark(++controlSequence, now));
        }
    }

    /** Duration taunt's per-tick work: boost the caster on every nearby mob and refresh its taunt stamp there. */
    public static void applyTaunt(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel server) || !(entity instanceof Player player)) {
            return;
        }
        UUID selfId = player.getUUID();
        long now = server.getGameTime();
        TauntMark mark = TAUNT.get(selfId);
        long sequence = mark != null ? mark.sequence() : ++controlSequence;
        TAUNT.put(selfId, new TauntMark(sequence, now));
        long expiry = now + TAUNT_GRACE_TICKS;
        for (Mob mob : nearbyThreatMobs(server, player)) {
            ThreatTable table = ((ThreatHolder) mob).getThreatTable();
            boost(table, selfId, tauntMultiplier(amplifier), server);
            table.controlTaunt(selfId, sequence, expiry);
        }
    }

    /** Instant area provoke: {@link #provokeMob} on every threat mob within {@code tauntRadius} of the caster. */
    public static void applyProvoke(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel server) || !(entity instanceof Player player)) {
            return;
        }
        long sequence = ++controlSequence;
        for (Mob mob : nearbyThreatMobs(server, player)) {
            provoke(mob, player, amplifier, sequence, server);
        }
    }

    /**
     * Single-target provoke: out-threat everyone on {@code mob} at the provoke multiplier and take a fresh control
     * stamp with a finite override window, so it overrides even an active taunt until the window lapses.
     */
    public static void provokeMob(Mob mob, Player caster, int amplifier) {
        if (mob.level() instanceof ServerLevel server) {
            provoke(mob, caster, amplifier, ++controlSequence, server);
        }
    }

    /** Drop {@code player}'s threat and control on {@code mob}, so it moves to the next player on its table. */
    public static void fadeMob(Mob mob, Player player) {
        if (!mob.level().isClientSide()) {
            ((ThreatHolder) mob).getThreatTable().remove(player.getUUID());
        }
    }

    private static void provoke(Mob mob, Player caster, int amplifier, long sequence, ServerLevel server) {
        UUID selfId = caster.getUUID();
        ThreatTable table = ((ThreatHolder) mob).getThreatTable();
        boost(table, selfId, provokeMultiplier(amplifier), server);
        table.controlProvoke(selfId, sequence, server.getGameTime() + ThreatConfig.get().provokeOverrideTicks);
    }

    /**
     * UUID of the player whose taunt/provoke should hold this mob right now: among its controllers that pass
     * {@code valid}, the one with the most recent unexpired stamp, or null if nobody is controlling it.
     */
    public static UUID lockedController(ThreatTable table, ServerLevel server, Predicate<UUID> valid) {
        long now = server.getGameTime();
        UUID best = null;
        long bestSeq = Long.MIN_VALUE;
        for (UUID id : table.controllers()) {
            long seq = table.controlSequence(id, now);
            if (seq > bestSeq && valid.test(id)) {
                bestSeq = seq;
                best = id;
            }
        }
        return best;
    }

    /** Raise {@code selfId} to {@code top-of-non-controllers * mult}, never lowering what it already held. */
    private static void boost(ThreatTable table, UUID selfId, double mult, ServerLevel server) {
        long now = server.getGameTime();
        // Exclude other controllers (taunters / active provokers) so simultaneous effects don't inflate each other.
        double othersTop = table.topThreatExcluding(uuid -> uuid.equals(selfId) || table.isControlling(uuid, now));
        // At least 1 so a boost off a cold table still produces real aggro.
        double value = Math.max(Math.max(othersTop * mult, 1.0), table.get(selfId));
        table.set(selfId, value);
    }

    private static List<Mob> nearbyThreatMobs(ServerLevel server, LivingEntity center) {
        double radius = ThreatConfig.get().tauntRadius;
        AABB box = center.getBoundingBox().inflate(radius);
        return server.getEntitiesOfClass(Mob.class, box, m -> m instanceof ThreatHolder && m.isAlive());
    }
}
