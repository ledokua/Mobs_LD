package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.entity.threat.ThreatHolder;
import net.ledok.mobs_ld.entity.threat.ThreatTable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Who is fighting a mob, for summon waves that grow with the group ({@code per_extra_player}) and runes placed around
 * the players ({@code placement.around: fighters}): players with threat on its table, alive, not spectating or in
 * creative, within {@value #RANGE} blocks. Bystanders who never hit, healed against or debuffed it do not count.
 * The counting and dealing rules are pure, so they are unit-tested.
 */
public final class Fighters {
    /** Players further away than this do not count as fighting. */
    public static final double RANGE = 48.0;
    /** Runes each fighter gets in turn before the rest are dealt at random. */
    public static final int ROUNDS = 2;

    private Fighters() {
    }

    /** The players fighting {@code mob} now, in the level's player order. */
    public static List<Player> of(Mob mob) {
        ThreatTable table = ((ThreatHolder) mob).getThreatTable();
        List<Player> fighters = new ArrayList<>();
        if (table.isEmpty()) {
            return fighters;
        }
        for (Player player : mob.level().players()) {
            if (player.isAlive() && !player.isSpectator() && !player.isCreative() && table.get(player.getUUID()) > 0.0
                    && player.distanceToSqr(mob) <= RANGE * RANGE) {
                fighters.add(player);
            }
        }
        return fighters;
    }

    /** Players beyond the first, which is what per-player bonuses count: 0 solo (and with nobody fighting). */
    public static int extra(int fighters) {
        return Math.max(0, fighters - 1);
    }

    /**
     * Deals {@code runes} runes to {@code players} players: one each in turn for {@value #ROUNDS} rounds (so a short
     * wave still reaches as many players as it can), then the rest to random players.
     *
     * @param random {@code bound -> [0, bound)}
     * @return for each rune, the index of the player it is placed for; empty without players
     */
    public static int[] deal(int runes, int players, IntUnaryOperator random) {
        if (players <= 0 || runes <= 0) {
            return new int[0];
        }
        int[] owners = new int[runes];
        int dealt = Math.min(runes, players * ROUNDS);
        for (int i = 0; i < dealt; i++) {
            owners[i] = i % players;
        }
        for (int i = dealt; i < runes; i++) {
            owners[i] = players == 1 ? 0 : random.applyAsInt(players);
        }
        return owners;
    }
}
