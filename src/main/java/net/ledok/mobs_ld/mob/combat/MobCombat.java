package net.ledok.mobs_ld.mob.combat;

import com.mojang.logging.LogUtils;
import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.threat.ThreatEffects;
import net.ledok.mobs_ld.entity.threat.ThreatHolder;
import net.ledok.mobs_ld.mob.LdMob;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviours;
import net.ledok.mobs_ld.mob.behaviour.Seconds;
import net.ledok.mobs_ld.mob.behaviour.SkullSpec;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitSpec;
import net.ledok.mobs_ld.mob.behaviour.event.SummonEvent;
import net.ledok.mobs_ld.mob.behaviour.event.SummonerWardEvent;
import net.ledok.mobs_ld.mob.host.HostAdapter;
import net.ledok.mobs_ld.mob.host.HostAdapters;
import net.ledok.mobs_ld.mob.host.HostOptions;
import net.ledok.mobs_ld.registry.ModDamageTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Server-side fight state of one mob that runs a behaviour (a data-driven LdMob, or any vanilla mob a datapack gives
 * one; see {@link CombatHost}): the ability in flight, cooldowns, boss phases, engagement, leash and wipe resets,
 * soft enrage, the boss bar and its summoned adds. {@link CombatGoal} decides <i>when</i> to move or start an
 * ability; everything else ticks here every server AI tick, even while no goal runs.
 */
public final class MobCombat {
    private static final Logger LOGGER = LogUtils.getLogger();
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
    /** A non-player that hurt a players-only boss may be its target for this long (ticks). */
    private static final int HURT_BY_WINDOW = 200;
    /** Arenas_LD marks the mobs of a running dungeon or raid with this tag (read here, never written). */
    public static final String ARENAS_RUN_TAG = "arenas_ld_run_mob";

    /** {@code /mobs_ld debug}: narrate ability decisions to operators in chat. */
    private static boolean debug;

    private final Mob mob;
    private final CombatHost host;
    private final HostAdapter adapter;
    private final TargetSlots slots;
    private final Map<String, Long> readyAt = new HashMap<>();
    /** When each {@code cooldown_group} is ready again (game time). */
    private final Map<String, Long> groupReadyAt = new HashMap<>();
    /** Abilities a phase's {@code ready_in} made ready regardless of their group's cooldown, until next used. */
    private final Set<String> readyInWins = new HashSet<>();
    private final List<UUID> summons = new ArrayList<>();
    /** Its adds from summons with a {@code minion_link}, and the link each was raised under ({@link MinionLinks}). */
    private final Map<UUID, SummonEvent.MinionLink> links = new HashMap<>();
    /** All its linked adds are dead: it takes {@link #exposedMultiplier} damage until it summons linked adds again. */
    private boolean exposed;
    private double exposedMultiplier = 1.0;
    /** Game time a shield-blocked hit hint was last sent (rate-limits sound and action-bar spam). */
    private long lastShieldHintAt = Long.MIN_VALUE;
    private final List<Fading> fading = new ArrayList<>();
    /** When each currently-unreachable candidate was first found unreachable (game time). */
    private final Map<UUID, Long> unreachableSince = new HashMap<>();
    /** Targets it has given up on until they are reachable again. */
    private final Set<UUID> unreachable = new HashSet<>();
    /** Non-players that hurt it recently (game time of the last hit), for {@code engage_on: players}. */
    private final Map<UUID, Long> hurtBy = new HashMap<>();
    private int reachCheckIn;
    /** Game time an ability event last set the body's velocity (see {@link #markBodyMoved}). */
    private long bodyMovedAt = Long.MIN_VALUE;
    /** Game time a dash last moved the body (see {@link #markDashing}). */
    private long dashedAt = Long.MIN_VALUE;
    /** Damage dealt to it over the last 10 s, per attacker ({@code top_damage}). */
    private final DamageLedger damageLedger = new DamageLedger();
    /** Stacks its hits put on players ({@code hit.stacks}). */
    private final HitStacks stacks = new HitStacks();
    /** Fighters' recent positions, sampled only while the phase has a hit with a {@code lead}. */
    private final MoveLead moveLead = new MoveLead();
    /** Who stands behind it, per ability with {@code when.behind} that is allowed and off cooldown now. */
    private final Map<String, BehindWatch> behind = new HashMap<>();
    /** The behaviour and phase {@link #phaseLead} was worked out for (see {@link #scanPhase}). */
    private MobBehaviour scannedBehaviour;
    private int scannedPhase = -1;
    /** Abilities of the scanned phase with a {@code lead} hit. */
    private List<String> phaseLead = List.of();
    /** Abilities of the scanned phase with a {@code when.behind} rule. */
    private List<String> phaseBehind = List.of();

    private AbilityRun current;
    /** The ability started last (for {@code no_repeat}); null after a reset. */
    private String lastStarted;
    /** Named abilities that have finished in this engagement (for {@code after_abilities}). */
    private final Set<String> completedAbilities = new HashSet<>();
    private AbilityRun deathRun;
    private long globalReadyAt;
    private long recoveryUntil;
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
    /** Per-mob state the host adapter keeps (e.g. the Wither's skull timers); see {@link #hostState}. */
    private Object hostState;
    /** {@link MobBehaviours#generation()} the {@code host.only_tagged} filter was last decided at; see {@link #behaviour}. */
    private int gateGeneration = -1;
    private boolean gateOpen;
    /** The engine holds the body (see {@link #syncBody}): the host adapter has been told so. */
    private boolean bodyClaimed;

    public MobCombat(Mob mob) {
        this.mob = mob;
        this.host = (CombatHost) mob;
        this.adapter = HostAdapters.get(mob.getType());
        this.slots = new TargetSlots(mob, this);
    }

