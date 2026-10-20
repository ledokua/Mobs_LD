package net.ledok.mobs_ld.entity.threat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.config.ThreatConfig;
import net.ledok.mobs_ld.registry.ModAttributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/** Wires player damage into the threat tables. Registered once from the mod initializer. */
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
            if (source.getEntity() instanceof Player player && !player.isSpectator()) {
                double threat = damageTaken * ThreatConfig.get().threatPerDamage * threatMultiplier(player);
                holder.getThreatTable().add(player.getUUID(), threat);
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

    /** Per-player threat scaling from the {@code mobs_ld:threat_multiplier} attribute (1.0 if absent). */
    private static double threatMultiplier(Player player) {
        AttributeInstance instance = player.getAttribute(ModAttributes.THREAT_MULTIPLIER);
        return instance == null ? 1.0 : instance.getValue();
    }
}
