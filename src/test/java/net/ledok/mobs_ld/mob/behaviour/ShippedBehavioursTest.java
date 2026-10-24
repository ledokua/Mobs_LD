package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.behaviour.event.ChainEvent;
import net.ledok.mobs_ld.mob.behaviour.event.DashEvent;
import net.ledok.mobs_ld.mob.behaviour.event.Telegraph;
import net.ledok.mobs_ld.mob.combat.SlotPicker;
import net.ledok.mobs_ld.mob.host.HostOptions;
import net.ledok.mobs_ld.mob.host.wither.WitherAdapter;
import net.ledok.mobs_ld.mob.host.wither.WitherHostOptions;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behaviour files shipped in the jar decode (and the Wither's per-phase skull cadence merges as intended), the
 * M3 references (slots, skulls, hit damage) are validated at load, and so are the M4 dash and chain fields.
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
        assertTrue(wither.abilities().keySet().containsAll(java.util.List.of("soul_rush", "soul_rush_2", "soul_rush_follow",
                "test_volley")));
        assertFalse(wither.abilities().containsKey("test_hover_slam"));
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
        assertEquals(java.util.List.of("soul_rush"), wither.phases().get(0).abilities());
        assertEquals(java.util.List.of("soul_rush_2"), wither.phases().get(1).abilities());
        assertEquals(java.util.List.of("soul_rush_2"), wither.phases().get(2).abilities());
        assertTrue(wither.stagger().isPresent());
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
