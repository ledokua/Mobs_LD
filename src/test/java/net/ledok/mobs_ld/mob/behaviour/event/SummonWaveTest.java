package net.ledok.mobs_ld.mob.behaviour.event;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.combat.Fighters;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Summon waves: the count and max_alive formulas, and how runes are dealt to the fighting players. */
class SummonWaveTest {
    private static <T> T parse(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static <T> boolean rejects(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isError();
    }

    /** The Grave Call count: 2-4 solo, +2 per extra fighting player, at most 12. */
    private static final SummonEvent.Count GRAVE_CALL = new SummonEvent.Count(2, 4, 2, 12);

    @Test
    void graveCallCountGrowsWithThePlayers() {
        Random random = new Random(11);
        int[][] expected = {{0, 2, 4}, {1, 2, 4}, {2, 4, 6}, {3, 6, 8}, {4, 8, 10}, {5, 10, 12}, {6, 12, 12}, {9, 12, 12}};
        for (int[] row : expected) {
            int fighters = row[0];
            int lowest = Integer.MAX_VALUE;
            int highest = Integer.MIN_VALUE;
            for (int i = 0; i < 400; i++) {
                int count = GRAVE_CALL.roll(fighters, random::nextInt);
                lowest = Math.min(lowest, count);
                highest = Math.max(highest, count);
            }
            assertEquals(row[1], lowest, fighters + " fighting: lowest");
            assertEquals(row[2], highest, fighters + " fighting: highest");
        }
    }

    @Test
    void aPlainCountIsAlwaysThatMany() {
        SummonEvent.Count two = SummonEvent.Count.of(2);
        for (int fighters = 0; fighters < 6; fighters++) {
            assertEquals(2, two.roll(fighters, bound -> {
                throw new AssertionError("a fixed count rolls nothing");
            }));
        }
        assertEquals(two, parse(SummonEvent.Count.CODEC, "2"));
        assertEquals(GRAVE_CALL, parse(SummonEvent.Count.CODEC, "{\"min\": 2, \"max\": 4, \"per_extra_player\": 2, \"cap\": 12}"));
        assertEquals(new SummonEvent.Count(3, 3, 0, Integer.MAX_VALUE), parse(SummonEvent.Count.CODEC, "{\"min\": 3}"));
        assertTrue(rejects(SummonEvent.Count.CODEC, "{\"min\": 4, \"max\": 2}"));
        assertTrue(rejects(SummonEvent.Count.CODEC, "{\"max\": 2}"));
        assertTrue(rejects(SummonEvent.Count.CODEC, "\"many\""));
    }

    @Test
    void maxAliveGrowsWithThePlayersUpToItsCap() {
        SummonEvent.MaxAlive graveCall = parse(SummonEvent.MaxAlive.CODEC, "{\"base\": 6, \"per_extra_player\": 3, \"cap\": 16}");
        int[] expected = {6, 6, 9, 12, 15, 16, 16};
        for (int fighters = 0; fighters < expected.length; fighters++) {
            assertEquals(expected[fighters], graveCall.at(fighters), fighters + " fighting");
        }
        SummonEvent.MaxAlive plain = parse(SummonEvent.MaxAlive.CODEC, "6");
        assertEquals(SummonEvent.MaxAlive.of(6), plain);
        assertEquals(6, plain.at(5));
    }

    @Test
    void runesAreDealtTwoEachInTurnThenAtRandom() {
        assertArrayEquals(new int[]{0, 0, 0, 0}, Fighters.deal(4, 1, bound -> 0));
        assertArrayEquals(new int[]{0, 1, 0, 1}, Fighters.deal(4, 2, bound -> 0));
        // Short of room: as many players as possible get one first.
        assertArrayEquals(new int[]{0, 1}, Fighters.deal(2, 3, bound -> 0));
        assertArrayEquals(new int[]{0, 1, 2, 0}, Fighters.deal(4, 3, bound -> 0));
        int[] trio = Fighters.deal(8, 3, bound -> bound - 1);
        assertArrayEquals(new int[]{0, 1, 2, 0, 1, 2, 2, 2}, trio);
        int[] each = new int[3];
        Arrays.stream(Fighters.deal(8, 3, new Random(3)::nextInt)).forEach(owner -> each[owner]++);
        for (int count : each) {
            assertTrue(count >= 2, "every player gets two runes before the extras: " + Arrays.toString(each));
        }
        assertEquals(0, Fighters.deal(3, 0, bound -> 0).length);
        assertEquals(0, Fighters.extra(0));
        assertEquals(0, Fighters.extra(1));
        assertEquals(2, Fighters.extra(3));
    }

    @Test
    void placementAndScaleDecode() {
        SummonEvent.Placement placement = parse(SummonEvent.Placement.CODEC,
                "{\"around\": \"fighters\", \"radius\": [3, 6], \"min_gap\": 2, \"clearance\": 3, \"ground\": true}");
        assertEquals(SummonEvent.Placement.Around.FIGHTERS, placement.around());
        assertEquals(new SummonEvent.Radius(3.0, 6.0), placement.radius());
        assertEquals(new SummonEvent.Radius(2.5, 2.5), parse(SummonEvent.Radius.CODEC, "2.5"));
        assertTrue(rejects(SummonEvent.Radius.CODEC, "[6, 3]"));
        assertTrue(rejects(SummonEvent.Placement.CODEC, "{\"around\": \"everyone\"}"));
        SummonEvent.Scale scale = parse(SummonEvent.Scale.CODEC,
                "{\"max_health\": 1.0, \"attack_damage\": 1.0, \"armor\": \"summoner_bonus\", \"damage_dealt\": \"copy\"}");
        assertTrue(scale.armor().orElseThrow().summonerBonus());
        assertTrue(scale.copiesDamageDealt());
        assertEquals(new SummonEvent.StatScale(1.0, false), scale.maxHealth().orElseThrow());
        assertEquals(new SummonEvent.StatScale(0.025, true),
                parse(SummonEvent.Scale.CODEC, "{\"max_health\": {\"of_summoner\": 0.025}}").maxHealth().orElseThrow());
        assertTrue(rejects(SummonEvent.Scale.CODEC, "{\"max_health\": {\"of_summoner\": -1}}"));
        assertTrue(rejects(SummonEvent.Scale.CODEC, "{\"max_health\": {\"share\": 0.025}}"));
        assertEquals(4.0, parse(SummonEvent.Scale.CODEC, "{\"armor\": 4}").armor().orElseThrow().flat());
        assertTrue(rejects(SummonEvent.Scale.CODEC, "{\"armor\": \"lots\"}"));
        assertTrue(rejects(SummonEvent.Scale.CODEC, "{\"damage_dealt\": \"double\"}"));
    }
}
