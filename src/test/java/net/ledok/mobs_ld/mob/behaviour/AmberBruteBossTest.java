package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.MobDeclaration;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.ledok.mobs_ld.mob.behaviour.event.CloudEvent;
import net.ledok.mobs_ld.mob.behaviour.event.FxEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitSpec;
import net.ledok.mobs_ld.mob.behaviour.event.TraceEvent;
import net.ledok.mobs_ld.mob.behaviour.event.VfxEvent;
import net.ledok.mobs_ld.mob.behaviour.event.VulnerableEvent;
import net.ledok.mobs_ld.network.SpellVfxKind;
import net.ledok.mobs_ld.registry.ModDamageTypes;
import net.ledok.mobs_ld.registry.ModEffects;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.keyframe.event.data.CustomInstructionKeyframeData;
import software.bernie.geckolib.animation.keyframe.event.data.ParticleKeyframeData;
import software.bernie.geckolib.animation.keyframe.event.data.SoundKeyframeData;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end verification for Amber Brute ({@code mobs_ld:amber_brute}): entity declaration, combat behaviour,
 * distinct vanilla-RPG VFX (zero overlap with Midra), real {@code trace} melee paths, custom damage type and tags,
 * localization keys, GeckoLib model/animation baking, textures, and pre-baked hit tracks.
 */
