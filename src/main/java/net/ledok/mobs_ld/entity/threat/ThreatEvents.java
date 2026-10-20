package net.ledok.mobs_ld.entity.threat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.config.ThreatConfig;
import net.ledok.mobs_ld.registry.ModAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/**
 * Wires damage from players and their summons into the threat tables. Registered once from the mod initializer.
 *
 * <p>Summons are MMO-style threat holders: a player-owned entity that can be attacked (a tamed wolf, a Spell
 * Engine spirit wolf or frost elemental) builds its own threat and can pull and tank the mob. A summon that
 * cannot be targeted (Spell Engine's unattackable turrets, which report {@code isInvulnerable()}) would leave the
 * mob chasing nothing, so its threat is credited to its owner instead.
 */
public final class ThreatEvents {
    private static final String MOD_PACKAGE = MobsLdMod.class.getPackageName();

    private ThreatEvents() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (damageTaken <= 0.0f || !(entity instanceof Mob mob) || !(entity instanceof ThreatHolder holder)) {
                return;
            }
            if (!shouldThreatTarget(mob)) {
                return; // mob has no threat goal (e.g. a passive vanilla mob) — don't bother tracking it
            }
            LivingEntity attacker = threatSource(source.getEntity());
            if (attacker != null) {
                double threat = damageTaken * ThreatConfig.get().threatPerDamage * threatMultiplier(attacker);
                holder.getThreatTable().add(attacker.getUUID(), threat);
            }
        });
    }

    /**
     * Whether {@code mob} should run the threat-targeting goal. This mod's own mobs always do; vanilla and
     * other mods' mobs are gated by config. Called once per mob at construction (see MobThreatMixin).
     */
    public static boolean shouldThreatTarget(Mob mob) {
        if (mob.getClass().getName().startsWith(MOD_PACKAGE)) {
            return true; // our own mobs: always threat-aware
        }
        ThreatConfig cfg = ThreatConfig.get();
        if (!cfg.affectVanillaMobs) {
            return false;
        }
        return !cfg.vanillaMonstersOnly || mob instanceof Monster;
    }

    /**
     * Who earns the threat for damage dealt by {@code attacker}: the player, a targetable player-owned summon
     * itself, or the owner of an untargetable one. Null for anything else (wild mobs, unowned entities).
     */
    private static LivingEntity threatSource(Entity attacker) {
        if (attacker instanceof Player player) {
            return player.isSpectator() ? null : player;
        }
        if (!ThreatConfig.get().summonThreat
                || !(attacker instanceof LivingEntity summon)
                || !(attacker instanceof OwnableEntity ownable)
                || !(ownable.getOwner() instanceof Player owner)
                || owner.isSpectator()) {
            return null;
        }
        return summon.canBeSeenAsEnemy() ? summon : owner;
    }

    /** Threat scaling from the {@code mobs_ld:threat_multiplier} attribute (1.0 if absent, e.g. on summons). */
    private static double threatMultiplier(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(ModAttributes.THREAT_MULTIPLIER);
        return instance == null ? 1.0 : instance.getValue();
    }
}
