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
 * Mob damage caps, persisted to {@code config/mobs_ld_damage.json} (loaded lazily, rewritten so new fields backfill).
 * Off by default: then only an attack profile's own {@code cap_of_target_max_health} caps anything.
 */
public final class DamageConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static DamageConfig instance;

    /** Caps every mob's hits on players at the shares below (an attack profile's own cap still wins). */
    public boolean capsEnabled = false;

    /** One hit of a normal mob takes at most this share of the player's max health, before armour. */
    public double capOfTargetMaxHealth = 0.40;

    /** The same for bosses (#c:bosses, or a Mobs_LD behaviour with a boss block). */
    public double bossCapOfTargetMaxHealth = 0.60;

    public static DamageConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** Reads the file again ({@code /mobs_ld damage config}). */
    public static DamageConfig reload() {
        instance = load();
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(MobsLdMod.MOD_ID + "_damage.json");
    }

    private static DamageConfig load() {
        Path path = path();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                DamageConfig cfg = GSON.fromJson(reader, DamageConfig.class);
                if (cfg != null) {
                    cfg.save();
                    return cfg;
                }
            } catch (Exception ignored) {
                // fall through to defaults
            }
        }
        DamageConfig cfg = new DamageConfig();
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

    private DamageConfig() {
    }
}
