package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.ledok.mobs_ld.mob.host.HostAdapter;
import net.ledok.mobs_ld.mob.host.HostAdapters;
import net.ledok.mobs_ld.mob.host.HostOptions;
import net.ledok.mobs_ld.mob.host.HostSpec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads {@code data/<namespace>/mob_behaviour/<path>.json} on every datapack (re)load; the file id is the entity
 * type it drives: one of this mod's data-driven mobs, or any other mob (e.g. {@code minecraft:zombie}). A broken file
 * is reported and skipped (that mob keeps no behaviour) instead of failing the reload, and so is a file whose
 * {@code host.enabled} is false (the off switch). Parts that cannot apply to the entity type (a host block for another
 * adapter, attributes the mob does not have, unknown host keys) are reported and ignored.
 *
 * <p>Mobs pick up a reload through {@link #generation()}: one int compare per tick tells a mob whether to look its
 * behaviour up again, so the many mobs without one pay nothing more.
 */
public final class MobBehaviours extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "mob_behaviour");

    /**
     * Vanilla mobs run by a Brain that ignores goals (it would fight the engine's movement): refused at reload, unless
     * their entity type's host adapter drives the Brain itself ({@link HostAdapter#acceptsBrain()}, the Warden's).
     */
    private static final Set<EntityType<?>> BRAIN_TYPES = Set.of(EntityType.WARDEN,
            EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.BREEZE,
            EntityType.ALLAY, EntityType.AXOLOTL, EntityType.FROG, EntityType.TADPOLE, EntityType.GOAT,
            EntityType.CAMEL, EntityType.SNIFFER, EntityType.ARMADILLO, EntityType.VILLAGER);
    /**
     * The same mobs by class, checked when a mob would attach: also catches other mods' subclasses of them (which
     * have their own entity type, so no adapter accepts their Brain).
     */
    private static final List<Class<?>> BRAIN_CLASSES = List.of(Warden.class,
            AbstractPiglin.class, Hoglin.class, Zoglin.class, Breeze.class, Allay.class, Axolotl.class, Frog.class,
            Tadpole.class, Goat.class, Camel.class, Sniffer.class, Armadillo.class, Villager.class);
    private static final Set<EntityType<?>> WARNED = ConcurrentHashMap.newKeySet();

    private static volatile Map<EntityType<?>, MobBehaviour> byType = Map.of();
    /**
     * The {@code damage_type} ids each loaded file names, checked against the server's damage types once they are
     * known (a reload listener runs before the world's registries can be read).
     */
    private static volatile Map<ResourceLocation, Set<String>> damageTypes = Map.of();
    /** Bumped by every reload; 0 = nothing loaded yet. */
    private static volatile int generation;

    private MobBehaviours() {
        super(new Gson(), "mob_behaviour");
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new MobBehaviours());
        ServerLifecycleEvents.SERVER_STARTED.register(MobBehaviours::checkDamageTypes);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> checkDamageTypes(server));
    }

    /** Reports {@code damage_type} ids in the loaded files that the server does not have (hits then deal their default). */
    private static void checkDamageTypes(MinecraftServer server) {
        Registry<DamageType> registry = server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        damageTypes.forEach((file, ids) -> {
            for (String id : ids) {
                ResourceLocation parsed = ResourceLocation.tryParse(id);
                if (parsed == null || !registry.containsKey(parsed)) {
                    LOGGER.warn("Mob behaviour {}: unknown damage_type {}; those hits deal their default type", file, id);
                }
            }
        });
    }

    /** Every string value of a {@code damage_type} key anywhere in {@code json}. */
    private static void collectDamageTypes(JsonElement json, Set<String> into) {
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> collectDamageTypes(element, into));
        } else if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getKey().equals("damage_type") && entry.getValue().isJsonPrimitive()) {
                    into.add(entry.getValue().getAsString());
                } else {
                    collectDamageTypes(entry.getValue(), into);
                }
            }
        }
    }

    /**
     * The behaviour loaded for {@code type}, whether or not its {@code host.only_tagged} filter lets it apply to a
     * given mob: for type-wide uses ({@code base_attributes} in the constructor). Per mob, use {@link #forMob}.
     */
    public static MobBehaviour get(EntityType<?> type) {
        return byType.get(type);
    }

    /**
     * The behaviour that applies to {@code mob}: its type's, if the file's {@code host.only_tagged} filter passes
     * (see {@link HostSpec#appliesTo}), else null; the same as {@link #get} for files without the filter. A mob that
     * has a combat answers from it (decided on attach and after each reload, see {@link MobCombat#behaviour}); any
     * other mob looks its tags up now.
     */
    public static MobBehaviour forMob(Mob mob) {
        MobCombat combat = CombatHost.of(mob);
        if (combat != null) {
            return combat.behaviour();
        }
        MobBehaviour behaviour = byType.get(mob.getType());
        return behaviour != null && behaviour.host().appliesTo(mob.getTags()) ? behaviour : null;
    }

    /** Changes whenever the loaded behaviours do. */
    public static int generation() {
        return generation;
    }

    /**
     * Whether the engine may attach to {@code mob}; false (with one warning per entity type) for the Ender Dragon and
     * for mob classes whose AI is a Brain, unless the entity type's host adapter drives that Brain
     * ({@link HostAdapter#acceptsBrain()}).
     */
    public static boolean canHost(Mob mob) {
        if (mob instanceof EnderDragon) {
            if (WARNED.add(mob.getType())) {
                LOGGER.warn("Mob behaviour for {} is ignored: its AI never reaches the mob tick, so the ability engine "
                        + "cannot run it", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
            }
            return false;
        }
        if (HostAdapters.get(mob.getType()).acceptsBrain()) {
            return true;
        }
        for (Class<?> refused : BRAIN_CLASSES) {
            if (refused.isInstance(mob)) {
                if (WARNED.add(mob.getType())) {
                    LOGGER.warn("Mob behaviour for {} is ignored: {} is not driven by goals, so the ability engine "
                            + "cannot run it", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), refused.getSimpleName());
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, MobBehaviour> parsed = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                MobBehaviour.CODEC.parse(JsonOps.INSTANCE, json)
                        .ifSuccess(behaviour -> parsed.put(id, behaviour))
                        .ifError(error -> LOGGER.error("Invalid mob behaviour {}: {}", id, error.message()));
            } catch (RuntimeException e) {
                // One file must never fail the whole datapack reload (a dedicated server would refuse to start).
                LOGGER.error("Invalid mob behaviour {}: {}", id, e.toString());
            }
        });
        Map<EntityType<?>, MobBehaviour> result = new IdentityHashMap<>();
        List<ResourceLocation> used = new ArrayList<>();
        parsed.forEach((id, behaviour) -> {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null) {
                LOGGER.warn("Mob behaviour {} names no registered entity type; it will never be used", id);
            } else if (type == EntityType.ENDER_DRAGON
                    || (BRAIN_TYPES.contains(type) && !HostAdapters.get(type).acceptsBrain())) {
                LOGGER.warn("Mob behaviour {} is ignored: that mob is not driven by goals, so the ability engine "
                        + "cannot run it", id);
            } else if (!DefaultAttributes.hasSupplier(type) || type == EntityType.PLAYER || type == EntityType.ARMOR_STAND) {
                LOGGER.warn("Mob behaviour {} is ignored: that entity is not a living mob", id);
            } else if (!behaviour.host().enabled()) {
                LOGGER.info("Mob behaviour {} is switched off (host.enabled is false); {} runs its own AI", id, id);
            } else {
                try {
                    checkHost(id, type, behaviour);
                    checkBaseAttributes(id, type, behaviour);
                } catch (RuntimeException e) {
                    LOGGER.warn("Mob behaviour {}: could not finish its reload checks: {}", id, e.toString());
                }
                result.put(type, behaviour);
                used.add(id);
            }
        });
        Map<ResourceLocation, Set<String>> named = new LinkedHashMap<>();
        for (ResourceLocation id : used) {
            Set<String> types = new TreeSet<>();
            collectDamageTypes(files.get(id), types);
            if (!types.isEmpty()) {
                named.put(id, types);
            }
        }
        damageTypes = named;
        byType = Collections.unmodifiableMap(result);
        generation++;
        LOGGER.info("Loaded {} mob behaviour(s): {}", used.size(), used);
    }

    /** Reports host blocks the entity type's adapter cannot use, and keys no host block defines. */
    private static void checkHost(ResourceLocation id, EntityType<?> type, MobBehaviour behaviour) {
        HostAdapter adapter = HostAdapters.get(type);
        ResourceLocation wanted = adapter.hostType();
        ResourceLocation given = behaviour.host().options().map(HostOptions::type).orElse(null);
        if (given != null && !given.equals(wanted)) {
            LOGGER.warn("Mob behaviour {}: host type {} does not apply to this entity ({}); its host options are ignored",
                    id, given, wanted == null ? "it takes none" : "it reads " + wanted);
        }
        for (String key : HostSpec.unknownKeys(behaviour.host().raw(), given)) {
            LOGGER.warn("Mob behaviour {}: unknown key '{}' in its host block is ignored{}", id, key,
                    given == null ? " (the block names no type)" : "");
        }
        for (MobBehaviour.Phase phase : behaviour.phases()) {
            phase.hostOverride().ifPresent(block -> {
                for (String key : HostSpec.unknownKeys(block, given)) {
                    LOGGER.warn("Mob behaviour {}: unknown key '{}' in the host block of the phase below {} is ignored",
                            id, key, phase.belowHealth());
                }
                if (block.get("only_tagged").result().isPresent()) {
                    LOGGER.warn("Mob behaviour {}: 'only_tagged' in the host block of the phase below {} is ignored "
                            + "(only the file's own host block filters mobs)", id, phase.belowHealth());
                }
                if (block.get("enabled").result().isPresent()) {
                    LOGGER.warn("Mob behaviour {}: 'enabled' in the host block of the phase below {} is ignored "
                            + "(only the file's own host block switches it off)", id, phase.belowHealth());
                }
            });
        }
    }

    /**
     * Reports {@code base_attributes} entries that name no attribute or one this entity type does not have, and values
     * outside the attribute's range as it stands now (another mod may have widened it, e.g. AttributeFix): the game
     * clamps those.
     */
    @SuppressWarnings("unchecked")
    private static void checkBaseAttributes(ResourceLocation id, EntityType<?> type, MobBehaviour behaviour) {
        if (behaviour.baseAttributes().isEmpty() && behaviour.referenceAttributes().isEmpty()) {
            return;
        }
        AttributeSupplier supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
        for (ResourceLocation attributeId : behaviour.referenceAttributes().keySet()) {
            Optional<Holder.Reference<Attribute>> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(attributeId);
            if (attribute.isEmpty() || !supplier.hasAttribute(attribute.get())) {
                LOGGER.warn("Mob behaviour {}: reference_attributes names {}, which {}; nothing reads it", id,
                        attributeId, attribute.isEmpty() ? "is no known attribute" : "this entity does not have");
            }
        }
        for (ResourceLocation attributeId : behaviour.baseAttributes().keySet()) {
            Optional<Holder.Reference<Attribute>> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(attributeId);
            if (attribute.isEmpty()) {
                LOGGER.warn("Mob behaviour {}: base_attributes names unknown attribute {}; skipped", id, attributeId);
            } else if (!supplier.hasAttribute(attribute.get())) {
                LOGGER.warn("Mob behaviour {}: base_attributes sets {}, which this entity does not have; skipped",
                        id, attributeId);
            } else if (attribute.get().value() instanceof RangedAttribute ranged) {
                double value = behaviour.baseAttributes().get(attributeId);
                if (value < ranged.getMinValue() || value > ranged.getMaxValue()) {
                    // Only "as it stands now": some cap-raising mods (AttributeFix on a dedicated server) widen the
                    // range after the first datapack load, so this can be a false alarm there.
                    LOGGER.warn("Mob behaviour {}: base_attributes sets {} to {}, outside its current range [{}, {}]; "
                            + "unless a mod raises that cap (AttributeFix), the game clamps it to {}", id, attributeId,
                            value, ranged.getMinValue(), ranged.getMaxValue(), ranged.sanitizeValue(value));
                }
            }
        }
    }
}
