package net.ledok.mobs_ld.mob.host.wither;

import com.mojang.logging.LogUtils;
import net.ledok.mobs_ld.mixin.MobGoalsAccessor;
import net.ledok.mobs_ld.mixin.wither.WitherBossAccessor;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.ledok.mobs_ld.mob.host.HostAdapter;
import net.ledok.mobs_ld.mob.host.HostAdapters;
import net.ledok.mobs_ld.mob.host.HostSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Runs the ability engine on the vanilla Wither ({@code data/minecraft/mob_behaviour/wither.json}). The Wither keeps
 * its entity, model, build ritual and 11 s charge, death and loot, and its own boss bar; the engine starts once the
 * charge is over (a Wither made by {@code /summon} or a spawner has none and starts at once).
 *
 * <p>What changes for a hosted Wither (WitherBossHostMixin applies the {@link WitherHostOptions}): its target goal
 * follows {@code boss.engage_on}; while an ability, a stagger or the walk home runs, its own flight stops (and during
 * an ability it holds still unless an event moves it); its hover height, regeneration, arrow immunity and block
 * breaking follow the host options of the current phase; each phase may recolour its bar. Skulls and side heads stay
 * vanilla for now. A Wither whose behaviour is absent or switched off is fully vanilla.
 */
public final class WitherAdapter implements HostAdapter {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The Wither's own nearest-target goal sits here (WitherBoss.registerGoals). */
    private static final int TARGET_GOAL_PRIORITY = 2;
    private static final WitherAdapter INSTANCE = new WitherAdapter();
    private static final MobBehaviour.BossBar VANILLA_BAR = new MobBehaviour.BossBar(BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);

    private WitherAdapter() {
    }

    /** Call from the mod initializer, before any world (or datapack) loads. */
    public static void register() {
        HostSpec.registerType(WitherHostOptions.TYPE, WitherHostOptions.CODEC);
        HostAdapters.register(EntityType.WITHER, INSTANCE);
    }

    /**
     * The options a Wither's mixins apply now: null while it is not hosted (no combat, or its behaviour is gone),
     * vanilla defaults while its file has no Wither host block (or it is another mod's Wither subclass, which does
     * not read them). Cheap: called every tick.
     */
    public static WitherHostOptions options(Mob wither) {
        MobCombat combat = CombatHost.of(wither);
        if (combat == null || combat.behaviour() == null) {
            return null;
        }
        return combat.adapter() == INSTANCE && combat.hostOptions() instanceof WitherHostOptions options
                ? options : WitherHostOptions.VANILLA;
    }

    /** Whether the engine owns the body now (ability, stagger, walk home), so the vanilla chase must not steer it. */
    public static boolean holdsMovement(Mob wither) {
        MobCombat combat = CombatHost.of(wither);
        return combat != null && combat.behaviour() != null && (combat.busy() || combat.returning());
    }

    /** Built Withers wait out the summoning charge (blue, immune, frozen); everything else starts at once. */
    @Override
    public boolean ready(Mob mob) {
        return !(mob instanceof WitherBoss wither) || wither.getInvulnerableTicks() <= 0;
    }

    /** Swaps the vanilla nearest-target goal for one that follows {@code boss.engage_on} (see WitherTargetGoal). */
    @Override
    public void install(Mob mob) {
        if (!(mob instanceof WitherBoss wither)) {
            return;
        }
        GoalSelector targets = ((MobGoalsAccessor) mob).mobs_ld$targetSelector();
        WrappedGoal vanilla = null;
        for (WrappedGoal goal : targets.getAvailableGoals()) {
            if (goal.getGoal() instanceof WitherTargetGoal) {
                return;
            }
            if (goal.getGoal().getClass() == NearestAttackableTargetGoal.class && goal.getPriority() == TARGET_GOAL_PRIORITY) {
                vanilla = goal;
            }
        }
        if (vanilla == null) {
            // Another mod changed the Wither's goals; engage_on still holds through the target filter.
            LOGGER.warn("Wither {} has no vanilla target goal to replace; leaving its target goals as they are", mob.getId());
            return;
        }
        targets.removeGoal(vanilla.getGoal());
        targets.addGoal(TARGET_GOAL_PRIORITY, new WitherTargetGoal(wither));
    }

    /**
     * Holds the body still through an ability, unless an event (a hover, a dash) moved it this tick: its move control
     * would otherwise let it sink and drift. A stagger is left to gravity, so it drops.
     */
    @Override
    public void tick(Mob mob) {
        MobCombat combat = CombatHost.of(mob);
        if (combat != null && combat.inAbility() && combat.behaviour() != null && !combat.bodyMovedThisTick()) {
            mob.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Vanilla's look, restored wherever the behaviour (or its current phase) sets none. */
    @Override
    public MobBehaviour.BossBar defaultBarStyle() {
        return VANILLA_BAR;
    }

    /** Its move control turns gravity on while nothing steers it, so a dash or chase would sink it. */
    @Override
    public boolean holdsAltitude() {
        return true;
    }

    /** The vanilla bar (purple, darkens the sky, health progress): the engine only recolours it per phase. */
    @Override
    public ServerBossEvent bossBar(Mob mob) {
        return mob instanceof WitherBoss ? ((WitherBossAccessor) mob).mobs_ld$bossEvent() : null;
    }

    @Override
    public ResourceLocation hostType() {
        return WitherHostOptions.TYPE;
    }
}
