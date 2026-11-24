package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.MobDeclaration;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.ledok.mobs_ld.mob.behaviour.event.CloudEvent;
import net.ledok.mobs_ld.mob.behaviour.event.DashEvent;
import net.ledok.mobs_ld.mob.behaviour.event.FxEvent;
import net.ledok.mobs_ld.mob.behaviour.event.GrabEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitSpec;
import net.ledok.mobs_ld.mob.behaviour.event.Origin;
import net.ledok.mobs_ld.mob.behaviour.event.PullEvent;
import net.ledok.mobs_ld.mob.behaviour.event.VfxEvent;
import net.ledok.mobs_ld.mob.behaviour.event.VulnerableEvent;
import net.ledok.mobs_ld.mob.combat.SlotPicker;
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
import software.bernie.geckolib.animation.EasingType;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
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
 * End-to-end verification for Nameless King ({@code mobs_ld:nameless_king}), strict 4-way pairwise VFX separation
 * across all custom bosses (Midra, Amber Brute, Emberroot, Nameless King), and codec validation for interactive
 * {@code grab}, {@code pull}, and {@code HitSpec} displacement physics.
 */
class NamelessKingBossTest {
    private static final ResourceLocation NAMELESS_KING_ID =
            ResourceLocation.fromNamespaceAndPath("mobs_ld", "nameless_king");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModEffects.register();
    }

    private static JsonElement readJson(String path) throws Exception {
        InputStream stream = NamelessKingBossTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing resource: " + path);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    @Test
    void entityDeclarationLoadsWithFixedHealthAndValidAttributes() throws Exception {
        MobDeclaration declaration = MobDeclaration.codec(NAMELESS_KING_ID)
                .parse(JsonOps.INSTANCE, readJson("/mobs_ld/entities/nameless_king.json"))
                .getOrThrow(msg -> new AssertionError("nameless_king entity declaration: " + msg));

        assertEquals(1.7F, declaration.width());
        assertEquals(4.7F, declaration.height());
        assertEquals(4.1F, declaration.eyeHeight().orElseThrow());
        assertFalse(declaration.fireImmune());
        assertEquals(78, declaration.deathTicks());
        assertEquals(580.0, declaration.attributes().get(ResourceLocation.withDefaultNamespace("generic.max_health")));
        assertTrue(declaration.spawnEgg().isPresent());
        assertEquals(3.2, declaration.render().walkBlocksPerCycle());
        assertEquals(1.1F, declaration.render().shadowRadius().orElseThrow());

        for (ResourceLocation attrId : declaration.attributes().keySet()) {
            assertTrue(BuiltInRegistries.ATTRIBUTE.containsKey(attrId), "Unknown attribute: " + attrId);
        }
        assertNotNull(NamelessKingBossTest.class.getResourceAsStream(
                "/assets/mobs_ld/models/item/nameless_king_spawn_egg.json"));
    }

    @Test
    void behaviourValidatesAndBossesUseDistinctVfx() throws Exception {
        MobBehaviour king = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/nameless_king.json"))
                .getOrThrow(msg -> new AssertionError("nameless_king behaviour: " + msg));
        MobBehaviour midra = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/midra.json")).getOrThrow();
        MobBehaviour brute = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/amber_brute.json")).getOrThrow();
        MobBehaviour ember = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/emberroot.json")).getOrThrow();

        assertEquals(Optional.of(14.0),
                king.declaredBase(ResourceLocation.withDefaultNamespace("generic.attack_damage")));
        assertTrue(king.intro().isPresent() && king.intro().get().invulnerable());
        assertEquals("spawn", king.intro().get().clip());
        assertTrue(king.onDeath().isPresent());
        assertEquals("death", king.onDeath().get().clip());

        assertEquals(3, king.phases().size());
        assertEquals(List.of(1.0, 0.6, 0.25),
                king.phases().stream().map(MobBehaviour.Phase::belowHealth).toList());
        assertTrue(king.phases().get(1).transition().isPresent() && king.phases().get(1).transition().get().invulnerable());
        assertEquals(Optional.of("phase2"), king.phases().get(1).texture());
        assertEquals(1.1, king.phases().get(1).attackSpeed());
        assertEquals(1.18, king.phases().get(2).attackSpeed());

        assertEquals(SlotPicker.Rule.SPREAD, king.targetSlots().get("storm_prey").rule());
        assertEquals(SlotPicker.Rule.LOWEST_THREAT, king.targetSlots().get("thunder_mark").rule());
        assertEquals(4.0, king.targetSlots().get("thunder_mark").offset());

        // Interactive storm force fields: outward gale, orbiting cyclone, and sky updrafts.
        assertTrue(king.abilities().get("storm_wave").events().stream()
                .anyMatch(e -> e instanceof PullEvent p && p.mode() == PullEvent.Mode.OUTWARD));
        assertTrue(king.abilities().get("cyclone_spear").events().stream()
                .anyMatch(e -> e instanceof PullEvent p && p.mode() == PullEvent.Mode.ORBIT));
        assertTrue(king.abilities().get("storm_nova").events().stream()
                .anyMatch(e -> e instanceof PullEvent p && p.mode() == PullEvent.Mode.UPDRAFT));
        assertEquals(Optional.of(2), king.abilities().get("falling_bolt").maxPlayers());
        assertEquals(Optional.of(3), king.abilities().get("tempest_judgment").minPlayers());
        long judgmentWaves = king.abilities().get("tempest_judgment").events().stream()
                .filter(e -> e instanceof HitEvent h && h.origin() == Origin.EACH_FIGHTER
                        && h.lead().isPresent() && h.hit().slamDown() >= 1.0).count();
        assertEquals(2, judgmentWaves, "tempest_judgment must drop 2 leading sky-bolt waves with slam_down");

        JsonObject enUs = readJson("/assets/mobs_ld/lang/en_us.json").getAsJsonObject();
        JsonObject ukUa = readJson("/assets/mobs_ld/lang/uk_ua.json").getAsJsonObject();
        for (String key : List.of("entity.mobs_ld.nameless_king", "item.mobs_ld.nameless_king_spawn_egg",
                "effect.mobs_ld.storm_static", "death.attack.mobs_ld.storm_lightning",
                "death.attack.mobs_ld.storm_lightning.player", "mobs_ld.stacks.storm_charge")) {
            assertTrue(enUs.has(key), "en_us missing " + key);
            assertTrue(ukUa.has(key), "uk_ua missing " + key);
        }

        List<MobBehaviour.Ability> timelines = allTimelines(king);
        Set<SpellVfxKind> kingKinds = new HashSet<>();
        int soundCount = 0;
        int lightningHits = 0;
        int dashCount = 0;
        int vulnerableCount = 0;
        int stormStaticUses = 0;
        ResourceLocation stormStaticId = ResourceLocation.fromNamespaceAndPath("mobs_ld", "storm_static");

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
                    assertFalse(hit.telegraph().show(), "Nameless King uses 3D VFX instead of flat ground boxes");
                    assertFalse(hit.telegraph().arrow(), "Nameless King must not draw flat ground arrows");
                    if (hit.hit().damageType().filter(id -> id.equals(ModDamageTypes.STORM_LIGHTNING.location())).isPresent()) {
                        lightningHits++;
                    }
                    for (HitSpec.EffectSpec eff : hit.hit().effects()) {
                        assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()), "Unknown effect: " + eff.id());
                        if (eff.id().equals(stormStaticId)) {
                            stormStaticUses++;
                        }
                    }
                } else if (event instanceof DashEvent dash) {
                    dashCount++;
                    assertFalse(dash.telegraph().show());
                    assertFalse(dash.telegraph().arrow());
                    if (dash.hit().isPresent()) {
                        for (HitSpec.EffectSpec eff : dash.hit().get().effects()) {
                            assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()), "Unknown effect: " + eff.id());
                            if (eff.id().equals(stormStaticId)) {
                                stormStaticUses++;
                            }
                        }
                    }
                } else if (event instanceof CloudEvent cloud) {
                    for (HitSpec.EffectSpec eff : cloud.effects()) {
                        assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()), "Unknown effect: " + eff.id());
                        if (eff.id().equals(stormStaticId)) {
                            stormStaticUses++;
                        }
                    }
                } else if (event instanceof VulnerableEvent) {
                    vulnerableCount++;
                } else if (event instanceof VfxEvent vfx) {
                    kingKinds.add(vfx.kind());
                    if (vfx.origin() == VfxEvent.VfxOrigin.LOCKED) {
                        double impact = vfx.at() + vfx.windup();
                        boolean matched = ability.events().stream().anyMatch(other -> {
                            if (other instanceof HitEvent hit) {
                                return FxEvent.sameMoment(hit.at(), impact) && hit.begin() <= vfx.at() + 1.0e-6;
                            }
                            if (other instanceof DashEvent dash) {
                                return FxEvent.sameMoment(dash.at(), impact) && dash.begin() <= vfx.at() + 1.0e-6;
                            }
                            if (other instanceof GrabEvent grab) {
                                return FxEvent.sameMoment(grab.at(), impact) && grab.begin() <= vfx.at() + 1.0e-6;
                            }
                            return false;
                        });
                        assertTrue(matched, "Locked VFX " + vfx.kind() + " at " + vfx.at() + " (impact " + impact
                                + ") has no matching early-locked hit/dash/grab in clip " + ability.clip());
                    }
                }
            }
        }

        assertTrue(soundCount >= 28, "Expected rich storm/lightning sound cues, got " + soundCount);
        assertTrue(lightningHits >= 10, "Expected storm_lightning hits, got " + lightningHits);
        assertTrue(dashCount >= 2, "Expected spear_thrust and sky_dive dashes, got " + dashCount);
        assertTrue(vulnerableCount >= 8, "Expected readable vulnerable punish windows, got " + vulnerableCount);
        assertTrue(stormStaticUses >= 10, "Expected custom mobs_ld:storm_static applications, got " + stormStaticUses);

        Set<SpellVfxKind> expectedKingKinds = Set.of(
                SpellVfxKind.SPEAR_ARC, SpellVfxKind.LIGHTNING_BOLT, SpellVfxKind.STORM_GALE,
                SpellVfxKind.TEMPEST_CYCLONE, SpellVfxKind.THUNDER_SPEAR_DIVE, SpellVfxKind.STORM_NOVA);
        assertEquals(expectedKingKinds, kingKinds, "Nameless King must use all 6 exclusive storm/lightning VFX kinds");

        // Each boss owns distinct VFX, including the Colossus's sand sweep.
        Set<SpellVfxKind> midraKinds = collectVfxKinds(midra);
        Set<SpellVfxKind> bruteKinds = collectVfxKinds(brute);
        Set<SpellVfxKind> emberKinds = collectVfxKinds(ember);
        assertTrue(Collections.disjoint(kingKinds, midraKinds), "Nameless King must not repeat Midra's VFX");
        assertTrue(Collections.disjoint(kingKinds, bruteKinds), "Nameless King must not repeat Amber Brute's VFX");
        assertTrue(Collections.disjoint(kingKinds, emberKinds), "Nameless King must not repeat Emberroot's VFX");
        MobBehaviour colossus = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                readJson("/data/mobs_ld/mob_behaviour/sand_golem_colossus.json")).getOrThrow();
        Set<SpellVfxKind> colossusKinds = collectVfxKinds(colossus);
        assertEquals(Set.of(SpellVfxKind.SAND_SWEEP), colossusKinds);
        for (Set<SpellVfxKind> bossKinds : List.of(midraKinds, bruteKinds, emberKinds, kingKinds)) {
            assertTrue(Collections.disjoint(colossusKinds, bossKinds), "The Colossus must have its own VFX");
        }
        assertEquals(SpellVfxKind.values().length,
                midraKinds.size() + bruteKinds.size() + emberKinds.size() + kingKinds.size() + colossusKinds.size(),
                "Every SpellVfxKind must belong uniquely to a boss");
    }

    @Test
    void stormLightningDamageTypeAndTagsShip() throws Exception {
        JsonObject type = readJson("/data/mobs_ld/damage_type/storm_lightning.json").getAsJsonObject();
        assertEquals("mobs_ld.storm_lightning", type.get("message_id").getAsString());
        assertEquals("when_caused_by_living_non_player", type.get("scaling").getAsString());
        assertEquals("hurt", type.get("effects").getAsString());

        for (String tag : List.of("/data/minecraft/tags/damage_type/bypasses_shield.json",
                "/data/spell_engine/tags/damage_type/evadable.json")) {
            JsonObject file = readJson(tag).getAsJsonObject();
            assertFalse(file.get("replace").getAsBoolean(), tag);
            assertTrue(file.getAsJsonArray("values").contains(new JsonPrimitive("mobs_ld:storm_lightning")), tag);
        }
        assertFalse(readJson("/data/loot_stage/tags/damage_type/unscaled.json").toString().contains("storm_lightning"),
                "Storm Lightning scales with damage_dealt");
        assertEquals(ResourceKey.create(Registries.DAMAGE_TYPE,
                        ResourceLocation.fromNamespaceAndPath("mobs_ld", "storm_lightning")),
                ModDamageTypes.STORM_LIGHTNING);
    }

    @Test
    void interactiveGrabPullAndHitSpecPhysicsAreValidated() {
        String base = "{\"abilities\": {\"a\": {\"duration\": 4, \"events\": [%s]}}}";

        String validGrab = "{\"type\": \"grab\", \"at\": 0.8, \"until\": 2.1, "
                + "\"shape\": {\"type\": \"cone\", \"radius\": 4.2, \"angle\": 80}, "
                + "\"hold\": {\"forward\": 2.2, \"up\": 1.5, \"lift\": 0.5}, \"rescue_damage\": 25.0}";
        assertEquals("", error(base.formatted(validGrab)));
        assertTrue(error(base.formatted(validGrab.replace("\"until\": 2.1", "\"until\": 0.5")))
                .contains("before or at its catch time"));

        String validPull = "{\"type\": \"pull\", \"from\": 0.5, \"until\": 1.5, "
                + "\"shape\": {\"type\": \"circle\", \"radius\": 7.0}, \"mode\": \"orbit\", \"strength\": 0.2}";
        assertEquals("", error(base.formatted(validPull)));
        assertTrue(error(base.formatted(validPull.replace("\"until\": 1.5", "\"until\": 0.2")))
                .contains("before it starts"));
        assertFalse(error(base.formatted(validPull.replace("\"orbit\"", "\"vortex\""))).isEmpty());

        String validHitPhysics = "{\"type\": \"hit\", \"at\": 1.0, \"shape\": {\"type\": \"circle\", \"radius\": 3.0}, "
                + "\"hit\": {\"amount\": 10.0, \"pull\": 0.8, \"slam_down\": 1.1}}";
        assertEquals("", error(base.formatted(validHitPhysics)));
        assertFalse(error(base.formatted(validHitPhysics.replace("\"pull\": 0.8", "\"pull\": -0.5"))).isEmpty());
        assertFalse(error(base.formatted(validHitPhysics.replace("\"slam_down\": 1.1", "\"slam_down\": 9.0"))).isEmpty());
    }

    @Test
    void geckoLibModelAndAll16SmoothedAnimationsBakeCleanly() throws Exception {
        Model rawModel = KeyFramesAdapter.GEO_GSON.fromJson(
                readJson("/assets/mobs_ld/geo/entity/nameless_king.geo.json"), Model.class);
        BakedGeoModel bakedModel = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(rawModel));

        Set<String> boneNames = new HashSet<>();
        Deque<GeoBone> open = new ArrayDeque<>(bakedModel.topLevelBones());
        while (!open.isEmpty()) {
            GeoBone bone = open.removeFirst();
            boneNames.add(bone.getName());
            open.addAll(bone.getChildBones());
        }
        assertEquals(1, bakedModel.topLevelBones().size(), "Single root bone per docs/models.md");
        assertTrue(boneNames.contains("root") && boneNames.contains("body")
                && boneNames.contains("head") && boneNames.contains("spear"));
        assertFalse(boneNames.contains("hitbox"), "Hidden ModelEngine hitbox bone must not be exported");
        assertEquals(26, boneNames.size(), "17 rig bones + 9 marker bones");

        Set<String> markers = Set.of("fx_feet", "fx_core", "fx_crown", "fx_hand_left",
                "fx_spear_base", "fx_spear_tip", "hit_spear_base", "hit_spear_mid", "hit_spear_tip");
        assertTrue(boneNames.containsAll(markers), "All marker bones must exist in nameless_king.geo.json");

        JsonObject animRoot = readJson("/assets/mobs_ld/animations/entity/nameless_king.animation.json").getAsJsonObject();
        BakedAnimations bakedAnims = KeyFramesAdapter.GEO_GSON.fromJson(
                animRoot.getAsJsonObject("animations"), BakedAnimations.class);
        assertEquals(16, bakedAnims.animations().size(), "All 16 Nameless King clips must bake cleanly");

        MobBehaviour king = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/nameless_king.json")).getOrThrow();
        Set<String> referencedClips = new HashSet<>(List.of("idle", "walk", "hurt", "death"));
        king.intro().ifPresent(a -> referencedClips.add(a.clipOr("intro")));
        king.onDeath().ifPresent(a -> referencedClips.add(a.clipOr("death")));
        king.abilities().forEach((name, a) -> referencedClips.add(a.clipOr(name)));
        king.phases().forEach(p -> p.transition().ifPresent(t -> referencedClips.add(t.clipOr("transition"))));
        king.boss().flatMap(MobBehaviour.Boss::softEnrage).flatMap(MobBehaviour.SoftEnrage::clip)
                .ifPresent(referencedClips::add);

        for (String clip : referencedClips) {
            String full = "animation.mobs_ld.nameless_king." + clip;
            assertNotNull(bakedAnims.getAnimation(full), "Missing clip in nameless_king.animation.json: " + full);
        }
        assertEquals(bakedAnims.animations().size(), referencedClips.size(),
                "Every single one of the 16 exported Nameless King clips must be actively used");

        int catmullCount = 0;
        int particleKeyframes = 0;
        int soundKeyframes = 0;
        int shakeKeyframes = 0;
        for (Animation anim : bakedAnims.animations().values()) {
            for (BoneAnimation ba : anim.boneAnimations()) {
                for (var kf : ba.rotationKeyFrames().xKeyframes()) {
                    if (kf.easingType() == EasingType.CATMULLROM) {
                        catmullCount++;
                    }
                }
            }
            for (ParticleKeyframeData pk : anim.keyFrames().particles()) {
                particleKeyframes++;
                ResourceLocation pid = ResourceLocation.parse(pk.getEffect());
                ParticleType<?> ptype = BuiltInRegistries.PARTICLE_TYPE.get(pid);
                assertTrue(ptype instanceof SimpleParticleType,
                        "Particle keyframe must be SimpleParticleType: " + pid + " in " + anim.name());
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
        assertTrue(catmullCount >= 200, "Expected smooth Catmull-Rom keyframes across clips, got " + catmullCount);
        assertTrue(particleKeyframes >= 35, "Expected client particle keyframes, got " + particleKeyframes);
        assertTrue(soundKeyframes >= 2, "Expected walk footstep sound keyframes, got " + soundKeyframes);
        assertTrue(shakeKeyframes >= 14, "Expected camera shake keyframes, got " + shakeKeyframes);
    }

    @Test
    void texturesAndBakedHitTracksShip() throws Exception {
        for (String tex : List.of("nameless_king.png", "nameless_king_glowmask.png",
                "nameless_king_phase2.png", "nameless_king_phase2_glowmask.png")) {
            assertNotNull(NamelessKingBossTest.class.getResourceAsStream("/assets/mobs_ld/textures/entity/" + tex),
                    "Missing texture: " + tex);
        }

        JsonObject tracks = readJson("/data/mobs_ld/hit_tracks/nameless_king.json").getAsJsonObject();
        assertEquals(1, tracks.get("version").getAsInt());
        assertEquals(20, tracks.get("rate").getAsInt());
        JsonObject clips = tracks.getAsJsonObject("clips");
        assertEquals(16, clips.size(), "All 16 clips must be baked in hit_tracks/nameless_king.json");
        for (var entry : clips.entrySet()) {
            JsonObject clip = entry.getValue().getAsJsonObject();
            int len = clip.get("length").getAsInt();
            JsonObject bones = clip.getAsJsonObject("bones");
            assertEquals(26, bones.size(), "All 26 bones in hit track clip " + entry.getKey());
            assertEquals(len + 1, bones.getAsJsonArray("hit_spear_tip").size(),
                    "Tick count mismatch in clip " + entry.getKey());
            assertEquals(len + 1, bones.getAsJsonArray("fx_spear_tip").size(),
                    "Tick count mismatch in clip " + entry.getKey());
        }
    }

    @Test
    void spearThrustAndSkyDiveCoverFullRangeAndLandOnTarget() throws Exception {
        MobDeclaration declaration = MobDeclaration.codec(NAMELESS_KING_ID)
                .parse(JsonOps.INSTANCE, readJson("/mobs_ld/entities/nameless_king.json")).getOrThrow();
        assertTrue(declaration.attributes().getOrDefault(
                        ResourceLocation.withDefaultNamespace("generic.step_height"), 0.0) >= 1.0,
                "Nameless King needs >= 1.0 step_height so spear dashes do not snag on 1-block steps");

        MobBehaviour king = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/nameless_king.json")).getOrThrow();
        for (String name : List.of("spear_thrust", "sky_dive")) {
            MobBehaviour.Ability ability = king.abilities().get(name);
            DashEvent dash = ability.events().stream().filter(DashEvent.class::isInstance)
                    .map(DashEvent.class::cast).findFirst().orElseThrow();
            assertTrue(dash.speed().isPresent(), name + " must use constant speed");
            assertTrue(dash.hit().isPresent(), name + " dash must deal contact damage");
            assertTrue(dash.maxDistance() >= ability.range().max() + dash.overshoot(),
                    name + " max_distance must cover range.max plus overshoot");
        }

        // sky_dive's follow-up lightning shockwave must lock after the dash completes so it explodes where he lands.
        MobBehaviour.Ability skyDive = king.abilities().get("sky_dive");
        DashEvent diveDash = skyDive.events().stream().filter(DashEvent.class::isInstance)
                .map(DashEvent.class::cast).findFirst().orElseThrow();
        HitEvent landingShock = skyDive.events().stream().filter(HitEvent.class::isInstance)
                .map(HitEvent.class::cast).findFirst().orElseThrow();
        double diveMaxFinish = diveDash.at() + diveDash.maxDistance() / (diveDash.speed().orElseThrow() * 20.0);
        assertTrue(landingShock.begin() >= diveMaxFinish - 0.01,
                "sky_dive landing shockwave must lock after the dive finishes (begin=" + landingShock.begin()
                        + ", diveMaxFinish=" + diveMaxFinish + ")");
    }

    private static String error(String json) {
        return MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error()
                .map(DataResult.Error::message).orElse("");
    }

    private static List<MobBehaviour.Ability> allTimelines(MobBehaviour b) {
        List<MobBehaviour.Ability> out = new ArrayList<>(b.abilities().values());
        b.intro().ifPresent(out::add);
        b.onDeath().ifPresent(out::add);
        b.phases().forEach(p -> p.transition().ifPresent(out::add));
        return out;
    }

    private static Set<SpellVfxKind> collectVfxKinds(MobBehaviour b) {
        Set<SpellVfxKind> kinds = new HashSet<>();
        for (MobBehaviour.Ability a : allTimelines(b)) {
            for (AbilityEvent e : a.events()) {
                if (e instanceof VfxEvent v) {
                    kinds.add(v.kind());
                }
            }
        }
        return kinds;
    }
}
