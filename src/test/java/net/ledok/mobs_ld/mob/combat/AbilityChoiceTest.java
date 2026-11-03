package net.ledok.mobs_ld.mob.combat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ability selection and timing rules of the Warden rework's M2: cooldown groups, no_repeat, fixed_speed. */
class AbilityChoiceTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MobBehaviour.Ability ability(String json) {
        return MobBehaviour.Ability.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void newFieldsDefaultToTodaysBehaviour() {
        MobBehaviour.Ability plain = ability("{\"duration\": 1}");
        assertFalse(plain.fixedSpeed());
        assertFalse(plain.noRepeat());
        assertEquals(Optional.empty(), plain.cooldownGroup());
        assertEquals(1.2, AbilityChoice.speed(plain, 1.2));
    }

    /** Lance and Volley share the {@code lance} group: using one puts both on the cooldown it rolled. */
    @Test
    void cooldownGroupIsShared() {
        MobBehaviour.Ability lance = ability("{\"duration\": 3, \"cooldown_group\": \"lance\", \"cooldown\": 14}");
        MobBehaviour.Ability volley = ability("{\"duration\": 3, \"cooldown_group\": \"lance\", \"cooldown\": 10}");
        MobBehaviour.Ability blow = ability("{\"duration\": 1.6, \"cooldown\": 6}");
        Map<String, Long> readyAt = new HashMap<>();
        Map<String, Long> groups = new HashMap<>();
        Set<String> readyIn = new HashSet<>();
        assertTrue(AbilityChoice.ready("sonic_volley", volley, 0L, readyAt, groups, readyIn));
        AbilityChoice.used("sonic_lance", lance, 100L + 280L, readyAt, groups, readyIn);
        assertFalse(AbilityChoice.ready("sonic_lance", lance, 200L, readyAt, groups, readyIn));
        assertFalse(AbilityChoice.ready("sonic_volley", volley, 200L, readyAt, groups, readyIn), "the group shares it");
        assertTrue(AbilityChoice.ready("crushing_blow", blow, 200L, readyAt, groups, readyIn), "other abilities are not held");
        assertTrue(AbilityChoice.ready("sonic_volley", volley, 380L, readyAt, groups, readyIn));
        // Its own cooldown (an initial cooldown) still counts on its own.
        readyAt.put("sonic_volley", 500L);
        assertFalse(AbilityChoice.ready("sonic_volley", volley, 400L, readyAt, groups, readyIn));
    }

    /** A shorter cooldown rolled later never cuts a group cooldown that runs longer. */
    @Test
    void groupCooldownIsNeverShortened() {
        MobBehaviour.Ability lance = ability("{\"duration\": 3, \"cooldown_group\": \"lance\"}");
        MobBehaviour.Ability volley = ability("{\"duration\": 3, \"cooldown_group\": \"lance\"}");
        Map<String, Long> readyAt = new HashMap<>();
        Map<String, Long> groups = new HashMap<>();
        Set<String> readyIn = new HashSet<>();
        AbilityChoice.used("sonic_lance", lance, 600L, readyAt, groups, readyIn);
        // A debug start of the Volley with a short cooldown, while the Lance's group cooldown runs.
        AbilityChoice.used("sonic_volley", volley, 300L, readyAt, groups, readyIn);
        assertEquals(600L, groups.get("lance"));
        assertFalse(AbilityChoice.ready("sonic_lance", lance, 400L, readyAt, groups, readyIn));
        assertFalse(AbilityChoice.ready("sonic_volley", volley, 400L, readyAt, groups, readyIn));
    }

