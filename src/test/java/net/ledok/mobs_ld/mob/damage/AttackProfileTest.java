package net.ledok.mobs_ld.mob.damage;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Attack profiles: the file format, its checks and what each action makes of a hit. */
class AttackProfileTest {
    private static final double EPSILON = 1.0e-6;

    private static Optional<AttackProfile> parse(String json) {
        return AttackProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result();
    }

    @Test
    void parsesTheDesignExample() {
        AttackProfile profile = parse("""
                { "entity": "bosses_of_mass_destruction:lich", "reference_hit": 12.0,
                  "attacks": [
                    { "match": { "damage_type": "minecraft:mob_attack" }, "hits": 1.0 },
                    { "match": { "direct": "bosses_of_mass_destruction:blue_fireball" }, "hits": 1.5 },
                    { "match": { "damage_type": "#minecraft:is_explosion" }, "multiply": 0.4 },
                    { "match": { "damage_type": "minecraft:magic", "no_attacker": true, "within": 24 }, "hits": 0.5 },
                    { "match": { "ability": "grave_call" }, "ignore": true },
                    { "set": 7 }
                  ],
                  "cap_of_target_max_health": 0.45 }""").orElseThrow();
        assertEquals(ResourceLocation.fromNamespaceAndPath("bosses_of_mass_destruction", "lich"), profile.entity());
        assertEquals(Optional.of(12.0), profile.referenceHit());
        assertEquals(Optional.of(0.45), profile.capOfTargetMaxHealth());
        assertEquals(6, profile.attacks().size());
        AttackProfile.Match noAttacker = profile.attacks().get(3).match();
        assertTrue(noAttacker.noAttacker());
        assertEquals(24.0, noAttacker.within(), EPSILON);
        assertEquals(AttackProfile.Match.ANY, profile.attacks().get(5).match());
        assertTrue(profile.referenceAttackDamage().isEmpty());
    }

    @Test
    void rejectsBadFiles() {
        // Two actions, no action, "hits" without a reference_hit, a broken damage type id.
        assertFalse(parse("{ \"entity\": \"zombie\", \"reference_hit\": 3, \"attacks\": [ { \"hits\": 1, \"multiply\": 2 } ] }").isPresent());
        assertFalse(parse("{ \"entity\": \"zombie\", \"attacks\": [ { \"match\": {} } ] }").isPresent());
        assertFalse(parse("{ \"entity\": \"zombie\", \"attacks\": [ { \"hits\": 1 } ] }").isPresent());
        assertFalse(parse("{ \"entity\": \"zombie\", \"attacks\": [ { \"match\": { \"damage_type\": \"#Bad Tag\" }, \"set\": 1 } ] }").isPresent());
        // A cap alone (no attacks) is a valid profile.
        assertTrue(parse("{ \"entity\": \"creeper\", \"cap_of_target_max_health\": 0.3 }").isPresent());
    }

    @Test
    void actionsResizeTheHit() {
        AttackProfile.Attack hits = new AttackProfile.Attack(AttackProfile.Match.ANY, Optional.of(1.5), Optional.empty(),
                Optional.empty(), false);
        // 1.5 hits of 12 at R 1, then at R 1.3 (attack_damage raised 30 % by a level)
        assertEquals(18.0, hits.apply(90.0F, 12.0, 1.0), EPSILON);
        assertEquals(23.4, hits.apply(90.0F, 12.0, 1.3), 1.0e-4);
        AttackProfile.Attack multiply = new AttackProfile.Attack(AttackProfile.Match.ANY, Optional.empty(),
                Optional.of(0.35), Optional.empty(), false);
        assertEquals(15.05, multiply.apply(43.0F, 0.0, 1.0), 1.0e-4); // a point-blank creeper
        AttackProfile.Attack set = new AttackProfile.Attack(AttackProfile.Match.ANY, Optional.empty(), Optional.empty(),
                Optional.of(9.0), false);
        assertEquals(9.0, set.apply(129.0F, 0.0, 2.0), EPSILON);
        AttackProfile.Attack ignore = new AttackProfile.Attack(AttackProfile.Match.ANY, Optional.empty(),
                Optional.empty(), Optional.empty(), true);
        assertEquals(129.0, ignore.apply(129.0F, 12.0, 2.0), EPSILON);
    }

    @Test
    void matchOnDirectAndAbility() {
        AttackProfile.Match match = new AttackProfile.Match(Optional.empty(),
                Optional.of(ResourceLocation.withDefaultNamespace("small_fireball")), Optional.of("volley"), false, 16.0);
        ResourceLocation fireball = ResourceLocation.withDefaultNamespace("small_fireball");
        assertTrue(match.matches(null, fireball, "volley"));
        assertFalse(match.matches(null, fireball, "dash"));
        assertFalse(match.matches(null, null, "volley"));
        assertTrue(AttackProfile.Match.ANY.matches(null, null, null));
        assertEquals(List.of(), parse("{ \"entity\": \"zombie\" }").orElseThrow().attacks());
    }
}
