package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
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
 * The behaviour files shipped in the jar decode (and the Wither's per-phase skull cadence merges as intended), and
 * the M3 references (slots, skulls, hit damage) are validated at load.
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
