package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.MobDeclaration;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.ledok.mobs_ld.mob.behaviour.event.CloudEvent;
import net.ledok.mobs_ld.mob.behaviour.event.DashEvent;
import net.ledok.mobs_ld.mob.behaviour.event.DespawnEvent;
import net.ledok.mobs_ld.mob.behaviour.event.FxEvent;
import net.ledok.mobs_ld.mob.behaviour.event.GrabEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitSpec;
import net.ledok.mobs_ld.mob.behaviour.event.ListenEvent;
import net.ledok.mobs_ld.mob.behaviour.event.Origin;
import net.ledok.mobs_ld.mob.behaviour.event.PullEvent;
import net.ledok.mobs_ld.mob.behaviour.event.SummonEvent;
import net.ledok.mobs_ld.mob.behaviour.event.TraceEvent;
import net.ledok.mobs_ld.mob.behaviour.event.VfxEvent;
import net.ledok.mobs_ld.network.SpellVfxKind;
import net.ledok.mobs_ld.registry.ModEffects;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.EasingType;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
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
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end verification for all custom boss adds — the four thematic minions ({@code frenzy_wisp},
 * {@code amber_mite}, {@code ember_cinderling}, {@code storm_cloudlet}) and the three distinct mechanic structures
 * ({@code amber_monolith}, {@code ember_pyre}, {@code storm_rod}) — plus each boss's unique signature encounter
 * mechanic, global arena ultimate, expanded minion pack sizes, and faster storm cloudlets.
 */
class CustomMinionsTest {
    private static final List<String> ALL_CUSTOM_ADDS = List.of(
            "frenzy_wisp", "amber_mite", "ember_cinderling", "storm_cloudlet",
            "amber_monolith", "ember_pyre", "storm_rod");

