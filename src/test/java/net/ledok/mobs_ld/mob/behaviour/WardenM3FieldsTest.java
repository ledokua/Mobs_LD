package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.mob.behaviour.event.ChainEvent;
import net.ledok.mobs_ld.mob.behaviour.event.EffectEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitSpec;
import net.ledok.mobs_ld.mob.behaviour.event.ListenEvent;
import net.ledok.mobs_ld.mob.behaviour.event.Origin;
import net.ledok.mobs_ld.mob.behaviour.event.SummonEvent;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.ListenRules;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Warden rework's M3 data: the {@code ring} shape and its hit test, the new ability conditions and their
 * defaults, {@code hit.origin: each_fighter} with {@code lead}, {@code hit.stacks}, {@code effect on: aim},
 * {@code chain.taunt_wins}, {@code listen}, {@code damage_taken.while_adds} and {@code placement.around: home}.
 */
class WardenM3FieldsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static String error(String json) {
        return MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error()
                .map(DataResult.Error::message).orElse("");
    }

    private static MobBehaviour behaviour(String json) {
        return MobBehaviour.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static MobBehaviour.Ability ability(String json) {
        return MobBehaviour.Ability.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    /** A point {@code distance} blocks from the origin in the direction of yaw {@code degrees}. */
    private static boolean hits(Shape shape, double distance, double degrees) {
        double angle = Math.toRadians(degrees);
        return AbilityRun.inside(shape, -Math.sin(angle) * distance, Math.cos(angle) * distance, 37.0F);
    }

    // --- ring ---

    /** Deafening Rings: inside the inner radius is safe, the band from inner to outer (edges included) is hit. */
    @Test
    void ringHitsItsBandOnly() {
        Shape.Ring ring = new Shape.Ring(6.0, 9.0);
        for (double degrees = 0.0; degrees < 360.0; degrees += 15.0) {
            assertFalse(hits(ring, 0.0, degrees), "the centre is safe");
            assertFalse(hits(ring, 5.9, degrees), "hug it: inside is safe");
            assertTrue(hits(ring, 6.001, degrees), "inner edge");
            assertTrue(hits(ring, 7.5, degrees));
            assertTrue(hits(ring, 8.999, degrees), "outer edge");
            assertFalse(hits(ring, 9.1, degrees), "past it");
        }
        assertTrue(ring.contains(6.0, 0.0) && ring.contains(0.0, -9.0), "both edges are part of the band");
        assertEquals(9.0, ring.reach());
        assertEquals(new AttackZone.Ring(6.0F, 9.0F), ring.zone());
        assertEquals(9.0F, ring.zone().maxForwardReach());
        for (double[] point : ring.centreLine(2.0)) {
            assertEquals(7.5, Math.hypot(point[0], point[1]), 1.0e-9, "particles run along the band's middle");
        }
    }

    @Test
    void ringIsValidated() {
        String base = "{\"abilities\": {\"a\": {\"duration\": 3, \"events\": [{\"type\": \"hit\", \"at\": 1, "
                + "\"shape\": {\"type\": \"ring\", %s}}]}}}";
        assertEquals("", error(base.formatted("\"inner\": 0, \"outer\": 3")));
        assertTrue(error(base.formatted("\"inner\": 9, \"outer\": 6")).contains("outer"));
        assertFalse(error(base.formatted("\"inner\": 3, \"outer\": 3")).isEmpty());
        assertFalse(error(base.formatted("\"inner\": 3")).isEmpty());
    }

    // --- ability conditions ---

    /** Every M3 ability field is optional and off: a file without them means what it did. */
    @Test
    void conditionsDefaultToTodaysBehaviour() {
        MobBehaviour.Ability plain = ability("{\"duration\": 1}");
        assertEquals(Optional.empty(), plain.minPlayers());
        assertEquals(Optional.empty(), plain.maxPlayers());
        assertEquals(Optional.empty(), plain.when());
        assertFalse(plain.includeUnreachable());
        assertFalse(plain.countsPlayers());
        assertTrue(plain.allowsPlayers(0) && plain.allowsPlayers(40));
        assertTrue(plain.behind().isEmpty());
    }

    @Test
    void minAndMaxPlayers() {
        MobBehaviour.Ability raise = ability("{\"duration\": 1, \"min_players\": 2}");
        assertTrue(raise.countsPlayers());
        assertFalse(raise.allowsPlayers(0));
        assertFalse(raise.allowsPlayers(1), "a solo dungeon player has one wave only");
        assertTrue(raise.allowsPlayers(2) && raise.allowsPlayers(5));
        MobBehaviour.Ability band = ability("{\"duration\": 1, \"min_players\": 2, \"max_players\": 3}");
        assertTrue(band.allowsPlayers(3));
        assertFalse(band.allowsPlayers(4));
        MobBehaviour.Ability small = ability("{\"duration\": 1, \"max_players\": 1}");
        assertTrue(small.allowsPlayers(0) && small.allowsPlayers(1));
        assertFalse(small.allowsPlayers(2));
        assertTrue(error("{\"abilities\": {\"a\": {\"duration\": 1, \"min_players\": 3, \"max_players\": 2}}}")
                .contains("max_players"));
    }

    @Test
    void whenBehindAndIncludeUnreachableDecode() {
        MobBehaviour.Ability backlash = ability("{\"duration\": 2, \"include_unreachable\": true, "
                + "\"when\": {\"behind\": {\"players\": 2, \"for\": 4, \"range\": 4.5, \"angle\": 140}}}");
        assertTrue(backlash.includeUnreachable());
        assertEquals(new MobBehaviour.Behind(2, 4.0, 4.5, 140.0, 3.0), backlash.behind().orElseThrow());
        assertEquals(new MobBehaviour.Behind(1, 0.0, 5.0, 120.0, 3.0),
                ability("{\"duration\": 2, \"when\": {\"behind\": {}}}").behind().orElseThrow(), "defaults");
        assertTrue(ability("{\"duration\": 2, \"when\": {}}").behind().isEmpty());
    }

    @Test
    void topDamageIsATargetRule() {
        assertEquals(MobBehaviour.TargetRule.TOP_DAMAGE, ability("{\"duration\": 1, \"target\": \"top_damage\"}").target());
        assertEquals("top_damage", MobBehaviour.TargetRule.TOP_DAMAGE.serialized());
        assertTrue(error("{\"abilities\": {\"a\": {\"duration\": 1, \"target\": \"most_damage\"}}}").contains("top_damage"));
    }

    // --- events ---

    private static HitEvent hit(String json) {
        MobBehaviour.Ability ability = ability("{\"duration\": 3, \"events\": [" + json + "]}");
        return (HitEvent) ability.events().get(0);
    }

    @Test
    void hitDefaultsKeepEveryEarlierHit() {
        HitEvent plain = hit("{\"type\": \"hit\", \"at\": 1, \"shape\": {\"type\": \"circle\", \"radius\": 2}}");
        assertEquals(Origin.SELF, plain.origin());
        assertTrue(plain.lead().isEmpty());
        assertFalse(plain.once());
        assertTrue(plain.hit().stacks().isEmpty());
        assertEquals(HitSpec.DEFAULT, plain.hit());
    }

    @Test
    void eachFighterWithLeadAndStacksDecode() {
        HitEvent mine = hit("{\"type\": \"hit\", \"at\": 1.4, \"origin\": \"each_fighter\", \"lead\": 0.6, \"once\": true, "
                + "\"shape\": {\"type\": \"circle\", \"radius\": 2.5}, \"hit\": {\"amount\": 5, "
                + "\"stacks\": {\"id\": \"sculk_brand\", \"per_stack\": 0.12, \"max\": 4, \"duration\": 15, \"min_players\": 3}}}");
        assertEquals(Origin.EACH_FIGHTER, mine.origin());
        assertEquals(Optional.of(0.6), mine.lead());
        assertTrue(mine.once());
        HitSpec.Stacks brand = mine.hit().stacks().orElseThrow();
        assertEquals(new HitSpec.Stacks("sculk_brand", 0.12, 4, 15.0, 3), brand);
        assertEquals("mobs_ld.stacks.sculk_brand", brand.messageKey());
        String base = "{\"abilities\": {\"a\": {\"duration\": 3, \"events\": [%s]}}}";
        assertTrue(error(base.formatted("{\"type\": \"hit\", \"at\": 1, \"lead\": 0.6, "
                + "\"shape\": {\"type\": \"circle\", \"radius\": 2}}")).contains("each_fighter"), "lead needs each_fighter");
        assertFalse(error(base.formatted("{\"type\": \"hit\", \"at\": 1, \"shape\": {\"type\": \"circle\", \"radius\": 2}, "
                + "\"hit\": {\"stacks\": {\"id\": \"x\", \"per_stack\": 0.1}}}")).isEmpty(), "a stack needs its duration");
        assertEquals(new HitSpec.Stacks("x", 0.1, 1, 5.0, 0), HitSpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"stacks\": {\"id\": \"x\", \"per_stack\": 0.1, \"duration\": 5}}")).getOrThrow().stacks().orElseThrow());
        assertFalse(error(base.formatted("{\"type\": \"cloud\", \"at\": 1, \"origin\": \"each_fighter\", \"radius\": 3, "
                + "\"duration\": 2, \"effects\": []}")).isEmpty(), "each_fighter is for hits");
    }

    @Test
    void effectOnAimAndChainTauntWinsDecode() {
        MobBehaviour.Ability sniff = ability("{\"duration\": 4.2, \"events\": ["
                + "{\"type\": \"effect\", \"at\": 3.8, \"on\": \"aim\", \"effects\": [{\"id\": \"minecraft:glowing\", \"duration\": 8}]},"
                + "{\"type\": \"effect\", \"at\": 1, \"radius\": 4, \"effects\": []},"
                + "{\"type\": \"chain\", \"at\": 4.2, \"to\": \"b\", \"taunt_wins\": true},"
                + "{\"type\": \"chain\", \"at\": 4.2, \"to\": \"b\"}]}");
        assertEquals(EffectEvent.On.AIM, ((EffectEvent) sniff.events().get(0)).on());
        assertEquals(EffectEvent.On.AROUND, ((EffectEvent) sniff.events().get(1)).on(), "default: as effects always worked");
        assertTrue(((ChainEvent) sniff.events().get(2)).tauntWins());
        assertFalse(((ChainEvent) sniff.events().get(3)).tauntWins(), "default: the kept aim stays");
    }

    @Test
    void listenDecodesAndIsValidated() {
        String base = "{\"abilities\": {\"a\": {\"duration\": 5, \"events\": [%s]}, \"b\": {\"duration\": 1}}}";
        String listen = "{\"type\": \"listen\", \"from\": 1, \"until\": 3, \"chain_to\": \"b\", \"chain_at\": 4.2, "
                + "\"hit\": {\"amount\": 13}}";
        assertEquals("", error(base.formatted(listen)));
        ListenEvent event = (ListenEvent) behaviour(base.formatted(listen)).abilities().get("a").events().get(0);
        assertEquals(1.0, event.begin());
        assertEquals(4.2, event.end(), 1.0e-9, "it hands over at chain_at");
        assertEquals(40.0, event.range());
        assertEquals(0.6, event.moveTolerance());
        assertEquals(Set.of(ListenRules.Trigger.values()), Set.copyOf(event.triggers()), "every trigger by default");
        assertEquals(List.of("b"), event.chains());
        assertTrue(error(base.formatted(listen.replace("\"b\"", "\"c\""))).contains("'c'"), "chain_to is checked");
        assertTrue(error(base.formatted(listen.replace("\"until\": 3", "\"until\": 0.5"))).contains("before it starts"));
        assertTrue(error(base.formatted(listen.replace("4.2", "2"))).contains("chain_at"));
        assertFalse(error(base.formatted(listen.replace("\"from\": 1,", "\"from\": 1, \"triggers\": [],"))).isEmpty());
        assertFalse(error(base.formatted(listen.replace("\"from\": 1,", "\"from\": 1, \"triggers\": [\"blink\"],"))).isEmpty());
        ListenEvent plain = (ListenEvent) behaviour(base.formatted("{\"type\": \"listen\", \"from\": 1, \"until\": 3, "
                + "\"triggers\": [\"move\", \"jump\"]}")).abilities().get("a").events().get(0);
        assertEquals(3.0, plain.end(), "without chain_at it ends with the window");
        assertTrue(plain.chains().isEmpty());
    }

    // --- phases and summons ---

    private static DamageSource anyDamage() {
        return new DamageSource(Holder.direct(new DamageType("test", 0.0F)));
    }

    /** Resonators: x0.5 only while one of its own adds of that type lives; rules without while_adds always apply. */
    @Test
    void damageTakenWhileAdds() {
        MobBehaviour behaviour = behaviour("{\"phases\": [{\"below_health\": 1.0, \"damage_taken\": ["
                + "{\"kind\": \"all\", \"multiplier\": 0.5, \"while_adds\": \"mobs_ld:sculk_resonator\"},"
                + "{\"kind\": \"all\", \"multiplier\": 0.8}]}]}");
        MobBehaviour.Phase phase = behaviour.phases().get(0);
        ResourceLocation resonator = ResourceLocation.fromNamespaceAndPath("mobs_ld", "sculk_resonator");
        assertEquals(0.4, phase.damageMultiplier(anyDamage(), resonator::equals), 1.0e-9, "a resonator stands");
        assertEquals(0.8, phase.damageMultiplier(anyDamage(), id -> false), 1.0e-9, "none left");
        assertEquals(0.8, phase.damageMultiplier(anyDamage(), ResourceLocation.withDefaultNamespace("zombie")::equals),
                1.0e-9, "an add of another type does not count");
        assertTrue(phase.damageTaken().get(1).whileAdds().isEmpty());
    }

    @Test
    void placementAroundHome() {
        SummonEvent.Placement placement = SummonEvent.Placement.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"around\": \"home\", \"radius\": [14, 18], \"min_gap\": 8}")).getOrThrow();
        assertEquals(SummonEvent.Placement.Around.HOME, placement.around());
        Vec3 anchor = new Vec3(1, 64, 1);
        Vec3 owner = new Vec3(10, 64, 10);
        Vec3 aim = new Vec3(5, 64, 5);
        Vec3 home = new Vec3(-20, 70, 30);
        assertEquals(home, SummonEvent.centre(SummonEvent.Placement.Around.HOME, anchor, owner, aim, home),
                "the arena centre, wherever the mob and the players are");
        assertEquals(anchor, SummonEvent.centre(SummonEvent.Placement.Around.HOME, anchor, owner, aim, null),
                "no home yet: around itself");
        assertEquals(anchor, SummonEvent.centre(SummonEvent.Placement.Around.SELF, anchor, owner, aim, home));
        assertEquals(owner, SummonEvent.centre(SummonEvent.Placement.Around.FIGHTERS, anchor, owner, aim, home));
        assertEquals(aim, SummonEvent.centre(SummonEvent.Placement.Around.AIM, anchor, null, aim, home));
    }
}
