package net.ledok.mobs_ld.mob.combat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.behaviour.event.SummonEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code minion_link}: its data, the share a killed add takes off its summoner and the exposure message. */
class MinionLinkTest {
    private static final double EPSILON = 1.0e-9;

    private static Optional<SummonEvent.MinionLink> parse(String json) {
        return SummonEvent.MinionLink.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result();
    }

    @Test
    void parsesAndDefaults() {
        SummonEvent.MinionLink link = parse("""
                { "death_damage": { "of_minion_max_health": 0.5, "cap_of_summoner_max_health": 0.03 },
                  "all_dead": { "damage_taken": 1.5 } }""").orElseThrow();
        assertEquals(new SummonEvent.DeathDamage(0.5, Optional.of(0.03)), link.deathDamage().orElseThrow());
        assertEquals(new SummonEvent.AllDead(1.5, SummonEvent.Until.NEXT_SUMMON), link.allDead().orElseThrow());
        assertEquals(0.0, link.whileAliveHealPerSecond(), EPSILON);
        assertEquals(0.0, link.whileAliveHealPercentPerSecond(), EPSILON);
        assertTrue(parse("{ \"all_dead\": { \"damage_taken\": 1.5, \"until\": \"next_summon\" } }").isPresent());

        SummonEvent.MinionLink siphon = parse("{ \"while_alive_heal_per_second\": 3.0 }").orElseThrow();
        assertEquals(3.0, siphon.whileAliveHealPerSecond(), EPSILON);
        assertTrue(siphon.healsSummoner());
        assertTrue(siphon.deathDamage().isEmpty() && siphon.allDead().isEmpty());
        assertTrue(parse("{ \"while_alive_heal_per_second\": -1.0 }").isEmpty());

        SummonEvent.MinionLink pctSiphon = parse("{ \"while_alive_heal_percent_per_second\": 0.015 }").orElseThrow();
        assertEquals(0.015, pctSiphon.whileAliveHealPercentPerSecond(), EPSILON);
        assertTrue(pctSiphon.healsSummoner());
        assertEquals(9.6, pctSiphon.healPerSecondFor(640.0), EPSILON);
        assertEquals(30.0, pctSiphon.healPerSecondFor(2000.0), EPSILON);
        assertTrue(parse("{ \"while_alive_heal_percent_per_second\": -0.05 }").isEmpty());
        assertTrue(parse("{ \"while_alive_heal_percent_per_second\": 1.5 }").isEmpty());
    }

    @Test
    void rejectsAnEmptyLinkAndBadValues() {
        assertTrue(parse("{}").isEmpty());
        assertTrue(parse("{ \"death_damage\": { \"of_minion_max_health\": -1 } }").isEmpty());
        assertTrue(parse("{ \"death_damage\": { \"of_minion_max_health\": 0.5, \"cap_of_summoner_max_health\": 2 } }").isEmpty());
        assertTrue(parse("{ \"all_dead\": { \"damage_taken\": 1.5, \"until\": \"forever\" } }").isEmpty());
    }

    @Test
    void deathDamageIsAShareOfTheAddCappedByTheSummoner() {
        // The live Wither: skeletons at 1.5% of its 2,000 HP (30 HP) take half that, 15, off it; no cap.
        assertEquals(15.0, new SummonEvent.DeathDamage(0.5, Optional.empty()).amount(30.0, 2000.0), EPSILON);
        // A cap of 0.5% of the summoner's 2,000 HP holds it to 10.
        assertEquals(10.0, new SummonEvent.DeathDamage(0.5, Optional.of(0.005)).amount(30.0, 2000.0), EPSILON);
        assertEquals(15.0, new SummonEvent.DeathDamage(0.5, Optional.of(0.03)).amount(30.0, 2000.0), EPSILON);
    }

    @Test
    void exposurePercent() {
        assertEquals("+50", MinionLinks.percent(1.5));
        assertEquals("+100", MinionLinks.percent(2.0));
        assertEquals("-25", MinionLinks.percent(0.75));
    }
}
