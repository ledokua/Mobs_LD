package net.ledok.mobs_ld.entity.boss;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.boss.ai.BossAbilityGoal;
import net.ledok.mobs_ld.entity.boss.ai.BossMovementGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public abstract class BaseBossMob extends Monster {
    private static final ResourceLocation ENRAGE_SPEED_MOD_ID = ResourceLocation.fromNamespaceAndPath("mobs_ld", "enrage_speed");
    private static final ResourceLocation ENRAGE_DAMAGE_MOD_ID = ResourceLocation.fromNamespaceAndPath("mobs_ld", "enrage_damage");

    private final Map<String, Integer> abilityCooldowns = new HashMap<>();
    private final Map<String, AbilityDefinition> abilities;
    private final List<BossPhase> phases;
    private final EnrageConfig enrageConfig;

    private final Set<String> usedThresholdAbilities = new HashSet<>();
    private final Set<String> pendingPhaseEntryAbilities = new HashSet<>();
    private final Set<UUID> trackedPlayerIds = new HashSet<>();
    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS
    );

    private final Map<String, Integer> passiveWindupTimers = new HashMap<>();
    private final Map<String, AttackZoneDisplay> passiveDisplays = new HashMap<>();
    private final Map<String, Vec3> passiveOrigins = new HashMap<>();
    private final Map<String, Float> passiveYaws = new HashMap<>();
    private BossAbilityGoal bossAbilityGoal;
    private boolean cleanedUp = false;

    private int currentPhase = 0;
    private BossPhase activePhase;
    private AbilityDefinition activeAbility = null;
    private boolean windingUp = false;
    private boolean damageImmune = false;
    private int attackCooldown = 0;

    private boolean enraged = false;
    private float hpAtEnrageWindowStart = -1.0F;
    private int enrageWindowTimer = -1;

    protected BaseBossMob(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.abilities = new HashMap<>(defineAbilities());
        this.phases = List.copyOf(definePhases());
        this.enrageConfig = defineEnrage();
        if (phases.isEmpty()) {
            throw new IllegalStateException("Boss must define at least one phase");
        }
        transitionToPhase(0);
        this.bossBar.setName(getBossBarName());
    }

    protected abstract Map<String, AbilityDefinition> defineAbilities();

    protected abstract List<BossPhase> definePhases();

    protected abstract EnrageConfig defineEnrage();

    protected abstract double idealAttackDistance();

    protected abstract Component getBossBarName();

    public static AttributeSupplier.Builder createBaseAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        bossAbilityGoal = new BossAbilityGoal(this);
        goalSelector.addGoal(2, bossAbilityGoal);
        goalSelector.addGoal(3, new BossMovementGoal(this));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 16.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (activePhase.canRotate() && getTarget() != null) {
            getLookControl().setLookAt(getTarget(), 30.0F, 30.0F);
        }

        tickCooldowns();
        tickPhaseCheck();
        tickEnrageCheck();
        tickBossBar();
        tickPassiveAbilities();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (damageImmune) {
            return false;
        }
        float multiplier = activePhase.damageProfile().getMultiplierFor(source);
        if (multiplier <= 0.0F) {
            return false;
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            trackedPlayerIds.add(player.getUUID());
        }
        return super.hurt(source, amount * multiplier);
    }

    @Override
    public void die(DamageSource source) {
        cleanupBossState();
        super.die(source);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        cleanupBossState();
        super.remove(reason);
    }

    private void cleanupBossState() {
        if (cleanedUp) {
            return;
        }
        cleanedUp = true;
        if (bossAbilityGoal != null) {
            bossAbilityGoal.stop();
        }
        goalSelector.getAvailableGoals().forEach(wrappedGoal -> wrappedGoal.getGoal().stop());
        bossBar.setVisible(false);
        bossBar.removeAllPlayers();
        if (level() instanceof ServerLevel world) {
            new HashSet<>(passiveWindupTimers.keySet())
                    .forEach(id -> cleanupPassiveAbility(id, world));
        }
        passiveDisplays.values().forEach(AttackZoneDisplay::remove);
        passiveDisplays.clear();
        passiveWindupTimers.clear();
        passiveOrigins.clear();
        passiveYaws.clear();
    }

    public void tickCooldowns() {
        abilityCooldowns.replaceAll((id, ticks) -> Math.max(0, ticks - 1));
    }

    private void tickPhaseCheck() {
        float hpFraction = getHealth() / getMaxHealth();
        int newPhase = 0;
        for (int i = 0; i < phases.size(); i++) {
            if (hpFraction <= phases.get(i).hpThreshold()) {
                newPhase = i;
            }
        }
        if (newPhase != currentPhase) {
            transitionToPhase(newPhase);
        }
    }

    private void transitionToPhase(int index) {
        currentPhase = index;
        activePhase = phases.get(index);
        cancelActiveAbility();
        for (String id : activePhase.abilityIds()) {
            AbilityDefinition ability = abilities.get(id);
            if (ability != null && !abilityCooldowns.containsKey(id)) {
                abilityCooldowns.put(id, Math.max(0, ability.initialCooldown()));
            } else {
                abilityCooldowns.putIfAbsent(id, 0);
            }
        }
        activePhase.abilityIds().stream()
                .map(abilities::get)
                .filter(Objects::nonNull)
                .filter(a -> a.trigger() instanceof TriggerCondition.OnPhaseEntry)
                .forEach(this::scheduleAbility);
    }

    private void tickEnrageCheck() {
        if (enraged || enrageConfig == null) {
            return;
        }
        if (hpAtEnrageWindowStart < 0.0F) {
            hpAtEnrageWindowStart = getHealth();
            enrageWindowTimer = enrageConfig.timeWindowTicks();
        }
        if (--enrageWindowTimer <= 0) {
            hpAtEnrageWindowStart = getHealth();
            enrageWindowTimer = enrageConfig.timeWindowTicks();
        }

        float hpLostFraction = (hpAtEnrageWindowStart - getHealth()) / getMaxHealth();
        if (hpLostFraction >= enrageConfig.hpLossThreshold()) {
            triggerEnrage();
        }
    }

    private void triggerEnrage() {
        enraged = true;
        if (enrageConfig == null) {
            return;
        }

        if (getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(ENRAGE_SPEED_MOD_ID);
            getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(new AttributeModifier(
                    ENRAGE_SPEED_MOD_ID,
                    enrageConfig.speedMultiplier() - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }
        if (getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(ENRAGE_DAMAGE_MOD_ID);
            getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(new AttributeModifier(
                    ENRAGE_DAMAGE_MOD_ID,
                    enrageConfig.damageMultiplier() - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }

        abilityCooldowns.replaceAll((id, ticks) -> (int) (ticks * enrageConfig.cooldownMultiplier()));
        for (String id : enrageConfig.unlockedAbilityIds()) {
            if (!activePhase.abilityIds().contains(id)) {
                activePhase.abilityIds().add(id);
            }
            abilityCooldowns.put(id, 0);
        }

        bossBar.setColor(BossEvent.BossBarColor.PURPLE);
        bossBar.setName(getBossBarName().copy()
                .append(" ")
                .append(Component.translatable("boss.mobs_ld.enraged").withStyle(ChatFormatting.RED)));
    }

    private void tickBossBar() {
        if (!isAlive()) {
            return;
        }
        if (!(level() instanceof ServerLevel world)) {
            return;
        }
        bossBar.setVisible(true);
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (!enraged) {
            bossBar.setName(getBossBarName());
        }

        Set<ServerPlayer> inRange = new HashSet<>(world.getEntitiesOfClass(
                ServerPlayer.class,
                new AABB(blockPosition()).inflate(64.0)
        ));

        for (UUID id : trackedPlayerIds) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                inRange.add(player);
            }
        }

        Set<ServerPlayer> currentPlayers = new HashSet<>(bossBar.getPlayers());
        for (ServerPlayer player : currentPlayers) {
            if (!inRange.contains(player)) {
                bossBar.removePlayer(player);
            }
        }
        for (ServerPlayer player : inRange) {
            bossBar.addPlayer(player);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    private void tickPassiveAbilities() {
        if (!(level() instanceof ServerLevel world)) {
            return;
        }
        Set<String> phaseAbilityIds = new HashSet<>(activePhase.abilityIds());
        for (String id : new HashSet<>(passiveWindupTimers.keySet())) {
            if (phaseAbilityIds.contains(id)) {
                continue;
            }
            cleanupPassiveAbility(id, world);
        }

        for (String id : activePhase.abilityIds()) {
            AbilityDefinition ability = abilities.get(id);
            if (ability == null || !ability.isPassive()) {
                continue;
            }

            int windup = passiveWindupTimers.getOrDefault(id, -1);
            if (windup >= 0) {
                int newTimer = windup - 1;
                passiveWindupTimers.put(id, newTimer);
                ability.onWindupTick(world, this, newTimer);
                AttackZoneDisplay display = passiveDisplays.get(id);
                if (display != null) {
                    display.update(newTimer, Math.max(1, ability.windupTicks()));
                    if (newTimer <= 2) {
                        display.setBrightRed();
                    }
                }
                if (newTimer <= 0) {
                    Vec3 origin = passiveOrigins.getOrDefault(id, position());
                    float yaw = passiveYaws.getOrDefault(id, getYRot());
                    ability.onActivate(world, this, origin, yaw);
                    ability.onEnd(world, this);
                    if (display != null) {
                        display.remove();
                    }
                    passiveDisplays.remove(id);
                    passiveWindupTimers.remove(id);
                    passiveOrigins.remove(id);
                    passiveYaws.remove(id);
                    abilityCooldowns.put(id, resolvedCooldown(ability));
                }
                continue;
            }

            int cd = abilityCooldowns.getOrDefault(id, 0);
            if (cd > 0) {
                continue;
            }
            if (!canTrigger(ability) || !ability.canUse(world, this)) {
                continue;
            }

            Vec3 origin = resolvePassiveOrigin(ability);
            float yaw = resolvePassiveYaw(ability);
            passiveOrigins.put(id, origin);
            passiveYaws.put(id, yaw);
            ability.onWindupStart(world, this);

            if (ability.windupTicks() > 0) {
                passiveWindupTimers.put(id, ability.windupTicks());
                if (ability.zone() != null) {
                    AttackDisplayConfig cfg = ability.displayConfig() != null
                            ? ability.displayConfig() : AttackDisplayConfig.DEFAULT;
                    float displayYaw = yaw;
                    AttackZoneDisplay display = AttackZoneDisplay.spawn(
                            world, origin, displayYaw, ability.zone(), cfg, ability.windupTicks()
                    );
                    passiveDisplays.put(id, display);
                }
            } else {
                ability.onActivate(world, this, origin, yaw);
                ability.onEnd(world, this);
                abilityCooldowns.put(id, resolvedCooldown(ability));
                passiveOrigins.remove(id);
                passiveYaws.remove(id);
            }
        }
    }

    private Vec3 resolvePassiveOrigin(AbilityDefinition ability) {
        if (ability.zone() instanceof AttackZone.CircleTarget && getTarget() != null) {
            return ability.resolveTargetOrigin(getTarget());
        }
        return position();
    }

    private float resolvePassiveYaw(AbilityDefinition ability) {
        if (ability.zone() instanceof AttackZone.CircleRays) {
            LivingEntity target = getTarget();
            if (target != null) {
                double dx = target.getX() - getX();
                double dz = target.getZ() - getZ();
                return (float) Math.toDegrees(Math.atan2(-dx, dz));
            }
        }
        return getYRot();
    }

    private void cleanupPassiveAbility(String id, ServerLevel world) {
        AttackZoneDisplay display = passiveDisplays.remove(id);
        if (display != null) {
            display.remove();
        }
        passiveWindupTimers.remove(id);
        passiveOrigins.remove(id);
        passiveYaws.remove(id);
        AbilityDefinition ability = abilities.get(id);
        if (ability != null) {
            ability.onEnd(world, this);
        }
    }

    public int resolvedCooldown(AbilityDefinition ability) {
        int base = Math.max(0, ability.cooldownTicks());
        if (enraged && enrageConfig != null) {
            base = (int) (base * enrageConfig.cooldownMultiplier());
        }
        if (ability.trigger() instanceof TriggerCondition.RandomFromPool random) {
            int min = Math.max(0, random.minCooldown());
            int max = Math.max(min, random.maxCooldown());
            base = min + getRandom().nextInt(max - min + 1);
        }
        return base;
    }

    public boolean canTrigger(AbilityDefinition ability) {
        return switch (ability.trigger()) {
            case TriggerCondition.OnTimer ignored -> true;
            case TriggerCondition.RandomFromPool ignored -> true;
            case TriggerCondition.AtHpThreshold t -> (getHealth() / getMaxHealth() <= t.threshold())
                    && !usedThresholdAbilities.contains(ability.id());
            case TriggerCondition.OnPhaseEntry ignored -> pendingPhaseEntryAbilities.contains(ability.id());
        };
    }

    public void scheduleAbility(AbilityDefinition ability) {
        abilityCooldowns.put(ability.id(), 0);
        if (ability.trigger() instanceof TriggerCondition.OnPhaseEntry) {
            pendingPhaseEntryAbilities.add(ability.id());
        }
    }

    public void consumePhaseEntryAbility(String abilityId) {
        pendingPhaseEntryAbilities.remove(abilityId);
    }

    public void markThresholdAbilityUsed(String abilityId) {
        usedThresholdAbilities.add(abilityId);
    }

    public void setCooldown(String abilityId, int cooldown) {
        abilityCooldowns.put(abilityId, Math.max(0, cooldown));
    }

    public int getCooldown(String abilityId) {
        return abilityCooldowns.getOrDefault(abilityId, 0);
    }

    public void setActiveAbility(AbilityDefinition ability) {
        this.activeAbility = ability;
    }

    public AbilityDefinition getActiveAbility() {
        return activeAbility;
    }

    public void setWindingUp(boolean windingUp) {
        this.windingUp = windingUp;
    }

    public boolean isWindingUp() {
        return windingUp;
    }

    public void setDamageImmune(boolean damageImmune) {
        this.damageImmune = damageImmune;
    }

    public void cancelActiveAbility() {
        setActiveAbility(null);
        setWindingUp(false);
        setDamageImmune(false);
    }

    public BossPhase getActivePhase() {
        return activePhase;
    }

    public Map<String, AbilityDefinition> getAbilities() {
        return abilities;
    }

    public List<BossPhase> getPhases() {
        return phases;
    }

    public int getAttackCooldown() {
        return attackCooldown;
    }

    public int getGlobalAttackLockout() {
        return 0;
    }

    public boolean isInPersistPhase() {
        AbilityDefinition active = getActiveAbility();
        if (active == null || active.canMoveWhilePersisting()) {
            return false;
        }
        return bossAbilityGoal != null && bossAbilityGoal.isInPersistPhase();
    }

    public void setAttackCooldown(int attackCooldown) {
        this.attackCooldown = Math.max(0, attackCooldown);
    }

    public double getIdealAttackDistance() {
        return idealAttackDistance();
    }

    public boolean isMovementLocked() {
        return false;
    }

    public void applyZoneDamage(AttackZone zone, Vec3 origin, float yawDegrees, float damage) {
        if (!(level() instanceof ServerLevel world)) {
            return;
        }
        float reach = zone.maxForwardReach();
        AABB broad = new AABB(origin, origin).inflate(reach + 1.0F);
        List<ServerPlayer> players = world.getEntitiesOfClass(ServerPlayer.class, broad);
        Vec3 forward = new Vec3(-Math.sin(Math.toRadians(yawDegrees)), 0.0, Math.cos(Math.toRadians(yawDegrees)));

        for (ServerPlayer player : players) {
            if (isInZone(player.position(), zone, origin, forward)) {
                player.hurt(world.damageSources().mobAttack(this), damage);
            }
        }
    }

    private boolean isInZone(Vec3 pos, AttackZone zone, Vec3 origin, Vec3 forward) {
        Vec3 to = pos.subtract(origin);
        return switch (zone) {
            case AttackZone.Rectangle r -> {
                Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
                double fwdDist = to.dot(forward);
                double sideDist = Math.abs(to.dot(right));
                yield fwdDist >= r.offsetForward()
                        && fwdDist <= r.offsetForward() + r.length()
                        && sideDist <= r.width() / 2.0F;
            }
            case AttackZone.Cone c -> {
                double dist = to.length();
                if (dist <= 1.0e-6 || dist > c.maxDistance()) {
                    yield false;
                }
                double angle = Math.toDegrees(Math.acos(to.normalize().dot(forward)));
                yield angle <= c.angleDegrees() / 2.0;
            }
            case AttackZone.Circle c -> to.lengthSqr() <= (double) c.radius() * c.radius();
            case AttackZone.CircleTarget c -> to.lengthSqr() <= (double) c.radius() * c.radius();
            case AttackZone.CircleRays r -> {
                float baseYawRad = (float) Math.atan2(-forward.x, forward.z);
                int rectCount = Math.max(1, r.rayCount() / 2);
                Vec3 toFlat = new Vec3(to.x, 0.0, to.z);
                for (int i = 0; i < rectCount; i++) {
                    float yawRad = baseYawRad + (float) Math.toRadians(i * (180.0 / rectCount));
                    Vec3 rayForward = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
                    Vec3 rayRight = new Vec3(-rayForward.z, 0.0, rayForward.x);
                    double alongRay = Math.abs(toFlat.dot(rayForward));
                    double acrossRay = Math.abs(toFlat.dot(rayRight));
                    if (alongRay <= r.length() && acrossRay <= r.width() * 0.5F) {
                        yield true;
                    }
                }
                yield false;
            }
        };
    }

    public void forceActivateCurrentAbility() {
        goalSelector.getAvailableGoals().forEach(wrapped -> {
            if (wrapped.getGoal() instanceof net.ledok.mobs_ld.entity.boss.ai.BossAbilityGoal abilityGoal) {
                abilityGoal.forceActivate();
            }
        });
    }

    public net.ledok.mobs_ld.entity.boss.ai.BossAbilityGoal getBossAbilityGoal() {
        return bossAbilityGoal;
    }
}
