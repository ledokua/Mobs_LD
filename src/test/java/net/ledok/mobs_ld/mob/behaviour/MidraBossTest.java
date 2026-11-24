package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.MobDeclaration;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.ledok.mobs_ld.mob.behaviour.event.CloudEvent;
import net.ledok.mobs_ld.mob.behaviour.event.DashEvent;
import net.ledok.mobs_ld.mob.behaviour.event.FxEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitSpec;
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
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end data and asset verification for Midra, Lord of Frenzied Flame ({@code mobs_ld:midra}): entity
 * declaration, combat behaviour, custom damage type and tags, localization keys, GeckoLib model/animation baking,
 * marker bones, client keyframe particles/instructions, textures, and pre-baked hit tracks.
 */
class MidraBossTest {
    private static final ResourceLocation MIDRA_ID = ResourceLocation.fromNamespaceAndPath("mobs_ld", "midra");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModEffects.register();
    }

    private static JsonElement readJson(String path) throws Exception {
        InputStream stream = MidraBossTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing resource: " + path);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    @Test
    void entityDeclarationLoadsAndAttributesExist() throws Exception {
        MobDeclaration declaration = MobDeclaration.codec(MIDRA_ID)
                .parse(JsonOps.INSTANCE, readJson("/mobs_ld/entities/midra.json"))
                .getOrThrow(msg -> new AssertionError("midra entity declaration: " + msg));

        assertEquals(1.8F, declaration.width());
        assertEquals(4.8F, declaration.height());
        assertEquals(4.35F, declaration.eyeHeight().orElseThrow());
        assertTrue(declaration.fireImmune(), "Lord of Frenzied Flame must be fire immune");
        assertEquals(80, declaration.deathTicks(), "Matches the 3.8 s death hold clip");
        assertTrue(declaration.spawnEgg().isPresent());
        assertEquals(3.0, declaration.render().walkBlocksPerCycle());
        assertEquals(1.1F, declaration.render().shadowRadius().orElseThrow());

        for (ResourceLocation attrId : declaration.attributes().keySet()) {
            assertTrue(BuiltInRegistries.ATTRIBUTE.containsKey(attrId), "Unknown attribute: " + attrId);
        }
        assertNotNull(MidraBossTest.class.getResourceAsStream("/assets/mobs_ld/models/item/midra_spawn_egg.json"));
    }

    @Test
    void behaviourLoadsWithAllPhasesSoundsParticlesAndLocalization() throws Exception {
        MobBehaviour midra = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/midra.json"))
                .getOrThrow(msg -> new AssertionError("midra behaviour: " + msg));

        assertEquals(java.util.Optional.of(14.0),
                midra.declaredBase(ResourceLocation.withDefaultNamespace("generic.attack_damage")));
        assertTrue(midra.intro().isPresent());
        assertEquals("spawn", midra.intro().get().clip());
        assertTrue(midra.intro().get().invulnerable());
        assertTrue(midra.onDeath().isPresent());
        assertEquals("death", midra.onDeath().get().clip());

        assertEquals(3, midra.phases().size());
        assertEquals(List.of(1.0, 0.55, 0.25),
                midra.phases().stream().map(MobBehaviour.Phase::belowHealth).toList());
        assertTrue(midra.phases().get(1).transition().isPresent(), "Phase 2 has invulnerable frenzy_burst transition");
        assertTrue(midra.phases().get(1).transition().get().invulnerable());
        assertEquals(java.util.Optional.of("phase2"), midra.phases().get(1).texture());
        assertEquals(1.12, midra.phases().get(1).attackSpeed());
        assertEquals(1.22, midra.phases().get(2).attackSpeed());

        MobBehaviour.Boss boss = midra.boss().orElseThrow();
        assertEquals(MobBehaviour.EngageOn.PLAYERS, boss.engageOn());
        assertEquals(36.0, boss.leash());
        assertTrue(boss.softEnrage().isPresent());

        // Validate every sound, effect, and action-bar translation key referenced across all timelines.
        JsonObject enUs = readJson("/assets/mobs_ld/lang/en_us.json").getAsJsonObject();
        JsonObject ukUa = readJson("/assets/mobs_ld/lang/uk_ua.json").getAsJsonObject();
        for (String key : List.of("entity.mobs_ld.midra", "item.mobs_ld.midra_spawn_egg",
                "death.attack.mobs_ld.frenzied_flame", "death.attack.mobs_ld.frenzied_flame.player",
                "mobs_ld.stacks.frenzy")) {
            assertTrue(enUs.has(key), "en_us missing " + key);
            assertTrue(ukUa.has(key), "uk_ua missing " + key);
        }

        List<MobBehaviour.Ability> timelines = new ArrayList<>(midra.abilities().values());
        midra.intro().ifPresent(timelines::add);
        midra.onDeath().ifPresent(timelines::add);
        midra.phases().forEach(p -> p.transition().ifPresent(timelines::add));

        int soundCount = 0;
        int frenzyHits = 0;
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
                    if (hit.hit().damageType().filter(id -> id.equals(ModDamageTypes.FRENZIED_FLAME.location())).isPresent()) {
                        frenzyHits++;
                    }
                    for (HitSpec.EffectSpec eff : hit.hit().effects()) {
                        assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()), "Unknown effect: " + eff.id());
                    }
                } else if (event instanceof DashEvent dash && dash.hit().isPresent()) {
                    for (HitSpec.EffectSpec eff : dash.hit().get().effects()) {
                        assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()), "Unknown effect: " + eff.id());
                    }
                } else if (event instanceof CloudEvent cloud) {
                    for (HitSpec.EffectSpec eff : cloud.effects()) {
                        assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()), "Unknown effect: " + eff.id());
                    }
                } else if (event instanceof VulnerableEvent) {
                    vulnerableCount++;
                }
            }
        }
        assertTrue(soundCount >= 30, "Expected rich sound cues, got " + soundCount);
        assertTrue(frenzyHits >= 12, "Expected Frenzied Flame hits across the kit, got " + frenzyHits);
        assertTrue(vulnerableCount >= 4, "Expected vulnerable punish windows, got " + vulnerableCount);

        // Verify custom 3D VFX coverage: zero flat ground telegraphs or arrows, every locked VFX aligns exactly with
        // an early-locked hit or dash at (at + windup), and all 11 3D spell archetypes are active across Midra's kit.
        Set<SpellVfxKind> usedVfxKinds = new HashSet<>();
        int vfxCount = 0;
        for (MobBehaviour.Ability ability : timelines) {
            for (AbilityEvent event : ability.events()) {
                if (event instanceof HitEvent hit) {
                    assertFalse(hit.telegraph().show(), "Midra hits use custom 3D VFX instead of flat ground boxes");
                    assertFalse(hit.telegraph().arrow(), "Midra hits must not render flat ground arrows");
                } else if (event instanceof DashEvent dash) {
                    assertFalse(dash.telegraph().show(), "Midra thrust_lunge uses custom 3D lance_lunge instead of flat strip");
                    assertFalse(dash.telegraph().arrow(), "Midra thrust_lunge must not render a flat arrow");
                } else if (event instanceof VfxEvent vfx) {
                    vfxCount++;
                    usedVfxKinds.add(vfx.kind());
                    if (vfx.origin() == VfxEvent.VfxOrigin.LOCKED) {
                        double impact = vfx.at() + vfx.windup();
                        boolean matched = ability.events().stream().anyMatch(other -> {
                            if (other instanceof HitEvent hit) {
                                return FxEvent.sameMoment(hit.at(), impact) && hit.begin() <= vfx.at() + 1.0e-6;
                            }
                            if (other instanceof DashEvent dash) {
                                return FxEvent.sameMoment(dash.at(), impact) && dash.begin() <= vfx.at() + 1.0e-6;
                            }
                            return false;
                        });
                        assertTrue(matched, "Locked VFX " + vfx.kind() + " at " + vfx.at() + " (impact " + impact
                                + ") has no matching early-locked hit/dash in clip " + ability.clip());
                    }
                }
            }
        }
        assertTrue(vfxCount >= 35, "Expected custom 3D VFX across Midra's kit, got " + vfxCount);
        Set<SpellVfxKind> expectedMidraKinds = Set.of(
                SpellVfxKind.SLASH_ARC, SpellVfxKind.LANCE_LUNGE, SpellVfxKind.SWEEPING_BEAM,
                SpellVfxKind.EYE_CHARGE, SpellVfxKind.METEOR_STRIKE, SpellVfxKind.MAGMA_FISSURES,
                SpellVfxKind.NOVA_DOME, SpellVfxKind.SHOCKWAVE_RING, SpellVfxKind.FLAME_WAVE,
                SpellVfxKind.FLAME_PILLAR, SpellVfxKind.VORTEX_SLASH);
        assertEquals(expectedMidraKinds, usedVfxKinds,
                "All 11 Frenzied Flame 3D spell VFX kinds should be used in Midra's kit");
    }

    @Test
    void frenziedFlameDamageTypeAndTagsShip() throws Exception {
        JsonObject type = readJson("/data/mobs_ld/damage_type/frenzied_flame.json").getAsJsonObject();
        assertEquals("mobs_ld.frenzied_flame", type.get("message_id").getAsString());
        assertEquals("when_caused_by_living_non_player", type.get("scaling").getAsString());
        assertEquals("burning", type.get("effects").getAsString());

        for (String tag : List.of("/data/minecraft/tags/damage_type/bypasses_shield.json",
                "/data/spell_engine/tags/damage_type/evadable.json")) {
            JsonObject file = readJson(tag).getAsJsonObject();
            assertFalse(file.get("replace").getAsBoolean(), tag);
            assertTrue(file.getAsJsonArray("values").contains(new JsonPrimitive("mobs_ld:frenzied_flame")), tag);
        }
        assertFalse(readJson("/data/loot_stage/tags/damage_type/unscaled.json").toString().contains("frenzied_flame"),
                "Frenzied Flame scales with damage_dealt");
        assertEquals(ResourceKey.create(Registries.DAMAGE_TYPE,
                        ResourceLocation.fromNamespaceAndPath("mobs_ld", "frenzied_flame")),
                ModDamageTypes.FRENZIED_FLAME);
    }

    @Test
    void geckoLibModelAndAnimationsBakeCleanly() throws Exception {
        // Deserialize through GeckoLib's actual GSON and BakedModelFactory.
        Model rawModel = KeyFramesAdapter.GEO_GSON.fromJson(
                readJson("/assets/mobs_ld/geo/entity/midra.geo.json"), Model.class);
        BakedGeoModel bakedModel = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(rawModel));

        Set<String> boneNames = new HashSet<>();
        Deque<GeoBone> open = new ArrayDeque<>(bakedModel.topLevelBones());
        while (!open.isEmpty()) {
            GeoBone bone = open.removeFirst();
            boneNames.add(bone.getName());
            open.addAll(bone.getChildBones());
        }
        assertEquals(1, bakedModel.topLevelBones().size(), "Single root bone per docs/models.md");
        assertTrue(boneNames.contains("root") && boneNames.contains("head") && boneNames.contains("sword"));
        assertFalse(boneNames.contains("hitbox"), "Hidden ModelEngine hitbox bone must not be exported");
        assertEquals(30, boneNames.size(), "20 rig bones + 10 marker bones");

        Set<String> markers = Set.of("fx_feet", "fx_chest", "fx_eye", "fx_crown", "fx_hand_left",
                "fx_sword_mid", "fx_sword_tip", "hit_hand_left", "hit_sword_mid", "hit_sword_tip");
        assertTrue(boneNames.containsAll(markers), "All marker bones must exist in midra.geo.json");

        // Deserialize all 20 animations through GeckoLib's BakedAnimationsAdapter.
        JsonObject animRoot = readJson("/assets/mobs_ld/animations/entity/midra.animation.json").getAsJsonObject();
        BakedAnimations bakedAnims = KeyFramesAdapter.GEO_GSON.fromJson(
                animRoot.getAsJsonObject("animations"), BakedAnimations.class);
        assertEquals(20, bakedAnims.animations().size(), "All 20 Midra clips must deserialize cleanly");

        // Every animation referenced by midra.json must exist in midra.animation.json.
        MobBehaviour midra = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/midra.json")).getOrThrow();
        Set<String> referencedClips = new HashSet<>(List.of("idle", "walk", "hurt", "death"));
        midra.intro().ifPresent(a -> referencedClips.add(a.clipOr("intro")));
        midra.onDeath().ifPresent(a -> referencedClips.add(a.clipOr("death")));
        midra.abilities().forEach((name, a) -> referencedClips.add(a.clipOr(name)));
        midra.phases().forEach(p -> p.transition().ifPresent(t -> referencedClips.add(t.clipOr("transition"))));
        midra.boss().flatMap(MobBehaviour.Boss::softEnrage).flatMap(MobBehaviour.SoftEnrage::clip).ifPresent(referencedClips::add);

        for (String clip : referencedClips) {
            String full = "animation.mobs_ld.midra." + clip;
            assertNotNull(bakedAnims.getAnimation(full), "Missing clip in midra.animation.json: " + full);
        }

        // Validate every client-side particle, sound, and shake keyframe across all 20 clips.
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
        assertTrue(particleKeyframes >= 80, "Expected rich client-side particle keyframes, got " + particleKeyframes);
        assertTrue(soundKeyframes >= 3, "Expected client-side walk/idle sound keyframes, got " + soundKeyframes);
        assertTrue(shakeKeyframes >= 15, "Expected camera shake keyframes, got " + shakeKeyframes);
    }

    @Test
    void texturesAndBakedHitTracksShip() throws Exception {
        for (String tex : List.of("midra.png", "midra_glowmask.png", "midra_phase2.png", "midra_phase2_glowmask.png")) {
            assertNotNull(MidraBossTest.class.getResourceAsStream("/assets/mobs_ld/textures/entity/" + tex),
                    "Missing texture: " + tex);
        }

        JsonObject tracks = readJson("/data/mobs_ld/hit_tracks/midra.json").getAsJsonObject();
        assertEquals(1, tracks.get("version").getAsInt());
        assertEquals(20, tracks.get("rate").getAsInt());
        JsonObject clips = tracks.getAsJsonObject("clips");
        assertEquals(20, clips.size(), "All 20 clips must be baked in hit_tracks/midra.json");
        for (var entry : clips.entrySet()) {
            JsonObject clip = entry.getValue().getAsJsonObject();
            int len = clip.get("length").getAsInt();
            JsonObject bones = clip.getAsJsonObject("bones");
            assertEquals(30, bones.size(), "All 30 bones in hit track clip " + entry.getKey());
            assertEquals(len + 1, bones.getAsJsonArray("hit_sword_tip").size(),
                    "Tick count mismatch in clip " + entry.getKey());
        }
    }
}
