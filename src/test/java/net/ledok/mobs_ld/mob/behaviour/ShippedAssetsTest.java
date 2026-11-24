package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.MobDeclaration;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.ledok.mobs_ld.mob.behaviour.event.ChainEvent;
import net.ledok.mobs_ld.mob.behaviour.event.ChaseEvent;
import net.ledok.mobs_ld.mob.behaviour.event.ClipEvent;
import net.ledok.mobs_ld.mob.behaviour.event.FxEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitEvent;
import net.ledok.mobs_ld.mob.behaviour.event.SandstoneSpikesEvent;
import net.ledok.mobs_ld.mob.behaviour.event.SummonEvent;
import net.ledok.mobs_ld.mob.behaviour.event.TraceEvent;
import net.ledok.mobs_ld.mob.behaviour.event.VfxEvent;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cross-checks shipped GeckoLib assets and the server timelines that use them. */
class ShippedAssetsTest {
    private static final Path RESOURCES = Path.of(System.getProperty("mobs_ld.projectDir", "."),
            "src", "main", "resources");
    private static final double EPSILON = 1.0e-7;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void customMobAssetsMatchTheirTimelines() throws IOException {
        try (Stream<Path> declarations = Files.list(RESOURCES.resolve("mobs_ld/entities"))) {
            List<Path> files = declarations.filter(path -> path.toString().endsWith(".json")).sorted().toList();
            assertFalse(files.isEmpty(), "No shipped mob declarations found");
            for (Path declaration : files) {
                checkMob(declaration.getFileName().toString().replaceFirst("\\.json$", ""), declaration);
            }
        }
    }

    private static void checkMob(String mob, Path declarationFile) throws IOException {
        String context = "mobs_ld:" + mob;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mobs_ld", mob);
        MobDeclaration declaration = MobDeclaration.codec(id).parse(JsonOps.INSTANCE, read(declarationFile))
                .getOrThrow(error -> new AssertionError(context + " declaration: " + error));
        Path geometry = RESOURCES.resolve("assets/mobs_ld/geo/entity/" + mob + ".geo.json");
        Path animation = RESOURCES.resolve("assets/mobs_ld/animations/entity/" + mob + ".animation.json");
        Set<String> bones = new HashSet<>();
        JsonArray geometries = read(geometry).getAsJsonArray("minecraft:geometry");
        assertEquals(1, geometries.size(), context + " geometry count");
        for (JsonElement entry : geometries.get(0).getAsJsonObject().getAsJsonArray("bones")) {
            String name = entry.getAsJsonObject().get("name").getAsString();
            assertTrue(bones.add(name), context + " duplicates geometry bone " + name);
        }

        Clips clips = new Clips(mob, read(animation).getAsJsonObject("animations"));
        assertNotNull(clips.require("idle", context));
        assertTrue(clips.find("walk") != null || clips.find("run") != null, context + " needs a walk or run clip");
        clips.require("death", context);
        for (var entry : clips.animations().entrySet()) {
            checkAnimation(entry.getValue().getAsJsonObject(), bones, context + "/" + entry.getKey());
        }
        declaration.render().actionTextures().keySet().forEach(clip -> clips.require(clip, context + " action_textures"));

        Path track = RESOURCES.resolve("data/mobs_ld/hit_tracks/" + mob + ".json");
        JsonObject tracks = Files.isRegularFile(track) ? checkTracks(track, geometry, animation, clips, bones, context)
                : new JsonObject();
        Path timeline = RESOURCES.resolve("data/mobs_ld/mob_behaviour/" + mob + ".json");
        if (!Files.isRegularFile(timeline)) {
            return;
        }
        MobBehaviour behaviour = MobBehaviour.CODEC.parse(JsonOps.INSTANCE, read(timeline))
                .getOrThrow(error -> new AssertionError(context + " behaviour: " + error));
        behaviour.abilities().forEach((name, ability) ->
                checkAbility(ability, name, ability.clipOr(name), clips, bones, tracks, context + "/" + name));
        behaviour.intro().ifPresent(ability ->
                checkAbility(ability, "intro", ability.clipOr("intro"), clips, bones, tracks, context + "/intro"));
        behaviour.onDeath().ifPresent(ability ->
                checkAbility(ability, "on_death", "death", clips, bones, tracks, context + "/on_death"));
        behaviour.phases().forEach(phase -> phase.transition().ifPresent(ability ->
                checkAbility(ability, "transition", ability.clipOr("transition"), clips, bones, tracks,
                        context + "/phase below " + phase.belowHealth() + " transition")));
        behaviour.stagger().ifPresent(stagger -> {
            JsonObject clip = clips.require(stagger.clip(), context + "/stagger");
            assertTrue(length(clip) <= stagger.duration() + EPSILON, context + "/stagger cuts its animation short");
        });
        behaviour.boss().flatMap(MobBehaviour.Boss::softEnrage).flatMap(MobBehaviour.SoftEnrage::clip)
                .ifPresent(clip -> clips.require(clip, context + "/soft_enrage"));
    }

