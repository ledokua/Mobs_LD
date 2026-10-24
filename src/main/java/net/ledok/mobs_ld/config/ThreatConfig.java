package net.ledok.mobs_ld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.ledok.mobs_ld.MobsLdMod;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tunables for the aggro/threat system, persisted to {@code config/mobs_ld_threat.json}. Loaded lazily on
 * first access and rewritten so new fields backfill into an existing file.
 */
public final class ThreatConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ThreatConfig instance;

    /** Threat points generated per point of damage a player deals to a threat-tracking mob. */
    public double threatPerDamage = 1.0;

    /**
     * Multiplier step per taunt level: level N snaps the caster to {@code top * (1 + step * N)}.
     * 0.3 -> level 1 = 1.3x, level 2 = 1.6x, level 3 = 1.9x.
     */
    public double tauntMultiplierStep = 0.3;

    /**
     * Multiplier step per provoke level, same formula as the taunt step. 0.5 -> level 1 = 1.5x, level 2 = 2.0x.
     * Provoke is deliberately stronger so a thrown provoke can override and out-threat an active taunt.
     */
    public double provokeMultiplierStep = 0.5;

    /**
     * Ticks a thrown provoke hard-overrides everything (including an active taunt lock). When this window lapses,
     * a still-active taunt automatically reclaims the mob. 20 ticks = 1 second; 60 = 3 seconds.
     */
    public int provokeOverrideTicks = 60;

    /**
     * When true (and Spell Engine is installed), Spell Engine's AGGRO impact feeds the threat system instead of
     * being undone next tick: SET = a single-mob provoke by the caster, CLEAR = the cleared player's threat and
     * control on that mob are dropped, so it moves to the next player.
     */
    public boolean spellEngineAggro = true;

    /** Provoke amplifier used for Spell Engine AGGRO SET (0 = level 1, i.e. {@code 1 + provokeMultiplierStep}). */
    public int spellEngineAggroAmplifier = 0;

    /** Horizontal radius (blocks) around the taunting player within which mobs are provoked. */
    public double tauntRadius = 10.0;

    /** Threat lost per second while a mob is actively threat-targeting (0 = threat never decays). */
    public double threatDecayPerSecond = 0.0;

    /**
     * Hard ceiling on any single holder's threat against a mob. Far above any legitimate damage total, but a
     * safety rail against pathological/exploited values — kept low enough that taunt multiplication never
     * overflows to {@code Infinity}. Raise only if you genuinely run damage numbers near this.
     */
    public double maxThreat = 1.0e12;

    /**
     * MMO-style summon threat. When true, damage from a player-owned entity (tamed wolf, Spell Engine summon)
     * builds threat for that summon itself, so it can pull and tank mobs; an untargetable summon's threat goes to
     * its owner. When false, summon damage generates no threat at all (players only).
     */
    public boolean summonThreat = true;

    /**
     * MMO-style heal threat: threat the healer earns per point of health actually restored (overheal earns nothing).
     * Every mob already fighting the healed player or summon, within {@link #healThreatRadius} of it, adds it to the
     * healer, so a healer keeping the tank up draws attention but never pulls mobs that are not in the fight.
     * 0 turns heal threat off. Sources: Spell Engine heal spells and healing potions thrown or drunk by a player.
     */
    public double threatPerHeal = 0.5;

    /** Blocks around the healed (or buffed) entity within which fighting mobs register the heal or buff. */
    public double healThreatRadius = 32.0;

    /**
     * Threat per effect level (amplifier + 1) for a harmful effect a player or summon puts on a mob (Spell Engine
     * debuffs, splash/lingering potions, tipped arrows). Debuffing a mob is attacking it, so it can pull the mob.
     * Only a new application counts: refreshing an effect that is still running earns nothing. 0 = off.
     */
    public double threatPerDebuff = 10.0;

    /**
     * Threat per effect level for a beneficial effect a player or summon puts on another player or summon, credited
     * by every mob already fighting the buffed one (as for heals). Buffs on oneself (own potions, gear, relics) and
     * refreshes of a running effect earn nothing. 0 = off.
     */
    public double threatPerBuff = 5.0;

    /** When true, vanilla (and other mods') mobs also get the threat-targeting goal. This mod's own mobs always do. */
    public boolean affectVanillaMobs = true;

    /** When {@link #affectVanillaMobs} is on, restrict it to hostile {@code Monster}s so passive/neutral mobs are left alone. */
    public boolean vanillaMonstersOnly = true;

    public static ThreatConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(MobsLdMod.MOD_ID + "_threat.json");
    }

    private static ThreatConfig load() {
        Path path = path();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                ThreatConfig cfg = GSON.fromJson(reader, ThreatConfig.class);
                if (cfg != null) {
                    cfg.save();
                    return cfg;
                }
            } catch (Exception ignored) {
                // fall through to defaults
            }
        }
        ThreatConfig cfg = new ThreatConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException ignored) {
            // config is best-effort; defaults still work in memory
        }
    }

    private ThreatConfig() {
    }
}
