package net.ledok.mobs_ld.mob.behaviour.event;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hit block's optional fields keep their old defaults. */
class HitSpecTest {
    private static HitSpec parse(String json) {
        return HitSpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void igniteIsOffUnlessSet() {
        assertEquals(0.0, parse("{ \"damage\": 1.0 }").ignite());
        assertEquals(3.0, parse("{ \"damage\": 1.0, \"ignite\": 3 }").ignite());
        assertEquals(0.0, HitSpec.DEFAULT.ignite());
        assertTrue(HitSpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{ \"ignite\": -1 }")).isError());
    }
}