    private static void checkAnimation(JsonObject clip, Set<String> bones, String context) {
        double duration = length(clip);
        assertTrue(Double.isFinite(duration) && duration >= 0.0, context + " has invalid animation_length");
        for (var bone : object(clip, "bones").entrySet()) {
            assertTrue(bones.contains(bone.getKey()), context + " animates unknown bone " + bone.getKey());
            for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                if (channel.getValue().isJsonObject() && !channel.getValue().getAsJsonObject().has("vector")) {
                    for (String time : channel.getValue().getAsJsonObject().keySet()) {
                        checkTime(time, duration, context + "/" + bone.getKey() + "/" + channel.getKey());
                    }
                }
            }
        }
        for (String type : List.of("sound_effects", "particle_effects", "timeline")) {
            for (var keyframe : object(clip, type).entrySet()) {
                String where = context + "/" + type + "@" + keyframe.getKey();
                checkTime(keyframe.getKey(), duration, where);
                JsonElement cue = keyframe.getValue();
                assertFalse(cue.isJsonArray(), where + " must contain a single effect for GeckoLib");
                if (type.equals("particle_effects") && cue.getAsJsonObject().has("locator")) {
                    String marker = cue.getAsJsonObject().get("locator").getAsString();
                    assertTrue(marker.isEmpty() || bones.contains(marker) && List.of("fx_", "hit_", "proj_").stream()
                            .anyMatch(marker::startsWith), where + " has unknown or untracked marker " + marker);
                }
            }
        }
    }

    private static void checkTime(String timestamp, double duration, String context) {
        double time = Double.parseDouble(timestamp);
        assertTrue(time >= 0.0 && time <= duration + EPSILON,
                context + " keyframe " + timestamp + "s is outside the " + duration + "s clip");
    }

    private static void checkAbility(MobBehaviour.Ability ability, String name, String openingClip, Clips clips,
                                     Set<String> bones, JsonObject tracks, String context) {
        assertTrue(Double.isFinite(ability.duration()) && ability.duration() >= 0.0, context + " has invalid duration");
        JsonObject opening = clips.require(openingClip, context);
        // Events can intentionally outlast the declared duration (for example a chase after its windup).
        double runEnd = ability.duration();
        Map<Double, JsonObject> cues = new LinkedHashMap<>();
        cues.put(0.0, opening);
        for (AbilityEvent event : ability.events()) {
            double start = event.begin();
            double end = event.end();
            assertTrue(start >= 0.0 && Double.isFinite(end) && end + EPSILON >= start,
                    context + " has invalid " + event.type() + " timing " + start + "–" + end);
            runEnd = Math.max(runEnd, end);
            if (event instanceof ClipEvent cue) {
                cues.put(cue.at(), clips.require(cue.clip(), context + "/clip"));
            } else if (event instanceof ChaseEvent chase && !chase.clip().isEmpty()) {
                cues.put(chase.at(), clips.require(chase.clip(), context + "/chase"));
            }
            if (event instanceof TraceEvent trace) {
                checkTrace(trace, ability.clipOr(name), clips, bones, tracks, context);
            }
        }
        // An unconditional chain deliberately replaces the current clip with the follow-up's clip.
        boolean chains = ability.events().stream().anyMatch(event -> event instanceof ChainEvent chain && !chain.ifReady());
        if (cues.size() == 1 && !chains && !extendsPose(opening)) {
            assertTrue(length(opening) <= runEnd + EPSILON, context + " ends at " + runEnd
                    + "s before its " + length(opening) + "s opening animation finishes");
        }
        for (AbilityEvent event : ability.events()) {
            if (event instanceof HitEvent || event instanceof SummonEvent || event instanceof TraceEvent
                    || event instanceof SandstoneSpikesEvent) {
                var cue = cues.entrySet().stream().filter(entry -> entry.getKey() <= event.end() + EPSILON)
                        .max(Map.Entry.comparingByKey()).orElseThrow();
                double poseEnd = cue.getKey() + length(cue.getValue());
                assertTrue(extendsPose(cue.getValue()) || event.end() <= poseEnd + EPSILON
                                || hasLockedVisualWarning(event, ability.events()),
                        context + " " + event.type() + " at " + event.end() + "s plays after its pose ends at " + poseEnd + "s");
            }
        }
    }

    /** A delayed area hit can have a separate warning anchored to the already locked hit zones. */
    private static boolean hasLockedVisualWarning(AbilityEvent event, List<AbilityEvent> timeline) {
        return event instanceof HitEvent hit && timeline.stream().anyMatch(beat -> beat instanceof VfxEvent vfx
                && vfx.origin() == VfxEvent.VfxOrigin.LOCKED && vfx.windup() > 0.0
                && vfx.begin() + EPSILON >= hit.begin() && FxEvent.sameMoment(vfx.end(), hit.at()));
    }

    private static boolean extendsPose(JsonObject clip) {
        String loop = clip.has("loop") ? clip.get("loop").getAsString() : "";
        return loop.equals("true") || loop.equals("hold_on_last_frame");
    }

    private static void checkTrace(TraceEvent trace, String defaultClip, Clips clips, Set<String> bones,
                                   JsonObject tracks, String context) {
        String clipName = trace.clip().orElse(defaultClip);
        JsonObject animation = clips.require(clipName, context + "/trace");
        JsonObject track = tracks.getAsJsonObject(clipName);
        assertNotNull(track, context + " needs baked trace clip " + clipName + " (re-bake with /mobs_ldc bake)");
        assertTrue(trace.to() <= length(animation) + EPSILON, context + " trace exceeds animation " + clipName);
        for (String bone : trace.path()) {
            assertTrue(bones.contains(bone), context + " trace refers to unknown bone " + bone);
            assertTrue(track.getAsJsonObject("bones").has(bone), context + " trace needs baked bone " + bone + " in " + clipName);
        }
    }

    private static JsonObject checkTracks(Path file, Path geometry, Path animation, Clips clips, Set<String> bones,
                                          String context) throws IOException {
        JsonObject root = read(file);
        String rebake = "re-bake with /mobs_ldc bake " + clips.mob();
        assertEquals(20, root.get("rate").getAsInt(), context + " hit track sample rate");
        JsonObject tracks = root.getAsJsonObject("clips");
        Set<String> expectedClips = new TreeSet<>();
        clips.animations().keySet().forEach(full -> expectedClips.add(full.substring(full.lastIndexOf('.') + 1)));
        boolean legacy = !root.has("source");
        if (legacy) {
            Set<String> missingClips = new TreeSet<>(expectedClips);
            missingClips.removeAll(tracks.keySet());
            Set<String> missingBones = new TreeSet<>();
            tracks.entrySet().forEach(entry -> {
                Set<String> absent = new HashSet<>(bones);
                absent.removeAll(entry.getValue().getAsJsonObject().getAsJsonObject("bones").keySet());
                missingBones.addAll(absent);
            });
            System.err.println("Mobs_LD asset warning: " + context + " hit tracks have no source fingerprints"
                    + (missingClips.isEmpty() ? "" : "; missing clips " + missingClips)
                    + (missingBones.isEmpty() ? "" : "; missing model bones " + missingBones) + "; " + rebake);
        } else {
            try (InputStream geometryBytes = Files.newInputStream(geometry);
                 InputStream animationBytes = Files.newInputStream(animation)) {
                assertEquals(HitTrackSource.fingerprints(geometryBytes, animationBytes), root.get("source"),
                        context + " stale hit tracks; " + rebake);
            }
            assertEquals(expectedClips, tracks.keySet(), context + " hit track clips; " + rebake);
        }
        for (var entry : tracks.entrySet()) {
            String where = context + " hit track " + entry.getKey();
            JsonObject track = entry.getValue().getAsJsonObject();
            JsonObject clip = clips.require(entry.getKey(), where);
            int ticks = track.get("length").getAsInt();
            assertEquals((int) Math.ceil(length(clip) * 20 - EPSILON), ticks, where + " length changed; " + rebake);
            JsonObject samplesByBone = track.getAsJsonObject("bones");
            if (!legacy) {
                assertEquals(bones, samplesByBone.keySet(), where + " model bones changed; " + rebake);
            }
            for (var bone : samplesByBone.entrySet()) {
                String path = where + "/" + bone.getKey();
                assertTrue(bones.contains(bone.getKey()), path + " no longer exists; " + rebake);
                JsonArray samples = bone.getValue().getAsJsonArray();
                assertEquals(ticks + 1, samples.size(), path + " sample count; " + rebake);
                for (JsonElement sample : samples) {
                    JsonArray xyz = sample.getAsJsonArray();
                    assertEquals(3, xyz.size(), path + " sample must have three coordinates");
                    for (JsonElement component : xyz) {
                        assertTrue(Double.isFinite(component.getAsDouble()), path + " sample has invalid coordinate");
                    }
                }
            }
        }
        return tracks;
    }

    private static double length(JsonObject clip) {
        return clip.get("animation_length").getAsDouble();
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private record Clips(String mob, JsonObject animations) {
        JsonObject find(String name) {
            JsonObject clip = animations.getAsJsonObject("animation.mobs_ld." + mob + "." + name);
            return clip != null ? clip : animations.getAsJsonObject(name);
        }

        JsonObject require(String name, String context) {
            JsonObject clip = find(name);
            assertNotNull(clip, context + " refers to missing animation " + name);
            return clip;
        }
    }
}
