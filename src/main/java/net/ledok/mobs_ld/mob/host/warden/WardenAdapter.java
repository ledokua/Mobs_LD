package net.ledok.mobs_ld.mob.host.warden;

import com.mojang.logging.LogUtils;
import net.ledok.mobs_ld.mixin.MobNavigationAccessor;
import net.ledok.mobs_ld.mixin.warden.WardenAccessor;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviours;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.ledok.mobs_ld.mob.host.Head;
import net.ledok.mobs_ld.mob.host.HostAdapter;
import net.ledok.mobs_ld.mob.host.HostAdapters;
import net.ledok.mobs_ld.mob.host.HostSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.AngerManagement;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Runs the ability engine on the vanilla Warden ({@code data/minecraft/mob_behaviour/warden.json}), whose AI is a
 * Brain, not goals. The file names {@code host.only_tagged}, so only the Wardens Arenas_LD spawns for a run are hosted;
 * every other Warden never gets a combat and stays fully vanilla. A hosted Warden keeps its entity, model, sounds,
 * pathing, anger and death; the engine changes (WardenHostMixin applies it):
 * <ul>
 *   <li><b>Muzzles.</b> Its vanilla swing and sonic boom never start (their cooldown memories are kept set), its
 *       vibration listener is deaf while it fights (so tendril twitches are only engine tells), and it never digs
 *       away (DIG_COOLDOWN kept set, plus the hard guard in DiggingMixin).</li>
 *   <li><b>Target.</b> Threat picks it as for every host: {@code Mob.setTarget} is written to the Brain's
 *       ATTACK_TARGET ({@link #onTargetSet}), its anger at that target is kept angry so the Brain never drops it, and
 *       {@code canTargetEntity} follows the engine's target rules ({@code engage_on}, unreachable players, nobody while
 *       walking home), which also gates what it hears and grows angry at.</li>
 *   <li><b>Body.</b> Between abilities its Brain walks it at the target (it needs {@code movement.style: vanilla}).
 *       While the engine owns the body (an ability, a stagger, the walk home) the Brain is held: memories still
 *       expire and sensors still run, but no Brain behaviour starts or ticks ({@link #holdsBrain}).</li>
 *   <li><b>Clips</b>: an ability's clip, a {@code clip} event and the stagger play the vanilla Warden animations
 *       through {@link WardenCues} ({@code attack}, {@code sonic_boom}, {@code tendrils}, {@code roar},
 *       {@code sniff}).</li>
 *   <li><b>Options</b> ({@link WardenHostOptions}): no Darkness pulse, shields never disabled, follow range 48, the
 *       heartbeat per phase.</li>
 * </ul>
 * Not yet (later milestones): the engine's own burrow (the {@code dig} and {@code emerge} cues) and the emerge on
 * spawn.
 */
public final class WardenAdapter implements HostAdapter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WardenAdapter INSTANCE = new WardenAdapter();
    /**
     * Muzzle memories are re-set every tick with this expiry: they never lapse while it is hosted, and lapse on their
     * own within a second once it is not (the file was removed), so a Warden handed back to vanilla swings again.
     */
    private static final long MUZZLE_TICKS = 20L;
    /** Vanilla's dig-away timer (WardenAi.DIGGING_COOLDOWN). */
    private static final long DIG_COOLDOWN_TICKS = 1200L;
    /** Anger kept on its target: ANGRY starts at 80, and anger decays 1 per second. */
    private static final int KEEP_ANGER = 100;
    /** WardenAi's core {@code Swim(0.8F)}. */
    private static final float SWIM_CHANCE = 0.8F;
    /** Width of the rendered body (arm to arm) at scale 1; its hitbox is 0.9. */
    private static final double RENDERED_WIDTH = 2.1;

    private WardenAdapter() {
    }

    /** Call from the mod initializer, before any world (or datapack) loads. */
    public static void register() {
        HostSpec.registerType(WardenHostOptions.TYPE, WardenHostOptions.CODEC);
        HostAdapters.register(EntityType.WARDEN, INSTANCE);
    }

    /**
     * The combat of a hosted Warden (a behaviour applies to it now), else null: always null for world Wardens, which
     * never get a combat. Cheap: one field read for them, called from the Warden's hot paths.
     */
    public static MobCombat hosted(Mob warden) {
        MobCombat combat = CombatHost.of(warden);
        return combat != null && combat.adapter() == INSTANCE && combat.behaviour() != null ? combat : null;
    }

    /** The host options in effect now for a hosted Warden's combat (the defaults without a Warden host block). */
    public static WardenHostOptions options(MobCombat combat) {
        return combat.hostOptions() instanceof WardenHostOptions options ? options : WardenHostOptions.DEFAULTS;
    }

    /**
     * Whether vanilla digging must never take this Warden: it is hosted, or it belongs to an Arenas_LD run (a raid
     * boss that digs away is a lost raid), hosted or not. Reads its tags: for rare paths only.
     */
    public static boolean locksDig(Warden warden) {
        return hosted(warden) != null || MobCombat.inArenasRun(warden);
    }

    /**
     * Vanilla's Digging is about to discard {@code warden} (its dig ran out): true = keep it, because it is hosted or
     * an Arenas_LD run mob; it then stands back up and the log says so (the dig locks failed somewhere).
     */
    public static boolean blockDigDiscard(Warden warden) {
        if (!locksDig(warden)) {
            return false;
        }
        LOGGER.warn("Warden {} at {} finished digging in, but it is {}: not discarded (its dig cooldown was missing)",
                warden.getId(), warden.blockPosition().toShortString(),
                hosted(warden) != null ? "running a Mobs_LD behaviour" : "an Arenas_LD run mob");
        warden.setPose(Pose.STANDING);
        return true;
    }

    /**
     * At the head of its AI step, before its Brain ticks (also before the engine attaches, on its very first tick):
     * a Warden that is hosted or {@code runTagged} gets its dig cooldown, a hosted one its muzzles too. Vanilla only
     * sets DIG_COOLDOWN in finalizeSpawn, which a Warden spawned by Arenas_LD or {@code /summon} with NBT never runs.
     */
    public static void seed(Warden warden, boolean runTagged) {
        MobCombat combat = hosted(warden);
        if (combat != null) {
            muzzle(warden, combat);
        } else if (runTagged) {
            warden.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, DIG_COOLDOWN_TICKS);
        }
    }

    /**
     * The run tag appeared after its first tick (the tag is re-read every second): attaches the engine now if the
     * behaviour applies, instead of at the next reload. Called outside goal iteration (its AI step).
     */
    public static void attachIfHosted(Warden warden) {
        if (CombatHost.of(warden) == null && MobBehaviours.forMob(warden) != null && MobBehaviours.canHost(warden)) {
            ((CombatHost) warden).mobs_ld$combatOrCreate();
        }
    }

    /**
     * Vanilla's Digging would start (the DIG activity is selected, so its dig cooldown is missing) on a Warden that
     * must never dig: refused, and the cooldown set so DIG stops being its activity instead of freezing it there.
     * True = refused.
     */
    public static boolean refuseDig(Warden warden) {
        if (!locksDig(warden)) {
            return false;
        }
        warden.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, DIG_COOLDOWN_TICKS);
        return true;
    }

    /**
     * A held Brain does not run its core Swim behaviour, so it would sink: the same rule, run by the hold (Swim(0.8):
     * jump with that chance per tick while deep enough in water, or in lava).
     */
    public static void swimWhileHeld(Warden warden) {
        if (Swim.shouldSwim(warden) && warden.getRandom().nextFloat() < SWIM_CHANCE) {
            warden.getJumpControl().jump();
        }
    }

    /**
     * Keeps a hosted Warden's own attacks and dig-away from starting: MeleeAttack needs ATTACK_COOLING_DOWN absent,
     * SonicBoom SONIC_BOOM_COOLDOWN, the vibration listener VIBRATION_COOLDOWN (only while it fights: before that it
     * hears players like vanilla) and the DIG activity DIG_COOLDOWN.
     */
    private static void muzzle(Warden warden, MobCombat combat) {
        Brain<Warden> brain = warden.getBrain();
        brain.setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, MUZZLE_TICKS);
        brain.setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_COOLDOWN, Unit.INSTANCE, MUZZLE_TICKS);
        if (combat.engaged()) {
            brain.setMemoryWithExpiry(MemoryModuleType.VIBRATION_COOLDOWN, Unit.INSTANCE, MUZZLE_TICKS);
        }
        brain.setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, DIG_COOLDOWN_TICKS);
    }

    /**
     * Tops its anger at {@code target} up to {@link #KEEP_ANGER}: the Brain's FIGHT drops a target it is not angry at
     * (StopAttackingIfTargetInvalid), and anger decays 1 per second. The public AngerManagement call has no side
     * effects (Warden.increaseAngerAt would refresh the dig timer, swap targets and play a sound).
     */
    private static void keepAngry(Warden warden, LivingEntity target) {
        if (target == null || !target.isAlive() || !warden.canTargetEntity(target)) {
            return;
        }
        AngerManagement anger = warden.getAngerManagement();
        int now = anger.getActiveAnger(target);
        if (now < KEEP_ANGER) {
            anger.increaseAnger(target, KEEP_ANGER - now);
        }
    }

    @Override
    public boolean acceptsBrain() {
        return true;
    }

    /** Not while it digs or emerges (invulnerable, a 1-block hitbox, mid-clip): a Warden that emerges starts after. */
    @Override
    public boolean ready(Mob mob) {
        return !mob.hasPose(Pose.DIGGING) && !mob.hasPose(Pose.EMERGING);
    }

    /**
     * Persistent; muzzled and dig-locked at once; its follow range raised (this mob's base value only, saved with
     * it). Vanilla's FIGHT look range was fixed at 16 when its Brain was built, which only affects where it looks.
     */
    @Override
    public void install(Mob mob) {
        if (!(mob instanceof Warden warden)) {
            return;
        }
        warden.setPersistenceRequired();
        MobCombat combat = CombatHost.of(warden);
        // Always rebuilt here: a Warden loaded from disk has its saved range already, but its navigation was built
        // with the default one before the save was read.
        applyFollowRange(warden, combat == null ? WardenHostOptions.DEFAULTS.followRange() : options(combat).followRange(), true);
        if (combat != null) {
            muzzle(warden, combat);
        }
        // TODO(M4): emerge_on_spawn (EMERGING for 134 ticks when a run spawns it).
    }

    /**
     * Sets its base follow range to {@code range} and rebuilds its navigation, whose path search budget (follow range
     * x 16 visited nodes) is fixed when it is built: when the range changes, or always with {@code rebuild}.
     */
    private static void applyFollowRange(Warden warden, double range, boolean rebuild) {
        AttributeInstance followRange = warden.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange == null || (!rebuild && followRange.getBaseValue() == range)) {
            return;
        }
        followRange.setBaseValue(range);
        warden.getNavigation().stop();
        MobNavigationAccessor navigation = (MobNavigationAccessor) warden;
        navigation.mobs_ld$setNavigation(navigation.mobs_ld$createNavigation(warden.level()));
    }

    /**
     * After the engine's tick (so they hold for the next Brain tick): muzzles; once a second, anger kept and the follow
     * range of the options in effect now (a phase or a reload may change it).
     */
    @Override
    public void tick(Mob mob) {
        if (!(mob instanceof Warden warden)) {
            return;
        }
        MobCombat combat = hosted(warden);
        if (combat == null) {
            return;
        }
        muzzle(warden, combat);
        WardenCues.tick(warden, combat);
        if (warden.tickCount % 20 == 0) {
            keepAngry(warden, warden.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null));
            applyFollowRange(warden, options(combat).followRange(), false);
        }
    }

    /** An ability, a {@code clip} event or the stagger names a clip: its vanilla cue ({@link WardenCues}). */
    @Override
    public void onAction(Mob mob, String clip, double speed) {
        if (mob instanceof Warden warden && hosted(warden) instanceof MobCombat combat) {
            WardenCues.play(warden, combat, clip);
        }
    }

    /**
     * The action ended: a roar or sniff pose goes back to STANDING. Also when the behaviour has just gone away (the
     * combat is inert then), so it never keeps a pose.
     */
    @Override
    public void onStopAction(Mob mob) {
        if (mob instanceof Warden warden && CombatHost.of(warden) instanceof MobCombat combat) {
            WardenCues.stop(warden, combat);
        }
    }

    /** A new ability cuts off whatever pose cue the previous one held (a roar or sniff), before its own clip. */
    @Override
    public void onAbilityStart(Mob mob) {
        onStopAction(mob);
    }

    /**
     * The heart rate its clients show while it fights: the phase's {@code heartbeat} instead of its anger (what the
     * client's heartbeat reads); {@code vanilla} or not fighting = its anger, as vanilla.
     */
    public static int heartbeat(Warden warden, int anger) {
        MobCombat combat = hosted(warden);
        if (combat == null || !combat.engaged()) {
            return anger;
        }
        return options(combat).heartbeat().orElse(anger);
    }

    /**
     * {@code Warden.getTarget()} reads the Brain's ATTACK_TARGET, so the target goes there (not through
     * {@code setAttackTarget}, which would also hold its sonic boom for 10 s), any pending roar target is dropped, and
     * it is made angry enough at the new target for FIGHT to keep it. The anger kept on the previous target is
     * cleared when the engine drops or replaces it, or the Brain would roar at that player and take them back.
     * Anger it gains on its own (hits, noise, touch) is left alone, so it still wakes like vanilla.
     */
    @Override
    public void onTargetSet(Mob mob, LivingEntity target) {
        if (!(mob instanceof Warden warden) || hosted(warden) == null) {
            return;
        }
        Brain<Warden> brain = warden.getBrain();
        LivingEntity previous = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (previous != null && previous != target) {
            warden.getAngerManagement().clearAnger(previous);
        }
        if (target == null) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            return;
        }
        brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
        brain.eraseMemory(MemoryModuleType.ROAR_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        keepAngry(warden, target);
    }

    /** While the engine owns the body: an ability, a stagger or the walk home (later: while burrowed). */
    @Override
    public boolean holdsBrain(Mob mob) {
        MobCombat combat = hosted(mob);
        return combat != null && (combat.busy() || combat.returning());
    }

    /**
     * Ends whatever its Brain was doing (a roar resets its pose, a half-charged boom never fires) and stops its walk,
     * so the next held ticks resume nothing. Never stops the Brain while it digs: vanilla's Digging.stop discards the
     * Warden (DiggingMixin guards that too).
     */
    @Override
    public void onClaimBody(Mob mob) {
        if (!(mob instanceof Warden warden) || !(warden.level() instanceof ServerLevel level)) {
            return;
        }
        Brain<Warden> brain = warden.getBrain();
        if (!warden.hasPose(Pose.DIGGING)) {
            brain.stopAll(level, warden);
        }
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        warden.getNavigation().stop();
    }

    /**
     * Forgets the fight so it does not come straight back: a new, empty anger table (no roar at whoever it was angry
     * at), no target, roar target, walk, disturbance or projectile memory, standing pose. DIG_COOLDOWN stays. Also
     * when its behaviour goes away and it turns back to vanilla.
     */
    @Override
    public void onReset(Mob mob) {
        if (!(mob instanceof Warden warden) || !(warden.level() instanceof ServerLevel)) {
            return;
        }
        ((WardenAccessor) warden).mobs_ld$setAngerManagement(new AngerManagement(warden::canTargetEntity, List.of()));
        Brain<Warden> brain = warden.getBrain();
        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        brain.eraseMemory(MemoryModuleType.ROAR_TARGET);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.DISTURBANCE_LOCATION);
        brain.eraseMemory(MemoryModuleType.RECENT_PROJECTILE);
        if (!warden.hasPose(Pose.STANDING) && !warden.hasPose(Pose.DIGGING)) {
            warden.setPose(Pose.STANDING);
        }
    }

    /** The chest, where vanilla's sonic boom starts. */
    @Override
    public Optional<Vec3> muzzle(Mob mob, Head head) {
        if (head != Head.CENTER) {
            return Optional.empty();
        }
        return Optional.of(mob.position().add(mob.getAttachments().get(EntityAttachment.WARDEN_CHEST, 0, mob.getYRot())));
    }

    /** The model is about 2.1 blocks across the arms (its hitbox is 0.9), times {@code generic.scale}. */
    @Override
    public double bodyWidth(Mob mob) {
        return RENDERED_WIDTH * mob.getScale();
    }

    @Override
    public ResourceLocation hostType() {
        return WardenHostOptions.TYPE;
    }

    /** {@code /mobs_ld brain}: what its Brain holds right now, one line each. */
    public static List<String> describe(Warden warden) {
        Brain<Warden> brain = warden.getBrain();
        MobCombat combat = hosted(warden);
        List<String> lines = new ArrayList<>();
        lines.add(warden.getName().getString() + " " + warden.getId() + ": " + (combat == null ? "not hosted (vanilla)"
                : "hosted") + ", brain " + (INSTANCE.holdsBrain(warden) ? "held by the engine" : "running")
                + ", activity " + brain.getActiveNonCoreActivity().map(Object::toString).orElse("none"));
        LivingEntity target = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        lines.add("ATTACK_TARGET: " + (target == null ? "none"
                : target.getName().getString() + " (anger " + warden.getAngerManagement().getActiveAnger(target) + ")"));
        lines.add("DIG_COOLDOWN: " + (brain.hasMemoryValue(MemoryModuleType.DIG_COOLDOWN)
                ? "set, " + expiry(brain.getTimeUntilExpiry(MemoryModuleType.DIG_COOLDOWN)) : "ABSENT (it may dig away)"));
        lines.add("pose: " + warden.getPose().name().toLowerCase(Locale.ROOT)
                + (combat == null ? "" : " (" + WardenCues.describe(combat) + ")") + ", burrowed: no");
        return lines;
    }

    private static String expiry(long ticks) {
        return ticks == Long.MAX_VALUE ? "no expiry" : String.format("expires in %.1f s", ticks / 20.0);
    }
}
