package net.ledok.mobs_ld.mob.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world-free rules behind the Warden rework's M3: lead prediction (Echo Mines), behind detection (Sculk
 * Backlash), stacks (Sculk Brand), the listen triggers (Listening Silence) and the damage window ({@code top_damage}).
 */
class WardenM3RulesTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    // --- Lead prediction ---

    /** 2 blocks in the last 0.5 s = 4 blocks/s: 0.6 s ahead is 2.4 blocks further on, the height kept. */
    @Test
    void leadFollowsTheLastHalfSecond() {
        Vec3 predicted = MoveLead.predict(new Vec3(0, 64, 0), new Vec3(2, 65, 0), 0.5, 0.6, MoveLead.MAX_LEAD_BLOCKS);
        assertEquals(4.4, predicted.x, 1.0e-9);
        assertEquals(65.0, predicted.y, 1.0e-9, "only horizontal movement is predicted");
        assertEquals(0.0, predicted.z, 1.0e-9);
        Vec3 diagonal = MoveLead.predict(new Vec3(0, 0, 0), new Vec3(-1, 0, 1), 0.5, 1.0, MoveLead.MAX_LEAD_BLOCKS);
        assertEquals(-3.0, diagonal.x, 1.0e-9);
        assertEquals(3.0, diagonal.z, 1.0e-9);
    }

    /** A sprint-jump or a roll would throw the zone far away: the move is capped at 4 blocks, keeping its direction. */
    @Test
    void leadIsCapped() {
        Vec3 newest = new Vec3(5, 0, 0);
        Vec3 predicted = MoveLead.predict(new Vec3(0, 0, 0), newest, 0.5, 1.0, MoveLead.MAX_LEAD_BLOCKS);
        assertEquals(newest.x + 4.0, predicted.x, 1.0e-9, "10 blocks/s for 1 s capped to 4");
        Vec3 slanted = MoveLead.predict(new Vec3(0, 0, 0), new Vec3(3, 0, 4), 0.5, 1.0, 4.0);
        assertEquals(4.0, Math.hypot(slanted.x - 3, slanted.z - 4), 1.0e-9);
        assertEquals(3.0 / 4.0, (slanted.x - 3) / (slanted.z - 4), 1.0e-9, "direction kept");
    }

    @Test
    void stillOrUnsampledPlayersStayPut() {
        Vec3 here = new Vec3(7, 64, -3);
        assertEquals(here, MoveLead.predict(here, here, 0.5, 0.6, 4.0), "standing still");
        assertEquals(here, MoveLead.predict(new Vec3(0, 64, 0), here, 0.0, 0.6, 4.0), "no time span: no velocity");
        assertEquals(here, MoveLead.predict(new Vec3(0, 64, 0), here, 0.5, 0.0, 4.0), "no lead");
    }

    /**
     * Samples are taken at the end of each tick and a zone locks during the next one: the movement is measured over
     * the real ticks since the oldest sample in the window, not the sample count (which overshot by about 10 %).
     */
    @Test
    void leadUsesTheRealElapsedTicks() {
        MoveLead lead = new MoveLead();
        for (long tick = 0; tick <= 10; tick++) {
            lead.sample(A, new Vec3(0.2 * tick, 64, 0), tick); // 0.2 blocks per tick = 4 blocks/s
        }
        // Locking on tick 11, standing at 2.2: 4 blocks/s for 0.6 s = 2.4 further on.
        assertEquals(4.6, lead.predict(A, new Vec3(2.2, 64, 0), 0.6, 11).x, 1.0e-9);
        // Locking much later with an old trail: the newest sample older than the window, over its real age.
        assertEquals(2.2 + 0.6 * (2.2 - 2.0) / 1.0, lead.predict(A, new Vec3(2.2, 64, 0), 0.6, 30).x, 1.0e-9);
        assertEquals(new Vec3(1, 64, 1), lead.predict(B, new Vec3(1, 64, 1), 0.6, 11), "no trail: where it stands");
        lead.clear();
        assertTrue(lead.isEmpty());
    }

    // --- Behind ---

    /** Facing south (yaw 0, forward +Z), its back is -Z; a 140 degree rear cone is 70 to each side of it. */
    @Test
    void behindIsTheRearConeInRange() {
        assertTrue(BehindWatch.isBehind(0, 0, -3, 0.0F, 4.5, 140, 3));
        assertFalse(BehindWatch.isBehind(0, 0, 3, 0.0F, 4.5, 140, 3), "in front");
        assertFalse(BehindWatch.isBehind(0, 0, -5, 0.0F, 4.5, 140, 3), "too far");
        double in = Math.toRadians(69.0);
        double out = Math.toRadians(71.0);
        // 69 / 71 degrees off the back direction (-Z), to the side.
        assertTrue(BehindWatch.isBehind(Math.sin(in) * 3, 0, -Math.cos(in) * 3, 0.0F, 4.5, 140, 3));
        assertFalse(BehindWatch.isBehind(Math.sin(out) * 3, 0, -Math.cos(out) * 3, 0.0F, 4.5, 140, 3));
        assertFalse(BehindWatch.isBehind(3, 0, 0, 0.0F, 4.5, 140, 3), "beside it");
        assertTrue(BehindWatch.isBehind(0, 0, 0, 0.0F, 4.5, 140, 3), "on top of it");
        // Facing east (yaw -90: forward +X), its back is -X.
        assertTrue(BehindWatch.isBehind(-3, 0, 0, -90.0F, 4.5, 140, 3));
        assertFalse(BehindWatch.isBehind(3, 0, 0, -90.0F, 4.5, 140, 3));
    }

    /** A player on a ledge above (or in a pit below) its back does not count. */
    @Test
    void behindHasAHeightBand() {
        assertTrue(BehindWatch.isBehind(0, 3, -3, 0.0F, 4.5, 140, 3), "3 blocks up: still behind");
        assertTrue(BehindWatch.isBehind(0, -3, -3, 0.0F, 4.5, 140, 3));
        assertFalse(BehindWatch.isBehind(0, 3.5, -3, 0.0F, 4.5, 140, 3), "on a ledge");
        assertFalse(BehindWatch.isBehind(0, -3.5, -3, 0.0F, 4.5, 140, 3), "in a pit");
    }

    /** Two players must each stand behind it for 4 s without a break; stepping out restarts that player's count. */
    @Test
    void behindNeedsEnoughPlayersForLongEnough() {
        BehindWatch watch = new BehindWatch();
        long forTicks = 80;
        watch.update(Set.of(A), 0);
        watch.update(Set.of(A, B), 20);
        assertEquals(0, watch.count(79, forTicks));
        watch.update(Set.of(A, B), 80);
        assertEquals(1, watch.count(80, forTicks), "A has 4 s, B only 3 s");
        watch.update(Set.of(A, B), 100);
        assertEquals(2, watch.count(100, forTicks));
        watch.update(Set.of(A), 101); // B steps out
        watch.update(Set.of(A, B), 102); // and back in: B starts over
        assertEquals(1, watch.count(150, forTicks));
        assertEquals(2, watch.count(182, forTicks));
        watch.clear();
        assertEquals(0, watch.count(1000, forTicks));
    }

    // --- Stacks ---

    /** Sculk Brand: +12 % per stack, at most 4 (x1.48), each new stack refreshing the 15 s. */
    @Test
    void stacksMultiplyLaterHitsUpToTheirMax() {
        HitStacks stacks = new HitStacks();
        assertEquals(1.0, stacks.multiplier(A, 0), "no stacks");
        assertEquals(1, stacks.add(A, "sculk_brand", 0.12, 4, 300, 0));
        assertEquals(1.12, stacks.multiplier(A, 10), 1.0e-9);
        assertEquals(2, stacks.add(A, "sculk_brand", 0.12, 4, 300, 100));
        assertEquals(3, stacks.add(A, "sculk_brand", 0.12, 4, 300, 200));
        assertEquals(4, stacks.add(A, "sculk_brand", 0.12, 4, 300, 300));
        assertEquals(4, stacks.add(A, "sculk_brand", 0.12, 4, 300, 400), "capped at max");
        assertEquals(1.48, stacks.multiplier(A, 400), 1.0e-9);
        assertEquals(1.0, stacks.multiplier(B, 400), "per victim");
    }

    @Test
    void stacksExpireTogetherAfterTheirDuration() {
        HitStacks stacks = new HitStacks();
        stacks.add(A, "sculk_brand", 0.12, 4, 300, 0);
        stacks.add(A, "sculk_brand", 0.12, 4, 300, 200); // refreshed: lasts until 500
        assertEquals(2, stacks.count(A, "sculk_brand", 499));
        assertEquals(1.24, stacks.multiplier(A, 499), 1.0e-9);
        assertEquals(0, stacks.count(A, "sculk_brand", 500));
        assertEquals(1.0, stacks.multiplier(A, 500), 1.0e-9, "all gone at once");
        assertTrue(stacks.isEmpty(), "run-out stacks are dropped");
        assertEquals(1, stacks.add(A, "sculk_brand", 0.12, 4, 300, 600), "a new brand starts over");
    }

    /** A victim who died, logged out or left the mob's level loses its stacks; others keep theirs. */
    @Test
    void stacksOfGoneVictimsAreDropped() {
        HitStacks stacks = new HitStacks();
        stacks.add(A, "sculk_brand", 0.12, 4, 300, 0);
        stacks.add(B, "sculk_brand", 0.12, 4, 300, 0);
        stacks.removeVictims(A::equals);
        assertEquals(0, stacks.count(A, "sculk_brand", 1));
        assertEquals(1, stacks.count(B, "sculk_brand", 1));
        assertEquals(1.0, stacks.multiplier(A, 1));
    }

    @Test
    void severalStackIdsMultiplyAndResetClearsAll() {
        HitStacks stacks = new HitStacks();
        stacks.add(A, "sculk_brand", 0.12, 4, 300, 0);
        stacks.add(A, "other", 0.5, 2, 300, 0);
        assertEquals(1.12 * 1.5, stacks.multiplier(A, 1), 1.0e-9);
        stacks.clear();
        assertEquals(1.0, stacks.multiplier(A, 1));
    }

    // --- Listen ---

    private static ListenRules.Sample at(double x, double z) {
        return new ListenRules.Sample(x, z, false, false, 0, false);
    }

    private static ListenRules.Trigger first(ListenRules.Sample start, ListenRules.Sample now) {
        return new ListenRules.Tracker(start, 0).observe(ListenRules.Trigger.ALL, now, 1, 0.6);
    }

    @Test
    void listenCatchesEachTrigger() {
        ListenRules.Sample start = at(0, 0);
        assertNull(first(start, at(0.3, 0.3)), "a small shuffle is fine");
        assertEquals(ListenRules.Trigger.MOVE, first(start, at(0.5, 0.5)));
        assertEquals(ListenRules.Trigger.SPRINT, first(start, new ListenRules.Sample(0, 0, false, true, 0, false)));
        assertEquals(ListenRules.Trigger.JUMP, first(start, new ListenRules.Sample(0, 0, false, false, 1, false)));
        assertEquals(ListenRules.Trigger.USE_ITEM, first(start, new ListenRules.Sample(0, 0, true, false, 0, false)));
    }

    /** A shield already raised (or a bow already drawn) when the window opened is not "starting" to use it. */
    @Test
    void listenOnlyCountsStartingToUseAnItem() {
        ListenRules.Sample holding = new ListenRules.Sample(0, 0, true, false, 0, false);
        ListenRules.Sample lowered = new ListenRules.Sample(0, 0, false, false, 0, false);
        ListenRules.Tracker tracker = new ListenRules.Tracker(holding, 0);
        assertNull(tracker.observe(ListenRules.Trigger.ALL, holding, 1, 0.6));
        assertNull(tracker.observe(ListenRules.Trigger.ALL, lowered, 2, 0.6));
        assertEquals(ListenRules.Trigger.USE_ITEM, tracker.observe(ListenRules.Trigger.ALL, holding, 3, 0.6),
                "lowered and raised again");
    }

    @Test
    void listenOnlyChecksItsTriggers() {
        Set<ListenRules.Trigger> moveOnly = EnumSet.of(ListenRules.Trigger.MOVE);
        ListenRules.Sample start = at(0, 0);
        assertNull(new ListenRules.Tracker(start, 0).observe(moveOnly, new ListenRules.Sample(0, 0, true, true, 3, false),
                1, 0.6));
        assertEquals(ListenRules.Trigger.MOVE, new ListenRules.Tracker(start, 0).observe(moveOnly, at(0, 3), 1, 0.6),
                "a roll moves about 3 blocks");
        assertNull(new ListenRules.Tracker(start, 0).observe(EnumSet.of(ListenRules.Trigger.DAMAGE), at(9, 9), 1, 0.6),
                "damage is reported by the damage event, never by the poll");
    }

    /**
     * A hit's knock-up makes the server count a jump, and its knockback moves the player: neither counts for
     * {@value ListenRules#PUSH_GRACE_TICKS} ticks after the player was hurt or pushed, and the spot they must keep is
     * where they landed.
     */
    @Test
    void listenForgivesKnockbackAndKnockUp() {
        ListenRules.Tracker tracker = new ListenRules.Tracker(at(0, 0), 0);
        Set<ListenRules.Trigger> all = ListenRules.Trigger.ALL;
        // Tick 5: hurt and shoved 2 blocks; tick 6: the server sees the knock-up as a jump, still sliding.
        assertNull(tracker.observe(all, new ListenRules.Sample(2, 0, false, false, 0, true), 5, 0.6));
        assertNull(tracker.observe(all, new ListenRules.Sample(2.8, 0, false, false, 1, true), 6, 0.6));
        assertNull(tracker.observe(all, new ListenRules.Sample(3.0, 0, false, false, 1, false), 14, 0.6),
                "landed during the grace");
        assertNull(tracker.observe(all, new ListenRules.Sample(3.0, 0, false, false, 1, false), 16, 0.6),
                "standing still where it landed is fine");
        assertEquals(ListenRules.Trigger.MOVE, tracker.observe(all, new ListenRules.Sample(3.7, 0, false, false, 1, false),
                17, 0.6), "moving away from the landing spot counts again");
        // The grace does not excuse what a push cannot cause.
        ListenRules.Tracker shoved = new ListenRules.Tracker(new ListenRules.Sample(0, 0, false, false, 0, true), 0);
        assertEquals(ListenRules.Trigger.SPRINT, shoved.observe(all, new ListenRules.Sample(0, 0, false, true, 0, true), 1, 0.6));
        ListenRules.Tracker drawing = new ListenRules.Tracker(at(0, 0), 0);
        assertEquals(ListenRules.Trigger.USE_ITEM, drawing.observe(all, new ListenRules.Sample(0, 0, true, false, 0, true), 1, 0.6));
        // A real jump after the grace still counts.
        ListenRules.Tracker later = new ListenRules.Tracker(at(0, 0), 0);
        assertNull(later.observe(all, new ListenRules.Sample(0, 0, false, false, 1, true), 1, 0.6));
        assertNull(later.observe(all, new ListenRules.Sample(0, 0, false, false, 1, false), 11, 0.6));
        assertEquals(ListenRules.Trigger.JUMP, later.observe(all, new ListenRules.Sample(0, 0, false, false, 2, false), 12, 0.6));
    }

    /** Damage counts as acting in the window only when dealt by an action taken during it. */
    @Test
    void listenDamageMustBeAnActionInTheWindow() {
        assertTrue(ListenRules.countsAsAction(true, true, false, 400, 20), "a melee swing");
        assertTrue(ListenRules.countsAsAction(true, false, false, 5, 20), "an arrow loosed during the window");
        assertFalse(ListenRules.countsAsAction(true, false, false, 25, 20), "an arrow already in flight when it opened");
        assertFalse(ListenRules.countsAsAction(true, false, false, 20, 20), "loosed the tick it opened");
        assertFalse(ListenRules.countsAsAction(false, false, false, 0, 20), "poison, fire, damage over time");
        assertFalse(ListenRules.countsAsAction(true, true, true, 400, 20), "thorns");
    }

    // --- top_damage ---

    @Test
    void topDamageCountsTheLastTenSeconds() {
        DamageLedger ledger = new DamageLedger();
        ledger.record(A, 30, 0);
        ledger.record(B, 10, 50);
        ledger.record(B, 10, 150);
        assertEquals(A, ledger.top(150, id -> true));
        assertEquals(30.0, ledger.dealtBy(A, 200), 1.0e-9, "200 ticks old: still in");
        assertEquals(B, ledger.top(201, id -> true), "A's hit fell out of the window");
        assertEquals(10.0, ledger.dealtBy(B, 251), 1.0e-9);
        assertNull(ledger.top(400, id -> true), "nobody hit it for 10 s");
    }

    @Test
    void topDamageSkipsIneligibleAttackers() {
        DamageLedger ledger = new DamageLedger();
        ledger.record(A, 50, 0);
        ledger.record(B, 5, 1);
        ledger.record(C, 8, 2);
        assertEquals(C, ledger.top(10, id -> !id.equals(A)), "A is out of range or unreachable");
        assertNull(ledger.top(10, id -> false));
        ledger.record(B, 0.0, 3);
        ledger.record(B, -4.0, 3);
        assertEquals(5.0, ledger.dealtBy(B, 10), 1.0e-9, "nothing recorded for no damage");
    }

    @Test
    void topDamageTieGoesToWhoReachedItFirst() {
        DamageLedger ledger = new DamageLedger();
        ledger.record(A, 10, 0);
        ledger.record(B, 10, 1);
        assertEquals(A, ledger.top(5, id -> true));
    }
}
