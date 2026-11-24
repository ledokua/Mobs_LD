package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.effect.BossStatusEffect;
import net.ledok.mobs_ld.mob.MobDeclaration;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.ledok.mobs_ld.mob.behaviour.event.ChainEvent;
import net.ledok.mobs_ld.mob.behaviour.event.CloudEvent;
import net.ledok.mobs_ld.mob.behaviour.event.DashEvent;
import net.ledok.mobs_ld.mob.behaviour.event.EffectEvent;
import net.ledok.mobs_ld.mob.behaviour.event.FxEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitSpec;
import net.ledok.mobs_ld.mob.behaviour.event.Origin;
import net.ledok.mobs_ld.mob.behaviour.event.TraceEvent;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end verification for Emberroot ({@code mobs_ld:emberroot}) and the custom boss status effect icons:
 * entity declaration, combat behaviour across all 14 clips (including {@code damdge -> hurt}), exclusive vanilla-RPG
 * 3D VFX (zero overlap with Midra and Amber Brute), custom {@code ember_blight} damage type, custom boss status
 * effects and 18x18 HUD icons, GeckoLib Catmull-Rom animation baking, textures, and pre-baked hit tracks.
 */
class EmberrootBossTest {
    private static final ResourceLocation EMBERROOT_ID =
            ResourceLocation.fromNamespaceAndPath("mobs_ld", "emberroot");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModEffects.register();
    }

    private static JsonElement readJson(String path) throws Exception {
        InputStream stream = EmberrootBossTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing resource: " + path);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    @Test
    void entityDeclarationLoadsAndAttributesExist() throws Exception {
        MobDeclaration declaration = MobDeclaration.codec(EMBERROOT_ID)
                .parse(JsonOps.INSTANCE, readJson("/mobs_ld/entities/emberroot.json"))
                .getOrThrow(msg -> new AssertionError("emberroot entity declaration: " + msg));

        assertEquals(2.4F, declaration.width());
        assertEquals(3.9F, declaration.height());
        assertEquals(2.9F, declaration.eyeHeight().orElseThrow());
        assertTrue(declaration.fireImmune(), "Molten-hearted Emberroot must be fire immune");
        assertEquals(70, declaration.deathTicks(), "Matches the 3.4 s death hold clip");
        assertTrue(declaration.spawnEgg().isPresent());
        assertEquals(3.2, declaration.render().walkBlocksPerCycle());
        assertEquals(1.45F, declaration.render().shadowRadius().orElseThrow());

        for (ResourceLocation attrId : declaration.attributes().keySet()) {
            assertTrue(BuiltInRegistries.ATTRIBUTE.containsKey(attrId), "Unknown attribute: " + attrId);
        }
        assertNotNull(EmberrootBossTest.class.getResourceAsStream(
                "/assets/mobs_ld/models/item/emberroot_spawn_egg.json"));
    }

    @Test
    void behaviourUsesAllAbilitiesExclusiveVfxAndValidAssets() throws Exception {
        MobBehaviour emberroot = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/emberroot.json"))
                .getOrThrow(msg -> new AssertionError("emberroot behaviour: " + msg));
        MobBehaviour midra = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/midra.json"))
                .getOrThrow(msg -> new AssertionError("midra behaviour: " + msg));
        MobBehaviour brute = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/amber_brute.json"))
                .getOrThrow(msg -> new AssertionError("amber_brute behaviour: " + msg));

        assertEquals(Optional.of(14.0),
                emberroot.declaredBase(ResourceLocation.withDefaultNamespace("generic.attack_damage")));
        assertTrue(emberroot.intro().isPresent());
        assertEquals("spawn", emberroot.intro().get().clip());
        assertTrue(emberroot.intro().get().invulnerable());
        assertTrue(emberroot.onDeath().isPresent());
        assertEquals("death", emberroot.onDeath().get().clip());

        // Raid boss structure: 3 phases, secondary target slots, and 5 dedicated min_players: 3 raid abilities.
        assertEquals(3, emberroot.phases().size());
        assertEquals(List.of(1.0, 0.6, 0.25),
                emberroot.phases().stream().map(MobBehaviour.Phase::belowHealth).toList());
        assertTrue(emberroot.phases().get(1).transition().isPresent(), "Phase 2 uses invulnerable rib_tear transition");
        assertTrue(emberroot.phases().get(1).transition().get().invulnerable());
        assertEquals(Optional.of("phase2"), emberroot.phases().get(1).texture());
        assertEquals(1.1, emberroot.phases().get(1).attackSpeed());
        assertEquals(1.2, emberroot.phases().get(2).attackSpeed());

        assertEquals(SlotPicker.Rule.SPREAD, emberroot.targetSlots().get("cinder_prey").rule());
        assertEquals(SlotPicker.Rule.LOWEST_THREAT, emberroot.targetSlots().get("ember_mark").rule());
        assertEquals(4.0, emberroot.targetSlots().get("ember_mark").offset());

        for (String raidAbility : List.of("root_cascade", "cataclysm_drum", "subterranean_hunt",
                "furnace_crossfire", "roll_crush_p2")) {
            MobBehaviour.Ability a = emberroot.abilities().get(raidAbility);
            assertNotNull(a, "Missing raid ability: " + raidAbility);
            assertEquals(Optional.of(3), a.minPlayers(), raidAbility + " must require at least 3 fighting players");
        }
        // Verify multi-wave each_fighter, multi-ring, top_damage hunt, rotated 6-ray star, and tauntable double roll.
        long rootCascadeWaves = emberroot.abilities().get("root_cascade").events().stream()
                .filter(e -> e instanceof HitEvent h && h.origin() == Origin.EACH_FIGHTER && h.lead().isPresent()).count();
        assertEquals(3, rootCascadeWaves, "root_cascade must pressure the entire raid with 3 leading waves");

        long cataclysmRings = emberroot.abilities().get("cataclysm_drum").events().stream()
                .filter(e -> e instanceof HitEvent h && h.shape() instanceof Shape.Ring).count();
        assertEquals(3, cataclysmRings, "cataclysm_drum must sweep 3 concentric arena rings");
        assertTrue(emberroot.abilities().get("cataclysm_drum").events().stream()
                .anyMatch(e -> e instanceof HitEvent h && h.origin() == Origin.EACH_FIGHTER));

        MobBehaviour.Ability hunt = emberroot.abilities().get("subterranean_hunt");
        assertEquals(MobBehaviour.TargetRule.TOP_DAMAGE, hunt.target());
        assertTrue(hunt.events().stream().anyMatch(e -> e instanceof EffectEvent eff && eff.on() == EffectEvent.On.AIM));
        assertTrue(hunt.events().stream().anyMatch(e -> e instanceof HitEvent h && h.origin() == Origin.EACH_FIGHTER));

        List<HitEvent> crossfireRays = emberroot.abilities().get("furnace_crossfire").events().stream()
                .filter(HitEvent.class::isInstance).map(HitEvent.class::cast).toList();
        assertEquals(2, crossfireRays.size(), "furnace_crossfire fires 2 rotated 6-ray star waves");
        assertTrue(crossfireRays.get(0).shape() instanceof Shape.Rays r0 && r0.count() == 6);
        assertTrue(crossfireRays.get(1).shape() instanceof Shape.Rays r1 && r1.count() == 6);
        assertEquals(Optional.of(30.0), crossfireRays.get(1).rotate());
        assertTrue(crossfireRays.get(1).turnBody());

        for (String name : List.of("furnace_blast", "furnace_crossfire")) {
            MobBehaviour.Ability furnace = emberroot.abilities().get(name);
            List<HitEvent> pulses = furnace.events().stream().filter(HitEvent.class::isInstance)
                    .map(HitEvent.class::cast).toList();
            assertEquals(2, pulses.size());
            for (HitEvent pulse : pulses) {
                assertTrue(pulse.blockedByBarrier());
                assertTrue(furnace.events().stream().anyMatch(e -> e instanceof VfxEvent visual
                        && visual.kind() == SpellVfxKind.CINDER_ERUPTION && visual.origin() == VfxEvent.VfxOrigin.LOCKED
                        && FxEvent.sameMoment(pulse.at(), visual.end())), "Missing matching visual for " + name);
            }
        }

        assertTrue(emberroot.abilities().get("roll_crush_p2").events().stream()
                .anyMatch(e -> e instanceof ChainEvent c && c.to().equals("roll_crush_follow") && c.tauntWins()));
        assertEquals(0, emberroot.abilities().get("roll_crush_follow").weight());
        for (String name : List.of("roll_crush", "roll_crush_p2", "roll_crush_follow")) {
            DashEvent roll = emberroot.abilities().get(name).events().stream()
                    .filter(DashEvent.class::isInstance).map(DashEvent.class::cast).findFirst().orElseThrow();
            assertEquals(Optional.of("roll_barrier_recover"), roll.onBarrier());
            assertTrue(roll.onStop().isEmpty());
        }
        assertEquals("hurt", emberroot.abilities().get("roll_barrier_recover").clipOr("roll_barrier_recover"));
        assertEquals(2, emberroot.abilities().get("rib_backlash").behind().orElseThrow().players());

        JsonObject enUs = readJson("/assets/mobs_ld/lang/en_us.json").getAsJsonObject();
        JsonObject ukUa = readJson("/assets/mobs_ld/lang/uk_ua.json").getAsJsonObject();
        for (String key : List.of("entity.mobs_ld.emberroot", "item.mobs_ld.emberroot_spawn_egg",
                "death.attack.mobs_ld.ember_blight", "death.attack.mobs_ld.ember_blight.player",
                "mobs_ld.stacks.cinder_mark", "effect.mobs_ld.frenzied_burn",
                "effect.mobs_ld.amber_mire", "effect.mobs_ld.charred_roots")) {
            assertTrue(enUs.has(key), "en_us missing " + key);
            assertTrue(ukUa.has(key), "uk_ua missing " + key);
        }

        List<MobBehaviour.Ability> timelines = allTimelines(emberroot);
        Set<SpellVfxKind> emberKinds = new HashSet<>();
        int soundCount = 0;
        int blightHits = 0;
        int traceCount = 0;
        int dashCount = 0;
        int vulnerableCount = 0;
        int charredRootsUses = 0;
        ResourceLocation charredRootsId = ResourceLocation.fromNamespaceAndPath("mobs_ld", "charred_roots");

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
                    assertFalse(hit.telegraph().show(), "Emberroot uses 3D VFX instead of flat ground boxes");
                    assertFalse(hit.telegraph().arrow(), "Emberroot must not draw flat ground arrows");
                    if (hit.hit().damageType().filter(id -> id.equals(ModDamageTypes.EMBER_BLIGHT.location())).isPresent()) {
                        blightHits++;
                    }
                    for (HitSpec.EffectSpec eff : hit.hit().effects()) {
                        assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()), "Unknown effect: " + eff.id());
                        if (eff.id().equals(charredRootsId)) {
                            charredRootsUses++;
                        }
                    }
                } else if (event instanceof DashEvent dash) {
                    dashCount++;
                    assertFalse(dash.telegraph().show(), "Emberroot dashes must not draw flat ground strips");
                    assertFalse(dash.telegraph().arrow(), "Emberroot dashes must not draw flat ground arrows");
                    if (dash.hit().isPresent()) {
                        for (HitSpec.EffectSpec eff : dash.hit().get().effects()) {
                            assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()), "Unknown effect: " + eff.id());
                        }
                    }
                } else if (event instanceof TraceEvent trace) {
                    traceCount++;
                    for (HitSpec.EffectSpec eff : trace.hit().effects()) {
                        assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()), "Unknown effect: " + eff.id());
                    }
                } else if (event instanceof CloudEvent cloud) {
                    for (HitSpec.EffectSpec eff : cloud.effects()) {
                        assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()), "Unknown effect: " + eff.id());
                        if (eff.id().equals(charredRootsId)) {
                            charredRootsUses++;
                        }
                    }
                } else if (event instanceof VulnerableEvent) {
                    vulnerableCount++;
                } else if (event instanceof VfxEvent vfx) {
                    emberKinds.add(vfx.kind());
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
        assertTrue(soundCount >= 28, "Expected rich sound cues, got " + soundCount);
        assertTrue(blightHits >= 9, "Expected ember_blight hits, got " + blightHits);
        assertTrue(traceCount >= 1, "Expected real animation-driven right-hand swipe trace");
        assertTrue(dashCount >= 3, "Expected shoulder_barge, burrow_ambush, and roll_crush dashes, got " + dashCount);
        assertTrue(vulnerableCount >= 7, "Expected generous vulnerable punish windows, got " + vulnerableCount);
        assertTrue(charredRootsUses >= 6, "Expected custom mobs_ld:charred_roots debuff applications, got " + charredRootsUses);

        Set<SpellVfxKind> expectedEmberKinds = Set.of(
                SpellVfxKind.ROOT_TENDRILS, SpellVfxKind.CINDER_ERUPTION, SpellVfxKind.ROLL_TRAIL,
                SpellVfxKind.BURROW_MOUND, SpellVfxKind.DRUM_TREMOR, SpellVfxKind.RIB_FURNACE);
        assertEquals(expectedEmberKinds, emberKinds, "Emberroot must use all 6 exclusive charred-furnace VFX kinds");

        // Strict three-way pairwise VFX separation across Midra, Amber Brute, and Emberroot.
        Set<SpellVfxKind> midraKinds = collectVfxKinds(midra);
        Set<SpellVfxKind> bruteKinds = collectVfxKinds(brute);
        assertTrue(Collections.disjoint(emberKinds, midraKinds), "Emberroot must not repeat Midra's VFX");
        assertTrue(Collections.disjoint(emberKinds, bruteKinds), "Emberroot must not repeat Amber Brute's VFX");
        assertTrue(Collections.disjoint(midraKinds, bruteKinds), "Midra and Amber Brute VFX must stay disjoint");
    }

    @Test
    void customBossStatusEffectsAndPixelArtIconsShip() throws Exception {
        for (String name : List.of("frenzied_burn", "amber_mire", "charred_roots", "storm_static")) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mobs_ld", name);
            assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(id), "Missing registered MobEffect: " + id);
            assertInstanceOf(BossStatusEffect.class, BuiltInRegistries.MOB_EFFECT.get(id),
                    "Boss status effect should extend BossStatusEffect: " + id);

            String iconPath = "/assets/mobs_ld/textures/mob_effect/" + name + ".png";
            try (InputStream stream = EmberrootBossTest.class.getResourceAsStream(iconPath)) {
                assertNotNull(stream, "Missing 18x18 status effect icon: " + iconPath);
                BufferedImage img = ImageIO.read(stream);
                assertNotNull(img, "Unreadable PNG icon: " + iconPath);
                assertEquals(18, img.getWidth(), "Minecraft mob_effect icon width must be 18: " + iconPath);
                assertEquals(18, img.getHeight(), "Minecraft mob_effect icon height must be 18: " + iconPath);
            }
        }
    }

    @Test
    void emberBlightDamageTypeAndTagsShip() throws Exception {
        JsonObject type = readJson("/data/mobs_ld/damage_type/ember_blight.json").getAsJsonObject();
        assertEquals("mobs_ld.ember_blight", type.get("message_id").getAsString());
        assertEquals("when_caused_by_living_non_player", type.get("scaling").getAsString());
        assertEquals("burning", type.get("effects").getAsString());

        for (String tag : List.of("/data/minecraft/tags/damage_type/bypasses_shield.json",
                "/data/spell_engine/tags/damage_type/evadable.json")) {
            JsonObject file = readJson(tag).getAsJsonObject();
            assertFalse(file.get("replace").getAsBoolean(), tag);
            assertTrue(file.getAsJsonArray("values").contains(new JsonPrimitive("mobs_ld:ember_blight")), tag);
        }
        assertFalse(readJson("/data/loot_stage/tags/damage_type/unscaled.json").toString().contains("ember_blight"),
                "Ember Blight scales with damage_dealt");
        assertEquals(ResourceKey.create(Registries.DAMAGE_TYPE,
                        ResourceLocation.fromNamespaceAndPath("mobs_ld", "ember_blight")),
                ModDamageTypes.EMBER_BLIGHT);
    }

    @Test
    void geckoLibModelAndAll14SmoothedAnimationsBakeCleanly() throws Exception {
        Model rawModel = KeyFramesAdapter.GEO_GSON.fromJson(
                readJson("/assets/mobs_ld/geo/entity/emberroot.geo.json"), Model.class);
        BakedGeoModel bakedModel = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(rawModel));

        Set<String> boneNames = new HashSet<>();
        Deque<GeoBone> open = new ArrayDeque<>(bakedModel.topLevelBones());
        while (!open.isEmpty()) {
            GeoBone bone = open.removeFirst();
            boneNames.add(bone.getName());
            open.addAll(bone.getChildBones());
        }
        assertEquals(1, bakedModel.topLevelBones().size(), "Single root bone per docs/models.md");
        assertTrue(boneNames.contains("root") && boneNames.contains("body") && boneNames.contains("core"));
        assertFalse(boneNames.contains("hitbox"), "Hidden ModelEngine hitbox bone must not be exported");
        assertEquals(24, boneNames.size(), "16 rig bones + 8 marker bones");

        Set<String> markers = Set.of("fx_feet", "fx_core", "fx_crown", "fx_shoulder_right",
                "fx_hand_right", "hit_hand_right", "fx_hand_left", "hit_hand_left");
        assertTrue(boneNames.containsAll(markers), "All marker bones must exist in emberroot.geo.json");

        JsonObject animRoot = readJson("/assets/mobs_ld/animations/entity/emberroot.animation.json").getAsJsonObject();
        BakedAnimations bakedAnims = KeyFramesAdapter.GEO_GSON.fromJson(
                animRoot.getAsJsonObject("animations"), BakedAnimations.class);
        assertEquals(14, bakedAnims.animations().size(), "All 14 Emberroot clips must bake cleanly");
        assertNull(bakedAnims.getAnimation("animation.mobs_ld.emberroot.damdge"),
                "Misspelled damdge clip must be mapped to hurt");
        assertNotNull(bakedAnims.getAnimation("animation.mobs_ld.emberroot.hurt"),
                "Renamed hurt clip must exist so LdMob hurt controller triggers");

        MobBehaviour emberroot = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/emberroot.json")).getOrThrow();
        Set<String> referencedClips = new HashSet<>(List.of("idle", "walk", "hurt", "death"));
        emberroot.intro().ifPresent(a -> referencedClips.add(a.clipOr("intro")));
        emberroot.onDeath().ifPresent(a -> referencedClips.add(a.clipOr("death")));
        emberroot.abilities().forEach((name, a) -> referencedClips.add(a.clipOr(name)));
        emberroot.phases().forEach(p -> p.transition().ifPresent(t -> referencedClips.add(t.clipOr("transition"))));
        emberroot.boss().flatMap(MobBehaviour.Boss::softEnrage).flatMap(MobBehaviour.SoftEnrage::clip)
                .ifPresent(referencedClips::add);

        for (String clip : referencedClips) {
            String full = "animation.mobs_ld.emberroot." + clip;
            assertNotNull(bakedAnims.getAnimation(full), "Missing clip in emberroot.animation.json: " + full);
        }
        assertEquals(bakedAnims.animations().size(), referencedClips.size(),
                "Every single one of the 14 exported Emberroot clips must be actively used");

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
        assertTrue(catmullCount >= 200, "Expected smooth Catmull-Rom keyframes across Emberroot clips, got " + catmullCount);
        assertTrue(particleKeyframes >= 40, "Expected client particle keyframes, got " + particleKeyframes);
        assertTrue(soundKeyframes >= 3, "Expected client walk/idle sound keyframes, got " + soundKeyframes);
        assertTrue(shakeKeyframes >= 18, "Expected camera shake keyframes, got " + shakeKeyframes);
    }

    @Test
    void texturesAndBakedHitTracksShip() throws Exception {
        for (String tex : List.of("emberroot.png", "emberroot_glowmask.png",
                "emberroot_phase2.png", "emberroot_phase2_glowmask.png")) {
            assertNotNull(EmberrootBossTest.class.getResourceAsStream("/assets/mobs_ld/textures/entity/" + tex),
                    "Missing texture: " + tex);
        }

        JsonObject tracks = readJson("/data/mobs_ld/hit_tracks/emberroot.json").getAsJsonObject();
        assertEquals(1, tracks.get("version").getAsInt());
        assertEquals(20, tracks.get("rate").getAsInt());
        JsonObject clips = tracks.getAsJsonObject("clips");
        assertEquals(14, clips.size(), "All 14 clips must be baked in hit_tracks/emberroot.json");
        for (var entry : clips.entrySet()) {
            JsonObject clip = entry.getValue().getAsJsonObject();
            int len = clip.get("length").getAsInt();
            JsonObject bones = clip.getAsJsonObject("bones");
            assertEquals(24, bones.size(), "All 24 bones in hit track clip " + entry.getKey());
            assertEquals(len + 1, bones.getAsJsonArray("hit_hand_right").size(),
                    "Tick count mismatch in clip " + entry.getKey());
            assertEquals(len + 1, bones.getAsJsonArray("fx_core").size(),
                    "Tick count mismatch in clip " + entry.getKey());
        }
    }

    @Test
    void dashesCoverTriggerRangeAndStepHeightPreventsSnags() throws Exception {
        MobDeclaration declaration = MobDeclaration.codec(EMBERROOT_ID)
                .parse(JsonOps.INSTANCE, readJson("/mobs_ld/entities/emberroot.json")).getOrThrow();
        assertTrue(declaration.attributes().getOrDefault(
                        ResourceLocation.withDefaultNamespace("generic.step_height"), 0.0) >= 1.0,
                "Emberroot needs >= 1.0 step_height so charges do not snag on 1-block steps");

        MobBehaviour emberroot = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                        readJson("/data/mobs_ld/mob_behaviour/emberroot.json")).getOrThrow();
        for (String name : List.of("shoulder_barge", "roll_crush", "roll_crush_p2")) {
            MobBehaviour.Ability ability = emberroot.abilities().get(name);
            DashEvent dash = ability.events().stream().filter(DashEvent.class::isInstance)
                    .map(DashEvent.class::cast).findFirst().orElseThrow();
            assertTrue(dash.hit().isPresent(), name + " dash must deal contact damage");
            assertTrue(dash.maxDistance() >= ability.range().max() + dash.overshoot(),
                    name + " max_distance must reach targets at range.max plus overshoot");
        }
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
