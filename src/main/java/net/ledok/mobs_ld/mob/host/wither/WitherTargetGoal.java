package net.ledok.mobs_ld.mob.host.wither;

import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * Stands in for the Wither's own nearest-target goal (same priority and settings), and is that goal exactly while its
 * behaviour does not say {@code engage_on: players} (or is gone): the nearest living thing that is no undead "wither
 * friend". Under {@code engage_on: players} it only looks for players, the way a player-targeting goal does (the
 * nearest one in follow range it can see), so cows and endermen never start the fight. Decided on every search, so a
 * reload switches it either way.
 */
final class WitherTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
    WitherTargetGoal(WitherBoss wither) {
        super(wither, LivingEntity.class, 0, false, false, candidate -> isPrey(wither, candidate));
    }

    /** WitherBoss's own selector (not undead, attackable), narrowed by the behaviour's engage rule. */
    private static boolean isPrey(WitherBoss wither, LivingEntity candidate) {
        if (candidate.getType().is(EntityTypeTags.WITHER_FRIENDS) || !candidate.attackable()) {
            return false;
        }
        MobCombat combat = CombatHost.of(wither);
        return combat == null || combat.mayTarget(candidate);
    }

    @Override
    protected void findTarget() {
        MobCombat combat = CombatHost.of(mob);
        if (combat != null && combat.engagesPlayersOnly()) {
            target = mob.level().getNearestPlayer(targetConditions, mob, mob.getX(), mob.getEyeY(), mob.getZ());
            return;
        }
        super.findTarget();
    }
}
