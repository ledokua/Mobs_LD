package net.ledok.mobs_ld.mob.host.wither;

import net.ledok.mobs_ld.mob.behaviour.Seconds;
import net.ledok.mobs_ld.mob.behaviour.SkullSpec;
import net.ledok.mobs_ld.mob.combat.CombatTargets;
import net.ledok.mobs_ld.mob.combat.HeadCadence;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.ledok.mobs_ld.mob.combat.Skulls;
import net.ledok.mobs_ld.mob.host.Head;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/**
 * A hosted Wither's own skull fire between abilities, in place of vanilla's (whose shots WitherBossHostMixin stops
 * once the host options name skulls): the middle head fires the {@code center} spec at its main target (the tank),
 * each side head the {@code side} spec at whoever it is aimed at (its target slot), each exactly every {@code every}
 * (rolled per shot), from its own mouth.
 *
 * <p>Timing ({@link HeadCadence}): the timers only run while the fight is on and nothing else is, so an ability or a
 * stagger pauses them and the heads never burst all at once when it ends; the first shots come one interval into the
 * fight. The side heads are kept apart: the left one starts half an interval after the right one, and neither fires
 * within half a side interval of the other, so on a lone player the second skull does not vanish in the first one's
 * hurt immunity. The middle head runs on its own. A head whose target is out of sight or range (or that a
 * {@code look} holds) waits until it can fire.
 */
final class WitherSkulls {
    private static final Head[] HEADS = {Head.CENTER, Head.RIGHT, Head.LEFT};
    /** A head holding its shot (no target, out of sight or range) looks again this often. */
    private static final int HOLD_TICKS = 4;

    private final HeadCadence cadence = new HeadCadence(HEADS.length).pair(1, 2);

    void tick(WitherBoss wither, MobCombat combat, WitherHostOptions options) {
        if (!combat.engaged() || combat.returning()) {
            cadence.stop();
            return;
        }
        if (!cadence.running()) {
            int[] intervals = new int[HEADS.length];
            for (int i = 0; i < HEADS.length; i++) {
                SkullSpec spec = spec(options, HEADS[i]);
                intervals[i] = spec == null ? 0 : interval(wither, spec);
            }
            cadence.start(intervals); // counted from the next running tick: the first shots come one interval in
            return;
        }
        if (combat.busy()) {
            return; // all skull fire pauses while an ability or a stagger runs
        }
        cadence.advance();
        SkullSpec side = options.skulls().get(WitherHostOptions.SIDE_SKULL);
        int gap = side == null ? 0 : side.every().map(WitherSkulls::halfMean).orElse(0);
        for (int i = 0; i < HEADS.length; i++) {
            Head head = HEADS[i];
            SkullSpec spec = spec(options, head);
            if (spec == null || spec.every().isEmpty() || !cadence.ready(i, gap) || combat.slots().looking(head)) {
                continue;
            }
            LivingEntity target = head == Head.CENTER ? wither.getTarget() : sideTarget(wither, head);
            Vec3 muzzle = combat.adapter().muzzle(wither, head).orElseGet(wither::getEyePosition);
            if (target == null || !CombatTargets.isEnemy(wither, target)
                    || wither.distanceToSqr(target) > spec.range() * spec.range()
                    || !Skulls.clearShot(wither, muzzle, target)) {
                cadence.hold(i, HOLD_TICKS);
                continue;
            }
            String name = head == Head.CENTER ? WitherHostOptions.CENTER_SKULL : WitherHostOptions.SIDE_SKULL;
            Skulls.fire(wither, muzzle, Skulls.aimPoint(target), name, spec.speed(), false, true);
            cadence.fired(i, interval(wither, spec));
        }
    }

    private static SkullSpec spec(WitherHostOptions options, Head head) {
        return options.skulls().get(head == Head.CENTER ? WitherHostOptions.CENTER_SKULL : WitherHostOptions.SIDE_SKULL);
    }

    private static int interval(WitherBoss wither, SkullSpec spec) {
        return spec.every().map(every -> every.rollTicks(wither.getRandom()::nextDouble)).orElse(0);
    }

    /** Half the average of a cadence, in ticks: how far apart the side heads' shots stay. */
    private static int halfMean(Seconds every) {
        return (int) Math.round((every.min() + every.max()) / 2.0 * 20.0 / 2.0);
    }

    /** Whom a side head is aimed at (its synced target id, which only ever holds living entities). */
    private static LivingEntity sideTarget(WitherBoss wither, Head head) {
        int id = wither.getAlternativeTarget(WitherAdapter.targetIndex(head));
        if (id <= 0) {
            return null;
        }
        Entity entity = wither.level().getEntity(id);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }
}
