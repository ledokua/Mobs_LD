package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.behaviour.event.ChainEvent;
import net.ledok.mobs_ld.mob.behaviour.event.DashEvent;
import net.ledok.mobs_ld.mob.behaviour.event.FxEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HoverEvent;
import net.ledok.mobs_ld.mob.behaviour.event.SummonEvent;
import net.ledok.mobs_ld.mob.behaviour.event.Telegraph;
import net.ledok.mobs_ld.mob.combat.SlotPicker;
import net.ledok.mobs_ld.mob.host.HostOptions;
import net.ledok.mobs_ld.mob.host.wither.WitherAdapter;
import net.ledok.mobs_ld.mob.host.wither.WitherHostOptions;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behaviour files shipped in the jar decode (and the Wither's per-phase skull cadence merges as intended), the
 * M3 references (slots, skulls, hit damage) are validated at load, and so are the M4 dash and chain fields and the M5
 * summon fields (which leave Gorefist's and Rot Wart's files meaning what they did).
 */
class ShippedBehavioursTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        WitherAdapter.register();
    }

    private static MobBehaviour load(String path) throws Exception {
        try (Reader reader = new InputStreamReader(ShippedBehavioursTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            DataResult<MobBehaviour> result = MobBehaviour.CODEC.parse(JsonOps.INSTANCE, json);
            return result.getOrThrow(message -> new AssertionError(path + ": " + message));
        }
    }

    @Test
    void ldMobsLoad() throws Exception {
        load("/data/mobs_ld/mob_behaviour/gorefist.json");
        load("/data/mobs_ld/mob_behaviour/rot_wart.json");
    }

    /** M5's summon and cooldown fields are all optional: Gorefist's and Rot Wart's files mean exactly what they did. */
    @Test
    void ldMobsKeepTheirSummonsAndCooldowns() throws Exception {
        MobBehaviour gorefist = load("/data/mobs_ld/mob_behaviour/gorefist.json");
        MobBehaviour.Ability summon = gorefist.abilities().get("summon_rot_warts");
        assertEquals(Seconds.of(25.0), summon.cooldown());
        assertEquals(Seconds.of(12.0), summon.initialCooldown());
        assertEquals(Seconds.of(18.0), gorefist.phases().get(1).cooldowns().get("summon_rot_warts"));
        assertEquals(5.0, gorefist.phases().get(1).readyIn().get("summon_rot_warts"));
        List<SummonEvent> summons = new ArrayList<>(summons(summon));
        summons.addAll(summons(gorefist.phases().get(1).transition().orElseThrow()));
        assertEquals(2, summons.size());
        for (SummonEvent event : summons) {
            assertEquals(ResourceLocation.fromNamespaceAndPath("mobs_ld", "rot_wart"), event.entity());
            assertEquals(SummonEvent.Count.of(2), event.count());
            assertEquals(SummonEvent.MaxAlive.of(6), event.maxAlive());
            assertEquals(0.75, event.forward());
            assertEquals(2.6, event.up());
            assertEquals(1.5, event.spread());
            assertTrue(event.telegraph().isEmpty() && event.placement().isEmpty() && event.scale().isEmpty());
            assertTrue(event.loot() && event.xp());
            assertFalse(event.bound());
            assertEquals(SummonEvent.AddTarget.AIM, event.target());
            assertEquals(event.at(), event.begin(), "untelegraphed: fires at its time, as before");
            assertEquals(event.at(), event.end());
        }
        for (MobBehaviour behaviour : List.of(gorefist, load("/data/mobs_ld/mob_behaviour/rot_wart.json"))) {
            for (MobBehaviour.Ability ability : behaviour.abilities().values()) {
                assertTrue(ability.cooldown().fixed() && ability.initialCooldown().fixed());
                assertFalse(ability.skipWhenFull());
            }
        }
        assertEquals(Seconds.of(4.0), gorefist.abilities().values().iterator().next().cooldown());
    }

    private static List<SummonEvent> summons(MobBehaviour.Ability ability) {
        return ability.events().stream().filter(SummonEvent.class::isInstance).map(SummonEvent.class::cast).toList();
    }

    @Test
    void cooldownsTakeTimeRanges() {
        String json = "{\"abilities\": {\"a\": {\"duration\": 1, \"cooldown\": [30, 60], \"initial_cooldown\": 20, "
                + "\"skip_when_full\": true}}, \"phases\": [{\"below_health\": 1.0, \"cooldowns\": {\"a\": [10, 12]}}]}";
        MobBehaviour behaviour = MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        MobBehaviour.Ability ability = behaviour.abilities().get("a");
        assertEquals(new Seconds(30.0, 60.0), ability.cooldown());
        assertEquals(Seconds.of(20.0), ability.initialCooldown());
        assertTrue(ability.skipWhenFull());
        assertEquals(new Seconds(10.0, 12.0), behaviour.phases().get(0).cooldowns().get("a"));
        java.util.Random random = new java.util.Random(5);
        boolean low = false;
        boolean high = false;
        for (int i = 0; i < 500; i++) {
            double rolled = ability.cooldown().roll(random::nextDouble);
            assertTrue(rolled >= 30.0 && rolled <= 60.0, "rolled " + rolled);
            low |= rolled < 35.0;
            high |= rolled > 55.0;
        }
        assertTrue(low && high, "a fresh value each use, across the range");
        assertFalse(error(json.replace("[30, 60]", "[60, 30]")).isEmpty());
    }

    @Test
    void witherLoads() throws Exception {
        MobBehaviour wither = load("/data/minecraft/mob_behaviour/wither.json");
        assertEquals(SlotPicker.Rule.LOWEST_THREAT, wither.targetSlots().get("right_head").rule());
        assertEquals(SlotPicker.Rule.SPREAD, wither.targetSlots().get("left_head").rule());
        assertEquals(new Seconds(9.0, 11.0), wither.targetSlots().get("left_head").repick());
        assertEquals(5.0, wither.targetSlots().get("left_head").offset());
        assertEquals(java.util.Optional.of("right_head"), wither.leadSlot("left_head"));
        assertTrue(wither.leadSlot("right_head").isEmpty());
        double[] center = {4.0, 3.5, 3.0};
        Seconds[] side = {new Seconds(4.0, 5.0), new Seconds(3.5, 4.5), new Seconds(3.0, 4.0)};
        for (int phase = 0; phase < 3; phase++) {
            HostOptions options = wither.hostOptions(phase).orElseThrow();
            assertTrue(options instanceof WitherHostOptions);
            assertEquals(Seconds.of(center[phase]), options.skulls().get("center").every().orElseThrow(), "phase " + phase);
            assertEquals(side[phase], options.skulls().get("side").every().orElseThrow(), "phase " + phase);
            // The phase blocks only change the cadence: the hits stay the file's.
            assertEquals(2.4, options.skulls().get("center").hit().amount().orElseThrow());
            assertEquals(1, options.skulls().get("side").hit().effects().size());
            assertEquals(1.0, options.skulls().get("volley").splash().orElseThrow().amount());
        }
    }

    @Test
    void witherSoulRushLoads() throws Exception {
        MobBehaviour wither = load("/data/minecraft/mob_behaviour/wither.json");
        assertEquals(java.util.Set.of("soul_rush", "soul_rush_2", "soul_rush_follow", "grave_call"),
                wither.abilities().keySet());
        DashEvent rush = dash(wither.abilities().get("soul_rush"));
        assertEquals(java.util.Optional.of(1.4), rush.speed());
        assertTrue(rush.width().blocks().isEmpty(), "width: body");
        assertEquals(DashEvent.Push.SIDE, rush.push());
        assertEquals(java.util.Optional.of(0.4), rush.height());
        assertEquals(4.0, rush.staggerMinDistance());
        assertEquals(Telegraph.Style.SWEEP, rush.telegraph().style());
        assertTrue(rush.telegraph().arrow());
        assertEquals(0.8, rush.begin(), 1.0e-9);
        assertEquals(20.0, rush.hit().orElseThrow().amount().orElseThrow());
        // 28 blocks at 1.4 per tick take 1 s: the event outlasts the longest rush, the ability outlasts the event.
        assertTrue(rush.end() >= 1.8 + 1.0 && rush.end() <= wither.abilities().get("soul_rush").duration());
        DashEvent follow = dash(wither.abilities().get("soul_rush_follow"));
        assertFalse(follow.telegraph().show());
        assertEquals(12.0, follow.hit().orElseThrow().amount().orElseThrow());
        assertEquals(MobBehaviour.TargetRule.slot("left_head"), wither.abilities().get("soul_rush_follow").target());
        // The follow rush locks 0.3 s before it starts, 0.7 s after the snap.
        assertEquals(0.4, wither.abilities().get("soul_rush_follow").trackUntil().orElseThrow(), 1.0e-9);
        assertEquals(0.7, follow.at(), 1.0e-9);
        assertTrue(wither.abilities().get("soul_rush_2").events().stream().anyMatch(event -> event instanceof ChainEvent chain
                && chain.to().equals("soul_rush_follow") && Math.abs(chain.at() - 2.9) < 1.0e-9));
        assertEquals(java.util.List.of("soul_rush", "grave_call"), wither.phases().get(0).abilities());
        assertEquals(java.util.List.of("soul_rush_2", "grave_call"), wither.phases().get(1).abilities());
        assertEquals(java.util.List.of("soul_rush_2", "grave_call"), wither.phases().get(2).abilities());
        assertEquals(java.util.Map.of("soul_rush_2", 6.0), wither.phases().get(1).readyIn());
        assertTrue(wither.phases().get(2).readyIn().isEmpty());
        assertTrue(wither.phases().stream().allMatch(phase -> phase.cooldowns().isEmpty()));
        assertTrue(wither.stagger().isPresent());
    }

    @Test
    void witherGraveCallLoads() throws Exception {
        MobBehaviour wither = load("/data/minecraft/mob_behaviour/wither.json");
        MobBehaviour.Ability call = wither.abilities().get("grave_call");
        assertEquals(new Seconds(30.0, 60.0), call.cooldown());
        assertEquals(Seconds.of(20.0), call.initialCooldown());
        assertEquals(0, call.weight());
        assertEquals(java.util.Optional.of(0.0), call.forceAfter());
        assertTrue(call.skipWhenFull());
        assertEquals(3.0, call.duration());
        SummonEvent summon = summons(call).get(0);
        assertEquals(ResourceLocation.withDefaultNamespace("wither_skeleton"), summon.entity());
        assertEquals(new SummonEvent.Count(2, 4, 2, 12), summon.count());
        assertEquals(new SummonEvent.MaxAlive(6, 3, 16), summon.maxAlive());
        SummonEvent.Placement placement = summon.placement().orElseThrow();
        assertEquals(SummonEvent.Placement.Around.FIGHTERS, placement.around());
        assertEquals(new SummonEvent.Radius(3.0, 6.0), placement.radius());
        assertEquals(2.0, placement.minGap());
        assertEquals(3, placement.clearance());
        assertTrue(placement.ground());
        SummonEvent.Runes runes = summon.telegraph().orElseThrow();
        assertEquals(0.9, runes.radius());
        assertEquals(0x3A1F5C, runes.telegraph().color());
        // Runes at 0.2 s, the adds rise at 1.6 s.
        assertEquals(0.2, summon.begin(), 1.0e-9);
        assertEquals(1.6, summon.end(), 1.0e-9);
        SummonEvent.Scale scale = summon.scale().orElseThrow();
        assertEquals(java.util.Optional.of(1.0), scale.maxHealth());
        assertEquals(java.util.Optional.of(1.0), scale.attackDamage());
        assertTrue(scale.armor().orElseThrow().summonerBonus());
        assertTrue(scale.copiesDamageDealt());
        assertFalse(summon.loot());
        assertFalse(summon.xp());
        assertTrue(summon.bound());
        assertEquals(SummonEvent.AddTarget.RUNE_PLAYER, summon.target());
        assertTrue(call.events().stream().anyMatch(event -> event instanceof HoverEvent hover
                && hover.relativeTo() == HoverEvent.Reference.GROUND && hover.height() == 3.0 && hover.until() == 3.0));
        FxEvent fx = call.events().stream().filter(FxEvent.class::isInstance).map(FxEvent.class::cast).findFirst().orElseThrow();
        assertEquals("mobs_ld.cue.wither.grave_call", fx.message().orElseThrow().key());
        assertEquals(0.6F, fx.sound().orElseThrow().pitch());
        assertEquals(4.0, fx.particles().orElseThrow().column());
    }

    @Test
    void summonsAreValidated() {
        String base = "{\"abilities\": {\"a\": {\"duration\": 3, \"events\": [%s]}}}";
        String plain = "{\"type\": \"summon\", \"at\": 1, \"entity\": \"minecraft:zombie\", \"count\": 2, \"max_alive\": 4}";
        assertEquals("", error(base.formatted(plain)));
        assertEquals("", error(base.formatted(plain.replace("\"count\": 2", "\"count\": {\"min\": 1, \"max\": 3}"))));
        assertFalse(error(base.formatted(plain.replace("\"count\": 2", "\"count\": {\"min\": 3, \"max\": 1}"))).isEmpty());
        assertFalse(error(base.formatted(plain.replace("}", ", \"target\": \"nearest\"}"))).isEmpty());
        assertFalse(error(base.formatted(plain.replace("}", ", \"placement\": {\"radius\": [5, 2]}}"))).isEmpty());
        assertFalse(error(base.formatted(plain.replace("}", ", \"telegraph\": {\"radius\": 0}}"))).isEmpty());
    }

    private static DashEvent dash(MobBehaviour.Ability ability) {
        return ability.events().stream().filter(DashEvent.class::isInstance).map(DashEvent.class::cast).findFirst()
                .orElseThrow();
    }

    @Test
    void dashAndChainAreValidated() {
        String base = "{\"abilities\": {\"a\": {\"duration\": 3, \"events\": [%s]}, \"b\": {\"duration\": 1}}}";
        String timed = "{\"type\": \"dash\", \"at\": 1, \"until\": 2, \"max_distance\": 10}";
        assertEquals("", error(base.formatted(timed)));
        assertEquals("", error(base.formatted("{\"type\": \"dash\", \"at\": 1, \"speed\": 1.4, \"max_distance\": 28, "
                + "\"width\": \"body\", \"push\": \"side\", \"telegraph\": {\"style\": \"sweep\", \"arrow\": true}}")));
        assertEquals("", error(base.formatted(timed.replace("}", ", \"width\": 3.5}"))));
        assertTrue(error(base.formatted("{\"type\": \"dash\", \"at\": 1, \"max_distance\": 10}")).contains("speed"));
        assertTrue(error(base.formatted(timed.replace("\"until\": 2", "\"until\": 0.5"))).contains("before it starts"));
        assertFalse(error(base.formatted(timed.replace("}", ", \"width\": \"wide\"}"))).isEmpty());
        assertFalse(error(base.formatted(timed.replace("}", ", \"push\": \"up\"}"))).isEmpty());
        assertFalse(error(base.formatted(timed.replace("}", ", \"telegraph\": {\"style\": \"spin\"}}"))).isEmpty());
        assertEquals("", error(base.formatted("{\"type\": \"chain\", \"at\": 2, \"to\": \"b\"}")));
        assertTrue(error(base.formatted("{\"type\": \"chain\", \"at\": 2, \"to\": \"c\"}")).contains("'c'"));
    }

    private static String error(String json) {
        return MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error()
                .map(DataResult.Error::message).orElse("");
    }

    @Test
    void referencesAreValidated() {
        String host = "\"host\": {\"type\": \"mobs_ld:wither\", \"skulls\": {\"volley\": {\"hit\": {\"amount\": 4}}}}, "
                + "\"target_slots\": {\"right_head\": {\"rule\": \"lowest_threat\"}}, ";
        String ok = "{" + host + "\"abilities\": {\"a\": {\"duration\": 1, \"target\": \"slot:right_head\", \"events\": ["
                + "{\"type\": \"projectile\", \"at\": 0, \"skull\": \"volley\", \"aim\": \"slot:right_head\"},"
                + "{\"type\": \"look\", \"at\": 0, \"until\": 1, \"heads\": \"side\", \"target\": \"current\"},"
                + "{\"type\": \"fx\", \"at\": 0, \"particles\": {\"id\": \"minecraft:soul_fire_flame\", \"at\": \"heads\"}}]}}}";
        assertEquals("", error(ok));
        assertTrue(error(ok.replace("\"target\": \"slot:right_head\"", "\"target\": \"slot:left_head\"")).contains("left_head"));
        assertTrue(error(ok.replace("\"skull\": \"volley\"", "\"skull\": \"center\"")).contains("center"));
        assertTrue(error(ok.replace("\"aim\": \"slot:right_head\"", "\"aim\": \"slot:nope\"")).contains("nope"));
        assertTrue(error(ok.replace("minecraft:soul_fire_flame", "minecraft:dust")).contains("dust"));
        assertFalse(error(ok.replace("{\"amount\": 4}", "{\"amount\": 4, \"damage\": 2}")).isEmpty());
        assertFalse(error(ok.replace("\"lowest_threat\"", "\"highest_threat\"")).isEmpty());
        assertFalse(error(ok.replace("\"heads\": \"side\"", "\"heads\": \"tail\"")).isEmpty());
        assertEquals("", error(ok.replace("\"target\": \"slot:right_head\"", "\"target\": \"lowest_threat\"")));
        // An offset slot must re-pick inside its lead's period, or it would never get its turn.
        String slots = "\"target_slots\": {\"right_head\": {\"rule\": \"lowest_threat\"}}, ";
        String paired = "\"target_slots\": {\"right_head\": {\"rule\": \"lowest_threat\", \"repick\": [9, 11]}, "
                + "\"left_head\": {\"rule\": \"spread\", \"offset\": 5}}, ";
        assertEquals("", error(ok.replace(slots, paired)));
        assertTrue(error(ok.replace(slots, paired.replace("\"offset\": 5", "\"offset\": 9"))).contains("offset"));
    }
}