class AmberBruteBossTest {
    private static final ResourceLocation AMBER_BRUTE_ID =
            ResourceLocation.fromNamespaceAndPath("mobs_ld", "amber_brute");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModEffects.register();
    }

    private static JsonElement readJson(String path) throws Exception {
        InputStream stream = AmberBruteBossTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing resource: " + path);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    @Test
    void entityDeclarationLoadsAndAttributesExist() throws Exception {
        MobDeclaration declaration = MobDeclaration.codec(AMBER_BRUTE_ID)
                .parse(JsonOps.INSTANCE, readJson("/mobs_ld/entities/amber_brute.json"))
                .getOrThrow(msg -> new AssertionError("amber_brute entity declaration: " + msg));

        assertEquals(2.0F, declaration.width());
        assertEquals(4.6F, declaration.height());
        assertEquals(3.85F, declaration.eyeHeight().orElseThrow());
        assertFalse(declaration.fireImmune(), "Forest/woodland Amber Brute is not fire immune");
        assertEquals(70, declaration.deathTicks(), "Matches the 3.4 s death hold clip");
        assertTrue(declaration.spawnEgg().isPresent());
        assertEquals(3.4, declaration.render().walkBlocksPerCycle());
        assertEquals(1.25F, declaration.render().shadowRadius().orElseThrow());

        for (ResourceLocation attrId : declaration.attributes().keySet()) {
            assertTrue(BuiltInRegistries.ATTRIBUTE.containsKey(attrId), "Unknown attribute: " + attrId);
        }
        assertNotNull(AmberBruteBossTest.class.getResourceAsStream(
                "/assets/mobs_ld/models/item/amber_brute_spawn_egg.json"));
    }

    @Test
    void behaviourUsesDistinctMechanicsVfxAndAllAssetsValidate() throws Exception {
        MobBehaviour brute = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/amber_brute.json"))
                .getOrThrow(msg -> new AssertionError("amber_brute behaviour: " + msg));
        MobBehaviour midra = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/midra.json"))
                .getOrThrow(msg -> new AssertionError("midra behaviour: " + msg));

        assertEquals(Optional.of(13.0),
                brute.declaredBase(ResourceLocation.withDefaultNamespace("generic.attack_damage")));
        assertTrue(brute.intro().isPresent());
        assertEquals("spawn", brute.intro().get().clip());
        assertTrue(brute.intro().get().invulnerable());
        assertTrue(brute.onDeath().isPresent());
        assertEquals("death", brute.onDeath().get().clip());

        assertEquals(2, brute.phases().size());
        assertEquals(List.of(1.0, 0.55),
                brute.phases().stream().map(MobBehaviour.Phase::belowHealth).toList());
        assertTrue(brute.phases().get(1).transition().isPresent(), "Phase 2 has invulnerable crystal_roar transition");
        assertTrue(brute.phases().get(1).transition().get().invulnerable());
        assertEquals(Optional.of("phase2"), brute.phases().get(1).texture());
        assertEquals(1.12, brute.phases().get(1).attackSpeed());
        assertFalse(brute.phases().get(1).damageTaken().isEmpty(), "Phase 2 hardens amber crust against projectiles");

        JsonObject enUs = readJson("/assets/mobs_ld/lang/en_us.json").getAsJsonObject();
        JsonObject ukUa = readJson("/assets/mobs_ld/lang/uk_ua.json").getAsJsonObject();
        for (String key : List.of("entity.mobs_ld.amber_brute", "item.mobs_ld.amber_brute_spawn_egg",
                "death.attack.mobs_ld.amber_shatter", "death.attack.mobs_ld.amber_shatter.player",
                "mobs_ld.stacks.amber_resin")) {
            assertTrue(enUs.has(key), "en_us missing " + key);
            assertTrue(ukUa.has(key), "uk_ua missing " + key);
        }

        List<MobBehaviour.Ability> timelines = new ArrayList<>(brute.abilities().values());
        brute.intro().ifPresent(timelines::add);
        brute.onDeath().ifPresent(timelines::add);
        brute.phases().forEach(p -> p.transition().ifPresent(timelines::add));

        Set<SpellVfxKind> bruteKinds = new HashSet<>();
        int soundCount = 0;
        int shatterHits = 0;
        int traceCount = 0;
        int vulnerableCount = 0;
        for (MobBehaviour.Ability ability : timelines) {
            for (AbilityEvent event : ability.events()) {
                if (event instanceof FxEvent fx) {
                    if (fx.sound().isPresent()) {
                        soundCount++;
                        ResourceLocation sid = fx.sound().get().id();
                        assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(sid), "Unknown sound: " + sid);
                    }
                    if (fx.message().isPresent()) {
                        String key = fx.message().get().key();
                        assertTrue(enUs.has(key), "en_us missing cue " + key);
                        assertTrue(ukUa.has(key), "uk_ua missing cue " + key);
                    }
                } else if (event instanceof HitEvent hit) {
                    assertFalse(hit.telegraph().show(), "Amber Brute uses 3D crystal VFX instead of flat ground boxes");
                    assertFalse(hit.telegraph().arrow(), "Amber Brute must not draw ground arrows");
                    if (hit.hit().damageType().filter(id -> id.equals(ModDamageTypes.AMBER_SHATTER.location())).isPresent()) {
                        shatterHits++;
                    }
                    for (HitSpec.EffectSpec eff : hit.hit().effects()) {
                        assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()), "Unknown effect: " + eff.id());
                    }
                } else if (event instanceof TraceEvent trace) {
                    traceCount++;
                    for (HitSpec.EffectSpec eff : trace.hit().effects()) {
                        assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()), "Unknown effect: " + eff.id());
                    }
                } else if (event instanceof CloudEvent cloud) {
                    for (HitSpec.EffectSpec eff : cloud.effects()) {
                        assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()), "Unknown effect: " + eff.id());
                    }
                } else if (event instanceof VulnerableEvent) {
                    vulnerableCount++;
                } else if (event instanceof VfxEvent vfx) {
                    bruteKinds.add(vfx.kind());
                    if (vfx.origin() == VfxEvent.VfxOrigin.LOCKED) {
                        double impact = vfx.at() + vfx.windup();
                        boolean matched = ability.events().stream().anyMatch(other ->
                                other instanceof HitEvent hit
                                        && FxEvent.sameMoment(hit.at(), impact)
                                        && hit.begin() <= vfx.at() + 1.0e-6);
                        assertTrue(matched, "Locked VFX " + vfx.kind() + " at " + vfx.at() + " (impact " + impact
                                + ") has no matching early-locked hit in clip " + ability.clip());
                    }
                }
            }
        }
        assertTrue(soundCount >= 22, "Expected rich tactile sound cues, got " + soundCount);
        assertTrue(shatterHits >= 9, "Expected amber_shatter crystal hits, got " + shatterHits);
        assertTrue(traceCount >= 4, "Expected real animation-driven claw/knuckle trace swings, got " + traceCount);
        assertTrue(vulnerableCount >= 4, "Expected clear melee vulnerable punish windows, got " + vulnerableCount);

        // Interactive Amber Brute upgrades: double_rake hook -> pop -> spike combo, clamping amber_snare, high launch roar.
        List<TraceEvent> doubleRakeTraces = brute.abilities().get("double_rake").events().stream()
                .filter(TraceEvent.class::isInstance).map(TraceEvent.class::cast).toList();
        assertEquals(2, doubleRakeTraces.size());
        assertTrue(doubleRakeTraces.get(0).hit().pull() >= 0.8, "First rake hooks player inward");
        assertTrue(doubleRakeTraces.get(1).hit().knockUp() >= 0.65, "Second rake pops player upward");
        assertTrue(brute.abilities().get("double_rake").events().stream()
                .anyMatch(e -> e instanceof HitEvent h && h.hit().slamDown() >= 1.0), "Finisher spikes airborne target down");
        assertTrue(brute.abilities().get("amber_snare").events().stream()
                .anyMatch(e -> e instanceof HitEvent h && h.hit().pull() > 0.5 && h.hit().slamDown() > 0.5));
        assertTrue(brute.abilities().get("crystal_resonance").events().stream()
                .anyMatch(e -> e instanceof HitEvent h && h.hit().knockUp() >= 0.8));

        Set<SpellVfxKind> expectedAmberKinds = Set.of(
                SpellVfxKind.AMBER_SPIRE, SpellVfxKind.AMBER_GEODE_TRAP, SpellVfxKind.GROUND_FRACTURE,
                SpellVfxKind.SHARD_SPRAY, SpellVfxKind.RESONANCE_RING, SpellVfxKind.CLAW_RAKE);
        assertEquals(expectedAmberKinds, bruteKinds, "Amber Brute must exercise all 6 vanilla-RPG amber VFX kinds");

        // Verify strict VFX separation from Midra (no repeated VFX archetypes).
        Set<SpellVfxKind> midraKinds = new HashSet<>();
        List<MobBehaviour.Ability> midraTimelines = new ArrayList<>(midra.abilities().values());
        midra.intro().ifPresent(midraTimelines::add);
        midra.onDeath().ifPresent(midraTimelines::add);
        midra.phases().forEach(p -> p.transition().ifPresent(midraTimelines::add));
        for (MobBehaviour.Ability a : midraTimelines) {
            for (AbilityEvent e : a.events()) {
                if (e instanceof VfxEvent v) {
                    midraKinds.add(v.kind());
                }
            }
        }
        assertTrue(Collections.disjoint(bruteKinds, midraKinds),
                "Amber Brute must not repeat Midra's VFX archetypes");
    }

    @Test
    void amberShatterDamageTypeAndTagsShip() throws Exception {
        JsonObject type = readJson("/data/mobs_ld/damage_type/amber_shatter.json").getAsJsonObject();
        assertEquals("mobs_ld.amber_shatter", type.get("message_id").getAsString());
        assertEquals("when_caused_by_living_non_player", type.get("scaling").getAsString());
        assertEquals("thorns", type.get("effects").getAsString());

        for (String tag : List.of("/data/minecraft/tags/damage_type/bypasses_shield.json",
                "/data/spell_engine/tags/damage_type/evadable.json")) {
            JsonObject file = readJson(tag).getAsJsonObject();
            assertFalse(file.get("replace").getAsBoolean(), tag);
            assertTrue(file.getAsJsonArray("values").contains(new JsonPrimitive("mobs_ld:amber_shatter")), tag);
        }
        assertFalse(readJson("/data/loot_stage/tags/damage_type/unscaled.json").toString().contains("amber_shatter"),
                "Amber Shatter scales with damage_dealt");
        assertEquals(ResourceKey.create(Registries.DAMAGE_TYPE,
                        ResourceLocation.fromNamespaceAndPath("mobs_ld", "amber_shatter")),
                ModDamageTypes.AMBER_SHATTER);
    }

    @Test
    void geckoLibModelAndAllAnimationsBakeCleanly() throws Exception {
        Model rawModel = KeyFramesAdapter.GEO_GSON.fromJson(
                readJson("/assets/mobs_ld/geo/entity/amber_brute.geo.json"), Model.class);
        BakedGeoModel bakedModel = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(rawModel));

        Set<String> boneNames = new HashSet<>();
        Deque<GeoBone> open = new ArrayDeque<>(bakedModel.topLevelBones());
        while (!open.isEmpty()) {
            GeoBone bone = open.removeFirst();
            boneNames.add(bone.getName());
            open.addAll(bone.getChildBones());
        }
        assertEquals(1, bakedModel.topLevelBones().size(), "Single root bone per docs/models.md");
        assertTrue(boneNames.contains("root") && boneNames.contains("head") && boneNames.contains("forearm_r")
                && boneNames.contains("forearm_l"), "Core bones including look-at head bone must exist");
        assertFalse(boneNames.contains("hitbox"), "Hidden ModelEngine hitbox bone must not be exported");
        assertFalse(boneNames.contains("h_head"), "h_head must be renamed to head for LdMobModel look-at");
        assertEquals(27, boneNames.size(), "19 rig bones + 8 marker bones");

        Set<String> markers = Set.of("fx_feet", "fx_chest", "fx_head", "fx_pack",
                "fx_fist_right", "hit_fist_right", "fx_claw_left", "hit_claw_left");
        assertTrue(boneNames.containsAll(markers), "All marker bones must exist in amber_brute.geo.json");

        JsonObject animRoot = readJson("/assets/mobs_ld/animations/entity/amber_brute.animation.json").getAsJsonObject();
        BakedAnimations bakedAnims = KeyFramesAdapter.GEO_GSON.fromJson(
                animRoot.getAsJsonObject("animations"), BakedAnimations.class);
        assertEquals(11, bakedAnims.animations().size(), "7 original + 4 physics-matched clips must bake");

        MobBehaviour brute = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/amber_brute.json")).getOrThrow();
        Set<String> referencedClips = new HashSet<>(List.of("idle", "walk", "hurt", "death"));
        brute.intro().ifPresent(a -> referencedClips.add(a.clipOr("intro")));
        brute.onDeath().ifPresent(a -> referencedClips.add(a.clipOr("death")));
        brute.abilities().forEach((name, a) -> referencedClips.add(a.clipOr(name)));
        brute.phases().forEach(p -> p.transition().ifPresent(t -> referencedClips.add(t.clipOr("transition"))));
        brute.boss().flatMap(MobBehaviour.Boss::softEnrage).flatMap(MobBehaviour.SoftEnrage::clip)
                .ifPresent(referencedClips::add);

        for (String clip : referencedClips) {
            String full = "animation.mobs_ld.amber_brute." + clip;
            assertNotNull(bakedAnims.getAnimation(full), "Missing clip in amber_brute.animation.json: " + full);
        }
        // Every clip exported in amber_brute.animation.json is actively used!
        assertEquals(bakedAnims.animations().size(), referencedClips.size(),
                "Every exported Amber Brute clip must be used by the boss");

        int particleKeyframes = 0;
        int soundKeyframes = 0;
        int shakeKeyframes = 0;
        for (Animation anim : bakedAnims.animations().values()) {
            for (ParticleKeyframeData pk : anim.keyFrames().particles()) {
                particleKeyframes++;
                ResourceLocation pid = ResourceLocation.parse(pk.getEffect());
                ParticleType<?> ptype = BuiltInRegistries.PARTICLE_TYPE.get(pid);
                assertTrue(ptype instanceof SimpleParticleType,
                        "Particle keyframe must be a SimpleParticleType: " + pid + " in " + anim.name());
                assertTrue(markers.contains(pk.getLocator()),
                        "Particle locator must match a marker bone: " + pk.getLocator() + " in " + anim.name());
            }
            for (SoundKeyframeData sk : anim.keyFrames().sounds()) {
                soundKeyframes++;
                ResourceLocation sid = ResourceLocation.parse(sk.getSound());
                assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(sid),
                        "Unknown client sound keyframe " + sid + " in " + anim.name());
            }
            for (CustomInstructionKeyframeData ik : anim.keyFrames().customInstructions()) {
                shakeKeyframes++;
                String text = ik.getInstructions().trim();
                assertTrue(text.startsWith("shake(") && text.endsWith(");"),
                        "Expected shake(...) instruction in " + anim.name() + ", got: " + text);
            }
        }
        assertTrue(particleKeyframes >= 25, "Expected restrained client-side particle keyframes, got " + particleKeyframes);
        assertTrue(soundKeyframes >= 2, "Expected client-side walk footstep sound keyframes, got " + soundKeyframes);
        assertTrue(shakeKeyframes >= 11, "Expected tactile camera shake keyframes, got " + shakeKeyframes);
    }

    @Test
    void texturesAndBakedHitTracksShip() throws Exception {
        for (String tex : List.of("amber_brute.png", "amber_brute_glowmask.png",
                "amber_brute_phase2.png", "amber_brute_phase2_glowmask.png")) {
            assertNotNull(AmberBruteBossTest.class.getResourceAsStream("/assets/mobs_ld/textures/entity/" + tex),
                    "Missing texture: " + tex);
        }

        JsonObject tracks = readJson("/data/mobs_ld/hit_tracks/amber_brute.json").getAsJsonObject();
        assertEquals(1, tracks.get("version").getAsInt());
        assertEquals(20, tracks.get("rate").getAsInt());
        JsonObject clips = tracks.getAsJsonObject("clips");
        assertEquals(11, clips.size(), "All 11 clips must be baked in hit_tracks/amber_brute.json");
        for (var entry : clips.entrySet()) {
            JsonObject clip = entry.getValue().getAsJsonObject();
            int len = clip.get("length").getAsInt();
            JsonObject bones = clip.getAsJsonObject("bones");
            assertEquals(27, bones.size(), "All 27 bones in hit track clip " + entry.getKey());
            assertEquals(len + 1, bones.getAsJsonArray("hit_claw_left").size(),
                    "Tick count mismatch in clip " + entry.getKey());
            assertEquals(len + 1, bones.getAsJsonArray("hit_fist_right").size(),
                    "Tick count mismatch in clip " + entry.getKey());
        }
    }
}