    /** A phase's ready_in wins over the group's cooldown, once; the next use puts the group on cooldown again. */
    @Test
    void readyInWinsOverTheGroup() {
        MobBehaviour.Ability lance = ability("{\"duration\": 3, \"cooldown_group\": \"lance\"}");
        MobBehaviour.Ability volley = ability("{\"duration\": 3, \"cooldown_group\": \"lance\"}");
        Map<String, Long> readyAt = new HashMap<>();
        Map<String, Long> groups = new HashMap<>();
        Set<String> readyIn = new HashSet<>();
        AbilityChoice.used("sonic_lance", lance, 1000L, readyAt, groups, readyIn);
        AbilityChoice.readyIn("sonic_volley", 300L, readyAt, readyIn);
        assertFalse(AbilityChoice.ready("sonic_volley", volley, 200L, readyAt, groups, readyIn), "not before its time");
        assertTrue(AbilityChoice.ready("sonic_volley", volley, 300L, readyAt, groups, readyIn), "ready_in wins");
        assertFalse(AbilityChoice.ready("sonic_lance", lance, 300L, readyAt, groups, readyIn), "only for the one named");
        AbilityChoice.used("sonic_volley", volley, 1200L, readyAt, groups, readyIn);
        assertFalse(readyIn.contains("sonic_volley"), "spent by its use");
        assertEquals(1200L, groups.get("lance"));
        assertFalse(AbilityChoice.ready("sonic_volley", volley, 1100L, readyAt, groups, readyIn));
    }

    private record Option(String name, MobBehaviour.Ability ability) {
    }

    @Test
    void noRepeatSkipsTheLastAbilityWhileAnotherIsAvailable() {
        MobBehaviour.Ability circle = ability("{\"duration\": 5, \"no_repeat\": true}");
        MobBehaviour.Ability blow = ability("{\"duration\": 1.6}");
        Option circleOption = new Option("sonic_circle", circle);
        Option blowOption = new Option("crushing_blow", blow);
        List<Option> both = List.of(circleOption, blowOption);
        assertEquals(List.of(blowOption), AbilityChoice.withoutRepeat(both, Option::name, Option::ability, "sonic_circle"));
        assertEquals(both, AbilityChoice.withoutRepeat(both, Option::name, Option::ability, "crushing_blow"));
        assertEquals(both, AbilityChoice.withoutRepeat(both, Option::name, Option::ability, null));
        assertEquals(List.of(circleOption), AbilityChoice.withoutRepeat(List.of(circleOption), Option::name,
                Option::ability, "sonic_circle"), "alone it may repeat");
        // Without no_repeat an ability may follow itself.
        assertEquals(List.of(blowOption, circleOption), AbilityChoice.withoutRepeat(List.of(blowOption, circleOption),
                Option::name, Option::ability, "crushing_blow"));
    }

    /**
     * Server ticks from the tick an ability's clip is sent to the tick an event at {@code at} seconds fires, as
     * {@code AbilityRun.tick} runs them: the ability's own clip goes out on the run's first tick (timeline 0), and
     * each tick fires the events due at the clock's reading, then advances it.
     */
    private static int ticksFromClip(double at, double speed) {
        AbilityClock clock = new AbilityClock(speed);
        int clipTick = -1;
        for (int tick = 0; tick < 10_000; tick++) {
            if (clipTick < 0) {
                clipTick = tick; // the opening clip, before any event of this tick
            }
            if (clock.seconds() >= at) {
                return tick - clipTick;
            }
            clock.advance();
        }
        throw new AssertionError("never fires");
    }

    /**
     * In a phase with attack_speed 1.2 a clip-timed ability keeps vanilla time (the Circle's wave lands on the
     * sonic_boom clip's chest burst, 1.7 s = 34 ticks after the clip starts, the tick the clip went out being
     * timeline 0), while a physical one speeds up.
     */
    @Test
    void fixedSpeedIgnoresThePhaseAttackSpeed() {
        MobBehaviour.Ability circle = ability("{\"duration\": 5, \"fixed_speed\": true}");
        MobBehaviour.Ability blow = ability("{\"duration\": 1.6}");
        assertEquals(1.0, AbilityChoice.speed(circle, 1.2));
        assertEquals(1.2, AbilityChoice.speed(blow, 1.2));
        assertEquals(34, ticksFromClip(1.7, AbilityChoice.speed(circle, 1.2)));
        assertEquals(68, ticksFromClip(3.4, AbilityChoice.speed(circle, 1.2)), "wave 2, one clip later");
        assertEquals(29, ticksFromClip(1.7, AbilityChoice.speed(blow, 1.2)), "1.7 s / 1.2, rounded up to a tick");
        assertEquals(34, ticksFromClip(1.7, AbilityChoice.speed(blow, 1.0)));
        assertEquals(0, ticksFromClip(0.0, 1.0), "a clip event at 0 goes out with the ability's own clip");
    }
}
