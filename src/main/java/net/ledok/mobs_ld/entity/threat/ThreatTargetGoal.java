package net.ledok.mobs_ld.entity.threat;

import net.ledok.mobs_ld.config.ThreatConfig;
import net.ledok.mobs_ld.mob.LdMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Targets the highest-threat valid holder on the mob's {@link ThreatTable}: a player or a player-owned summon
 * (see {@link ThreatEvents}). Runs at a higher priority than
 * the vanilla target goals, so as soon as anyone builds threat (by dealing damage, or via a taunt effect)
 * this takes over target selection. When the table is empty it stands down and lets
 * {@code NearestAttackableTargetGoal} provide the initial "nobody's hit me yet" aggro.
 */
public class ThreatTargetGoal extends Goal {
    private final Mob mob;
    private double decayRemainder = 0.0;

    public ThreatTargetGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    /** Every mob carries a threat table (see MobThreatMixin), so this cast always holds. */
    private ThreatTable table() {
        return ((ThreatHolder) mob).getThreatTable();
    }

    @Override
    public boolean canUse() {
        return resolveTop() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return resolveTop() != null;
    }

    @Override
    public void start() {
        mob.setTarget(resolveTop());
    }

    @Override
    public void stop() {
        // We stand down when the table is empty or every holder became invalid — dead, out of range, or
        // stealthed (Rogues). Match vanilla TargetGoal semantics and drop the target so the attack/movement
        // goals stop chasing; unlike vanilla our goal extends Goal, so nothing else clears it for us.
        mob.setTarget(null);
    }

    @Override
    public void tick() {
        pruneAndDecay();
        LivingEntity top = resolveTop();
        if (top != null && top != mob.getTarget()) {
            mob.setTarget(top);
        }
    }

    /** Highest-threat holder that is loaded in this level, attackable and within follow range, else null. */
    private LivingEntity resolveTop() {
        if (!(mob.level() instanceof ServerLevel server)) {
            return null;
        }
        ThreatTable table = table();
        if (table.isEmpty()) {
            return null;
        }
        // Most recent valid taunt/provoke hard-locks the mob onto its caster, overriding raw threat. Falls
        // through to threat if no controller is valid (e.g. they all moved out of range).
        UUID controller = ThreatEffects.lockedController(table, server, uuid -> isValidTarget(server, uuid));
        if (controller != null) {
            return holder(server, controller);
        }
        UUID id = table.topHolder(uuid -> isValidTarget(server, uuid));
        return id == null ? null : holder(server, id);
    }

    /** The player or summon behind a threat entry, if it is loaded in this level. */
    private static LivingEntity holder(ServerLevel server, UUID uuid) {
        return server.getEntity(uuid) instanceof LivingEntity living ? living : null;
    }

    private boolean isValidTarget(ServerLevel server, UUID uuid) {
        LivingEntity holder = holder(server, uuid);
        // mob.canAttack covers spectator/creative players, peaceful difficulty and invulnerable summons.
        if (holder == null || !holder.isAlive() || !mob.canAttack(holder) || StealthCompat.isHidden(holder)) {
            return false;
        }
        if (mob instanceof LdMob ldMob && ldMob.combat().isUnreachable(holder)) {
            return false; // e.g. on high ground: aggro passes to someone it can actually fight
        }
        double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        return mob.distanceToSqr(holder) <= range * range;
    }

    private void pruneAndDecay() {
        if (!(mob.level() instanceof ServerLevel server)) {
            return;
        }
        ThreatTable table = table();
        // Drop holders no longer in this level (logged out, changed dimension, despawned or unloaded summons);
        // their threat is meaningless until they return.
        table.prune(uuid -> server.getEntity(uuid) == null);

        double perSecond = ThreatConfig.get().threatDecayPerSecond;
        if (perSecond > 0.0) {
            decayRemainder += perSecond / 20.0;
            if (decayRemainder >= 1.0) {
                double whole = Math.floor(decayRemainder);
                table.decay(whole);
                decayRemainder -= whole;
            }
        }
    }
}
