package net.ledok.mobs_ld.mob.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.ledok.mobs_ld.mob.host.HostOptions;
import net.ledok.mobs_ld.mob.host.HostSpec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The reloadable half of a mob definition: how it moves, what it can do and, for bosses, how the fight evolves.
 * One file per entity type at {@code data/<namespace>/mob_behaviour/<path>.json} (see docs/behaviour.md).
 * All times are seconds, as in the art hand-off notes; distances are blocks.
 *
 * @param baseAttributes attribute id -> base value, set on every new mob of the type as it is constructed (before
 *                       its own constructor finishes and before other mods scale it); saved mobs keep their values
 * @param host           how the engine runs on a vanilla host (its adapter's options; an off switch)
 */
public record MobBehaviour(
        Map<ResourceLocation, Double> baseAttributes,
        HostSpec host,
        Movement movement,
        double globalCooldown,
        Optional<Ability> intro,
        Optional<Stagger> stagger,
        Optional<Ability> onDeath,
        Map<String, Ability> abilities,
        List<Phase> phases,
        Optional<Boss> boss
) {
    public static final Codec<MobBehaviour> CODEC = RecordCodecBuilder.<MobBehaviour>create(i -> i.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("base_attributes", Map.of()).forGetter(MobBehaviour::baseAttributes),
            HostSpec.CODEC.optionalFieldOf("host", HostSpec.NONE).forGetter(MobBehaviour::host),
            Movement.CODEC.optionalFieldOf("movement", Movement.DEFAULT).forGetter(MobBehaviour::movement),
            Codec.DOUBLE.optionalFieldOf("global_cooldown", 0.5).forGetter(MobBehaviour::globalCooldown),
            Ability.CODEC.optionalFieldOf("intro").forGetter(MobBehaviour::intro),
            Stagger.CODEC.optionalFieldOf("stagger").forGetter(MobBehaviour::stagger),
            Ability.CODEC.optionalFieldOf("on_death").forGetter(MobBehaviour::onDeath),
            Codec.unboundedMap(Codec.STRING, Ability.CODEC).optionalFieldOf("abilities", Map.of()).forGetter(MobBehaviour::abilities),
            Phase.CODEC.listOf().optionalFieldOf("phases", List.of()).forGetter(MobBehaviour::phases),
            Boss.CODEC.optionalFieldOf("boss").forGetter(MobBehaviour::boss)
    ).apply(i, MobBehaviour::new)).validate(MobBehaviour::validate);

    public MobBehaviour {
        // Keep authoring order (phases sorted from full health down) and a stable ability order.
        baseAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(baseAttributes));
        abilities = new LinkedHashMap<>(abilities);
        List<Phase> sorted = new ArrayList<>(phases);
        sorted.sort((a, b) -> Double.compare(b.belowHealth(), a.belowHealth()));
        phases = List.copyOf(sorted);
    }

    private static DataResult<MobBehaviour> validate(MobBehaviour behaviour) {
        List<Ability> all = new ArrayList<>(behaviour.abilities().values());
        behaviour.intro().ifPresent(all::add);
        behaviour.onDeath().ifPresent(all::add);
        behaviour.phases().forEach(phase -> phase.transition().ifPresent(all::add));
        for (Ability ability : all) {
            for (AbilityEvent event : ability.events()) {
                for (String chained : event.chains()) {
                    if (!behaviour.abilities().containsKey(chained)) {
                        return DataResult.error(() -> "A " + event.type() + " event chains into unknown ability '" + chained + "'");
                    }
                }
            }
        }
        for (Phase phase : behaviour.phases()) {
            for (String name : phase.abilities()) {
                if (!behaviour.abilities().containsKey(name)) {
                    return DataResult.error(() -> "Phase below " + phase.belowHealth() + " lists unknown ability '" + name + "'");
                }
            }
            for (String name : phase.readyIn().keySet()) {
                if (!behaviour.abilities().containsKey(name)) {
                    return DataResult.error(() -> "Phase below " + phase.belowHealth() + " sets ready_in of unknown ability '" + name + "'");
                }
            }
            for (String name : phase.cooldowns().keySet()) {
                if (!behaviour.abilities().containsKey(name)) {
                    return DataResult.error(() -> "Phase below " + phase.belowHealth() + " overrides cooldown of unknown ability '" + name + "'");
                }
            }
        }
        return resolvePhaseHosts(behaviour);
    }

    /** Lays each phase's own {@code host} block over the behaviour's, once at load (see {@link HostSpec#withOverride}). */
    private static DataResult<MobBehaviour> resolvePhaseHosts(MobBehaviour behaviour) {
        if (behaviour.phases().stream().noneMatch(phase -> phase.hostOverride().isPresent())) {
            return DataResult.success(behaviour);
        }
        List<Phase> resolved = new ArrayList<>();
        for (Phase phase : behaviour.phases()) {
            if (phase.hostOverride().isEmpty()) {
                resolved.add(phase);
                continue;
            }
            DataResult<HostOptions> merged = behaviour.host().withOverride(phase.hostOverride().get());
            Optional<HostOptions> options = merged.result();
            if (options.isEmpty()) {
                String message = merged.error().map(DataResult.Error::message).orElse("invalid host block");
                return DataResult.error(() -> "Phase below " + phase.belowHealth() + ": " + message);
            }
            resolved.add(phase.withHost(options.get()));
        }
        return DataResult.success(new MobBehaviour(behaviour.baseAttributes(), behaviour.host(), behaviour.movement(),
                behaviour.globalCooldown(), behaviour.intro(), behaviour.stagger(), behaviour.onDeath(),
                behaviour.abilities(), resolved, behaviour.boss()));
    }

    /**
     * The host adapter's options in effect for a phase: that phase's merged {@code host} block if it has one, else
     * the behaviour's own; empty = no typed host block (the adapter keeps its vanilla defaults).
     */
    public Optional<HostOptions> hostOptions(int phaseIndex) {
        if (!phases.isEmpty()) {
            Optional<HostOptions> own = phases.get(Math.min(phaseIndex, phases.size() - 1)).host();
            if (own.isPresent()) {
                return own;
            }
        }
        return host.options();
    }

    /**
     * Sets {@code base_attributes} on a newly constructed mob, then fills its health to a new max. Unknown ids and
     * attributes the mob does not have are skipped (both reported at reload).
     */
    public void applyBaseAttributes(LivingEntity entity) {
        boolean maxHealth = false;
        for (Map.Entry<ResourceLocation, Double> entry : baseAttributes.entrySet()) {
            Optional<Holder.Reference<Attribute>> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(entry.getKey());
            AttributeInstance instance = attribute.map(entity::getAttribute).orElse(null);
            if (instance == null) {
                continue;
            }
            instance.setBaseValue(entry.getValue());
            maxHealth |= attribute.get().is(Attributes.MAX_HEALTH);
        }
        if (maxHealth) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    /** Phase index for a health fraction: the last phase whose threshold the mob has dropped below. */
    public int phaseFor(double healthFraction) {
        int index = 0;
        for (int p = 0; p < phases.size(); p++) {
            if (healthFraction <= phases.get(p).belowHealth()) {
                index = p;
            }
        }
        return index;
    }

    /**
     * How the mob positions itself between abilities.
     *
     * @param follow      whom it moves toward: its threat target, or e.g. always the nearest player
     * @param reachHeight how far above its feet it can strike; empty = 85 % of its own height (big mobs reach higher)
     * @param reachDepth  how far below its feet it can strike (into a pit, off a ledge); empty = 1.5 blocks
     * @param reachRule   what makes a target reachable (see {@link ReachRule})
     * @param loseAfter   seconds a target must stay unreachable before the mob gives up on it; empty = 3 s by line of
     *                    sight, 2 s by path
     * @param groundScan  how many blocks below a point telegraphs and ground-relative moves look for the floor (a
     *                    high flyer needs more); deeper than this counts as no ground (the void)
     */
    public record Movement(Style style, double distance, double speed, TargetRule follow, Optional<Double> reachHeight,
                           Optional<Double> reachDepth, ReachRule reachRule, Optional<Double> loseAfter, int groundScan) {
        public static final Movement DEFAULT = new Movement(Style.STANDOFF, 2.0, 1.0, TargetRule.CURRENT,
                Optional.empty(), Optional.empty(), ReachRule.PATH, Optional.empty(), 8);
        public static final Codec<Movement> CODEC = RecordCodecBuilder.create(i -> i.group(
                Style.CODEC.optionalFieldOf("style", Style.STANDOFF).forGetter(Movement::style),
                Codec.DOUBLE.optionalFieldOf("distance", 2.0).forGetter(Movement::distance),
                Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(Movement::speed),
                TargetRule.CODEC.optionalFieldOf("follow", TargetRule.CURRENT).forGetter(Movement::follow),
                Codec.DOUBLE.optionalFieldOf("reach_height").forGetter(Movement::reachHeight),
                Codec.DOUBLE.optionalFieldOf("reach_depth").forGetter(Movement::reachDepth),
                ReachRule.CODEC.optionalFieldOf("reach_rule", ReachRule.PATH).forGetter(Movement::reachRule),
                Codec.doubleRange(0.0, 600.0).optionalFieldOf("lose_after").forGetter(Movement::loseAfter),
                Codec.intRange(1, 384).optionalFieldOf("ground_scan", 8).forGetter(Movement::groundScan)
        ).apply(i, Movement::new));

        /** Seconds a target must stay unreachable before the mob gives up on it. */
        public double loseAfterSeconds() {
            return loseAfter.orElse(reachRule == ReachRule.LINE_OF_SIGHT ? 3.0 : 2.0);
        }

        public enum Style implements StringRepresentable {
            /** Walk up to {@code distance} from the target and hold there, facing it. */
            STANDOFF("standoff"),
            /** Keep about {@code distance} away, backing off when the target closes in. */
            KITE("kite"),
            /** Never move; only turn to face the target. */
            STATIONARY("stationary"),
            /**
             * The host moves and attacks with its own AI between abilities (a vanilla Wither's flight and skulls); the
             * engine only takes over while an ability, a stagger or the walk home runs.
             */
            VANILLA("vanilla");

            public static final Codec<Style> CODEC = StringRepresentable.fromEnum(Style::values);
            private final String name;

            Style(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return name;
            }
        }

        /** When a target counts as reachable; one that is not, for {@code lose_after}, loses aggro. */
        public enum ReachRule implements StringRepresentable {
            /**
             * Inside the reach band or at the end of a full path (ground mobs); flyers and wall climbers always reach.
             */
            PATH("path"),
            /** While the mob can see it (a ranged flyer: hiding behind cover passes aggro on). */
            LINE_OF_SIGHT("line_of_sight");

            public static final Codec<ReachRule> CODEC = StringRepresentable.fromEnum(ReachRule::values);
            private final String name;

            ReachRule(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return name;
            }
        }
    }

    /**
     * One move in the mob's kit: an animation clip plus a timeline of {@link AbilityEvent}s.
     *
     * @param clip       action clip to play; empty = the ability's key
     * @param duration   how long the mob is committed to it (usually the clip length)
     * @param cooldown   time before it can be used again
     * @param weight     relative chance among the abilities usable right now; 0 = never picked on its own (only
     *                   reached by chaining from another ability's event)
     * @param range      distance to the aim target that allows starting it
     * @param target     who it aims at
     * @param trackUntil the mob keeps turning toward its aim until this time; empty = until the first telegraph
     * @param forceAfter once the mob has been fighting this long, it uses the ability as soon as it is off cooldown,
     *                   ignoring range (a fuse)
     * @param aimSide    blocks to the mob's right where its weapon strikes (a right-handed blade); the mob turns so
     *                   that line, not its body centre, meets the target. Events may re-aim per beat.
     */
    public record Ability(
            String clip,
            double duration,
            double cooldown,
            double initialCooldown,
            int weight,
            Range range,
            TargetRule target,
            Optional<Double> trackUntil,
            Optional<Double> forceAfter,
            double aimSide,
            boolean invulnerable,
            List<AbilityEvent> events
    ) {
        public static final Codec<Ability> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("clip", "").forGetter(Ability::clip),
                Codec.DOUBLE.fieldOf("duration").forGetter(Ability::duration),
                Codec.DOUBLE.optionalFieldOf("cooldown", 0.0).forGetter(Ability::cooldown),
                Codec.DOUBLE.optionalFieldOf("initial_cooldown", 0.0).forGetter(Ability::initialCooldown),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Ability::weight),
                Range.CODEC.optionalFieldOf("range", Range.ANY).forGetter(Ability::range),
                TargetRule.CODEC.optionalFieldOf("target", TargetRule.CURRENT).forGetter(Ability::target),
                Codec.DOUBLE.optionalFieldOf("track_until").forGetter(Ability::trackUntil),
                Codec.DOUBLE.optionalFieldOf("force_after").forGetter(Ability::forceAfter),
                Codec.DOUBLE.optionalFieldOf("aim_side", 0.0).forGetter(Ability::aimSide),
                Codec.BOOL.optionalFieldOf("invulnerable", false).forGetter(Ability::invulnerable),
                AbilityEvent.CODEC.listOf().optionalFieldOf("events", List.of()).forGetter(Ability::events)
        ).apply(i, Ability::new));

        public String clipOr(String key) {
            return clip.isEmpty() ? key : clip;
        }
    }

    /**
     * Where the aim must be to start an ability: horizontal distance {@code min..max}, and its feet at most
     * {@code above} blocks over / {@code below} under the mob's feet. Both default to the mob's reach band (see
     * {@link Movement}), so a swing is only started at someone it can actually hit.
     */
    public record Range(double min, double max, Optional<Double> above, Optional<Double> below) {
        public static final Range ANY = new Range(0.0, Double.MAX_VALUE, Optional.empty(), Optional.empty());
        public static final Codec<Range> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("min", 0.0).forGetter(Range::min),
                Codec.DOUBLE.optionalFieldOf("max", Double.MAX_VALUE).forGetter(Range::max),
                Codec.DOUBLE.optionalFieldOf("above").forGetter(Range::above),
                Codec.DOUBLE.optionalFieldOf("below").forGetter(Range::below)
        ).apply(i, Range::new));

        public boolean contains(double distance, double heightDifference, double reachUp, double reachDown) {
            return distance >= min && distance <= max
                    && heightDifference <= above.orElse(reachUp) && heightDifference >= -below.orElse(reachDown);
        }
    }

    /** Who an ability aims at. CURRENT is the threat target (normally the tank). */
    public enum TargetRule implements StringRepresentable {
        CURRENT("current"),
        NEAREST("nearest"),
        FARTHEST("farthest"),
        RANDOM("random");

        public static final Codec<TargetRule> CODEC = StringRepresentable.fromEnum(TargetRule::values);
        private final String name;

        TargetRule(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Helpless window after e.g. a charge into a wall: the clip holds while the mob does nothing. */
    public record Stagger(String clip, double duration) {
        public static final Codec<Stagger> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("clip", "stagger").forGetter(Stagger::clip),
                Codec.DOUBLE.fieldOf("duration").forGetter(Stagger::duration)
        ).apply(i, Stagger::new));
    }

    /**
     * A stage of the fight, active from {@code belowHealth} (fraction of max health) down to the next phase.
     *
     * @param abilities   names usable in this phase; empty = every ability
     * @param transition  played once on entering the phase (not for the first phase)
     * @param cooldowns   per-ability cooldown overrides (seconds)
     * @param readyIn     on entering the phase, these abilities become usable after the given seconds
     *                    (e.g. a second wave of adds 5 s into the phase)
     * @param attackSpeed multiplier on ability timelines and their clips (1.15 = 15 % faster)
     * @param texture     texture variant suffix, e.g. "phase2" -> {@code <mob>_phase2.png}
     * @param bar         boss bar colour / overlay while this phase runs, over {@code boss.bar} (also styles a host's
     *                    own bar, e.g. the Wither's)
     * @param hostOverride the phase's own {@code host} block as written
     * @param host        the host options in effect in this phase: {@code hostOverride} laid over the behaviour's
     *                    {@code host} block (resolved at load); empty = the behaviour's own
     */
    public record Phase(
            double belowHealth,
            List<String> abilities,
            Optional<Ability> transition,
            Map<String, Double> cooldowns,
            Map<String, Double> readyIn,
            double attackSpeed,
            List<DamageTaken> damageTaken,
            Optional<String> texture,
            Optional<Movement> movement,
            Optional<BarStyle> bar,
            Optional<Dynamic<?>> hostOverride,
            Optional<HostOptions> host
    ) {
        public static final Codec<Phase> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0.0, 1.0).fieldOf("below_health").forGetter(Phase::belowHealth),
                Codec.STRING.listOf().optionalFieldOf("abilities", List.of()).forGetter(Phase::abilities),
                Ability.CODEC.optionalFieldOf("transition").forGetter(Phase::transition),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("cooldowns", Map.of()).forGetter(Phase::cooldowns),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("ready_in", Map.of()).forGetter(Phase::readyIn),
                Codec.DOUBLE.optionalFieldOf("attack_speed", 1.0).forGetter(Phase::attackSpeed),
                DamageTaken.CODEC.listOf().optionalFieldOf("damage_taken", List.of()).forGetter(Phase::damageTaken),
                Codec.STRING.optionalFieldOf("texture").forGetter(Phase::texture),
                Movement.CODEC.optionalFieldOf("movement").forGetter(Phase::movement),
                BarStyle.CODEC.optionalFieldOf("bar").forGetter(Phase::bar),
                Codec.PASSTHROUGH.optionalFieldOf("host").forGetter(Phase::hostOverride)
        ).apply(i, (belowHealth, abilities, transition, cooldowns, readyIn, attackSpeed, damageTaken, texture, movement,
                bar, hostOverride) -> new Phase(belowHealth, abilities, transition, cooldowns, readyIn, attackSpeed,
                damageTaken, texture, movement, bar, hostOverride, Optional.empty())));

        /** This phase with its resolved host options. */
        Phase withHost(HostOptions options) {
            return new Phase(belowHealth, abilities, transition, cooldowns, readyIn, attackSpeed, damageTaken, texture,
                    movement, bar, hostOverride, Optional.of(options));
        }

        public boolean allows(String ability) {
            return abilities.isEmpty() || abilities.contains(ability);
        }

        public double damageMultiplier(DamageSource source) {
            double multiplier = 1.0;
            for (DamageTaken rule : damageTaken) {
                if (rule.kind().matches(source)) {
                    multiplier *= rule.multiplier();
                }
            }
            return multiplier;
        }
    }

    public record DamageTaken(Kind kind, double multiplier) {
        public static final Codec<DamageTaken> CODEC = RecordCodecBuilder.create(i -> i.group(
                Kind.CODEC.fieldOf("kind").forGetter(DamageTaken::kind),
                Codec.DOUBLE.fieldOf("multiplier").forGetter(DamageTaken::multiplier)
        ).apply(i, DamageTaken::new));

        public enum Kind implements StringRepresentable {
            /** Direct weapon/fist hits (vanilla player or mob attack damage types). */
            MELEE("melee"),
            PROJECTILE("projectile"),
            /** Everything else: spells, magic, fire, explosions. */
            OTHER("other"),
            ALL("all");

            public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
            private final String name;

            Kind(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return name;
            }

            boolean matches(DamageSource source) {
                boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
                boolean melee = !projectile && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                        || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO));
                return switch (this) {
                    case MELEE -> melee;
                    case PROJECTILE -> projectile;
                    case OTHER -> !melee && !projectile;
                    case ALL -> true;
                };
            }
        }
    }

    /**
     * Boss-only rules.
     *
     * @param leash       distance from its spawn point at which the boss gives up, walks home and resets
     * @param resetAfter  seconds without any target before an engaged boss resets (a wipe)
     * @param softEnrage  after this long in combat its damage is multiplied
     * @param engageOn    who may be its target and so start the fight
     */
    public record Boss(
            Optional<BossBar> bar,
            double leash,
            double resetAfter,
            Optional<SoftEnrage> softEnrage,
            EngageOn engageOn
    ) {
        public static final Codec<Boss> CODEC = RecordCodecBuilder.create(i -> i.group(
                BossBar.CODEC.optionalFieldOf("bar").forGetter(Boss::bar),
                Codec.DOUBLE.optionalFieldOf("leash", 0.0).forGetter(Boss::leash),
                Codec.DOUBLE.optionalFieldOf("reset_after", 15.0).forGetter(Boss::resetAfter),
                SoftEnrage.CODEC.optionalFieldOf("soft_enrage").forGetter(Boss::softEnrage),
                EngageOn.CODEC.optionalFieldOf("engage_on", EngageOn.ANY).forGetter(Boss::engageOn)
        ).apply(i, Boss::new));
    }

    /** Who a boss fights. */
    public enum EngageOn implements StringRepresentable {
        /** Whatever its own AI targets (a vanilla Wither: any living thing that is not undead). */
        ANY("any"),
        /**
         * Players only, plus player-owned summons on its threat table and mobs that hurt it in the last 10 s; cows and
         * endermen never pull it.
         */
        PLAYERS("players");

        public static final Codec<EngageOn> CODEC = StringRepresentable.fromEnum(EngageOn::values);
        private final String name;

        EngageOn(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record BossBar(BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        public static final Codec<BossBar> CODEC = RecordCodecBuilder.create(i -> i.group(
                BossColorCodec.COLOR.optionalFieldOf("color", BossEvent.BossBarColor.RED).forGetter(BossBar::color),
                BossColorCodec.OVERLAY.optionalFieldOf("overlay", BossEvent.BossBarOverlay.PROGRESS).forGetter(BossBar::overlay)
        ).apply(i, BossBar::new));
    }

    /** A phase's bar look: each part set here replaces {@code boss.bar}'s (or the host bar's own) while it runs. */
    public record BarStyle(Optional<BossEvent.BossBarColor> color, Optional<BossEvent.BossBarOverlay> overlay) {
        public static final Codec<BarStyle> CODEC = RecordCodecBuilder.create(i -> i.group(
                BossColorCodec.COLOR.optionalFieldOf("color").forGetter(BarStyle::color),
                BossColorCodec.OVERLAY.optionalFieldOf("overlay").forGetter(BarStyle::overlay)
        ).apply(i, BarStyle::new));
    }

    public record SoftEnrage(double after, double damageMultiplier, Optional<String> clip) {
        public static final Codec<SoftEnrage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("after").forGetter(SoftEnrage::after),
                Codec.DOUBLE.optionalFieldOf("damage_multiplier", 2.0).forGetter(SoftEnrage::damageMultiplier),
                Codec.STRING.optionalFieldOf("clip").forGetter(SoftEnrage::clip)
        ).apply(i, SoftEnrage::new));
    }

    /** Codecs for the vanilla boss bar enums, by their lowercase names ("red", "notched_10"). */
    static final class BossColorCodec {
        static final Codec<BossEvent.BossBarColor> COLOR = Codec.STRING.comapFlatMap(
                name -> {
                    BossEvent.BossBarColor color = BossEvent.BossBarColor.byName(name);
                    return color.getName().equals(name) ? DataResult.success(color) : DataResult.error(() -> "Unknown boss bar color " + name);
                }, BossEvent.BossBarColor::getName);
        static final Codec<BossEvent.BossBarOverlay> OVERLAY = Codec.STRING.comapFlatMap(
                name -> {
                    BossEvent.BossBarOverlay overlay = BossEvent.BossBarOverlay.byName(name);
                    return overlay.getName().equals(name) ? DataResult.success(overlay) : DataResult.error(() -> "Unknown boss bar overlay " + name);
                }, BossEvent.BossBarOverlay::getName);

        private BossColorCodec() {
        }
    }
}
