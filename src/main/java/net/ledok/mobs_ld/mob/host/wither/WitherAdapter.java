package net.ledok.mobs_ld.mob.host.wither;

import com.mojang.logging.LogUtils;
import net.ledok.mobs_ld.mixin.MobGoalsAccessor;
import net.ledok.mobs_ld.mixin.wither.WitherBossAccessor;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.ledok.mobs_ld.mob.host.Head;
import net.ledok.mobs_ld.mob.host.HostAdapter;
import net.ledok.mobs_ld.mob.host.HostAdapters;
import net.ledok.mobs_ld.mob.host.HostSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Runs the ability engine on the vanilla Wither ({@code data/minecraft/mob_behaviour/wither.json}). The Wither keeps
 * its entity, model, build ritual and 11 s charge, death and loot, and its own boss bar; the engine starts once the
 * charge is over (a Wither made by {@code /summon} or a spawner has none and starts at once).
 *
 * <p>What changes for a hosted Wither (WitherBossHostMixin applies the {@link WitherHostOptions}): its target goal
 * follows {@code boss.engage_on}; while an ability, a stagger or the walk home runs, its own flight stops (and during
 * an ability it holds still unless an event moves it); its hover height, regeneration, arrow immunity and block
 * breaking follow the host options of the current phase; each phase may recolour its bar. With {@code host.skulls}
 * Mobs_LD fires its skulls (see {@link WitherSkulls}: vanilla's shots and idle blue skulls stop), and with target
 * slots named {@code right_head} / {@code left_head} those slots aim the side heads (vanilla's random side-head
 * targets stop). A Wither whose behaviour is absent or switched off is fully vanilla.
 *
 * <p>Heads: the model's right head (on the Wither's own right) is driven by synced target 1 and the left head by
 * target 2 (WitherBossModel.prepareMobModel); their mouths are getHeadX/Y/Z(2) and (1) respectively (head index 2
 * is offset by {@code yBodyRot + 180}, i.e. to the body's right), the middle head's is index 0.
 */
public final class WitherAdapter implements HostAdapter {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The Wither's own nearest-target goal sits here (WitherBoss.registerGoals). */
    private static final int TARGET_GOAL_PRIORITY = 2;
    private static final WitherAdapter INSTANCE = new WitherAdapter();
    private static final MobBehaviour.BossBar VANILLA_BAR = new MobBehaviour.BossBar(BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);
    /** Target slots shown on the side heads. */
    public static final String RIGHT_HEAD_SLOT = "right_head";
    public static final String LEFT_HEAD_SLOT = "left_head";

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

    /**
     * Whether Mobs_LD fires this Wither's skulls now (its current host options name some): vanilla's shots, side-head
     * shots and idle blue skulls are then stopped. Cheap: checked on every vanilla shot.
     */
    public static boolean ownsSkulls(Mob wither) {
        WitherHostOptions options = options(wither);
        return options != null && !options.skulls().isEmpty();
    }

    /**
     * Whether target slots aim this Wither's side heads (its behaviour has a {@code right_head} or {@code left_head}
     * slot): vanilla's own side-head targeting is then off, for both heads.
     */
    public static boolean ownsSideHeads(Mob wither) {
        MobCombat combat = CombatHost.of(wither);
        MobBehaviour behaviour = combat == null ? null : combat.behaviour();
        return behaviour != null && (behaviour.targetSlots().containsKey(RIGHT_HEAD_SLOT)
                || behaviour.targetSlots().containsKey(LEFT_HEAD_SLOT));
    }

    /** The synced alternative-target index (1, 2) that drives a side head on the client. */
    static int targetIndex(Head head) {
        return head == Head.RIGHT ? 1 : 2;
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
        if (combat == null || combat.behaviour() == null) {
            return;
        }
        if (combat.inAbility() && !combat.bodyMovedThisTick()) {
            mob.setDeltaMovement(Vec3.ZERO);
        }
        if (mob instanceof WitherBoss wither && combat.hostOptions() instanceof WitherHostOptions options
                && !options.skulls().isEmpty()) {
            combat.hostState(WitherSkulls.class, WitherSkulls::new).tick(wither, combat, options);
        }
    }

    /** The three mouths: middle (head index 0), the body's right (index 2) and left (index 1); see the class notes. */
    @Override
    public Optional<Vec3> muzzle(Mob mob, Head head) {
        if (!(mob instanceof WitherBoss)) {
            return HostAdapter.super.muzzle(mob, head);
        }
        WitherBossAccessor heads = (WitherBossAccessor) mob;
        int index = switch (head) {
            case CENTER -> 0;
            case RIGHT -> 2;
            case LEFT -> 1;
        };
        return Optional.of(new Vec3(heads.mobs_ld$headX(index), heads.mobs_ld$headY(index), heads.mobs_ld$headZ(index)));
    }

    /**
     * Side heads turn by their synced target id (only ever a living entity: the server tick casts it, and any other
     * entity would crash it); the middle head by the look control, as vanilla's own goal turns it.
     */
    @Override
    public void aimHead(Mob mob, Head head, LivingEntity target) {
        if (!(mob instanceof WitherBoss wither)) {
            HostAdapter.super.aimHead(mob, head, target);
            return;
        }
        if (head == Head.CENTER) {
            if (target != null) {
                wither.getLookControl().setLookAt(target, 60.0F, 60.0F);
            }
            return;
        }
        wither.setAlternativeTarget(targetIndex(head), target != null && target.isAlive() ? target.getId() : 0);
    }

    @Override
    public Optional<Head> slotHead(String slot) {
        return switch (slot) {
            case RIGHT_HEAD_SLOT -> Optional.of(Head.RIGHT);
            case LEFT_HEAD_SLOT -> Optional.of(Head.LEFT);
            default -> Optional.empty();
        };
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