    private static final Set<String> FLYING_ADDS = Set.of("frenzy_wisp", "storm_cloudlet");
    private static final Set<String> STATIONARY_STRUCTURES = Set.of("amber_monolith", "ember_pyre", "storm_rod");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModEffects.register();
    }

    private static JsonElement readJson(String path) throws Exception {
        InputStream stream = CustomMinionsTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing resource: " + path);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    @Test
    void allCustomAddDeclarationsAttributesAndTexturesShipCleanly() throws Exception {
        JsonObject enUs = readJson("/assets/mobs_ld/lang/en_us.json").getAsJsonObject();
        JsonObject ukUa = readJson("/assets/mobs_ld/lang/uk_ua.json").getAsJsonObject();
        ResourceLocation flyingSpeed = ResourceLocation.withDefaultNamespace("generic.flying_speed");
        ResourceLocation stepHeight = ResourceLocation.withDefaultNamespace("generic.step_height");
        ResourceLocation kbRes = ResourceLocation.withDefaultNamespace("generic.knockback_resistance");

        assertTrue(enUs.has("mobs_ld.shield.blocked_by_adds"));
        assertTrue(ukUa.has("mobs_ld.shield.blocked_by_adds"));

        for (String name : ALL_CUSTOM_ADDS) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mobs_ld", name);
            MobDeclaration declaration = MobDeclaration.codec(id)
                    .parse(JsonOps.INSTANCE, readJson("/mobs_ld/entities/" + name + ".json"))
                    .getOrThrow(msg -> new AssertionError(name + " entity declaration: " + msg));

            assertTrue(declaration.spawnEgg().isPresent(), name + " must declare a spawn egg");
            assertNotNull(CustomMinionsTest.class.getResourceAsStream(
                    "/assets/mobs_ld/models/item/" + name + "_spawn_egg.json"),
                    "Missing spawn egg model for " + name);

            for (ResourceLocation attrId : declaration.attributes().keySet()) {
                assertTrue(BuiltInRegistries.ATTRIBUTE.containsKey(attrId),
                        "Unknown attribute " + attrId + " on " + name);
            }

            if (FLYING_ADDS.contains(name)) {
                assertEquals(MobDeclaration.Body.FLYING, declaration.body(), name + " must be a flying mob");
                assertTrue(declaration.attributes().containsKey(flyingSpeed),
                        name + " requires generic.flying_speed for FlyingMoveControl");
                if (name.equals("storm_cloudlet")) {
                    assertTrue(declaration.attributes().get(flyingSpeed) >= 0.38,
                            "storm_cloudlet should fly briskly after targets");
                }
            } else {
                assertEquals(MobDeclaration.Body.GROUND, declaration.body(), name + " must be a ground mob");
                assertTrue(declaration.attributes().getOrDefault(stepHeight, 0.0) >= 1.0,
                        name + " should step cleanly over 1-block ledges");
            }
            if (STATIONARY_STRUCTURES.contains(name)) {
                assertEquals(1.0, declaration.attributes().getOrDefault(kbRes, 0.0),
                        name + " pillar/totem must be immune to knockback");
            }

            for (String key : List.of("entity.mobs_ld." + name, "item.mobs_ld." + name + "_spawn_egg")) {
                assertTrue(enUs.has(key), "en_us missing " + key);
                assertTrue(ukUa.has(key), "uk_ua missing " + key);
            }

            for (String suffix : List.of(".png", "_glowmask.png")) {
                String texPath = "/assets/mobs_ld/textures/entity/" + name + suffix;
                try (InputStream stream = CustomMinionsTest.class.getResourceAsStream(texPath)) {
                    assertNotNull(stream, "Missing texture: " + texPath);
                    BufferedImage img = ImageIO.read(stream);
                    assertNotNull(img, "Unreadable PNG: " + texPath);
                    assertEquals(64, img.getWidth(), texPath + " width must be 64");
                    assertEquals(64, img.getHeight(), texPath + " height must be 64");
                }
            }
        }
    }

    @Test
    void allCustomAddBehavioursModelsAndAnimationsBakeCleanly() throws Exception {
        for (String name : ALL_CUSTOM_ADDS) {
            MobBehaviour behaviour = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                            readJson("/data/mobs_ld/mob_behaviour/" + name + ".json"))
                    .getOrThrow(msg -> new AssertionError(name + " behaviour: " + msg));

            if (FLYING_ADDS.contains(name)) {
                assertTrue(behaviour.movement().hoverHeight().isPresent(),
                        name + " must declare movement.hover_height to cruise above targets");
                assertTrue(behaviour.movement().hoverHeight().get() >= 2.0,
                        name + " hover_height should keep it airborne above player heads/chests");
                if (name.equals("storm_cloudlet")) {
                    assertTrue(behaviour.movement().speed() >= 1.15,
                            "storm_cloudlet movement speed must be tuned briskly");
                }
            }
            if (STATIONARY_STRUCTURES.contains(name)) {
                assertEquals(MobBehaviour.Movement.Style.STATIONARY, behaviour.movement().style(),
                        name + " pillar/totem must stay stationary");
            }

            List<MobBehaviour.Ability> timelines = new ArrayList<>(behaviour.abilities().values());
            behaviour.intro().ifPresent(timelines::add);
            behaviour.onDeath().ifPresent(timelines::add);

            for (MobBehaviour.Ability ability : timelines) {
                for (AbilityEvent event : ability.events()) {
                    if (event instanceof FxEvent fx && fx.sound().isPresent()) {
                        assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(fx.sound().get().id()),
                                "Unknown sound in " + name + ": " + fx.sound().get().id());
                    } else if (event instanceof HitEvent hit) {
                        assertFalse(hit.telegraph().show(), name + " uses 3D VFX instead of flat boxes");
                        assertFalse(hit.telegraph().arrow(), name + " must not draw flat ground arrows");
                        for (HitSpec.EffectSpec eff : hit.hit().effects()) {
                            assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()),
                                    "Unknown effect in " + name + ": " + eff.id());
                        }
                    } else if (event instanceof DashEvent dash) {
                        assertFalse(dash.telegraph().show(), name + " dashes use 3D VFX");
                        assertFalse(dash.telegraph().arrow(), name + " dashes must not draw arrows");
                        if (dash.hit().isPresent()) {
                            for (HitSpec.EffectSpec eff : dash.hit().get().effects()) {
                                assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()),
                                        "Unknown dash effect in " + name + ": " + eff.id());
                            }
                        }
                    } else if (event instanceof CloudEvent cloud) {
                        for (HitSpec.EffectSpec eff : cloud.effects()) {
                            assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(eff.id()),
                                    "Unknown cloud effect in " + name + ": " + eff.id());
                        }
                    } else if (event instanceof VfxEvent vfx && vfx.origin() == VfxEvent.VfxOrigin.LOCKED) {
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
                        assertTrue(matched, "Locked VFX " + vfx.kind() + " in " + name + "/" + ability.clip()
                                + " has no matching early-locked hit/dash at " + impact);
                    }
                }
            }

            // Bake GeckoLib model and animations and verify bone hierarchy, clips, and keyframes.
            Model rawModel = KeyFramesAdapter.GEO_GSON.fromJson(
                    readJson("/assets/mobs_ld/geo/entity/" + name + ".geo.json"), Model.class);
            BakedGeoModel bakedModel = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(rawModel));
            assertEquals(1, bakedModel.topLevelBones().size(), name + " must have a single root bone");

            Set<String> bones = new HashSet<>();
            Deque<GeoBone> open = new ArrayDeque<>(bakedModel.topLevelBones());
            while (!open.isEmpty()) {
                GeoBone bone = open.removeFirst();
                bones.add(bone.getName());
                open.addAll(bone.getChildBones());
            }
            assertTrue(bones.contains("root"), name + " must have a root bone");

            JsonObject animRoot = readJson("/assets/mobs_ld/animations/entity/" + name + ".animation.json").getAsJsonObject();
            BakedAnimations bakedAnims = KeyFramesAdapter.GEO_GSON.fromJson(
                    animRoot.getAsJsonObject("animations"), BakedAnimations.class);
            assertEquals(6, bakedAnims.animations().size(), name + " must ship all 6 clips");

            Set<String> referencedClips = new HashSet<>(List.of("idle", "walk", "hurt", "death"));
            behaviour.intro().ifPresent(a -> referencedClips.add(a.clipOr("intro")));
            behaviour.onDeath().ifPresent(a -> referencedClips.add(a.clipOr("death")));
            behaviour.abilities().forEach((key, a) -> referencedClips.add(a.clipOr(key)));
            for (String clip : referencedClips) {
                String full = "animation.mobs_ld." + name + "." + clip;
                assertNotNull(bakedAnims.getAnimation(full), "Missing animation clip " + full);
            }

            int catmullCount = 0;
            int particleCount = 0;
            for (Animation anim : bakedAnims.animations().values()) {
                for (BoneAnimation ba : anim.boneAnimations()) {
                    for (var kf : ba.rotationKeyFrames().xKeyframes()) {
                        if (kf.easingType() == EasingType.CATMULLROM) {
                            catmullCount++;
                        }
                    }
                }
                for (ParticleKeyframeData pk : anim.keyFrames().particles()) {
                    particleCount++;
                    ResourceLocation pid = ResourceLocation.parse(pk.getEffect());
                    ParticleType<?> ptype = BuiltInRegistries.PARTICLE_TYPE.get(pid);
                    assertTrue(ptype instanceof SimpleParticleType,
                            "Custom add particle must be SimpleParticleType: " + pid + " in " + anim.name());
                    assertTrue(bones.contains(pk.getLocator()),
                            "Particle locator " + pk.getLocator() + " missing in " + name + ".geo.json");
                }
                for (SoundKeyframeData sk : anim.keyFrames().sounds()) {
                    ResourceLocation sid = ResourceLocation.parse(sk.getSound());
                    assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(sid),
                            "Unknown client sound " + sid + " in " + anim.name());
                }
            }
            assertTrue(catmullCount >= 8, name + " should use smooth Catmull-Rom keyframes, got " + catmullCount);
            assertTrue(particleCount >= 4, name + " should emit client particle keyframes, got " + particleCount);
        }
    }

    @Test
    void allFourBossesHaveLargerMinionPacksDistinctMechanicsAndGlobalAttacks() throws Exception {
        Map<String, String> bossToMinion = Map.of(
                "midra", "frenzy_wisp",
                "amber_brute", "amber_mite",
                "emberroot", "ember_cinderling",
                "nameless_king", "storm_cloudlet");
        Map<String, String> bossToSummonAbility = Map.of(
                "midra", "summon_wisps",
                "amber_brute", "summon_mites",
                "emberroot", "summon_cinderlings",
                "nameless_king", "summon_cloudlets");
        Map<String, List<String>> bossNewRangedAbilities = Map.of(
                "midra", List.of("frenzy_barrage"),
                "amber_brute", List.of("amber_barrage", "amber_mortar"),
                "emberroot", List.of("cinder_barrage"),
                "nameless_king", List.of("sunlight_spear", "sunlight_spear_p2"));

        for (var entry : bossToMinion.entrySet()) {
            String bossName = entry.getKey();
            ResourceLocation expectedMinion = ResourceLocation.fromNamespaceAndPath("mobs_ld", entry.getValue());
            MobBehaviour boss = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                            readJson("/data/mobs_ld/mob_behaviour/" + bossName + ".json"))
                    .getOrThrow(msg -> new AssertionError(bossName + " behaviour: " + msg));

            String summonKey = bossToSummonAbility.get(bossName);
            MobBehaviour.Ability summonAbility = boss.abilities().get(summonKey);
            assertNotNull(summonAbility, bossName + " missing summon ability " + summonKey);
            assertTrue(summonAbility.skipWhenFull(), summonKey + " must set skip_when_full: true");
            for (MobBehaviour.Phase phase : boss.phases()) {
                assertTrue(phase.abilities().contains(summonKey),
                        bossName + " phase " + phase.belowHealth() + " should include " + summonKey);
            }

            SummonEvent abilitySummon = summonAbility.events().stream()
                    .filter(SummonEvent.class::isInstance).map(SummonEvent.class::cast).findFirst().orElseThrow();
            MobBehaviour.Ability p2Transition = boss.phases().get(1).transition().orElseThrow();
            SummonEvent transitionMinionSummon = p2Transition.events().stream()
                    .filter(e -> e instanceof SummonEvent s && s.entity().equals(expectedMinion))
                    .map(SummonEvent.class::cast).findFirst().orElseThrow();

            for (SummonEvent summon : List.of(abilitySummon, transitionMinionSummon)) {
                assertEquals(expectedMinion, summon.entity(), bossName + " must summon " + expectedMinion);
                assertTrue(summon.perType(), bossName + " minion summon must use per_type: true");
                assertTrue(summon.count().min() >= 3, bossName + " should spawn larger packs (>= 3 per wave)");
                assertTrue(summon.maxAlive().base() >= 5, bossName + " should allow >= 5 live minions");
                assertTrue(summon.bound() && !summon.loot() && !summon.xp());
                boolean expectGround = !FLYING_ADDS.contains(entry.getValue());
                assertEquals(expectGround, summon.placement().orElseThrow().ground());
            }

            for (String rangedKey : bossNewRangedAbilities.get(bossName)) {
                MobBehaviour.Ability ranged = boss.abilities().get(rangedKey);
                assertNotNull(ranged, bossName + " missing ranged ability " + rangedKey);
                assertTrue(ranged.includeUnreachable() && ranged.range().max() >= 26.0);
            }
            long rangedCount = boss.abilities().values().stream()
                    .filter(a -> a.range().max() >= 14.0 && !a.skipWhenFull()).count();
            assertTrue(rangedCount >= 4, bossName + " should have >= 4 ranged abilities, got " + rangedCount);
        }

        // 1. Amber Brute: invulnerable monolith barrier + Tectonic Cataclysm global.
        MobBehaviour brute = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                readJson("/data/mobs_ld/mob_behaviour/amber_brute.json")).getOrThrow();
        ResourceLocation monolithId = ResourceLocation.fromNamespaceAndPath("mobs_ld", "amber_monolith");
        for (MobBehaviour.Phase phase : brute.phases()) {
            assertTrue(phase.damageTaken().stream().anyMatch(d ->
                            d.kind() == MobBehaviour.DamageTaken.Kind.ALL && d.multiplier() == 0.0
                                    && d.whileAdds().equals(Optional.of(monolithId))),
                    "Amber Brute must be invulnerable while amber_monolith pillars stand");
        }
        SummonEvent raiseMonoliths = brute.abilities().get("raise_monoliths").events().stream()
                .filter(SummonEvent.class::isInstance).map(SummonEvent.class::cast).findFirst().orElseThrow();
        assertEquals(monolithId, raiseMonoliths.entity());
        assertTrue(raiseMonoliths.perType() && raiseMonoliths.minionLink().isPresent());
        assertTrue(brute.abilities().get("tectonic_cataclysm").events().stream()
                .filter(e -> e instanceof HitEvent h && h.shape() instanceof Shape.Ring).count() >= 2);

        // 2. Midra: Madness Gaze (ListenEvent freeze-or-burn) + Frenzy Apocalypse global + P2 ground wave fan.
        MobBehaviour midra = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                readJson("/data/mobs_ld/mob_behaviour/midra.json")).getOrThrow();
        ListenEvent gaze = midra.abilities().get("madness_gaze").events().stream()
                .filter(ListenEvent.class::isInstance).map(ListenEvent.class::cast).findFirst().orElseThrow();
        assertEquals(Optional.of("thrust_lunge"), gaze.chainTo());
        assertTrue(gaze.range() >= 34.0);
        assertEquals(2, midra.abilities().get("frenzy_apocalypse").events().stream()
                .filter(e -> e instanceof HitEvent h && h.shape() instanceof Shape.Rays r && r.count() == 8).count());
        assertNotNull(midra.abilities().get("ground_wave_sweep_p2"));

        // 3. Emberroot: Percentage-based life-siphoning Ember Pyres (+ 55% armor) + World Furnace Nova in/out safe-zone.
        MobBehaviour ember = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                readJson("/data/mobs_ld/mob_behaviour/emberroot.json")).getOrThrow();
        ResourceLocation pyreId = ResourceLocation.fromNamespaceAndPath("mobs_ld", "ember_pyre");
        SummonEvent kindle = ember.abilities().get("kindle_pyres").events().stream()
                .filter(SummonEvent.class::isInstance).map(SummonEvent.class::cast).findFirst().orElseThrow();
        SummonEvent p2Pyres = ember.phases().get(1).transition().orElseThrow().events().stream()
                .filter(e -> e instanceof SummonEvent s && s.entity().equals(pyreId))
                .map(SummonEvent.class::cast).findFirst().orElseThrow();
        for (SummonEvent pyreSummon : List.of(kindle, p2Pyres)) {
            assertEquals(pyreId, pyreSummon.entity());
            assertTrue(pyreSummon.perType());
            SummonEvent.MinionLink pyreLink = pyreSummon.minionLink().orElseThrow();
            assertEquals(0.0, pyreLink.whileAliveHealPerSecond(), 1.0e-9,
                    "Healing summons must scale by boss max-HP percentage rather than flat HP");
            assertTrue(pyreLink.whileAliveHealPercentPerSecond() >= 0.01
                            && pyreLink.whileAliveHealPercentPerSecond() <= 0.05,
                    "Ember Pyre heal percentage must be fair per live pyre (got "
                            + pyreLink.whileAliveHealPercentPerSecond() + ")");
        }
        for (MobBehaviour.Phase phase : ember.phases()) {
            assertTrue(phase.damageTaken().stream().anyMatch(d ->
                    d.whileAdds().equals(Optional.of(pyreId)) && d.multiplier() > 0.0 && d.multiplier() < 1.0));
        }
        MobBehaviour.Ability nova = ember.abilities().get("world_furnace_nova");
        assertTrue(nova.events().stream().anyMatch(e -> e instanceof HitEvent h
                && h.shape() instanceof Shape.Ring r && r.inner() >= 6.0 && r.outer() >= 30.0
                && h.at() - h.begin() >= 1.4),
                "world_furnace_nova outer inferno must give players >= 1.4 s to reach the safe center");
        assertTrue(nova.events().stream().anyMatch(e -> e instanceof HitEvent h
                && h.shape() instanceof Shape.Circle c && c.radius() >= 7.0));

        // 4. Nameless King: Dodgeable Timed-Overload Storm Conduits + Wrath of the Firstborn global + P2 storm wave fan.
        MobBehaviour king = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                readJson("/data/mobs_ld/mob_behaviour/nameless_king.json")).getOrThrow();
        ResourceLocation rodId = ResourceLocation.fromNamespaceAndPath("mobs_ld", "storm_rod");
        SummonEvent callRods = king.abilities().get("call_conductors").events().stream()
                .filter(SummonEvent.class::isInstance).map(SummonEvent.class::cast).findFirst().orElseThrow();
        assertEquals(rodId, callRods.entity());
        assertTrue(callRods.perType() && callRods.minionLink().isPresent());
        MobBehaviour stormRod = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                readJson("/data/mobs_ld/mob_behaviour/storm_rod.json")).getOrThrow();
        MobBehaviour.Ability overload = stormRod.abilities().get("conduit_overload");
        assertEquals(Optional.of(10.0), overload.forceAfter(), "Storm Conduits overload after 10 s if not destroyed");
        assertTrue(overload.events().stream().anyMatch(DespawnEvent.class::isInstance));
        HitEvent overloadHit = overload.events().stream()
                .filter(HitEvent.class::isInstance).map(HitEvent.class::cast).findFirst().orElseThrow();
        assertTrue(overloadHit.shape() instanceof Shape.Ring,
                "Storm Conduit overload must be a hollow ring so melee players at the rod's feet are safe");
        Shape.Ring overloadRing = (Shape.Ring) overloadHit.shape();
        assertTrue(overloadRing.inner() >= 2.2 && overloadRing.outer() <= 7.0,
                "Storm Conduit overload ring must have a safe center (>= 2.2) and dodgeable outer radius (<= 7.0), got "
                        + overloadRing.inner() + ".." + overloadRing.outer());
        assertTrue(overloadHit.at() - overloadHit.begin() >= 1.0,
                "Storm Conduit overload must telegraph >= 1.0 s before striking");
        assertTrue(king.abilities().get("wrath_of_the_firstborn").events().stream()
                .anyMatch(e -> e instanceof HitEvent h && h.origin() == Origin.EACH_FIGHTER));
        assertNotNull(king.abilities().get("storm_wave_p2"));

        // 5. No custom boss ability may attach two distinct locked VFX kinds onto a timestamp shared by multiple
        //    different HitEvents (otherwise VfxEvent.resolveLocked would cross-spawn each VFX onto the other hit).
        for (Map.Entry<String, MobBehaviour> pair : Map.of(
                "midra", midra, "amber_brute", brute, "emberroot", ember, "nameless_king", king).entrySet()) {
            List<MobBehaviour.Ability> timelines = new ArrayList<>(pair.getValue().abilities().values());
            pair.getValue().phases().forEach(p -> p.transition().ifPresent(timelines::add));
            for (MobBehaviour.Ability ability : timelines) {
                List<VfxEvent> locked = ability.events().stream()
                        .filter(e -> e instanceof VfxEvent v && v.origin() == VfxEvent.VfxOrigin.LOCKED)
                        .map(VfxEvent.class::cast).toList();
                for (int i = 0; i < locked.size(); i++) {
                    for (int j = i + 1; j < locked.size(); j++) {
                        VfxEvent a = locked.get(i);
                        VfxEvent b = locked.get(j);
                        double impact = a.at() + a.windup();
                        if (a.kind() != b.kind() && FxEvent.sameMoment(impact, b.at() + b.windup())) {
                            long hitsAtImpact = ability.events().stream()
                                    .filter(e -> e instanceof HitEvent h && FxEvent.sameMoment(h.at(), impact))
                                    .count();
                            assertTrue(hitsAtImpact <= 1,
                                    pair.getKey() + "/" + ability.clip() + " collides locked VFX "
                                            + a.kind() + " and " + b.kind() + " across " + hitsAtImpact
                                            + " hits at impact " + impact);
                        }
                    }
                }
            }
        }
    }

    @Test
    void allCustomEntitiesEnforceHollowWardRingsNoNonCombatCirclesNoLevitationAndFairDodgeWindows() throws Exception {
        List<String> allCustom = List.of(
                "midra", "amber_brute", "emberroot", "nameless_king",
                "frenzy_wisp", "amber_mite", "ember_cinderling", "storm_cloudlet",
                "amber_monolith", "ember_pyre", "storm_rod");
        Set<ResourceLocation> forbiddenEffects = Set.of(
                ResourceLocation.withDefaultNamespace("nausea"),
                ResourceLocation.withDefaultNamespace("levitation"),
                ResourceLocation.withDefaultNamespace("slow_falling"),
                ResourceLocation.withDefaultNamespace("blindness"),
                ResourceLocation.withDefaultNamespace("darkness"));

        for (String name : allCustom) {
            MobBehaviour behaviour = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                            readJson("/data/mobs_ld/mob_behaviour/" + name + ".json"))
                    .getOrThrow(msg -> new AssertionError(name + " behaviour: " + msg));

            // 1. Spawn (intro) and death (on_death) must never spawn ground circles, VFX rings, or death blasts.
            for (Optional<MobBehaviour.Ability> lifecycle : List.of(behaviour.intro(), behaviour.onDeath())) {
                if (lifecycle.isPresent()) {
                    for (AbilityEvent event : lifecycle.get().events()) {
                        assertFalse(event instanceof VfxEvent,
                                name + " intro/on_death must not emit non-damaging spell VFX circles");
                        assertFalse(event instanceof HitEvent,
                                name + " intro/on_death must not spawn death-circle damage hits");
                        assertFalse(event instanceof CloudEvent,
                                name + " intro/on_death must not leave death ground clouds");
                    }
                }
            }

            List<MobBehaviour.Ability> combatTimelines = new ArrayList<>(behaviour.abilities().values());
            behaviour.phases().forEach(p -> p.transition().ifPresent(combatTimelines::add));

            for (MobBehaviour.Ability ability : combatTimelines) {
                boolean hasDamage = ability.events().stream().anyMatch(e ->
                        e instanceof HitEvent || e instanceof TraceEvent || e instanceof ListenEvent
                                || e instanceof GrabEvent || (e instanceof DashEvent d && d.hit().isPresent()));
                for (AbilityEvent event : ability.events()) {
                    if (event instanceof VfxEvent vfx) {
                        assertTrue(hasDamage, name + "/" + ability.clip()
                                + " must not emit VFX (" + vfx.kind() + ") on non-damaging summon/utility abilities");
                    } else if (event instanceof PullEvent pull) {
                        assertFalse(pull.mode() == PullEvent.Mode.UPDRAFT,
                                name + "/" + ability.clip() + " must not use levitation updraft pulls");
                        assertEquals(0.0, pull.lift(), 1.0e-6,
                                name + "/" + ability.clip() + " pull must not lift players into the air");
                    } else if (event instanceof HitEvent hit) {
                        double windup = hit.at() - hit.begin();
                        assertTrue(windup >= 0.70 - 1.0e-6,
                                name + "/" + ability.clip() + " hit at " + hit.at()
                                        + " must give >= 0.70 s dodge window, got " + windup);
                        for (HitSpec.EffectSpec eff : hit.hit().effects()) {
                            assertFalse(forbiddenEffects.contains(eff.id()),
                                    name + "/" + ability.clip() + " uses forbidden debuff " + eff.id());
                        }
                        if (STATIONARY_STRUCTURES.contains(name)) {
                            assertTrue(hit.shape() instanceof Shape.Ring,
                                    "Ward " + name + "/" + ability.clip()
                                            + " must use a hollow Shape.Ring so its inner circle deals zero damage");
                            Shape.Ring ring = (Shape.Ring) hit.shape();
                            assertTrue(ring.inner() >= 2.2,
                                    "Ward " + name + "/" + ability.clip()
                                            + " ring must keep a safe inner radius >= 2.2, got " + ring.inner());
                        }
                    } else if (event instanceof DashEvent dash && dash.hit().isPresent()) {
                        double windup = dash.at() - dash.begin();
                        assertTrue(windup >= 0.75 - 1.0e-6,
                                name + "/" + ability.clip() + " damaging dash at " + dash.at()
                                        + " must give >= 0.75 s dodge window, got " + windup);
                        for (HitSpec.EffectSpec eff : dash.hit().get().effects()) {
                            assertFalse(forbiddenEffects.contains(eff.id()),
                                    name + "/" + ability.clip() + " uses forbidden dash debuff " + eff.id());
                        }
                    } else if (event instanceof GrabEvent grab) {
                        double windup = grab.at() - grab.begin();
                        assertTrue(windup >= 0.75 - 1.0e-6,
                                name + "/" + ability.clip() + " grab at " + grab.at()
                                        + " must give >= 0.75 s dodge window, got " + windup);
                        for (Optional<HitSpec> spec : List.of(grab.catchHit(), grab.releaseHit(),
                                grab.tickHit().map(GrabEvent.TickHit::hit))) {
                            if (spec.isPresent()) {
                                for (HitSpec.EffectSpec eff : spec.get().effects()) {
                                    assertFalse(forbiddenEffects.contains(eff.id()),
                                            name + "/" + ability.clip() + " uses forbidden grab debuff " + eff.id());
                                }
                            }
                        }
                    }
                }
            }
        }

        // Verify both Midra and Frenzied Eye-Wisps fire their lasers from their face/eye height, not the ground below.
        MobBehaviour wisp = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                readJson("/data/mobs_ld/mob_behaviour/frenzy_wisp.json")).getOrThrow();
        VfxEvent wispBeam = wisp.abilities().get("frenzy_glare").events().stream()
                .filter(e -> e instanceof VfxEvent v && v.kind() == SpellVfxKind.SWEEPING_BEAM)
                .map(VfxEvent.class::cast).findFirst().orElseThrow();
        assertEquals(VfxEvent.VfxOrigin.SELF, wispBeam.origin(),
                "frenzy_wisp laser must anchor to self/fx_eye rather than ground-snapped locked zone");
        assertTrue(wispBeam.placement().up() >= 0.55,
                "frenzy_wisp laser must originate at its face/eye height (>= 0.55)");

        MobBehaviour midra = MobBehaviour.CODEC.parse(JsonOps.INSTANCE,
                readJson("/data/mobs_ld/mob_behaviour/midra.json")).getOrThrow();
        for (String beamKey : List.of("frenzy_beam", "frenzy_beam_p2")) {
            VfxEvent midraBeam = midra.abilities().get(beamKey).events().stream()
                    .filter(e -> e instanceof VfxEvent v && v.kind() == SpellVfxKind.SWEEPING_BEAM)
                    .map(VfxEvent.class::cast).findFirst().orElseThrow();
            assertEquals(VfxEvent.VfxOrigin.SELF, midraBeam.origin());
            assertTrue(midraBeam.placement().up() >= 4.3,
                    "Midra " + beamKey + " must originate at his face/eye bone height (>= 4.3)");
        }
    }
}
