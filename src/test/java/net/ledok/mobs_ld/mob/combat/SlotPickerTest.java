package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.mob.combat.SlotPicker.Candidate;
import net.ledok.mobs_ld.mob.combat.SlotPicker.Rule;
import net.ledok.mobs_ld.mob.combat.SlotPicker.Status;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Wither's head rules: the middle head is the tank (the main target), the right head the lowest-threat player
 * ({@link Rule#LOWEST_THREAT}), the left head spreads ({@link Rule#SPREAD}).
 */
class SlotPickerTest {
    private static final double RANGE = 30.0;
    private static final IntUnaryOperator FIRST = bound -> 0;
    private static final IntUnaryOperator LAST = bound -> bound - 1;

    private static Candidate<String> player(String id, double threat) {
        return new Candidate<>(id, true, threat, true, false, 10.0, true);
    }

    private static Candidate<String> pet(String id, double threat) {
        return new Candidate<>(id, false, threat, true, false, 10.0, true);
    }

    private static Candidate<String> hidden(String id, double threat) {
        return new Candidate<>(id, true, threat, true, true, 10.0, true);
    }

    private static String right(List<Candidate<String>> candidates, String tank, IntUnaryOperator random) {
        return SlotPicker.pick(Rule.LOWEST_THREAT, candidates, RANGE, true, tank, Map.of(), null, random);
    }

    /** The left head, with the middle head on the tank and the right head on {@code right}. */
    private static String left(List<Candidate<String>> candidates, String tank, String right, String previous,
                               IntUnaryOperator random) {
        Map<String, Integer> headsOn = new HashMap<>();
        if (tank != null) {
            headsOn.merge(tank, 1, Integer::sum);
        }
        if (right != null) {
            headsOn.merge(right, 1, Integer::sum);
        }
        return SlotPicker.pick(Rule.SPREAD, candidates, RANGE, true, tank, headsOn, previous, random);
    }

    @Test
    void soloWithAPetPutsEveryHeadOnThePlayer() {
        List<Candidate<String>> candidates = List.of(player("steve", 40.0), pet("wolf", 5.0));
        String right = right(candidates, "steve", FIRST);
        assertEquals("steve", right);
        for (IntUnaryOperator random : List.of(FIRST, LAST)) {
            assertEquals("steve", left(candidates, "steve", right, null, random));
            assertEquals("steve", left(candidates, "steve", right, "steve", random));
        }
    }

    @Test
    void soloEvenWithTheWolfHoldingAggroLeavesThePetToTheMiddleHead() {
        // A tamed wolf tanking: the side heads still go to the player, never the pet.
        List<Candidate<String>> candidates = List.of(player("steve", 10.0), pet("wolf", 50.0));
        String right = right(candidates, "wolf", FIRST);
        assertEquals("steve", right);
        assertEquals("steve", left(candidates, "wolf", right, null, LAST));
    }

    @Test
    void twoPlayersTheLeftHeadAlternates() {
        List<Candidate<String>> candidates = List.of(player("tank", 100.0), player("healer", 20.0));
        String right = right(candidates, "tank", FIRST);
        assertEquals("healer", right);
        String previous = left(candidates, "tank", right, null, FIRST);
        List<String> picks = new ArrayList<>();
        picks.add(previous);
        for (int repick = 0; repick < 6; repick++) {
            String next = left(candidates, "tank", right, previous, new Random(repick)::nextInt);
            assertNotEquals(previous, next, "re-pick " + repick + " must switch players");
            picks.add(next);
            previous = next;
        }
        assertTrue(picks.contains("tank") && picks.contains("healer"), picks.toString());
    }

    @Test
    void threePlayersGetOneHeadEach() {
        List<Candidate<String>> candidates = List.of(player("tank", 100.0), player("dps", 50.0), player("healer", 10.0));
        String right = right(candidates, "tank", FIRST);
        assertEquals("healer", right);
        for (IntUnaryOperator random : List.of(FIRST, LAST)) {
            assertEquals("dps", left(candidates, "tank", right, null, random));
            assertEquals("dps", left(candidates, "tank", right, "dps", random)); // only free player: kept
        }
    }

    @Test
    void fourPlayersTheLeftHeadAvoidsItsPreviousPick() {
        List<Candidate<String>> candidates = List.of(player("tank", 100.0), player("a", 50.0), player("b", 40.0),
                player("healer", 10.0));
        String right = right(candidates, "tank", FIRST);
        assertEquals("healer", right);
        // a and b are both free: it switches away from whichever it had.
        for (IntUnaryOperator random : List.of(FIRST, LAST)) {
            assertEquals("b", left(candidates, "tank", right, "a", random));
            assertEquals("a", left(candidates, "tank", right, "b", random));
        }
    }

    @Test
    void lowestThreatTiesGoToSomeoneOtherThanTheTank() {
        // Fight just started: nobody has threat yet, the tank is whoever it targeted first.
        List<Candidate<String>> candidates = List.of(player("tank", 0.0), player("a", 0.0), player("b", 0.0));
        assertEquals("a", right(candidates, "tank", FIRST));
        assertEquals("b", right(candidates, "tank", LAST));
        for (int seed = 0; seed < 20; seed++) {
            assertNotEquals("tank", right(candidates, "tank", new Random(seed)::nextInt));
        }
        // Threat 0 counts as lowest; a tie inside the TIE margin is still a tie.
        List<Candidate<String>> close = List.of(player("tank", 5.0), player("a", 5.0 + 1.0e-9), player("b", 9.0));
        assertEquals("a", right(close, "tank", FIRST));
        // The tank alone at the bottom keeps the head (solo, or everyone else is busier).
        assertEquals("tank", right(List.of(player("tank", 0.0), player("a", 3.0)), "tank", LAST));
    }

    @Test
    void spreadTiesOnFewestHeadsGoAwayFromThePreviousPick() {
        // Everyone already has a head (two players, both tank and right head on someone): fewest-heads tie.
        List<Candidate<String>> candidates = List.of(player("a", 10.0), player("b", 10.0));
        Map<String, Integer> headsOn = Map.of("a", 1, "b", 1);
        assertEquals("b", SlotPicker.pick(Rule.SPREAD, candidates, RANGE, true, "a", headsOn, "a", FIRST));
        assertEquals("a", SlotPicker.pick(Rule.SPREAD, candidates, RANGE, true, "a", headsOn, "b", LAST));
        // Fewest heads wins over avoiding the previous pick: b has fewer, so it stays even though it was the last pick.
        Map<String, Integer> busy = Map.of("a", 2, "b", 1);
        assertEquals("b", SlotPicker.pick(Rule.SPREAD, candidates, RANGE, true, "a", busy, "b", FIRST));
    }

    @Test
    void stealthedPlayersAreNeverPicked() {
        // Rogue vanish: the hidden player has the least threat but is skipped.
        List<Candidate<String>> candidates = List.of(hidden("rogue", 0.0), player("tank", 80.0));
        assertEquals("tank", right(candidates, "tank", FIRST));
        assertEquals("tank", left(candidates, "tank", "tank", null, FIRST));
        // Solo player in vanish: no player qualifies, so only the left head falls back to the pet on the table.
        List<Candidate<String>> vanished = List.of(hidden("rogue", 30.0), pet("wolf", 12.0), pet("stray_cat", 0.0));
        assertNull(right(vanished, "wolf", FIRST));
        assertEquals("wolf", left(vanished, "wolf", null, null, LAST));
        // A pet without threat is never a fallback.
        assertNull(left(List.of(hidden("rogue", 30.0), pet("stray_cat", 0.0)), null, null, null, FIRST));
    }

    @Test
    void outOfRangeOutOfSightAndUntargetableAreSkipped() {
        Candidate<String> far = new Candidate<>("far", true, 0.0, true, false, 31.0, true);
        Candidate<String> behindWall = new Candidate<>("wall", true, 0.0, true, false, 10.0, false);
        Candidate<String> creative = new Candidate<>("creative", true, 0.0, false, false, 5.0, true);
        Candidate<String> tank = player("tank", 50.0);
        List<Candidate<String>> candidates = List.of(far, behindWall, creative, tank);
        assertEquals("tank", right(candidates, "tank", FIRST));
        assertFalse(SlotPicker.eligible(far, RANGE, true));
        assertFalse(SlotPicker.eligible(behindWall, RANGE, true));
        assertTrue(SlotPicker.eligible(behindWall, RANGE, false)); // a slot without line_of_sight takes it
        assertFalse(SlotPicker.eligible(creative, RANGE, false));
        assertNull(right(List.of(far, behindWall, creative), null, FIRST));
    }

    @Test
    void repicksEveryTenSecondsWithTheLeftHeadFiveSecondsBehind() {
        SlotPicker.Timer right = new SlotPicker.Timer();
        SlotPicker.Timer left = new SlotPicker.Timer();
        long offset = 100;
        // Both pick as the fight starts.
        assertTrue(right.due(0, Status.NONE, 40));
        assertTrue(left.due(0, Status.NONE, 40, right, offset));
        right.picked(0, true, 200, 0, 10);
        left.picked(0, true, 210, offset, 10);
        assertFalse(right.due(199, Status.SEEN, 40));
        assertTrue(right.due(200, Status.SEEN, 40));
        // The left head re-picks 5 s after the right head's pick, not on its own 10.5 s.
        assertFalse(left.due(99, Status.SEEN, 40, right, offset));
        assertTrue(left.due(100, Status.SEEN, 40, right, offset));
        left.picked(100, true, 210, offset, 10);
        assertEquals(200, left.ticksUntilPick(100, right, offset)); // waits for the right head's next pick (200) + 5 s
        right.picked(200, true, 180, 0, 10); // rolled 9 s this time
        assertFalse(left.due(299, Status.SEEN, 40, right, offset));
        assertTrue(left.due(300, Status.SEEN, 40, right, offset));
        left.picked(300, true, 220, offset, 10);
        // The right head loses its target and re-picks at once: the left one moves with it, 5 s later.
        assertTrue(right.due(330, Status.GONE, 40));
        right.picked(330, true, 200, 0, 10);
        assertFalse(left.due(380, Status.SEEN, 40, right, offset)); // its old time (right's 380 + 5 s) moved along
        assertFalse(left.due(429, Status.SEEN, 40, right, offset));
        assertTrue(left.due(430, Status.SEEN, 40, right, offset));
        left.picked(430, true, 220, offset, 10);
        // The left head loses its own target: it re-picks at once and then keeps the phase (right's next + 5 s).
        assertTrue(left.due(450, Status.GONE, 40, right, offset));
        left.picked(450, true, 220, offset, 10);
        assertEquals(630, 450 + left.ticksUntilPick(450, right, offset));
        assertFalse(left.due(629, Status.SEEN, 40, right, offset));
        assertTrue(left.due(630, Status.SEEN, 40, right, offset));
        // Many cycles of independent 9-11 s rolls never bring the two together: no drift.
        SlotPicker.Timer lead = new SlotPicker.Timer();
        SlotPicker.Timer follower = new SlotPicker.Timer();
        Random random = new Random(3);
        lead.picked(0, true, 200, 0, 10);
        follower.picked(0, true, 200, offset, 10);
        assertTrue(follower.due(100, Status.SEEN, 40, lead, offset));
        follower.picked(100, true, 180 + random.nextInt(41), offset, 10);
        long leadNext = 200;
        for (int cycle = 0; cycle < 50; cycle++) {
            long now = leadNext;
            assertFalse(follower.due(now - 1, Status.SEEN, 40, lead, offset), "cycle " + cycle);
            assertTrue(lead.due(now, Status.SEEN, 40), "cycle " + cycle);
            int roll = 180 + random.nextInt(41);
            lead.picked(now, true, roll, 0, 10);
            leadNext = now + roll;
            assertFalse(follower.due(now + offset - 1, Status.SEEN, 40, lead, offset), "cycle " + cycle);
            assertTrue(follower.due(now + offset, Status.SEEN, 40, lead, offset), "cycle " + cycle);
            follower.picked(now + offset, true, 180 + random.nextInt(41), offset, 10);
        }
        // A reset (fight over) picks at once next time.
        left.reset();
        right.reset();
        assertTrue(right.due(5000, Status.NONE, 40));
        assertTrue(left.due(5000, Status.NONE, 40, right, offset));
    }

    @Test
    void withoutALeadTheOffsetDelaysTheFirstScheduledRepick() {
        SlotPicker.Timer left = new SlotPicker.Timer();
        left.picked(0, true, 200, 100, 10);
        assertFalse(left.due(299, Status.SEEN, 40));
        assertTrue(left.due(300, Status.SEEN, 40));
        left.picked(300, true, 200, 100, 10);
        assertFalse(left.due(499, Status.SEEN, 40));
        assertTrue(left.due(500, Status.SEEN, 40));
        // A lead that holds nobody leaves the follower on its own timer instead of re-picking with every retry.
        SlotPicker.Timer lead = new SlotPicker.Timer();
        lead.picked(500, false, 200, 0, 10);
        left.picked(500, true, 200, 100, 10);
        assertFalse(left.due(520, Status.SEEN, 40, lead, 100));
        assertFalse(left.due(699, Status.SEEN, 40, lead, 100));
        assertTrue(left.due(700, Status.SEEN, 40, lead, 100));
    }

    @Test
    void aPetHoldingTheSpreadSlotGivesItBackToAPlayerAtOnce() {
        SlotPicker.Timer left = new SlotPicker.Timer();
        left.picked(0, true, 200, 0, 10);
        assertFalse(left.due(20, Status.SEEN, 40));
        assertTrue(left.due(20, Status.FALLBACK, 40));
        // With the player back in sight, the spread pick prefers the player over the pet it held.
        List<Candidate<String>> candidates = List.of(player("steve", 30.0), pet("wolf", 50.0));
        assertEquals("steve", left(candidates, "wolf", "steve", "wolf", FIRST));
    }

    @Test
    void lostTargetsAreReplaced() {
        SlotPicker.Timer timer = new SlotPicker.Timer();
        timer.picked(0, true, 200, 0, 10);
        // Died, left, too far or vanished: at once.
        assertTrue(timer.due(5, Status.GONE, 40));
        // Out of the head's sight: after lost_after (2 s), not before; seeing it again resets the grace.
        timer.picked(10, true, 200, 0, 10);
        assertFalse(timer.due(20, Status.HIDDEN, 40));
        assertEquals(10, timer.ticksHidden(30));
        assertFalse(timer.due(59, Status.HIDDEN, 40));
        assertTrue(timer.due(60, Status.HIDDEN, 40));
        timer.picked(60, true, 200, 0, 10);
        assertFalse(timer.due(70, Status.HIDDEN, 40));
        assertFalse(timer.due(80, Status.SEEN, 40));
        assertEquals(-1, timer.ticksHidden(80));
        assertFalse(timer.due(100, Status.HIDDEN, 40));
        assertFalse(timer.due(139, Status.HIDDEN, 40));
        assertTrue(timer.due(140, Status.HIDDEN, 40));
        // Nobody to pick: tries again shortly instead of waiting a whole re-pick period.
        timer.picked(140, false, 200, 0, 10);
        assertFalse(timer.due(149, Status.NONE, 40));
        assertTrue(timer.due(150, Status.NONE, 40));
    }
}
