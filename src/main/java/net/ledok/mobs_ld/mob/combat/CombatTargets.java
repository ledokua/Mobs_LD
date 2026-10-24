package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.entity.threat.StealthCompat;
import net.ledok.mobs_ld.entity.threat.ThreatHolder;
import net.ledok.mobs_ld.entity.threat.ThreatTable;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Who a mob running a behaviour may hurt and aim at. */
public final class CombatTargets {
    private CombatTargets() {
    }

    /**
     * Hostile to the mob: players and player-owned summons it is allowed to attack, plus whatever it is currently
     * targeting. Other monsters, its own adds and teammates are never hit by its areas.
     */
    public static boolean isEnemy(Mob mob, LivingEntity candidate) {
        if (candidate == mob || !candidate.isAlive() || mob.isAlliedTo(candidate) || !mob.canAttack(candidate)) {
            return false;
        }
        if (candidate instanceof Player || candidate == mob.getTarget()) {
            return true;
        }
        return candidate instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player;
    }

    /** Players the mob could aim at right now: alive, attackable, not stealthed, within its follow range. */
    public static List<LivingEntity> candidates(Mob mob) {
        double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        MobCombat combat = CombatHost.of(mob);
        List<LivingEntity> result = new ArrayList<>();
        for (Player player : mob.level().players()) {
            if (isEnemy(mob, player) && !StealthCompat.isHidden(player) && mob.distanceToSqr(player) <= range * range
                    && (combat == null || !combat.isUnreachable(player))) {
                result.add(player);
            }
        }
        LivingEntity target = mob.getTarget();
        if (target != null && !(target instanceof Player) && isEnemy(mob, target)) {
            result.add(target); // a summon holding aggro is a fair pick too
        }
        return result;
    }

    /** Applies a target rule; null when nobody qualifies. */
    public static LivingEntity pick(Mob mob, MobBehaviour.TargetRule rule) {
        switch (rule.kind()) {
            case CURRENT -> {
                LivingEntity target = mob.getTarget();
                return target != null && isEnemy(mob, target) ? target : null;
            }
            case SLOT -> {
                MobCombat combat = CombatHost.of(mob);
                LivingEntity held = combat == null ? null : combat.slots().target(rule.slot().orElse(""));
                return held != null && isEnemy(mob, held) ? held : null;
            }
            default -> {
            }
        }
        List<LivingEntity> options = candidates(mob);
        if (options.isEmpty()) {
            return null;
        }
        return switch (rule.kind()) {
            case NEAREST -> options.stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            case FARTHEST -> options.stream().max(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            case RANDOM -> options.get(mob.getRandom().nextInt(options.size()));
            case LOWEST_THREAT -> lowestThreat(mob, options);
            case CURRENT, SLOT -> throw new AssertionError();
        };
    }

    /** The lowest-threat player among {@code options} (0 counts); a tie goes to someone other than the tank. */
    private static LivingEntity lowestThreat(Mob mob, List<LivingEntity> options) {
        ThreatTable table = ((ThreatHolder) mob).getThreatTable();
        List<SlotPicker.Candidate<LivingEntity>> players = new ArrayList<>();
        for (LivingEntity option : options) {
            if (option instanceof Player) {
                players.add(new SlotPicker.Candidate<>(option, true, table.get(option.getUUID()), true, false, 0.0, true));
            }
        }
        return SlotPicker.lowestThreat(players, mob.getTarget(), bound -> mob.getRandom().nextInt(bound));
    }

    /** Horizontal distance between the two bodies' centres. */
    public static double distance(LivingEntity a, LivingEntity b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }
}
