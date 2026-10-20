package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.entity.threat.StealthCompat;
import net.ledok.mobs_ld.mob.LdMob;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Who a data-driven mob may hurt and aim at. */
public final class CombatTargets {
    private CombatTargets() {
    }

    /**
     * Hostile to the mob: players and player-owned summons it is allowed to attack, plus whatever it is currently
     * targeting. Other monsters, its own adds and teammates are never hit by its areas.
     */
    public static boolean isEnemy(LdMob mob, LivingEntity candidate) {
        if (candidate == mob || !candidate.isAlive() || mob.isAlliedTo(candidate) || !mob.canAttack(candidate)) {
            return false;
        }
        if (candidate instanceof Player || candidate == mob.getTarget()) {
            return true;
        }
        return candidate instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player;
    }

    /** Players the mob could aim at right now: alive, attackable, not stealthed, within its follow range. */
    public static List<LivingEntity> candidates(LdMob mob) {
        double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        List<LivingEntity> result = new ArrayList<>();
        for (Player player : mob.level().players()) {
            if (isEnemy(mob, player) && !StealthCompat.isHidden(player) && mob.distanceToSqr(player) <= range * range
                    && !mob.combat().isUnreachable(player)) {
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
    public static LivingEntity pick(LdMob mob, MobBehaviour.TargetRule rule) {
        if (rule == MobBehaviour.TargetRule.CURRENT) {
            LivingEntity target = mob.getTarget();
            return target != null && isEnemy(mob, target) ? target : null;
        }
        List<LivingEntity> options = candidates(mob);
        if (options.isEmpty()) {
            return null;
        }
        return switch (rule) {
            case NEAREST -> options.stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            case FARTHEST -> options.stream().max(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            case RANDOM -> options.get(mob.getRandom().nextInt(options.size()));
            case CURRENT -> throw new AssertionError();
        };
    }

    /** Horizontal distance between the two bodies' centres. */
    public static double distance(LivingEntity a, LivingEntity b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }
}
