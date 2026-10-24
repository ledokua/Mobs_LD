package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seconds: a number or a [min, max] range rolled per use. */
class SecondsTest {
    private static Seconds parse(String json) {
        return Seconds.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static boolean rejects(String json) {
        return Seconds.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isError();
    }

    @Test
    void readsNumbersAndRanges() {
        assertEquals(Seconds.of(4.0), parse("4"));
        assertEquals(new Seconds(9.0, 11.0), parse("[9, 11]"));
        assertEquals(Seconds.of(3.5), parse("[3.5, 3.5]"));
        assertTrue(rejects("[5, 4]"));
        assertTrue(rejects("[1, 2, 3]"));
        assertTrue(rejects("-1"));
        assertTrue(rejects("\"soon\""));
    }

    @Test
    void rollsInsideTheRange() {
        Seconds repick = new Seconds(9.0, 11.0);
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            int ticks = repick.rollTicks(random::nextDouble);
            assertTrue(ticks >= 180 && ticks <= 220, "rolled " + ticks);
        }
        assertEquals(80, Seconds.of(4.0).rollTicks(random::nextDouble));
        assertEquals(0, Seconds.of(0.0).rollTicks(random::nextDouble));
    }
}