    /**
     * Whether {@code entity} belongs to a running Arenas_LD dungeon or raid. Arenas owns the wipe there, so Mobs_LD's
     * leash and wipe reset stay off.
     */
    public static boolean inArenasRun(Entity entity) {
        return entity.getTags().contains(ARENAS_RUN_TAG);
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

    /**
     * Tells operators near the mob what it just decided, and the server log (for a console without players nearby);
     * only while {@code /mobs_ld debug} is on.
     */
    public void trace(String message) {
        if (!debug || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        String text = "[" + mob.getType().getDescription().getString() + " " + mob.getId() + "] " + message;
        LOGGER.info("Mobs_LD trace {}", text);
        Component line = Component.literal(text).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer player : level.players()) {
            if (player.hasPermissions(2) && player.distanceToSqr(mob) < 64.0 * 64.0) {
                player.sendSystemMessage(line);
            }
        }
    }

    /**
     * Current behaviour for this mob; null until a datapack provides one for its type (the mob then just wanders), and
     * null while the file's {@code host.only_tagged} filter keeps this mob out. The filter is decided on the first call
     * (the attach) and again after each datapack reload, not per tick, so a mob tagged later waits for a reload.
     */
    public MobBehaviour behaviour() {
        MobBehaviour behaviour = MobBehaviours.get(mob.getType());
        if (behaviour == null || behaviour.host().onlyTagged().isEmpty()) {
            return behaviour;
        }
        int generation = MobBehaviours.generation();
        if (generation != gateGeneration) {
            gateGeneration = generation;
            gateOpen = behaviour.host().appliesTo(mob.getTags());
        }
        return gateOpen ? behaviour : null;
    }

    /** How this mob's entity type hosts the engine (cues, readiness, its own boss bar). */
    public HostAdapter adapter() {
        return adapter;
    }

    /** Its target slots ({@code target_slots}) and the heads {@code look} events hold. */
    public TargetSlots slots() {
        return slots;
    }

    /**
     * State the host adapter keeps for this mob (adapters are shared by every mob of a type): the current one if it
     * is a {@code type}, else a fresh one from {@code create}.
     */
    @SuppressWarnings("unchecked")
    public <T> T hostState(Class<T> type, Supplier<T> create) {
        if (!type.isInstance(hostState)) {
            hostState = create.get();
        }
        return (T) hostState;
    }

    /** The skull spec {@code name} in the host options in effect now (see {@link #hostOptions}), or null. */
    public SkullSpec skullSpec(String name) {
        HostOptions options = hostOptions();
        return options == null ? null : options.skulls().get(name);
    }

    // --- Ticking ---

    public void tick() {
        tickFight();
        syncBody();
        if (mob instanceof LdMob add) {
            LivingEntity aim = current == null ? null : current.aim();
            add.setWardTargetId(aim != null && current.wardMultiplier(aim) < 1.0 ? aim.getId() : -1);
        }
    }

    private void tickFight() {
        ServerLevel level = (ServerLevel) mob.level();
        long now = level.getGameTime();
        tickFading(now);
        if (home == null) {
            home = mob.position();
        }
        if (!stacks.isEmpty()) {
            // A victim that died, logged out or left this level loses its stacks.
            stacks.removeVictims(id -> !(level.getEntity(id) instanceof LivingEntity victim) || !victim.isAlive());
        }
        MobBehaviour behaviour = behaviour();
        if (behaviour == null) {
            if (!inert) {
                goInert();
            }
            return;
        }
        inert = false;
        if (exposed && now % MinionLinks.FX_INTERVAL == 0) {
            MinionLinks.tickFx(mob, level);
        }
        if (engaged && mob.isAlive()) {
            tickTethersAndSiphon(behaviour, level, now);
        }
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
        LivingEntity target = mob.getTarget();
        if (target != null && !mayTarget(target)) {
            // Set before the engine was attached (or past the hook by other code), or a non-player whose hit is old.
            mob.setTarget(null);
        }
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
            double recover = behaviour.stagger().map(MobBehaviour.Stagger::recover).orElse(0.0);
            if (recover > 0.0) {
                // Back on its feet, but nothing ready (a forced summon) fires the same tick.
                globalReadyAt = Math.max(globalReadyAt, now + Math.round(recover * 20.0));
                trace(String.format("stagger over, no ability for %.1f s", recover));
            }
        }
        if (!mob.isRemoved()) {
            slots.tick(behaviour, engaged);
        }
        if (engaged && !mob.isRemoved()) {
            tickWatches(behaviour, now);
        }
        if (engaged && !busy() && !mob.isRemoved() && movement().style() == MobBehaviour.Movement.Style.VANILLA) {
            // The combat goal only runs during abilities for these hosts, so abilities are picked from here.
            tryStartAbility();
        }
    }

    private void tickEngagement(MobBehaviour behaviour, long now) {
        LivingEntity target = mob.getTarget();
        if (movement().follow().kind() == MobBehaviour.TargetRule.Kind.SUMMONER) {
            Mob summoner = CombatTargets.summoner(mob);
            if (summoner != null) {
                target = summoner.getTarget();
            }
        }
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
        if (!inArenasRun(mob)) { // inside a run Arenas_LD decides what a wipe is
            if (idleTicks > boss.resetAfter() * 20.0) {
                reset(); // everyone died or left: a wipe
                return;
            }
            if (boss.leash() > 0.0 && mob.position().distanceTo(home) > boss.leash()) {
                reset(); // pulled out of its arena
                return;
            }
        }
        if (!enraged) {
            boss.softEnrage().ifPresent(rule -> {
                if (now - engagedAt >= rule.after() * 20.0) {
                    enrage(rule);
                }
            });
        }
    }

