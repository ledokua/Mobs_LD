package net.ledok.mobs_ld.mob.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A behaviour's {@code host} block: how the engine runs on a vanilla (or other mod's) entity type.
 * <pre>{"type": "mobs_ld:wither", "enabled": true, "hover_height": 3.5, ...}</pre>
 * <ul>
 *   <li>{@code enabled} (default true): false hosts nothing, exactly as if the behaviour file were absent, so a
 *       server datapack can switch off a file shipped in the jar;</li>
 *   <li>{@code type}: which options follow, read by the entity type's {@link HostAdapter} (see
 *       {@link HostAdapter#hostType()}); without it the block only carries {@code enabled};</li>
 *   <li>every other key is an option of that type, with vanilla behaviour as each option's default.</li>
 * </ul>
 * A phase may carry a {@code host} block of its own, laid over this one key by key ({@link #withOverride}).
 * Keys this mod does not know are ignored (reported once at reload), so files written for a later version load.
 *
 * @param raw the block as written, kept for merging phase overrides over it
 */
public record HostSpec(boolean enabled, Optional<HostOptions> options, Dynamic<?> raw) {
    private static final Map<ResourceLocation, MapCodec<? extends HostOptions>> TYPES = new LinkedHashMap<>();
    private static final Set<String> COMMON_KEYS = Set.of("type", "enabled");
    private static final MapCodec<Boolean> ENABLED = Codec.BOOL.optionalFieldOf("enabled", true);
    private static final MapCodec<Optional<ResourceLocation>> TYPE = ResourceLocation.CODEC.optionalFieldOf("type");

    /** No host block: hosted (if the file exists), with the adapter's vanilla defaults. */
    public static final HostSpec NONE = new HostSpec(true, Optional.empty(), new Dynamic<>(JsonOps.INSTANCE, new JsonObject()));
    public static final Codec<HostSpec> CODEC = Codec.PASSTHROUGH.comapFlatMap(HostSpec::parse, HostSpec::raw);

    /** Registers the options read under {@code host.type} = {@code type}; call from a mod initializer. */
    public static void registerType(ResourceLocation type, MapCodec<? extends HostOptions> codec) {
        if (TYPES.putIfAbsent(type, codec) != null) {
            throw new IllegalStateException("Host type " + type + " registered twice");
        }
    }

    public static DataResult<HostSpec> parse(Dynamic<?> raw) {
        return ENABLED.codec().parse(raw).flatMap(enabled -> TYPE.codec().parse(raw).flatMap(type -> {
            if (type.isEmpty()) {
                return DataResult.success(new HostSpec(enabled, Optional.empty(), raw));
            }
            MapCodec<? extends HostOptions> codec = TYPES.get(type.get());
            if (codec == null) {
                return DataResult.error(() -> "Unknown host type '" + type.get() + "' (expected one of " + TYPES.keySet() + ")");
            }
            return codec.codec().parse(raw).map(options -> new HostSpec(enabled, Optional.<HostOptions>of(options), raw));
        }));
    }

    /**
     * Options for a phase whose own {@code host} block is {@code override}: this block with the override's keys laid
     * over it (nested objects merge key by key, anything else replaces). A phase cannot change the type, and needs a
     * typed block to merge onto.
     */
    public DataResult<HostOptions> withOverride(Dynamic<?> override) {
        JsonElement over = json(override);
        if (!over.isJsonObject()) {
            return DataResult.error(() -> "A phase's host block must be an object, got " + over);
        }
        if (options.isEmpty()) {
            return DataResult.error(() -> "A phase sets host options, but the behaviour's own host block names no type");
        }
        ResourceLocation type = options.get().type();
        JsonElement base = json(raw);
        JsonObject merged = merge(base.isJsonObject() ? base.getAsJsonObject().deepCopy() : new JsonObject(), over.getAsJsonObject());
        return parse(new Dynamic<>(JsonOps.INSTANCE, merged)).flatMap(spec -> {
            HostOptions phase = spec.options().orElse(null);
            if (phase == null || !phase.type().equals(type)) {
                return DataResult.error(() -> "A phase's host block cannot change the host type " + type);
            }
            return DataResult.success(phase);
        });
    }

    /** Keys in {@code block} that neither every host block nor {@code type}'s options define (for reload warnings). */
    public static List<String> unknownKeys(Dynamic<?> block, ResourceLocation type) {
        JsonElement json = json(block);
        if (!json.isJsonObject()) {
            return List.of();
        }
        Set<String> known = new HashSet<>(COMMON_KEYS);
        MapCodec<? extends HostOptions> codec = type == null ? null : TYPES.get(type);
        if (codec != null) {
            codec.keys(JsonOps.INSTANCE).forEach(key -> known.add(key.getAsString()));
        }
        List<String> unknown = new ArrayList<>();
        for (String key : json.getAsJsonObject().keySet()) {
            if (!known.contains(key)) {
                unknown.add(key);
            }
        }
        return unknown;
    }

    private static JsonElement json(Dynamic<?> dynamic) {
        return dynamic.convert(JsonOps.INSTANCE).getValue();
    }

    private static JsonObject merge(JsonObject into, JsonObject over) {
        for (Map.Entry<String, JsonElement> entry : over.entrySet()) {
            JsonElement existing = into.get(entry.getKey());
            if (existing != null && existing.isJsonObject() && entry.getValue().isJsonObject()) {
                merge(existing.getAsJsonObject(), entry.getValue().getAsJsonObject());
            } else {
                into.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        return into;
    }
}
