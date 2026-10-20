package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.threat.ThreatHolder;
import net.ledok.mobs_ld.mob.LdMob;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviour;
import net.ledok.mobs_ld.mob.behaviour.MobBehaviours;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
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
 * Server-side fight state of one data-driven mob: the ability in flight, cooldowns, boss phases, engagement, leash
 * and wipe resets, soft enrage, the boss bar and its summoned adds. {@link CombatGoal} decides <i>when</i> to move
 * or start an ability; everything else ticks here every server tick, even while no goal runs.
 */
public final class MobCombat {
    private static final ResourceLocation SOFT_ENRAGE = ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "soft_enrage");
    /** Ticks a telegraph keeps flashing after its impact. */
    private static final int FLASH_TICKS = 4;
    /** A boss walking home gives up and teleports after this long. */
    private static final int RETURN_TIMEOUT = 200;

    /** {@code /mobs_ld debug}: narrate ability decisions to operators in chat. */
    private static boolean debug;

    private final LdMob mob;
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
    private Vec3 home;
    private boolean returning;
    private int returnTicks;
    private ServerBossEvent bar;

    public MobCombat(LdMob mob) {
        this.mob = mob;
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
            if (current != null) {
                current.cancel();
                current = null;
                mob.stopAction();
            }
            return;
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
            mob.stopAction();
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
        if (boss != null && idleTicks > boss.resetAfter() * 20.0) {
            reset(); // everyone died or left: a wipe
            return;
        }
        if (boss != null && boss.leash() > 0.0 && mob.position().distanceTo(home) > boss.leash()) {
            reset(); // pulled out of its arena
            return;
        }
        if (boss != null && !enraged) {
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

    private void enterPhase(MobBehaviour behaviour, int index) {
        trace("phase " + (index + 1));
        if (current != null) {
            current.cancel();
            current = null;
        }
        staggerUntil = -1;
        phase = index;
        MobBehaviour.Phase entered = behaviour.phases().get(index);
        mob.setTextureVariant(entered.texture().orElse(""));
        long now = mob.level().getGameTime();
        entered.readyIn().forEach((name, seconds) -> readyAt.put(name, now + Math.round(seconds * 20.0)));
        entered.transition().ifPresentOrElse(
                transition -> start("transition", transition, mob.getTarget()),
                mob::stopAction);
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
                mob.playAction(clip, 1.0);
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
        mob.playAction(ability.clipOr(name), speed);
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
            mob.playAction(stagger.clip(), 1.0);
        } else {
            mob.stopAction();
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
        if (now - since >= UNREACHABLE_AFTER && unreachable.add(id)) {
            trace(entity.getName().getString() + " is unreachable; switching targets");
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
        if (mob.getNavigation() instanceof FlyingPathNavigation) {
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

    // --- Damage ---

    public boolean invulnerable() {
        return returning || (current != null && current.ability().invulnerable());
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
        mob.stopAction();
        ((ThreatHolder) mob).getThreatTable().clear();
        mob.setTarget(null);
        mob.setHealth(mob.getMaxHealth());
        phase = 0;
        mob.setTextureVariant("");
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
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.removeModifier(SOFT_ENRAGE);
        }
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
        if (bar != null) {
            bar.removeAllPlayers();
        }
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

    /** Entity removed (killed, despawned, unloaded): nothing it spawned for the fight may linger. */
    public void onRemoved() {
        if (current != null) {
            current.cancel();
            current = null;
        }
        if (deathRun != null) {
            deathRun.cancel();
            deathRun = null;
        }
        for (Fading fade : fading) {
            fade.display.remove();
        }
        fading.clear();
        if (bar != null) {
            bar.removeAllPlayers();
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
        if (style == null) {
            if (bar != null) {
                bar.removeAllPlayers();
                bar = null;
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

    public void save(CompoundTag tag) {
        if (home != null) {
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(home.x));
            pos.add(DoubleTag.valueOf(home.y));
            pos.add(DoubleTag.valueOf(home.z));
            tag.put("mobs_ld_home", pos);
        }
    }

    public void load(CompoundTag tag) {
        if (tag.contains("mobs_ld_home", Tag.TAG_LIST)) {
            ListTag pos = tag.getList("mobs_ld_home", Tag.TAG_DOUBLE);
            if (pos.size() == 3) {
                home = new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
            }
        }
    }

    private record Candidate(String name, MobBehaviour.Ability ability, LivingEntity aim) {
    }

    private record Fading(AttackZoneDisplay display, long removeAt) {
    }
}