    /**
     * The per-player bookkeeping some abilities need, and only while one may use it: fighters' positions while an
     * ability with a {@code lead} hit is allowed by the phase and off cooldown, or running (also when chained or
     * forced: it then starts sampling and predicts once it has a trail), and who stands behind it for each
     * {@code when.behind} ability that is allowed and off cooldown (a watch that is not needed is dropped, so its
     * timers start over).
     */
    private void tickWatches(MobBehaviour behaviour, long now) {
        scanPhase(behaviour);
        boolean runLead = current != null && hasLeadHit(current.ability());
        if (phaseBehind.isEmpty() && phaseLead.isEmpty() && !runLead) {
            clearWatches(); // the usual case (every phase without such abilities): nothing to poll
            return;
        }
        boolean lead = runLead || anyReady(behaviour, phaseLead, now);
        List<String> watching = new ArrayList<>();
        for (String name : phaseBehind) {
            if (anyReady(behaviour, List.of(name), now)) {
                watching.add(name);
            }
        }
        behind.keySet().retainAll(watching);
        if (!lead) {
            moveLead.clear();
        }
        if (!lead && watching.isEmpty()) {
            return;
        }
        List<Player> fighters = Fighters.of(mob);
        if (lead) {
            moveLead.sample(fighters, now);
        }
        for (String name : watching) {
            MobBehaviour.Behind rule = behaviour.abilities().get(name).behind().orElseThrow();
            Set<UUID> back = new HashSet<>();
            for (Player fighter : fighters) {
                if (BehindWatch.isBehind(fighter.getX() - mob.getX(), fighter.getY() - mob.getY(),
                        fighter.getZ() - mob.getZ(), mob.yBodyRot, rule.range(), rule.angle(), rule.height())) {
                    back.add(fighter.getUUID());
                }
            }
            behind.computeIfAbsent(name, key -> new BehindWatch()).update(back, now);
        }
    }

    /** Whether any of the abilities {@code names} is off cooldown now. */
    private boolean anyReady(MobBehaviour behaviour, List<String> names, long now) {
        for (String name : names) {
            MobBehaviour.Ability ability = behaviour.abilities().get(name);
            if (ability != null && AbilityChoice.ready(name, ability, now, readyAt, groupReadyAt, readyInWins)) {
                return true;
            }
        }
        return false;
    }

