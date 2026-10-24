package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.threat.ThreatHolder;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviours;
import net.ledok.mobs_ld.mob.host.HostAdapter;
import net.ledok.mobs_ld.mob.host.HostAdapters;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side fight state of one mob that runs a behaviour (a data-driven LdMob, or any vanilla mob a datapack gives
 * one; see {@link CombatHost}): the ability in flight, cooldowns, boss phases, engagement, leash and wipe resets,
 * soft enrage, the boss bar and its summoned adds. {@link CombatGoal} decides <i>when</i> to move or start an
 * ability; everything else ticks here every server AI tick, even while no goal runs.
 */
public final class MobCombat {
    private static final ResourceLocation SOFT_ENRAGE = ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "soft_enrage");
    /** Ticks a telegraph keeps flashing after its impact. */
    private static final int FLASH_TICKS = 4;
    /** A boss walking home gives up and teleports after this long. */
    private static final int RETURN_TIMEOUT = 200;
    /** Ticks without a target after which a mob without a boss block gives up the fight (bosses reset instead). */
    private static final int DISENGAGE_AFTER = 200;
    /** Saved fight state (see {@link #save}). */
    private static final String SAVE_KEY = "mobs_ld_combat";
    /** Home as saved before {@link #SAVE_KEY}; still written so a world can go back to an older build. */
    private static final String LEGACY_HOME_KEY = "mobs_ld_home";

    /** {@code /mobs_ld debug}: narrate ability decisions to operators in chat. */
    private static boolean debug;

    private final Mob mob;
    private final CombatHost host;
    private final HostAdapter adapter;
    private final Map<String, Long> readyAt = new HashMap<>();
    private final List<UUID> summons = new ArrayList<>();
    private final List<Fading> fading = new ArrayList<>();
    /** When each currently-unreachable candidate was first found unreachable (game time). */
    private final Map<UUID, Long> unreachableSince = new HashMap<>();
    /** Targets it has given up on until they are reachable again. */
    private final Set<UUID> unreachable = new HashSet<>();
    private int reachCheckIn;

    private AbilityRun current;
    private AbilityRun deathRun;
    private long globalReadyAt;
    private long staggerUntil = -1;
    private int phase;
    private boolean engaged;
    private long engagedAt;
    private int idleTicks;
    private boolean enraged;
    /** Has ticked at least once (a load before that is the mob coming back from disk, not a live data edit). */
    private boolean started;
    /** Came back from disk in the middle of a fight; settled on the first tick (see {@link #load}). */
    private boolean loadedMidFight;
    /** The behaviour went away; the engine leftovers have been cleared once. */
    private boolean inert;
    private Vec3 home;
    private boolean returning;
    private int returnTicks;
    private ServerBossEvent bar;

    public MobCombat(Mob mob) {
        this.mob = mob;
        this.host = (CombatHost) mob;
        this.adapter = HostAdapters.get(mob.getType());
    }

    /** Leaves without dying: a puff of smoke, no drops, no {@code on_death}. */
    public static void despawnWithPoof(Mob mob) {
        mob.level().broadcastEntityEvent(mob, (byte) 60);
        mob.discard();
    }

    public static boolean isDebug() {
        return debug;
    }

    public static boolean toggleDebug() {
        debug = !debug;
        return debug;
    }

    /** Tells operators near the mob what it just decided (only while {@code /mobs_ld debug} is on). */
    public void trace(String message) {
        if (!debug || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        Component line = Component.literal("[" + mob.getType().getDescription().getString() + " " + mob.getId() + "] "
                + message).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer player : level.players()) {
            if (player.hasPermissions(2) && player.distanceToSqr(mob) < 64.0 * 64.0) {
                player.sendSystemMessage(line);
            }
        }
    }

    /** Current behaviour for this mob type; null until a datapack provides one (the mob then just wanders). */
    public MobBehaviour behaviour() {
        return MobBehaviours.get(mob.getType());
    }

    /** How this mob's entity type hosts the engine (cues, readiness, its own boss bar). */
    public HostAdapter adapter() {
        return adapter;
    }

    // --- Ticking ---

    public void tick() {
        ServerLevel level = (ServerLevel) mob.level();
        long now = level.getGameTime();
        tickFading(now);
        if (home == null) {
            home = mob.position();
        }
        MobBehaviour behaviour = behaviour();
        if (behaviour == null) {
            if (!inert) {
                goInert();
            }
            return;
        }
        inert = false;
        if (!started) {
            started = true;
            if (loadedMidFight) {
                loadedMidFight = false;
                settleLoadedFight(behaviour);
            }
        }
        updateBar(behaviour);
        if (returning) {
            tickReturn();
            return;
        }
        tickReachability(behaviour, now);
        tickEngagement(behaviour, now);
        if (engaged) {
            int desired = behaviour.phaseFor(mob.getHealth() / mob.getMaxHealth());
            if (desired > phase) {
                enterPhase(behaviour, desired);
            }
        }
        // Not tied to engagement: a debug-forced ability on an idle mob still plays out.
        AbilityRun run = current;
        if (run != null && !run.tick() && current == run && !mob.isRemoved()) {
            // An event may already have replaced the run or removed the mob (a despawning bomb): then it is over.
            finishCurrent(behaviour, now);
        }
        if (staggerUntil >= 0 && now >= staggerUntil) {
            staggerUntil = -1;
            host.mobs_ld$stopAction();
        }
    }

    private void tickEngagement(MobBehaviour behaviour, long now) {
        LivingEntity target = mob.getTarget();
        if (!engaged) {
            if (target != null && target.isAlive()) {
                engage(behaviour, target, now);
            }
            return;
        }
        MobBehaviour.Boss boss = behaviour.boss().orElse(null);
        idleTicks = target == null || !target.isAlive() ? idleTicks + 1 : 0;
        if (boss == null) {
            if (idleTicks > DISENGAGE_AFTER) {
                disengage(); // its own AI (wander, sun shelter, vanilla attacks) takes over again
            }
            return;
        }
        if (idleTicks > boss.resetAfter() * 20.0) {
            reset(); // everyone died or left: a wipe
            return;
        }
        if (boss.leash() > 0.0 && mob.position().distanceTo(home) > boss.leash()) {
            reset(); // pulled out of its arena
            return;
        }
        if (!enraged) {
            boss.softEnrage().ifPresent(rule -> {
                if (now - engagedAt >= rule.after() * 20.0) {
                    enrage(rule);
                }
            });
        }
    }

    private void engage(MobBehaviour behaviour, LivingEntity target, long now) {
        engaged = true;
        engagedAt = now;
        idleTicks = 0;
        behaviour.abilities().forEach((name, ability) -> {
            if (ability.initialCooldown() > 0.0) {
                readyAt.put(name, now + Math.round(ability.initialCooldown() * 20.0));
            }
        });
        behaviour.intro().ifPresent(intro -> start("intro", intro, target));
    }

    /** A mob without a boss block lost everyone: it stops fighting as it is (no heal; phase and cooldowns stay). */
    private void disengage() {
        trace("disengage");
        if (current != null) {
            current.cancel();
            current = null;
        }
        staggerUntil = -1;
        host.mobs_ld$stopAction();
        engaged = false;
        idleTicks = 0;
    }

    /**
     * First tick after coming back from disk mid-fight. A chunk only unloads once every player has left it, so a boss
     * that resumed would let a group chip it, leave to recover and come back: it resets instead. Other mobs simply
     * start their next fight fresh (they already load at phase 1, not engaged).
     */
    private void settleLoadedFight(MobBehaviour behaviour) {
        if (behaviour.boss().isPresent()) {
            reset();
        } else {
            clearEnrage();
        }
    }

    /**
     * The behaviour was removed by a reload: the mob goes back to its own AI and keeps no engine leftovers (its
     * combat stays, so the fight can start over if the file comes back).
     */
    private void goInert() {
        inert = true;
        if (current != null) {
            current.cancel();
            current = null;
        }
        host.mobs_ld$stopAction();
        staggerUntil = -1;
        returning = false;
        engaged = false;
        idleTicks = 0;
        clearEnrage();
        removeBar();
        unreachable.clear(); // nothing re-checks them now, and targeting would keep skipping those players
        unreachableSince.clear();
    }

    private void enterPhase(MobBehaviour behaviour, int index) {
        trace("phase " + (index + 1));
        if (current != null) {
            current.cancel();
            current = null;
        }
        staggerUntil = -1;
        phase = index;
        MobBehaviour.Phase entered = behaviour.phases().get(index);
        host.mobs_ld$setVariant(entered.texture().orElse(""));
        long now = mob.level().getGameTime();
        entered.readyIn().forEach((name, seconds) -> readyAt.put(name, now + Math.round(seconds * 20.0)));
        entered.transition().ifPresentOrElse(
                transition -> start("transition", transition, mob.getTarget()),
                host::mobs_ld$stopAction);
    }

    private void enrage(MobBehaviour.SoftEnrage rule) {
        enraged = true;
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && rule.damageMultiplier() != 1.0) {
            damage.addOrReplacePermanentModifier(new AttributeModifier(SOFT_ENRAGE, rule.damageMultiplier() - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        rule.clip().ifPresent(clip -> {
            if (current == null) {
                host.mobs_ld$playAction(clip, 1.0);
            }
        });
    }

    // --- Abilities ---

    /** Whether the mob is committed to something (an ability or a stagger) and must not move on its own. */
    public boolean busy() {
        return current != null || staggerUntil >= 0;
    }

    public boolean engaged() {
        return engaged;
    }

    public boolean returning() {
        return returning;
    }

    public MobBehaviour.Movement movement() {
        MobBehaviour behaviour = behaviour();
        if (behaviour == null) {
            return MobBehaviour.Movement.DEFAULT;
        }
        MobBehaviour.Phase active = activePhase(behaviour);
        return active != null && active.movement().isPresent() ? active.movement().get() : behaviour.movement();
    }

    /** Picks and starts an ability if one is usable right now. */
    public boolean tryStartAbility() {
        MobBehaviour behaviour = behaviour();
        if (behaviour == null || busy() || !engaged) {
            return false;
        }
        long now = mob.level().getGameTime();
        if (now < globalReadyAt) {
            return false;
        }
        MobBehaviour.Phase active = activePhase(behaviour);
        List<Candidate> options = new ArrayList<>();
        int totalWeight = 0;
        for (Map.Entry<String, MobBehaviour.Ability> entry : behaviour.abilities().entrySet()) {
            String name = entry.getKey();
            MobBehaviour.Ability ability = entry.getValue();
            if ((active != null && !active.allows(name)) || now < readyAt.getOrDefault(name, 0L)) {
                continue;
            }
            LivingEntity aim = CombatTargets.pick(mob, ability.target());
            if (aim == null) {
                continue;
            }
            boolean forced = ability.forceAfter().map(after -> now - engagedAt >= after * 20.0).orElse(false);
            if (forced) {
                start(name, ability, aim);
                return true;
            }
            if (ability.range().contains(CombatTargets.distance(mob, aim), aim.getY() - mob.getY(), reachUp(), reachDown())
                    && ability.weight() > 0) {
                options.add(new Candidate(name, ability, aim));
                totalWeight += ability.weight();
            }
        }
        if (options.isEmpty()) {
            return false;
        }
        int roll = mob.getRandom().nextInt(totalWeight);
        for (Candidate option : options) {
            roll -= option.ability.weight();
            if (roll < 0) {
                start(option.name, option.ability, option.aim);
                return true;
            }
        }
        return false;
    }

    /** Starts a named ability right away (also used by the debug command), cancelling whatever was running. */
    public void start(String name, MobBehaviour.Ability ability, LivingEntity aim) {
        if (current != null) {
            current.cancel();
        }
        staggerUntil = -1;
        double speed = attackSpeed();
        current = new AbilityRun(mob, this, name, ability, aim, speed);
        trace("start " + name + (aim != null ? " -> " + aim.getName().getString() + String.format(" (%.1f blocks)",
                CombatTargets.distance(mob, aim)) : "") + (speed != 1.0 ? " x" + speed : ""));
        host.mobs_ld$playAction(ability.clipOr(name), speed);
        long now = mob.level().getGameTime();
        readyAt.put(name, now + Math.round(cooldownOf(name, ability) * 20.0));
    }

    private void finishCurrent(MobBehaviour behaviour, long now) {
        boolean staggered = current.wasStaggered();
        String chained = current.chainTo();
        trace("end " + current.name() + (staggered ? " (staggered)" : chained != null ? " -> chain " + chained : ""));
        LivingEntity aim = current.aim();
        current = null;
        if (chained != null && behaviour.abilities().containsKey(chained)) {
            start(chained, behaviour.abilities().get(chained), aim);
            return;
        }
        globalReadyAt = now + Math.round(behaviour.globalCooldown() * 20.0);
        if (staggered && behaviour.stagger().isPresent()) {
            MobBehaviour.Stagger stagger = behaviour.stagger().get();
            staggerUntil = now + Math.round(stagger.duration() * 20.0);
            host.mobs_ld$playAction(stagger.clip(), 1.0);
        } else {
            host.mobs_ld$stopAction();
        }
    }

    private double cooldownOf(String name, MobBehaviour.Ability ability) {
        MobBehaviour behaviour = behaviour();
        MobBehaviour.Phase active = behaviour == null ? null : activePhase(behaviour);
        if (active != null && active.cooldowns().containsKey(name)) {
            return active.cooldowns().get(name);
        }
        return ability.cooldown();
    }

    private double attackSpeed() {
        MobBehaviour behaviour = behaviour();
        MobBehaviour.Phase active = behaviour == null ? null : activePhase(behaviour);
        return active == null ? 1.0 : Math.max(0.1, active.attackSpeed());
    }

    private MobBehaviour.Phase activePhase(MobBehaviour behaviour) {
        return behaviour.phases().isEmpty() ? null : behaviour.phases().get(Math.min(phase, behaviour.phases().size() - 1));
    }

    // --- Reachability ---

    /** Ticks a target must stay unreachable before the mob gives up on it. */
    private static final int UNREACHABLE_AFTER = 40;
    private static final int REACH_CHECK_INTERVAL = 10;

    /** Whether the mob has given up on {@code entity} because it cannot get to it (high ground, no path). */
    public boolean isUnreachable(LivingEntity entity) {
        return unreachable.contains(entity.getUUID());
    }

    /** Whether target selection must skip {@code entity}: given up on as unreachable, or anyone while walking home. */
    public boolean refusesTarget(LivingEntity entity) {
        return returning || unreachable.contains(entity.getUUID());
    }

    private void tickReachability(MobBehaviour behaviour, long now) {
        if (--reachCheckIn > 0) {
            return;
        }
        reachCheckIn = REACH_CHECK_INTERVAL;
        ServerLevel level = (ServerLevel) mob.level();
        LivingEntity target = mob.getTarget();
        if (target != null) {
            evaluateReach(target, behaviour, now);
        }
        // Players given up on are re-checked, so they get aggro back as soon as they come down.
        for (UUID id : List.copyOf(unreachable)) {
            if (!(level.getEntity(id) instanceof LivingEntity entity) || !entity.isAlive()) {
                unreachable.remove(id);
                unreachableSince.remove(id);
            } else if (entity != target) {
                evaluateReach(entity, behaviour, now);
            }
        }
    }

    private void evaluateReach(LivingEntity entity, MobBehaviour behaviour, long now) {
        UUID id = entity.getUUID();
        if (canReach(entity, movement())) {
            unreachableSince.remove(id);
            if (unreachable.remove(id)) {
                trace(entity.getName().getString() + " is reachable again");
            }
            return;
        }
        long since = unreachableSince.computeIfAbsent(id, key -> now);
        if (now - since >= UNREACHABLE_AFTER) {
            if (unreachable.add(id)) {
                trace(entity.getName().getString() + " is unreachable; switching targets");
            }
            // Every check, not only the first: some vanilla code sets targets directly, past the targeting filter
            // (HurtByTargetGoal alerting others, a target goal restoring its target, Spell Engine aggro).
            if (mob.getTarget() == entity) {
                mob.setTarget(null); // threat targeting picks the next reachable holder, if any
            }
        }
    }

    /** How far above its feet the mob strikes: the behaviour's {@code reach_height}, else 85 % of its height. */
    public double reachUp() {
        return movement().reachHeight().orElse(mob.getBbHeight() * 0.85);
    }

    /** How far below its feet the mob strikes. */
    public double reachDown() {
        return movement().reachDepth().orElse(1.5);
    }

    /** Within the reach band, or the mob has a full path to it. */
    private boolean canReach(LivingEntity entity, MobBehaviour.Movement movement) {
        double dy = entity.getY() - mob.getY();
        if (dy <= reachUp() && dy >= -reachDown()) {
            return true;
        }
        if (flies()) {
            return true; // flyers get up there
        }
        // Judge the path by where it actually ends: vanilla createPath may hand back the mob's current path to some
        // other spot (e.g. its walk home after a reset), whose canReach() says nothing about this target.
        Path path = mob.getNavigation().createPath(entity, 1);
        Node end = path == null ? null : path.getEndNode();
        if (end == null) {
            return false;
        }
        double dx = end.x + 0.5 - entity.getX();
        double dz = end.z + 0.5 - entity.getZ();
        double rise = entity.getY() - end.y;
        return dx * dx + dz * dz <= 4.0 && rise <= reachUp() && rise >= -reachDown();
    }

    /**
     * Flying navigation, a flying body (ghast, phantom), a flying move control, no gravity at all (vex) or a wall
     * climber (spider): such a mob gets up to a target its ground pathfinding would call unreachable. Climbers are
     * also kept away from createPath, which would overwrite the spot their navigation is climbing toward.
     */
    private boolean flies() {
        PathNavigation navigation = mob.getNavigation();
        return navigation instanceof FlyingPathNavigation || navigation instanceof WallClimberNavigation
                || mob instanceof FlyingMob || mob.getMoveControl() instanceof FlyingMoveControl || mob.isNoGravity();
    }

    // --- Damage ---

    public boolean invulnerable() {
        return (returning || (current != null && current.ability().invulnerable())) && behaviour() != null;
    }

    public float adjustDamage(DamageSource source, float amount) {
        MobBehaviour behaviour = behaviour();
        MobBehaviour.Phase active = behaviour == null ? null : activePhase(behaviour);
        return active == null ? amount : (float) (amount * active.damageMultiplier(source));
    }

    // --- Reset / leash ---

    /** Gives up the fight: full heal, phases and cooldowns reset, adds removed, walks home if it was pulled away. */
    public void reset() {
        trace("reset");
        if (current != null) {
            current.cancel();
            current = null;
        }
        staggerUntil = -1;
        host.mobs_ld$stopAction();
        ((ThreatHolder) mob).getThreatTable().clear();
        mob.setTarget(null);
        mob.setHealth(mob.getMaxHealth());
        phase = 0;
        host.mobs_ld$setVariant("");
        readyAt.clear();
        // Unreachable marks survive a reset: otherwise a cheeser on a tower is re-targeted at once and the boss
        // loops intro -> unreachable -> reset. They clear themselves when the player is reachable again.
        clearEnrage();
        discardSummons();
        engaged = false;
        idleTicks = 0;
        if (home != null && mob.position().distanceTo(home) > 2.0) {
            returning = true;
            returnTicks = 0;
        }
    }

    private void clearEnrage() {
        enraged = false;
        stripEnrage(mob);
    }

    /**
     * Removes a saved soft-enrage bonus from {@code mob} (the modifier is permanent, so it is saved with the mob's
     * attributes): for a mob that loads after its type lost the behaviour, or attaches to a fresh combat.
     */
    public static void stripEnrage(Mob mob) {
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.removeModifier(SOFT_ENRAGE);
        }
    }

    /**
     * The mob was moved to another dimension: the copy there was loaded from the original's save, which is not a
     * fight abandoned on disk, so it neither resets nor walks back to coordinates from the old dimension.
     */
    public void onTransferred() {
        loadedMidFight = false;
        home = null; // re-homes where it arrived, on its first tick
    }

    private void tickReturn() {
        returnTicks++;
        mob.setTarget(null);
        if (mob.position().distanceTo(home) < 1.5 || returnTicks > RETURN_TIMEOUT) {
            if (returnTicks > RETURN_TIMEOUT) {
                mob.teleportTo(home.x, home.y, home.z);
            }
            mob.getNavigation().stop();
            returning = false;
            return;
        }
        if (returnTicks % 20 == 1) {
            mob.getNavigation().moveTo(home.x, home.y, home.z, 1.2);
        }
    }

    // --- Death / removal ---

    public void onDeath() {
        if (current != null) {
            current.cancel();
            current = null;
        }
        discardSummons();
        removeBar();
        MobBehaviour behaviour = behaviour();
        if (behaviour != null && behaviour.onDeath().isPresent()) {
            // Its clip is the death animation; only the timeline runs.
            deathRun = new AbilityRun(mob, this, "on_death", behaviour.onDeath().get(), mob.getTarget(), 1.0);
        }
    }

    public void tickDeath() {
        if (mob.level() instanceof ServerLevel level) {
            tickFading(level.getGameTime());
            if (deathRun != null && !deathRun.tick()) {
                deathRun = null;
            }
        }
    }

    /** Entity destroyed (killed, discarded, despawned): nothing it spawned for the fight may linger. */
    public void onRemoved() {
        // Same deferred cleanup as an unload: whoever discarded the mob may still be iterating the level's entities.
        onUnloaded();
    }

    /**
     * Entity leaving the level without being destroyed (chunk unload, dimension change). This runs while the level is
     * still iterating the chunk's entities, so nothing is discarded now: the telegraphs it drew are removed on the next
     * server task pass (they are not saved, but unsaved entities stay behind in a hidden chunk and would return with
     * it). Summoned adds are kept, and stay tracked by UUID through the save.
     */
    public void onUnloaded() {
        List<AttackZoneDisplay> leftovers = new ArrayList<>();
        if (current != null) {
            leftovers.addAll(current.abandon());
            current = null;
        }
        if (deathRun != null) {
            leftovers.addAll(deathRun.abandon());
            deathRun = null;
        }
        for (Fading fade : fading) {
            leftovers.add(fade.display);
        }
        fading.clear();
        removeBar();
        if (!leftovers.isEmpty() && mob.level() instanceof ServerLevel level) {
            MinecraftServer server = level.getServer();
            server.tell(new TickTask(server.getTickCount(), () -> leftovers.forEach(AttackZoneDisplay::remove)));
        }
    }

    // --- Summons ---

    public void trackSummon(Entity summoned) {
        summons.add(summoned.getUUID());
    }

    public int aliveSummons() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return 0;
        }
        summons.removeIf(id -> {
            Entity entity = level.getEntity(id);
            return entity == null || !entity.isAlive();
        });
        return summons.size();
    }

    private void discardSummons() {
        if (mob.level() instanceof ServerLevel level) {
            for (UUID id : summons) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
        summons.clear();
    }

    // --- Telegraph fade-out ---

    void fadeOut(AttackZoneDisplay display) {
        fading.add(new Fading(display, mob.level().getGameTime() + FLASH_TICKS));
    }

    private void tickFading(long now) {
        for (Iterator<Fading> it = fading.iterator(); it.hasNext(); ) {
            Fading fade = it.next();
            if (now >= fade.removeAt) {
                fade.display.remove();
                it.remove();
            }
        }
    }

    // --- Boss bar ---

    private void updateBar(MobBehaviour behaviour) {
        MobBehaviour.BossBar style = behaviour.boss().flatMap(MobBehaviour.Boss::bar).orElse(null);
        ServerBossEvent hostBar = adapter.bossBar(mob);
        if (style == null || hostBar != null) {
            removeBar();
            if (style != null) {
                // The host shows and fills its own bar; the behaviour only picks its look.
                hostBar.setColor(style.color());
                hostBar.setOverlay(style.overlay());
            }
            return;
        }
        if (bar == null) {
            bar = new ServerBossEvent(mob.getDisplayName(), style.color(), style.overlay());
            for (ServerPlayer player : ((ServerLevel) mob.level()).players()) {
                if (player.distanceToSqr(mob) < 64.0 * 64.0) {
                    bar.addPlayer(player);
                }
            }
        }
        bar.setColor(style.color());
        bar.setOverlay(style.overlay());
        bar.setVisible(engaged);
        bar.setProgress(mob.getHealth() / mob.getMaxHealth());
    }

    private void removeBar() {
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
    }

    public void startSeenBy(ServerPlayer player) {
        if (bar != null) {
            bar.addPlayer(player);
        }
    }

    public void stopSeenBy(ServerPlayer player) {
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    // --- Save ---

    /**
     * Writes the fight state that must survive a chunk unload or restart: home, the adds it summoned (so a reset or
     * death still clears them) and whether a fight was on (phase, enrage, how long it had run), which decides what
     * the mob does when it comes back (see {@link #load}). The ability in flight and cooldowns are not kept.
     */
    public void save(CompoundTag tag) {
        CompoundTag state = new CompoundTag();
        if (home != null) {
            ListTag pos = homeTag();
            state.put("home", pos);
            tag.put(LEGACY_HOME_KEY, pos.copy());
        }
        state.putInt("phase", phase);
        state.putBoolean("enraged", enraged);
        state.putLong("fight_ticks", engaged ? Math.max(1L, mob.level().getGameTime() - engagedAt) : 0L);
        ListTag ids = new ListTag();
        for (UUID id : summons) {
            ids.add(NbtUtils.createUUID(id));
        }
        state.put("summons", ids);
        tag.put(SAVE_KEY, state);
    }

    /** Whether {@code tag} holds fight state written by {@link #save} (or the older home-only format). */
    public static boolean hasSavedState(CompoundTag tag) {
        return tag.contains(SAVE_KEY, Tag.TAG_COMPOUND) || tag.contains(LEGACY_HOME_KEY, Tag.TAG_LIST);
    }

    /**
     * Restores {@link #save}. The mob comes back at phase 1 and not engaged; if it was saved mid-fight (a phase past
     * the first, a running fight or live adds), its first tick resets a boss or starts others fresh.
     */
    public void load(CompoundTag tag) {
        CompoundTag state = tag.getCompound(SAVE_KEY);
        Vec3 savedHome = readHome(state.getList("home", Tag.TAG_DOUBLE));
        if (savedHome == null) {
            savedHome = readHome(tag.getList(LEGACY_HOME_KEY, Tag.TAG_DOUBLE));
        }
        if (savedHome != null) {
            home = savedHome;
        }
        if (!state.isEmpty()) {
            summons.clear();
            for (Tag id : state.getList("summons", Tag.TAG_INT_ARRAY)) {
                summons.add(NbtUtils.loadUUID(id));
            }
            if (!started) {
                // Not for a live mob whose data is merged by a command: that must not end its fight.
                loadedMidFight = state.getInt("phase") > 0 || state.getLong("fight_ticks") > 0
                        || state.getBoolean("enraged") || !summons.isEmpty();
            }
        }
    }

    private ListTag homeTag() {
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(home.x));
        pos.add(DoubleTag.valueOf(home.y));
        pos.add(DoubleTag.valueOf(home.z));
        return pos;
    }

    private static Vec3 readHome(ListTag pos) {
        return pos.size() == 3 ? new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2)) : null;
    }

    private record Candidate(String name, MobBehaviour.Ability ability, LivingEntity aim) {
    }

    private record Fading(AttackZoneDisplay display, long removeAt) {
    }
}
