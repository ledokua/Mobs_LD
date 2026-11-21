package net.ledok.mobs_ld.mob.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /mobs_ld damage audit}: records every hit on a player while it runs, to size mob attacks from data. A hit is
 * keyed by who deals it as DD Loot Stage sees it (the dealer: the attacker, else a living direct source, else nobody),
 * the dealer's entity type, the damage type, the direct source's entity type and the Mobs_LD ability the dealer is
 * running. Per key: how often it landed or was refused, the raw amount (as {@code LivingEntity.hurt} receives it, so
 * after a player's difficulty scaling and before any {@code loot_stage:damage_dealt}, Arenas tier, armour or
 * absorption), the raw amount as a share of the player's max health, the health (with absorption) it really took, the
 * dealer's attack_damage and damage_dealt, and for hits with no dealer the living non-players nearest to the player.
 * Server thread only; costs one boolean check per hit while it is off.
 */
public final class DamageAudit {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final TagKey<DamageType> UNSCALED =
            TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("loot_stage", "unscaled"));
    /** A hit with no dealer names the living non-players this close to the player as suspects. */
    private static final double SUSPECT_RANGE = 16.0;
    private static final String NONE = "-";

    private static boolean recording;
    private static LocalDateTime startedAt;
    private static final Map<Key, Stats> STATS = new HashMap<>();

    private DamageAudit() {
    }

    public static boolean recording() {
        return recording;
    }

    /** Starts a fresh recording; false if one is already running. */
    public static boolean start() {
        if (recording) {
            return false;
        }
        STATS.clear();
        startedAt = LocalDateTime.now();
        recording = true;
        return true;
    }

    /** Stops the recording and writes it; null if none ran. */
    public static Path stop(MinecraftServer server) throws IOException {
        if (!recording) {
            return null;
        }
        recording = false;
        return dump(server);
    }

    /** The number of hits recorded so far. */
    public static int hits() {
        return STATS.values().stream().mapToInt(stats -> stats.landed + stats.refused).sum();
    }

    /** {@link #stop} for the server going down: a running recording is not lost. */
    public static void onServerStopping(MinecraftServer server) {
        try {
            Path file = stop(server);
            if (file != null) {
                LOGGER.info("Mobs_LD damage audit written to {}", file);
            }
        } catch (IOException e) {
            LOGGER.error("Mobs_LD damage audit could not be written", e);
        }
    }

    /**
     * One hit on {@code player}: {@code raw} is what {@code hurt} was called with, {@code shaped} what its attack
     * profile made of it, {@code landed} what it returned and {@code taken} the health plus absorption it removed.
     */
    public static void record(ServerPlayer player, DamageSource source, float raw, float shaped, boolean landed,
                              float taken) {
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        LivingEntity dealer = attacker instanceof LivingEntity living ? living
                : direct instanceof LivingEntity living ? living : null;
        String kind;
        if (dealer == null || dealer == player || dealer instanceof Player) {
            // Players and self-inflicted damage are not mob damage: DD Loot Stage leaves them alone too.
            kind = dealer == null ? "none" : "player";
            dealer = null;
        } else {
            kind = dealer == attacker ? "attacker" : "direct";
        }
        String ability = dealer == null ? null : MobCombat.abilityOf(dealer);
        String damageType = source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("?");
        Key key = new Key(kind, dealer == null ? NONE : typeId(dealer), damageType,
                direct == null ? NONE : typeId(direct), ability == null ? NONE : ability);
        Stats stats = STATS.computeIfAbsent(key, k -> new Stats(source.is(UNSCALED)));
        if (!landed) {
            stats.refused++;
            return;
        }
        stats.landed++;
        double share = raw / Math.max(1.0F, player.getMaxHealth());
        stats.rawMin = Math.min(stats.rawMin, raw);
        stats.rawMax = Math.max(stats.rawMax, raw);
        stats.rawSum += raw;
        stats.shapedSum += shaped;
        if (shaped != raw) {
            stats.shapedCount++;
        }
        stats.shareMax = Math.max(stats.shareMax, share);
        stats.shareSum += share;
        stats.takenSum += taken;
        stats.takenMax = Math.max(stats.takenMax, taken);
        stats.targetMaxHealthSum += player.getMaxHealth();
        if (dealer != null) {
            AttributeInstance attack = dealer.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attack != null) {
                stats.attackSum += attack.getValue();
                stats.attackCount++;
            }
            AttributeInstance dealt = damageDealt().map(dealer::getAttribute).orElse(null);
            if (dealt != null) {
                stats.dealtSum += dealt.getValue();
                stats.dealtCount++;
            } else {
                stats.dealtMissing++;
            }
        } else if (kind.equals("none")) {
            AABB around = player.getBoundingBox().inflate(SUSPECT_RANGE);
            for (LivingEntity near : player.level().getEntitiesOfClass(LivingEntity.class, around,
                    entity -> entity.isAlive() && !(entity instanceof Player))) {
                stats.suspects.merge(typeId(near), 1, Integer::sum);
            }
        }
    }

    /** Writes what was recorded so far to {@code mobs_ld/damage_audit/<time>.json} in the server folder. */
    public static Path dump(MinecraftServer server) throws IOException {
        List<Map.Entry<Key, Stats>> rows = new ArrayList<>(STATS.entrySet());
        rows.sort(Comparator.comparingInt((Map.Entry<Key, Stats> row) -> row.getValue().landed).reversed());
        JsonObject root = new JsonObject();
        root.addProperty("started", startedAt == null ? NONE : startedAt.toString());
        root.addProperty("written", LocalDateTime.now().toString());
        root.addProperty("recording", recording);
        root.addProperty("hits", hits());
        root.addProperty("note", "raw = the amount LivingEntity.hurt received (after a player's difficulty scaling, "
                + "before any Mobs_LD attack profile, loot_stage:damage_dealt, Arenas tier, armour, absorption); "
                + "profiled = after the attack profile; taken = health + absorption removed; share = raw / target max "
                + "health");
        JsonArray list = new JsonArray();
        for (Map.Entry<Key, Stats> row : rows) {
            list.add(row.getValue().toJson(row.getKey()));
        }
        root.add("groups", list);
        Path file = server.getServerDirectory().resolve("mobs_ld").resolve("damage_audit")
                .resolve(LocalDateTime.now().format(STAMP) + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(root));
        return file;
    }

    /**
     * Living entity types whose default attributes lack {@code loot_stage:damage_dealt} (players aside), so DD Loot
     * Stage cannot scale what they deal; written to {@code mobs_ld/damage_audit/attributes_<time>.json}. Empty when
     * the attribute is not installed at all.
     */
    public static Missing missingDamageDealt(MinecraftServer server) throws IOException {
        Optional<Holder.Reference<Attribute>> attribute = damageDealt();
        List<String> missing = new ArrayList<>();
        int living = 0;
        if (attribute.isPresent()) {
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                if (type == EntityType.PLAYER || !hasSupplier(type)) {
                    continue;
                }
                living++;
                @SuppressWarnings("unchecked")
                EntityType<? extends LivingEntity> livingType = (EntityType<? extends LivingEntity>) type;
                if (!DefaultAttributes.getSupplier(livingType).hasAttribute(attribute.get())) {
                    missing.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
                }
            }
        }
        missing.sort(null);
        JsonObject root = new JsonObject();
        root.addProperty("attribute_installed", attribute.isPresent());
        root.addProperty("living_types", living);
        JsonArray list = new JsonArray();
        missing.forEach(list::add);
        root.add("missing_damage_dealt", list);
        Path file = server.getServerDirectory().resolve("mobs_ld").resolve("damage_audit")
                .resolve("attributes_" + LocalDateTime.now().format(STAMP) + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(root));
        return new Missing(attribute.isPresent(), living, missing, file);
    }

    @SuppressWarnings("unchecked")
    private static boolean hasSupplier(EntityType<?> type) {
        try {
            return DefaultAttributes.hasSupplier((EntityType<? extends LivingEntity>) type);
        } catch (ClassCastException e) {
            return false;
        }
    }

    private static Optional<Holder.Reference<Attribute>> damageDealt() {
        return BuiltInRegistries.ATTRIBUTE.getHolder(SummonStats.DAMAGE_DEALT);
    }

    private static String typeId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    /** What {@link #missingDamageDealt} found and where it wrote it. */
    public record Missing(boolean installed, int livingTypes, List<String> types, Path file) {
    }

    private record Key(String dealerKind, String dealer, String damageType, String direct, String ability) {
    }

    private static final class Stats {
        private final boolean unscaled;
        private int landed;
        private int refused;
        private double rawMin = Double.MAX_VALUE;
        private double rawMax;
        private double rawSum;
        private double shapedSum;
        private int shapedCount;
        private double shareMax;
        private double shareSum;
        private double takenSum;
        private double takenMax;
        private double targetMaxHealthSum;
        private double attackSum;
        private int attackCount;
        private double dealtSum;
        private int dealtCount;
        private int dealtMissing;
        private final Map<String, Integer> suspects = new LinkedHashMap<>();

        private Stats(boolean unscaled) {
            this.unscaled = unscaled;
        }

        private JsonObject toJson(Key key) {
            JsonObject row = new JsonObject();
            row.addProperty("dealer_kind", key.dealerKind());
            row.addProperty("dealer", key.dealer());
            row.addProperty("damage_type", key.damageType());
            row.addProperty("direct", key.direct());
            row.addProperty("ability", key.ability());
            row.addProperty("unscaled_type", unscaled);
            row.addProperty("landed", landed);
            row.addProperty("refused", refused);
            if (landed > 0) {
                row.addProperty("raw_min", round(rawMin));
                row.addProperty("raw_avg", round(rawSum / landed));
                row.addProperty("raw_max", round(rawMax));
                if (shapedCount > 0) {
                    row.addProperty("profiled_hits", shapedCount);
                    row.addProperty("profiled_avg", round(shapedSum / landed));
                }
                row.addProperty("share_avg", round(shareSum / landed));
                row.addProperty("share_max", round(shareMax));
                row.addProperty("taken_avg", round(takenSum / landed));
                row.addProperty("taken_max", round(takenMax));
                row.addProperty("target_max_health_avg", round(targetMaxHealthSum / landed));
            }
            if (attackCount > 0) {
                row.addProperty("dealer_attack_damage_avg", round(attackSum / attackCount));
            }
            if (dealtCount > 0) {
                row.addProperty("dealer_damage_dealt_avg", round(dealtSum / dealtCount));
            }
            if (dealtMissing > 0) {
                row.addProperty("dealer_without_damage_dealt", dealtMissing);
            }
            if (!suspects.isEmpty()) {
                JsonObject near = new JsonObject();
                suspects.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(8).forEach(entry -> near.addProperty(entry.getKey(), entry.getValue()));
                row.add("suspects_within_16", near);
            }
            return row;
        }

        private static double round(double value) {
            return Math.round(value * 1000.0) / 1000.0;
        }
    }
}
