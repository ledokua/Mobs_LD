package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.behaviour.event.HitSpec;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-instance gating ({@code host.only_tagged}) and {@code reference_attributes} (Warden rework M1): which mobs a
 * file applies to, the loader's refusal of a filter together with {@code base_attributes}, and the base R divides by.
 */
class InstanceGatingTest {
    private static final ResourceLocation ATTACK_DAMAGE = ResourceLocation.withDefaultNamespace("generic.attack_damage");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DataResult<MobBehaviour> parse(String json) {
        return MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static String error(String json) {
        return parse(json).error().map(DataResult.Error::message).orElse("");
    }

    @Test
    void onlyTaggedAppliesToTaggedMobsOnly() {
        MobBehaviour run = parse("{\"host\": {\"only_tagged\": [\"arenas_ld_run_mob\", \"other_tag\"]}}").getOrThrow();
        assertEquals(List.of("arenas_ld_run_mob", "other_tag"), run.host().onlyTagged());
        assertTrue(run.host().appliesTo(Set.of("arenas_ld_run_mob")));
        assertTrue(run.host().appliesTo(Set.of("x", "other_tag")), "any one of the listed tags");
        assertFalse(run.host().appliesTo(Set.of()), "an untagged mob stays vanilla");
        assertFalse(run.host().appliesTo(Set.of("arenas_ld_run")), "whole tags only");
        assertTrue(run.host().enabled());
    }

    @Test
    void withoutTheFilterEveryMobOfTheTypeIsHosted() {
        for (String json : List.of("{}", "{\"host\": {\"enabled\": true}}")) {
            MobBehaviour behaviour = parse(json).getOrThrow();
            assertTrue(behaviour.host().onlyTagged().isEmpty());
            assertTrue(behaviour.host().appliesTo(Set.of()));
            assertTrue(behaviour.host().appliesTo(Set.of("arenas_ld_run_mob")));
        }
    }

    @Test
    void anEmptyFilterIsRefused() {
        assertTrue(error("{\"host\": {\"only_tagged\": []}}").contains("only_tagged"));
        assertFalse(error("{\"host\": {\"only_tagged\": \"arenas_ld_run_mob\"}}").isEmpty(), "a list, not a string");
    }

    /** base_attributes are set in the constructor, before any tag: they would reach the mobs the filter keeps vanilla. */
    @Test
    void theLoaderRefusesOnlyTaggedWithBaseAttributes() {
        String both = "{\"base_attributes\": {\"minecraft:generic.max_health\": 800}, "
                + "\"host\": {\"only_tagged\": [\"arenas_ld_run_mob\"]}}";
        String message = error(both);
        assertTrue(message.contains("only_tagged") && message.contains("base_attributes"), message);
        assertEquals("", error("{\"reference_attributes\": {\"minecraft:generic.attack_damage\": 30}, "
                + "\"host\": {\"only_tagged\": [\"arenas_ld_run_mob\"]}}"));
        assertEquals("", error("{\"base_attributes\": {\"minecraft:generic.max_health\": 800}}"));
    }

    @Test
    void referenceAttributesAreTheBaseOfR() {
        MobBehaviour reference = parse("{\"reference_attributes\": {\"minecraft:generic.attack_damage\": 30}}").getOrThrow();
        assertTrue(reference.baseAttributes().isEmpty(), "never applied");
        assertEquals(Optional.of(30.0), reference.declaredBase(ATTACK_DAMAGE));
        assertEquals(1.0, HitSpec.damageScale(30.0, reference), 1.0e-9, "vanilla Warden: R = 1");
        assertEquals(0.5, HitSpec.damageScale(15.0, reference), 1.0e-9, "Weakness lowers R");
        assertEquals(1.5, HitSpec.damageScale(45.0, reference), 1.0e-9);
        // base_attributes win where both name the attribute.
        MobBehaviour both = parse("{\"base_attributes\": {\"minecraft:generic.attack_damage\": 10}, "
                + "\"reference_attributes\": {\"minecraft:generic.attack_damage\": 30}}").getOrThrow();
        assertEquals(Optional.of(10.0), both.declaredBase(ATTACK_DAMAGE));
        assertEquals(2.0, HitSpec.damageScale(20.0, both), 1.0e-9);
        // Neither (or a non-positive base): R is the live attack damage itself, as before.
        assertEquals(Optional.empty(), parse("{}").getOrThrow().declaredBase(ATTACK_DAMAGE));
        assertEquals(30.0, HitSpec.damageScale(30.0, parse("{}").getOrThrow()), 1.0e-9);
        assertEquals(30.0, HitSpec.damageScale(30.0, null), 1.0e-9);
        assertEquals(30.0, HitSpec.damageScale(30.0,
                parse("{\"reference_attributes\": {\"minecraft:generic.attack_damage\": 0}}").getOrThrow()), 1.0e-9);
    }
}