    /** Whether one of the ability's {@code hit} events places its zones ahead of moving players ({@code lead}). */
    private static boolean hasLeadHit(MobBehaviour.Ability ability) {
        for (AbilityEvent event : ability.events()) {
            if (event instanceof HitEvent hit && hit.lead().isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** Works out, once per behaviour and phase, which of the phase's abilities have {@code lead} hits or {@code when.behind}. */
    private void scanPhase(MobBehaviour behaviour) {
        if (behaviour == scannedBehaviour && phase == scannedPhase) {
            return;
        }
        scannedBehaviour = behaviour;
        scannedPhase = phase;
        MobBehaviour.Phase active = activePhase(behaviour);
        List<String> withLead = new ArrayList<>();
        List<String> withBehind = new ArrayList<>();
        for (Map.Entry<String, MobBehaviour.Ability> entry : behaviour.abilities().entrySet()) {
            if (active != null && !active.allows(entry.getKey())) {
                continue;
            }
            if (entry.getValue().behind().isPresent()) {
                withBehind.add(entry.getKey());
            }
            if (hasLeadHit(entry.getValue())) {
                withLead.add(entry.getKey());
            }
        }
        phaseLead = List.copyOf(withLead);
        phaseBehind = List.copyOf(withBehind);
    }

    /** Whether ability {@code name}'s {@code when.behind} rule holds now (true without one). */
    private boolean behindHolds(String name, MobBehaviour.Ability ability, long now) {
        MobBehaviour.Behind rule = ability.behind().orElse(null);
        if (rule == null) {
            return true;
        }
        BehindWatch watch = behind.get(name);
        return watch != null && watch.count(now, Math.round(rule.seconds() * 20.0)) >= rule.players();
    }

    private void engage(MobBehaviour behaviour, LivingEntity target, long now) {
        engaged = true;
        engagedAt = now;
        idleTicks = 0;
        completedAbilities.clear();
        behaviour.abilities().forEach((name, ability) -> {
            if (ability.initialCooldown().max() > 0.0) {
                readyAt.put(name, now + ticks(ability.initialCooldown()));
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
        recoveryUntil = 0;
        host.mobs_ld$stopAction();
        slots.clear();
        clearWatches();
        ListenWindows.closeFor(mob);
        engaged = false;
        idleTicks = 0;
    }

    /** Drops the per-player bookkeeping of the fight (positions, who stands behind it). */
    private void clearWatches() {
        moveLead.clear();
        behind.clear();
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
        slots.clear();
        staggerUntil = -1;
        recoveryUntil = 0;
        returning = false;
        engaged = false;
        idleTicks = 0;
        clearEnrage();
        removeBar();
        unreachable.clear(); // nothing re-checks them now, and targeting would keep skipping those players
        unreachableSince.clear();
        hurtBy.clear();
        clearWatches();
        ListenWindows.closeFor(mob);
        stacks.clear();
        damageLedger.clear();
        ServerBossEvent hostBar = adapter.bossBar(mob);
        MobBehaviour.BossBar vanilla = adapter.defaultBarStyle();
        if (hostBar != null && vanilla != null) {
            hostBar.setColor(vanilla.color());
            hostBar.setOverlay(vanilla.overlay());
        }
        adapter.onReset(mob);
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
        entered.readyIn().forEach((name, seconds) -> AbilityChoice.readyIn(name, now + Math.round(seconds * 20.0), readyAt,
                readyInWins));
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

    /** The ability running now, or null. */
    public String currentAbility() {
        return current == null ? null : current.name();
    }

    /** The ability {@code entity} runs now if the engine hosts it, else null. */
    public static String abilityOf(Entity entity) {
        MobCombat combat = CombatHost.of(entity);
        return combat == null ? null : combat.currentAbility();
    }

    /** Whether the mob is committed to an ability, stagger or recovery and must not move on its own. */
    public boolean busy() {
        return current != null || staggerUntil >= 0 || recovering();
    }

    public boolean recovering() {
        return recoveryUntil > mob.level().getGameTime();
    }

    /**
     * Tells the host adapter when the engine takes the body (ability, stagger, recovery or walk home) and when
     * it gives it back: {@link HostAdapter#onClaimBody} / {@link HostAdapter#onReleaseBody}, once per edge.
     */
    private void syncBody() {
        boolean claim = busy() || returning;
        if (claim != bodyClaimed) {
            bodyClaimed = claim;
            if (claim) {
                adapter.onClaimBody(mob);
            } else {
                adapter.onReleaseBody(mob);
            }
        }
    }

    /** Whether an ability (not a stagger) is running. */
    public boolean inAbility() {
        return current != null;
    }

    /** Index of the current boss phase (0 = the first; latched, it only goes down on a reset). */
    public int phase() {
        return phase;
    }

    /**
     * The host adapter's options in effect now (the current phase's, see {@link MobBehaviour#hostOptions}); null when
     * there are none: no behaviour (the mob is then fully vanilla), or no typed host block (the adapter's defaults).
     */
    public HostOptions hostOptions() {
        MobBehaviour behaviour = behaviour();
        return behaviour == null ? null : behaviour.hostOptions(phase).orElse(null);
    }

    /** An ability event drove the body this tick (a hover, dash or chase), so the host must not hold it still. */
    public void markBodyMoved() {
        bodyMovedAt = mob.level().getGameTime();
    }

    public boolean bodyMovedThisTick() {
        return bodyMovedAt == mob.level().getGameTime();
    }

    /**
     * A dash moves the body this tick: its move passes through entities with collision boxes (boats, shulkers,
     * minecarts), which would otherwise stop it like a wall, and climbables (vines, ladders, scaffolding) do not slow
     * it to a climb. Only the move of the tick it is marked in; nothing to undo.
     */
    public void markDashing() {
        dashedAt = mob.level().getGameTime();
    }

    /** Whether a dash moves the body this tick (see {@link #markDashing}); cheap, asked on every move. */
    public boolean dashingThisTick() {
        return dashedAt != Long.MIN_VALUE && dashedAt == mob.level().getGameTime();
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
        int fighters = -1; // counted once, if an ability asks
        for (Map.Entry<String, MobBehaviour.Ability> entry : behaviour.abilities().entrySet()) {
            String name = entry.getKey();
            MobBehaviour.Ability ability = entry.getValue();
            if ((active != null && !active.allows(name)) || !ability.allowsTags(mob.getTags())
                    || !prerequisiteMet(ability)
                    || !AbilityChoice.ready(name, ability, now, readyAt, groupReadyAt, readyInWins)
                    || (ability.skipWhenFull() && summonsFull(ability)) || !behindHolds(name, ability, now)) {
                continue;
            }
            if (ability.countsPlayers()) {
                if (fighters < 0) {
                    fighters = Fighters.of(mob).size();
                }
                if (!ability.allowsPlayers(fighters)) {
                    continue;
                }
            }
            LivingEntity aim = CombatTargets.pick(mob, ability.target(), ability.includeUnreachable());
            if (aim == null) {
                continue;
            }
            if (ability.target().kind() == MobBehaviour.TargetRule.Kind.SUMMONER
                    && (!mob.getSensing().hasLineOfSight(aim) || ability.events().stream().anyMatch(event ->
                    event instanceof SummonerWardEvent ward && !ward.inRange(mob, aim)))) {
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
            }
        }
        options = AbilityChoice.withoutRepeat(options, Candidate::name, Candidate::ability, lastStarted);
        if (options.isEmpty()) {
            return false;
        }
        for (Candidate option : options) {
            totalWeight += option.ability.weight();
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
        recoveryUntil = 0;
        double speed = AbilityChoice.speed(ability, attackSpeed());
        // Whatever ran before may have been cut off here (a chain, a phase transition, a command) without its action
        // being stopped: the host clears what it left on the body (a held pose) before the new clip.
        adapter.onAbilityStart(mob);
        current = new AbilityRun(mob, this, name, ability, aim, speed);
        lastStarted = name;
        trace("start " + name + (aim != null ? " -> " + aim.getName().getString() + String.format(" (%.1f blocks)",
                CombatTargets.distance(mob, aim)) : "") + (speed != 1.0 ? " x" + speed : ""));
        current.openWith(ability.clipOr(name));
        long now = mob.level().getGameTime();
        AbilityChoice.used(name, ability, now + ticks(cooldownOf(name, ability)), readyAt, groupReadyAt, readyInWins);
        syncBody(); // also when forced by a command, outside the tick
    }

    /** A time rolled now (a range gives a fresh value each use), in whole ticks. */
    private long ticks(Seconds seconds) {
        return Math.round(seconds.roll(mob.getRandom()::nextDouble) * 20.0);
    }

    /**
     * Whether {@code name} could start on its own now, range and weight aside: its phase allows it, it is off cooldown,
     * its opening prerequisite is met and, with {@code skip_when_full}, one of its summons has room ({@code chain.if_ready}).
     */
    public boolean readyToChain(String name) {
        MobBehaviour behaviour = behaviour();
        MobBehaviour.Ability ability = behaviour == null ? null : behaviour.abilities().get(name);
        if (ability == null) {
            return false;
        }
        MobBehaviour.Phase active = activePhase(behaviour);
        long now = mob.level().getGameTime();
        return (active == null || active.allows(name)) && ability.allowsTags(mob.getTags())
                && prerequisiteMet(ability)
                && AbilityChoice.ready(name, ability, now, readyAt, groupReadyAt, readyInWins)
                && !(ability.skipWhenFull() && summonsFull(ability));
    }

    private boolean prerequisiteMet(MobBehaviour.Ability ability) {
        return ability.afterAbilities().isEmpty() || ability.afterAbilities().stream().anyMatch(completedAbilities::contains);
    }

    /**
     * Whether none of the ability's {@code summon} events has room for another add now ({@code skip_when_full});
     * false for an ability that summons nothing.
     */
    private boolean summonsFull(MobBehaviour.Ability ability) {
        boolean summons = false;
        for (AbilityEvent event : ability.events()) {
            if (event instanceof SummonEvent summon) {
                summons = true;
                if (summon.room(mob, this) > 0) {
                    return false;
                }
            }
        }
        return summons;
    }

    private void finishCurrent(MobBehaviour behaviour, long now) {
        boolean staggered = current.wasStaggered();
        String chained = current.chainTo();
        MobBehaviour.Ability named = behaviour.abilities().get(current.name());
        if (!staggered && chained == null && named == current.ability()) {
            completedAbilities.add(current.name());
        }
        trace("end " + current.name() + (staggered ? " (staggered)" : chained != null ? " -> chain " + chained : ""));
        LivingEntity aim = current.aim();
        LivingEntity asked = current.chainAim();
        boolean tauntWins = current.chainTauntWins();
        Float facing = current.chainFacing();
        current = null;
        if (chained != null && behaviour.abilities().containsKey(chained)) {
            MobBehaviour.Ability next = behaviour.abilities().get(chained);
            LivingEntity nextAim = asked != null && asked.isAlive() ? asked : chainedAim(next, aim, tauntWins);
            start(chained, next, nextAim);
            if (facing != null) {
                current.lockFacing(facing);
            }
            return;
        }
        globalReadyAt = now + Math.round(behaviour.globalCooldown() * 20.0);
        if (staggered && behaviour.stagger().isPresent()) {
            MobBehaviour.Stagger stagger = behaviour.stagger().get();
            staggerUntil = now + Math.round(stagger.duration() * 20.0);
            host.mobs_ld$playAction(stagger.clip(), 1.0);
        } else {
            host.mobs_ld$stopAction();
            long recoveryTicks = named == null ? 0L
                    : Math.round(behaviour.boss().map(MobBehaviour.Boss::recovery).orElse(0.0) * 20.0);
            if (recoveryTicks > 0) {
                recoveryUntil = now + recoveryTicks;
                globalReadyAt = Math.max(globalReadyAt, recoveryUntil);
            }
        }
    }

    /**
     * Whom a chained-into ability aims at: the previous run's aim when its rule is {@code current} (a charge's finisher
     * hits whoever was charged, not the tank), unless {@code tauntWins} and a taunt lock holds the mob (then the
     * taunter); else a fresh pick by its own rule (e.g. a target slot's holder now), falling back to the previous aim
     * when that finds nobody.
     */
    private LivingEntity chainedAim(MobBehaviour.Ability next, LivingEntity previous, boolean tauntWins) {
        if (next.target().kind() == MobBehaviour.TargetRule.Kind.CURRENT) {
            LivingEntity taunter = tauntWins ? taunter() : null;
            if (taunter != null && taunter != previous) {
                trace("taunt lock: " + taunter.getName().getString() + " takes the chain");
            }
            return taunter != null ? taunter : previous;
        }
        LivingEntity picked = CombatTargets.pick(mob, next.target(), next.includeUnreachable());
        return picked != null ? picked : previous;
    }

    /** The player (or summon) whose live taunt or provoke holds this mob now, if it may be hit; else null. */
    private LivingEntity taunter() {
        ServerLevel level = (ServerLevel) mob.level();
        UUID id = ThreatEffects.lockedController(((ThreatHolder) mob).getThreatTable(), level,
                uuid -> level.getEntity(uuid) instanceof LivingEntity living && CombatTargets.isEnemy(mob, living));
        return id != null && level.getEntity(id) instanceof LivingEntity living ? living : null;
    }

    private Seconds cooldownOf(String name, MobBehaviour.Ability ability) {
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

    private static final int REACH_CHECK_INTERVAL = 10;

    /** Whether the mob has given up on {@code entity} because it cannot get to it (high ground, no path). */
    public boolean isUnreachable(LivingEntity entity) {
        return unreachable.contains(entity.getUUID());
    }

    /** Whether target selection must skip {@code entity}: given up on as unreachable, or anyone while walking home. */
    public boolean refusesTarget(LivingEntity entity) {
        return returning || unreachable.contains(entity.getUUID());
    }

    /** Whether a {@code boss.engage_on: players} rule is in force. */
    public boolean engagesPlayersOnly() {
        MobBehaviour behaviour = behaviour();
        MobBehaviour.Boss boss = behaviour == null ? null : behaviour.boss().orElse(null);
        return boss != null && boss.engageOn() == MobBehaviour.EngageOn.PLAYERS;
    }

    /**
     * Whether {@code candidate} may be the mob's target at all. Always, unless the behaviour engages players only:
     * then also player-owned summons on its threat table and mobs that hurt it in the last 10 s, nothing else.
     */
    public boolean mayTarget(LivingEntity candidate) {
        if (candidate instanceof Player || !engagesPlayersOnly()) {
            return true;
        }
        if (candidate instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player
                && ((ThreatHolder) mob).getThreatTable().get(candidate.getUUID()) > 0.0) {
            return true;
        }
        Long hitAt = hurtBy.get(candidate.getUUID());
        return hitAt != null && mob.level().getGameTime() - hitAt <= HURT_BY_WINDOW;
    }

    /** {@code attacker} (not a player) just damaged the mob: it may be targeted for a while under engage_on players. */
    public void noteHurtBy(LivingEntity attacker) {
        if (!engagesPlayersOnly()) {
            return;
        }
        long now = mob.level().getGameTime();
        hurtBy.values().removeIf(at -> now - at > HURT_BY_WINDOW);
        hurtBy.put(attacker.getUUID(), now);
    }

    private void tickReachability(MobBehaviour behaviour, long now) {
        if (--reachCheckIn > 0) {
            return;
        }
        reachCheckIn = REACH_CHECK_INTERVAL;
        ServerLevel level = (ServerLevel) mob.level();
        LivingEntity target = mob.getTarget();
        // Grace timers only run for the current target and those given up on: one that was swapped out mid-count
        // starts over, instead of being given up on at its next single miss.
        UUID targetId = target == null ? null : target.getUUID();
        unreachableSince.keySet().removeIf(id -> !id.equals(targetId) && !unreachable.contains(id));
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
        MobBehaviour.Movement movement = movement();
        if (canReach(entity, movement)) {
            unreachableSince.remove(id);
            if (unreachable.remove(id)) {
                trace(entity.getName().getString() + " is reachable again");
            }
            return;
        }
        long since = unreachableSince.computeIfAbsent(id, key -> now);
        if (now - since >= Math.round(movement.loseAfterSeconds() * 20.0)) {
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

    /**
     * By line of sight: the mob can see it. By path: within the reach band, or the mob has a full path to it (flyers
     * and climbers always do).
     */
    private boolean canReach(LivingEntity entity, MobBehaviour.Movement movement) {
        if (movement.reachRule() == MobBehaviour.Movement.ReachRule.LINE_OF_SIGHT) {
            return mob.getSensing().hasLineOfSight(entity);
        }
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

    /**
     * Whether an incoming {@code source} is refused outright (zero damage, zero hurt flash): while returning home,
     * during an invulnerable transition/intro, or when an active {@code damage_taken} rule scales this source to 0
     * (e.g., invulnerable while summoned monolith pillars stand). {@code mobs_ld:minion_link} backlash bypasses
     * add-driven shields so breaking a pillar still cracks the boss.
     */
    public boolean refusesDamage(DamageSource source) {
        if (invulnerable()) {
            return true;
        }
        if (source.is(ModDamageTypes.MINION_LINK)) {
            return false;
        }
        MobBehaviour behaviour = behaviour();
        if (behaviour == null) {
            return false;
        }
        MobBehaviour.Phase active = activePhase(behaviour);
        if (active != null && active.damageMultiplier(source, this::hasLiveAdd) <= 0.0) {
            if (mob.level() instanceof ServerLevel level) {
                long now = level.getGameTime();
                if (now - lastShieldHintAt >= 15L) {
                    lastShieldHintAt = now;
                    MinionLinks.announceShieldBlocked(mob, level, source);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Damage the mob takes: times the phase's {@code damage_taken} rules, any {@code vulnerable} window of the
     * ability running now, protection from any channeling adds and the {@code minion_link.all_dead} multiplier.
     */
    public float adjustDamage(DamageSource source, float amount) {
        MobBehaviour behaviour = behaviour();
        if (behaviour == null) {
            return amount;
        }
        if (source.is(ModDamageTypes.MINION_LINK)) {
            return amount;
        }
        MobBehaviour.Phase active = activePhase(behaviour);
        double multiplier = active == null ? 1.0 : active.damageMultiplier(source, this::hasLiveAdd);
        if (current != null) {
            multiplier *= current.damageTakenMultiplier();
        }
        if (exposed) {
            multiplier *= exposedMultiplier;
        }
        multiplier *= summonWardMultiplier();
        return multiplier == 1.0 ? amount : (float) (amount * multiplier);
    }

    /** Take the strongest active ward. Looking up the live add also drops wards when an add dies or unloads. */
    private double summonWardMultiplier() {
        if (summons.isEmpty() || !(mob.level() instanceof ServerLevel level)) {
            return 1.0;
        }
        double multiplier = 1.0;
        for (UUID id : summons) {
            multiplier = Math.min(multiplier, wardFrom(level, id));
        }
        return multiplier;
    }

    private double wardFrom(ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        if (entity == null || !entity.isAlive()) {
            return 1.0;
        }
        MobCombat add = CombatHost.of(entity);
        return add != null && add.current != null && add.behaviour() != null ? add.current.wardMultiplier(mob) : 1.0;
    }

    /** Counts live connections, so breaking a third ward removes the boss's damage bonus immediately. */
    public float adjustWardDamageDealt(float amount) {
        if (summons.isEmpty() || !(mob.level() instanceof ServerLevel level)) {
            return amount;
        }
        MobBehaviour behaviour = behaviour();
        MobBehaviour.WardDamage bonus = behaviour == null ? null
                : behaviour.boss().flatMap(MobBehaviour.Boss::wardDamage).orElse(null);
        if (bonus != null) {
            int connected = 0;
            for (UUID id : summons) {
                if (wardFrom(level, id) < 1.0 && ++connected >= bonus.minWards()) {
                    return (float) (amount * bonus.multiplier());
                }
            }
        }
        return amount;
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
        recoveryUntil = 0;
        globalReadyAt = 0;
        host.mobs_ld$stopAction();
        ((ThreatHolder) mob).getThreatTable().clear();
        mob.setTarget(null);
        slots.clear();
        mob.setHealth(mob.getMaxHealth());
        phase = 0;
        host.mobs_ld$setVariant("");
        readyAt.clear();
        groupReadyAt.clear();
        readyInWins.clear();
        lastStarted = null;
        completedAbilities.clear();
        stacks.clear();
        damageLedger.clear();
        clearWatches();
        ListenWindows.closeFor(mob);
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
        adapter.onReset(mob);
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
        recoveryUntil = 0;
        stacks.clear();
        clearWatches();
        ListenWindows.closeFor(mob);
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
        ListenWindows.closeFor(mob); // the abandoned run's window (its events were not told)
        if (!leftovers.isEmpty() && mob.level() instanceof ServerLevel level) {
            MinecraftServer server = level.getServer();
            server.tell(new TickTask(server.getTickCount(), () -> leftovers.forEach(AttackZoneDisplay::remove)));
        }
    }

    // --- Damage dealt to it, stacks, positions ---

    /** {@code attacker} dealt it {@code amount} (after armour): counted for {@code top_damage} for 10 s. */
    public void noteDamage(LivingEntity attacker, float amount) {
        damageLedger.record(attacker.getUUID(), amount, mob.level().getGameTime());
        if (current != null && amount > 0.0F) {
            current.noteDamageTaken(amount);
        }
    }

    /** Whoever {@code eligible} accepts that dealt it the most damage in the last 10 s; null if none did. */
    public UUID topDamage(Predicate<UUID> eligible) {
        return damageLedger.top(mob.level().getGameTime(), eligible);
    }

    /** What this mob's ability hits on {@code victim} are multiplied by, from the stacks they carry (1 = none). */
    public double stackMultiplier(LivingEntity victim) {
        return stacks.isEmpty() ? 1.0 : stacks.multiplier(victim.getUUID(), mob.level().getGameTime());
    }

    /**
     * A hit with {@code stacks} landed on {@code victim}: one more stack (if enough players fight it), and the victim
     * (a player) sees the count in its action bar.
     */
    public void addStack(LivingEntity victim, HitSpec.Stacks spec) {
        if (spec.minPlayers() > 0 && Fighters.of(mob).size() < spec.minPlayers()) {
            return;
        }
        int count = stacks.add(victim.getUUID(), spec.id(), spec.perStack(), spec.max(),
                Math.round(spec.duration() * 20.0), mob.level().getGameTime());
        trace(String.format("%s x%d on %s", spec.id(), count, victim.getName().getString()));
        if (victim instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable(spec.messageKey(), count), true);
        }
    }

    /**
     * Where {@code entity} is heading {@code lead} seconds ahead, from its movement over the last half second (only
     * fighters are tracked, and only while a {@code lead} hit is off cooldown or running); where it stands otherwise.
     */
    public Vec3 predictedPosition(LivingEntity entity, double lead) {
        return moveLead.predict(entity, lead, mob.level().getGameTime());
    }

    /** Where it lives: where it first ticked, or was carried to (the arena it walks back to); null before that. */
    public Vec3 home() {
        return home;
    }

    /** Whether one of its own adds of entity type {@code type} is alive now ({@code damage_taken.while_adds}). */
    public boolean hasLiveAdd(ResourceLocation type) {
        if (summons.isEmpty() || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        for (UUID id : summons) {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive() && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(type)) {
                return true;
            }
        }
        return false;
    }

    // --- Summons ---

    public void trackSummon(Entity summoned) {
        summons.add(summoned.getUUID());
    }

    /** A linked add entered the level; the first add of a new linked summon ends {@code all_dead}'s exposure. */
    public void trackLinkedSummon(Entity summoned, SummonEvent.MinionLink link) {
        links.put(summoned.getUUID(), link);
        if (exposed) {
            exposed = false;
            exposedMultiplier = 1.0;
            trace("minion_link: summoned again, no longer exposed");
        }
    }

    /**
     * One of its linked adds was killed: it takes the link's {@code death_damage} (credited to the add's killer), then,
     * if no linked add is left alive, the link's {@code all_dead} multiplier until it summons linked adds again.
     */
    void onLinkedSummonDeath(LivingEntity add, DamageSource source) {
        SummonEvent.MinionLink link = links.remove(add.getUUID());
        if (link == null || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (link.deathDamage().isPresent()) {
            float amount = (float) link.deathDamage().get().amount(add.getMaxHealth(), mob.getMaxHealth());
            if (amount > 0.0F) {
                LivingEntity killer = MinionLinks.killer(add, source, mob, this::ownsSummon);
                trace(String.format("minion_link: add killed by %s, %.1f damage",
                        killer == null ? "nobody" : killer.getName().getString(), amount));
                mob.hurt(MinionLinks.damage(level, killer), amount);
            }
        }
        // The hit may have killed it (its death clears the links) or the exposure may already be on.
        if (link.allDead().isPresent() && mob.isAlive() && !exposed && !linkedAddAlive(level)) {
            exposed = true;
            exposedMultiplier = link.allDead().get().damageTaken();
            trace("minion_link: all linked adds dead, damage taken x" + exposedMultiplier);
            MinionLinks.announce(mob, level, exposedMultiplier);
        }
    }

    /** Whether one of its linked adds is alive now; drops the ones that are gone (as {@link #aliveSummons} does). */
    private boolean linkedAddAlive(ServerLevel level) {
        links.keySet().removeIf(id -> {
            Entity entity = level.getEntity(id);
            return entity == null || !entity.isAlive();
        });
        return !links.isEmpty();
    }

    /** Whether all its linked adds are dead and it takes the {@code all_dead} multiplier now. */
    public boolean exposed() {
        return exposed;
    }

    /** Whether {@code entity} is one of the adds this mob summoned (its skulls fly through them). */
    public boolean ownsSummon(Entity entity) {
        return !summons.isEmpty() && summons.contains(entity.getUUID());
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

    /** Live summons of one specific entity type (used when a summon sets {@code per_type: true}). */
    public int aliveSummons(ResourceLocation entityType) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return 0;
        }
        aliveSummons();
        int count = 0;
        for (UUID id : summons) {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive()
                    && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(entityType)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Draws lightweight particle tethers from living barrier pillars ({@code damage_taken} with {@code while_adds}
     * multiplier {@code <= 0}) or healing totems ({@code minion_link.while_alive_heal_per_second}), and ticks the
     * per-second siphon heal while healing totems survive.
     */
    private void tickTethersAndSiphon(MobBehaviour behaviour, ServerLevel level, long now) {
        if (summons.isEmpty() && links.isEmpty()) {
            return;
        }
        if (now % MinionLinks.TETHER_INTERVAL == 0) {
            MobBehaviour.Phase active = activePhase(behaviour);
            if (active != null && !summons.isEmpty()) {
                boolean shielded = false;
                for (MobBehaviour.DamageTaken rule : active.damageTaken()) {
                    if (rule.multiplier() > 0.0 || rule.whileAdds().isEmpty()) {
                        continue;
                    }
                    ResourceLocation shieldType = rule.whileAdds().get();
                    for (UUID id : summons) {
                        Entity add = level.getEntity(id);
                        if (add != null && add.isAlive()
                                && BuiltInRegistries.ENTITY_TYPE.getKey(add.getType()).equals(shieldType)) {
                            MinionLinks.drawTether(level, add, mob, ParticleTypes.END_ROD);
                            shielded = true;
                        }
                    }
                }
                if (shielded) {
                    MinionLinks.tickShieldAura(mob, level);
                }
            }
            if (!links.isEmpty()) {
                for (Map.Entry<UUID, SummonEvent.MinionLink> entry : links.entrySet()) {
                    if (!entry.getValue().healsSummoner()) {
                        continue;
                    }
                    Entity add = level.getEntity(entry.getKey());
                    if (add != null && add.isAlive()) {
                        MinionLinks.drawTether(level, add, mob, ParticleTypes.FLAME);
                    }
                }
            }
        }
        if (now % 20 == 0 && !links.isEmpty()) {
            double maxHealth = mob.getMaxHealth();
            double totalHeal = 0.0;
            for (Map.Entry<UUID, SummonEvent.MinionLink> entry : links.entrySet()) {
                double rate = entry.getValue().healPerSecondFor(maxHealth);
                if (rate <= 0.0) {
                    continue;
                }
                Entity add = level.getEntity(entry.getKey());
                if (add != null && add.isAlive()) {
                    totalHeal += rate;
                }
            }
            if (totalHeal > 0.0 && mob.getHealth() < maxHealth) {
                mob.heal((float) totalHeal);
                level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight() * 0.72, mob.getZ(),
                        3, mob.getBbWidth() * 0.4, 0.3, mob.getBbWidth() * 0.4, 0.02);
            }
        }
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
        links.clear();
        exposed = false;
        exposedMultiplier = 1.0;
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

    /**
     * The bar's look is {@code boss.bar} with the current phase's {@code bar} laid over it. A host with its own bar
     * (the Wither's) keeps showing and filling it, and only takes the parts the behaviour sets.
     */
    private void updateBar(MobBehaviour behaviour) {
        MobBehaviour.BossBar style = behaviour.boss().flatMap(MobBehaviour.Boss::bar).orElse(null);
        MobBehaviour.Phase active = activePhase(behaviour);
        MobBehaviour.BarStyle phaseStyle = active == null ? null : active.bar().orElse(null);
        ServerBossEvent hostBar = adapter.bossBar(mob);
        // Parts nobody sets fall back to the host bar's own look (none for an engine bar: boss.bar sets both).
        MobBehaviour.BossBar fallback = style != null ? style : hostBar != null ? adapter.defaultBarStyle() : null;
        BossEvent.BossBarColor color = phaseStyle != null && phaseStyle.color().isPresent() ? phaseStyle.color().get()
                : fallback != null ? fallback.color() : null;
        BossEvent.BossBarOverlay overlay = phaseStyle != null && phaseStyle.overlay().isPresent() ? phaseStyle.overlay().get()
                : fallback != null ? fallback.overlay() : null;
        if (style == null || hostBar != null) {
            removeBar();
            if (hostBar != null) {
                if (color != null) {
                    hostBar.setColor(color);
                }
                if (overlay != null) {
                    hostBar.setOverlay(overlay);
                }
            }
            return;
        }
        if (bar == null) {
            bar = new ServerBossEvent(mob.getDisplayName(), color, overlay);
            for (ServerPlayer player : ((ServerLevel) mob.level()).players()) {
                if (player.distanceToSqr(mob) < 64.0 * 64.0) {
                    bar.addPlayer(player);
                }
            }
        }
        bar.setColor(color);
        bar.setOverlay(overlay);
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
        if (!links.isEmpty()) {
            ListTag linked = new ListTag();
            links.forEach((id, link) -> SummonEvent.MinionLink.CODEC.encodeStart(NbtOps.INSTANCE, link)
                    .result().ifPresent(encoded -> {
                        CompoundTag entry = new CompoundTag();
                        entry.putUUID("id", id);
                        entry.put("link", encoded);
                        linked.add(entry);
                    }));
            state.put("links", linked);
        }
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
            links.clear();
            for (Tag entry : state.getList("links", Tag.TAG_COMPOUND)) {
                CompoundTag linked = (CompoundTag) entry;
                if (linked.hasUUID("id")) {
                    SummonEvent.MinionLink.CODEC.parse(NbtOps.INSTANCE, linked.get("link")).result()
                            .ifPresent(link -> links.put(linked.getUUID("id"), link));
                }
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
