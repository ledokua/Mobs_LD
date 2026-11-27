package net.ledok.mobs_ld.mob.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.ledok.mobs_ld.mob.behaviour.event.FxEvent;
import net.ledok.mobs_ld.mob.behaviour.event.HitEvent;
import net.ledok.mobs_ld.mob.behaviour.event.SandstoneSpikesEvent;
import net.ledok.mobs_ld.mob.combat.SlotPicker;
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
import java.util.function.Predicate;

/**
 * The reloadable half of a mob definition: how it moves, what it can do and, for bosses, how the fight evolves.
 * One file per entity type at {@code data/<namespace>/mob_behaviour/<path>.json} (see docs/behaviour.md).
 * All times are seconds, as in the art hand-off notes; distances are blocks.
 *
 * @param baseAttributes attribute id -> base value, set on every new mob of the type as it is constructed (before
 *                       its own constructor finishes and before other mods scale it); saved mobs keep their values
 * @param referenceAttributes attribute id -> the base value the file's numbers are written against, never applied:
 *                       read after {@code base_attributes} as the base of R ({@code hit.amount}) and of summon
 *                       {@code scale} ratios, for a host whose stats come from elsewhere (an Arenas_LD Warden)
 * @param host           how the engine runs on a vanilla host (its adapter's options; an off switch)
 * @param targetSlots    secondary targets by name (e.g. the Wither's side heads), see {@link TargetSlot}
 */
public record MobBehaviour(
        Map<ResourceLocation, Double> baseAttributes,
        Map<ResourceLocation, Double> referenceAttributes,
        HostSpec host,
        Map<String, TargetSlot> targetSlots,
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
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("reference_attributes", Map.of()).forGetter(MobBehaviour::referenceAttributes),
            HostSpec.CODEC.optionalFieldOf("host", HostSpec.NONE).forGetter(MobBehaviour::host),
            Codec.unboundedMap(Codec.STRING, TargetSlot.CODEC).optionalFieldOf("target_slots", Map.of()).forGetter(MobBehaviour::targetSlots),
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
        referenceAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(referenceAttributes));
        targetSlots = Collections.unmodifiableMap(new LinkedHashMap<>(targetSlots));
        abilities = new LinkedHashMap<>(abilities);
        List<Phase> sorted = new ArrayList<>(phases);
        sorted.sort((a, b) -> Double.compare(b.belowHealth(), a.belowHealth()));
        phases = List.copyOf(sorted);
    }

    private static DataResult<MobBehaviour> validate(MobBehaviour behaviour) {
        if (!behaviour.host().onlyTagged().isEmpty() && !behaviour.baseAttributes().isEmpty()) {
            // base_attributes are set inside the mob's constructor, before any tag exists: they would reach every mob of
            // the type, also the ones only_tagged keeps vanilla.
            return DataResult.error(() -> "host.only_tagged cannot be combined with base_attributes (they are set as "
                    + "every mob of the type is built, before it carries any tag); declare the numbers' base in "
                    + "reference_attributes instead");
        }
        List<Ability> all = new ArrayList<>(behaviour.abilities().values());
        behaviour.intro().ifPresent(all::add);
        behaviour.onDeath().ifPresent(all::add);
        behaviour.phases().forEach(phase -> phase.transition().ifPresent(all::add));
        Map<String, SkullSpec> skulls = behaviour.host().options().map(HostOptions::skulls).orElse(Map.of());
        for (Ability ability : all) {
            for (String prerequisite : ability.afterAbilities()) {
                if (!behaviour.abilities().containsKey(prerequisite)) {
                    return DataResult.error(() -> "An ability's after_abilities names unknown ability '" + prerequisite + "'");
                }
            }
            String slotError = behaviour.unknownSlot(ability.target(), "An ability aims at");
            if (slotError != null) {
                return DataResult.error(() -> slotError);
            }
            String alongError = unmatchedAlong(ability);
            if (alongError != null) {
                return DataResult.error(() -> alongError);
            }
            for (AbilityEvent event : ability.events()) {
                if (event instanceof SandstoneSpikesEvent spikes && ability.events().stream().noneMatch(spikes::matches)) {
                    return DataResult.error(() -> "A sandstone_spikes event at " + spikes.at()
                            + " requires a rectangular hit at the same time");
                }
                for (String chained : event.chains()) {
                    if (!behaviour.abilities().containsKey(chained)) {
                        return DataResult.error(() -> "A " + event.type() + " event chains into unknown ability '" + chained + "'");
                    }
                }
                for (String slot : event.slots()) {
                    if (!behaviour.targetSlots().containsKey(slot)) {
                        return DataResult.error(() -> "A " + event.type() + " event names unknown target slot '" + slot
                                + "' (target_slots has " + behaviour.targetSlots().keySet() + ")");
                    }
                }
                for (String skull : event.skulls()) {
                    if (!skulls.containsKey(skull)) {
                        return DataResult.error(() -> "A " + event.type() + " event fires unknown skull '" + skull
                                + "' (the host block's skulls are " + skulls.keySet() + ")");
                    }
                }
            }
        }
        for (String name : behaviour.targetSlots().keySet()) {
            Optional<String> lead = behaviour.leadSlot(name);
            TargetSlot slot = behaviour.targetSlots().get(name);
            if (lead.isPresent() && slot.offset() >= behaviour.targetSlots().get(lead.get()).repick().min()) {
                return DataResult.error(() -> "Target slot '" + name + "' re-picks " + slot.offset() + " s after '"
                        + lead.get() + "', which re-picks every " + behaviour.targetSlots().get(lead.get()).repick()
                        + ": the offset must be shorter than that");
            }
        }
        List<Movement> movements = new ArrayList<>();
        movements.add(behaviour.movement());
        behaviour.phases().forEach(phase -> phase.movement().ifPresent(movements::add));
        for (Movement movement : movements) {
            String slotError = behaviour.unknownSlot(movement.follow(), "Movement follows");
            if (slotError != null) {
                return DataResult.error(() -> slotError);
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

    /**
     * The slot that target slot {@code name} keeps its {@code offset} behind: the first slot listed without an offset;
     * empty for a slot without an offset (or when every slot has one).
     */
    public Optional<String> leadSlot(String name) {
        TargetSlot slot = targetSlots.get(name);
        if (slot == null || slot.offset() <= 0.0) {
            return Optional.empty();
        }
        for (Map.Entry<String, TargetSlot> entry : targetSlots.entrySet()) {
            if (entry.getValue().offset() <= 0.0 && !entry.getKey().equals(name)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /**
     * An error message if one of the ability's {@code fx} events sends particles {@code along} the hit zones of its
     * beat but no {@code hit} event lands at that time (nothing to draw along), else null.
     */
    private static String unmatchedAlong(Ability ability) {
        for (AbilityEvent event : ability.events()) {
            if (event instanceof FxEvent fx && fx.particles().filter(p -> p.at() == FxEvent.Where.ALONG).isPresent()
                    && ability.events().stream().noneMatch(other -> other instanceof HitEvent hit
                    && FxEvent.sameMoment(hit.at(), fx.at()))) {
                return "An fx event at " + fx.at() + " sends particles along the hit zones of its beat, but no hit "
                        + "event lands at " + fx.at();
            }
        }
        return null;
    }

    /** An error message if {@code rule} names a target slot the behaviour does not define, else null. */
    private String unknownSlot(TargetRule rule, String what) {
        return rule.slot().filter(slot -> !targetSlots.containsKey(slot))
                .map(slot -> what + " unknown target slot '" + slot + "' (target_slots has " + targetSlots.keySet() + ")")
                .orElse(null);
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
        return DataResult.success(new MobBehaviour(behaviour.baseAttributes(), behaviour.referenceAttributes(), behaviour.host(),
                behaviour.targetSlots(), behaviour.movement(),
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
     * The base value of attribute {@code id} the file's numbers are written against: its {@code base_attributes} entry,
     * else its {@code reference_attributes} entry; empty when it declares neither.
     */
    public Optional<Double> declaredBase(ResourceLocation id) {
        Double base = baseAttributes.get(id);
        return Optional.ofNullable(base != null ? base : referenceAttributes.get(id));
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
     * @param hoverHeight for flying mobs, blocks above the followed target's feet to cruise and hold at between
     *                    abilities; empty = ground-level pathing
     */
    public record Movement(Style style, double distance, double speed, TargetRule follow, Optional<Double> reachHeight,
                           Optional<Double> reachDepth, ReachRule reachRule, Optional<Double> loseAfter, int groundScan,
                           Optional<Double> hoverHeight) {
        public static final Movement DEFAULT = new Movement(Style.STANDOFF, 2.0, 1.0, TargetRule.CURRENT,
                Optional.empty(), Optional.empty(), ReachRule.PATH, Optional.empty(), 8, Optional.empty());
        public static final Codec<Movement> CODEC = RecordCodecBuilder.create(i -> i.group(
                Style.CODEC.optionalFieldOf("style", Style.STANDOFF).forGetter(Movement::style),
                Codec.DOUBLE.optionalFieldOf("distance", 2.0).forGetter(Movement::distance),
                Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(Movement::speed),
                TargetRule.CODEC.optionalFieldOf("follow", TargetRule.CURRENT).forGetter(Movement::follow),
                Codec.DOUBLE.optionalFieldOf("reach_height").forGetter(Movement::reachHeight),
                Codec.DOUBLE.optionalFieldOf("reach_depth").forGetter(Movement::reachDepth),
                ReachRule.CODEC.optionalFieldOf("reach_rule", ReachRule.PATH).forGetter(Movement::reachRule),
                Codec.doubleRange(0.0, 600.0).optionalFieldOf("lose_after").forGetter(Movement::loseAfter),
                Codec.intRange(1, 384).optionalFieldOf("ground_scan", 8).forGetter(Movement::groundScan),
                Codec.doubleRange(0.0, 32.0).optionalFieldOf("hover_height").forGetter(Movement::hoverHeight)
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
     * @param cooldown   time before it can be used again (a range is rolled at each use)
     * @param initialCooldown after engaging, time before its first use (a range is rolled at each engage)
     * @param weight     relative chance among the abilities usable right now; 0 = never picked on its own (only
     *                   reached by chaining from another ability's event)
     * @param range      distance to the aim target that allows starting it
     * @param target     who it aims at
     * @param trackUntil the mob keeps turning toward its aim until this time; empty = until the first telegraph
     * @param forceAfter once the mob has been fighting this long, it uses the ability as soon as it is off cooldown,
     *                   ignoring range (a fuse)
     * @param aimSide    blocks to the mob's right where its weapon strikes (a right-handed blade); the mob turns so
     *                   that line, not its body centre, meets the target. Events may re-aim per beat.
     * @param skipWhenFull not picked (nor forced) while none of its {@code summon} events has room under its
     *                   {@code max_alive}; it stays ready and goes as soon as an add dies
     * @param fixedSpeed   its timeline (and clip) ignores the phase's {@code attack_speed}: for an ability timed to a
     *                     host clip that cannot play faster (the Warden's)
     * @param cooldownGroup abilities naming the same group share one cooldown: using one puts all of them on the
     *                     cooldown it rolled (its own, or the phase's override for it)
     * @param noRepeat     not picked right after itself while another ability can be picked
     * @param afterAbilities only picked once at least one of these named abilities completes in the current fight
     * @param minPlayers   only usable while at least this many players fight the mob ({@link
     *                     net.ledok.mobs_ld.mob.combat.Fighters}); empty = any number
     * @param maxPlayers   only usable while at most this many players fight it; empty = any number
     * @param when         extra conditions on the players around it (see {@link When})
     * @param includeUnreachable its target rule may also pick players the reach rule gave up on (a ranged attack at a
     *                     player on a ledge); {@code current} and {@code slot:} rules are not affected
     */
    public record Ability(
            String clip,
            double duration,
            Seconds cooldown,
            Seconds initialCooldown,
            int weight,
            Range range,
            TargetRule target,
            Optional<Double> trackUntil,
            Optional<Double> forceAfter,
            double aimSide,
            boolean invulnerable,
            boolean skipWhenFull,
            boolean fixedSpeed,
            Optional<String> cooldownGroup,
            boolean noRepeat,
            List<String> afterAbilities,
            Optional<Integer> minPlayers,
            Optional<Integer> maxPlayers,
            Optional<When> when,
            boolean includeUnreachable,
            List<String> onlyTagged,
            List<AbilityEvent> events
    ) {
        /**
         * The Warden rework's fields and later ones, decoded from the same JSON object as the rest (the format stays
         * flat): a separate group because one RecordCodecBuilder takes at most 16 fields. New optional ability fields
         * go here (or into a third group like it).
         */
        private record Scheduling(boolean fixedSpeed, Optional<String> cooldownGroup, boolean noRepeat,
                                  List<String> afterAbilities) {
            static final MapCodec<Scheduling> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.BOOL.optionalFieldOf("fixed_speed", false).forGetter(Scheduling::fixedSpeed),
                    Codec.STRING.optionalFieldOf("cooldown_group").forGetter(Scheduling::cooldownGroup),
                    Codec.BOOL.optionalFieldOf("no_repeat", false).forGetter(Scheduling::noRepeat),
                    Codec.STRING.listOf().optionalFieldOf("after_abilities", List.of()).forGetter(Scheduling::afterAbilities)
            ).apply(i, Scheduling::new));

            static Scheduling of(Ability ability) {
                return new Scheduling(ability.fixedSpeed(), ability.cooldownGroup(), ability.noRepeat(), ability.afterAbilities());
            }
        }

        /** The Warden rework's M3 conditions: who must be fighting, and where, for the ability to be usable. */
        private record Conditions(Optional<Integer> minPlayers, Optional<Integer> maxPlayers, Optional<When> when,
                                  boolean includeUnreachable, List<String> onlyTagged) {
            static final MapCodec<Conditions> CODEC = RecordCodecBuilder.<Conditions>mapCodec(i -> i.group(
                    Codec.intRange(0, 64).optionalFieldOf("min_players").forGetter(Conditions::minPlayers),
                    Codec.intRange(0, 64).optionalFieldOf("max_players").forGetter(Conditions::maxPlayers),
                    When.CODEC.optionalFieldOf("when").forGetter(Conditions::when),
                    Codec.BOOL.optionalFieldOf("include_unreachable", false).forGetter(Conditions::includeUnreachable),
                    Codec.STRING.listOf().optionalFieldOf("only_tagged", List.of()).forGetter(Conditions::onlyTagged)
            ).apply(i, Conditions::new)).validate(c -> c.minPlayers().isPresent() && c.maxPlayers().isPresent()
                    && c.maxPlayers().get() < c.minPlayers().get()
                    ? DataResult.error(() -> "An ability's max_players (" + c.maxPlayers().get() + ") is below its "
                    + "min_players (" + c.minPlayers().get() + ")")
                    : DataResult.success(c));

            static Conditions of(Ability ability) {
                return new Conditions(ability.minPlayers(), ability.maxPlayers(), ability.when(),
                        ability.includeUnreachable(), ability.onlyTagged());
            }
        }

        /** The fields the format had before the Warden rework. */
        private record Core(String clip, double duration, Seconds cooldown, Seconds initialCooldown, int weight,
                            Range range, TargetRule target, Optional<Double> trackUntil, Optional<Double> forceAfter,
                            double aimSide, boolean invulnerable, boolean skipWhenFull, List<AbilityEvent> events) {
            static final MapCodec<Core> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("clip", "").forGetter(Core::clip),
                Codec.DOUBLE.fieldOf("duration").forGetter(Core::duration),
                Seconds.CODEC.optionalFieldOf("cooldown", Seconds.of(0.0)).forGetter(Core::cooldown),
                Seconds.CODEC.optionalFieldOf("initial_cooldown", Seconds.of(0.0)).forGetter(Core::initialCooldown),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Core::weight),
                Range.CODEC.optionalFieldOf("range", Range.ANY).forGetter(Core::range),
                TargetRule.CODEC.optionalFieldOf("target", TargetRule.CURRENT).forGetter(Core::target),
                Codec.DOUBLE.optionalFieldOf("track_until").forGetter(Core::trackUntil),
                Codec.DOUBLE.optionalFieldOf("force_after").forGetter(Core::forceAfter),
                Codec.DOUBLE.optionalFieldOf("aim_side", 0.0).forGetter(Core::aimSide),
                Codec.BOOL.optionalFieldOf("invulnerable", false).forGetter(Core::invulnerable),
                Codec.BOOL.optionalFieldOf("skip_when_full", false).forGetter(Core::skipWhenFull),
                AbilityEvent.CODEC.listOf().optionalFieldOf("events", List.of()).forGetter(Core::events)
            ).apply(i, Core::new));

            static Core of(Ability a) {
                return new Core(a.clip(), a.duration(), a.cooldown(), a.initialCooldown(), a.weight(), a.range(),
                        a.target(), a.trackUntil(), a.forceAfter(), a.aimSide(), a.invulnerable(), a.skipWhenFull(),
                        a.events());
            }
        }

        public static final Codec<Ability> CODEC = RecordCodecBuilder.create(i -> i.group(
                Core.CODEC.forGetter(Core::of),
                Scheduling.CODEC.forGetter(Scheduling::of),
                Conditions.CODEC.forGetter(Conditions::of)
        ).apply(i, (core, scheduling, conditions) -> new Ability(core.clip(), core.duration(), core.cooldown(),
                core.initialCooldown(), core.weight(), core.range(), core.target(), core.trackUntil(),
                core.forceAfter(), core.aimSide(), core.invulnerable(), core.skipWhenFull(), scheduling.fixedSpeed(),
                scheduling.cooldownGroup(), scheduling.noRepeat(), scheduling.afterAbilities(), conditions.minPlayers(), conditions.maxPlayers(),
                conditions.when(), conditions.includeUnreachable(), conditions.onlyTagged(), core.events())));

        /**
         * Whether a mob with these entity tags may use it ({@code only_tagged}: at least one of them; empty = any mob),
         * e.g. {@code ["arenas_ld_run_mob"]} for a move only an Arenas_LD run's boss has.
         */
        public boolean allowsTags(java.util.Set<String> tags) {
            if (onlyTagged.isEmpty()) {
                return true;
            }
            for (String tag : onlyTagged) {
                if (tags.contains(tag)) {
                    return true;
                }
            }
            return false;
        }

        public String clipOr(String key) {
            return clip.isEmpty() ? key : clip;
        }

        /** Whether {@code fighters} fighting players satisfy {@code min_players} / {@code max_players}. */
        public boolean allowsPlayers(int fighters) {
            return fighters >= minPlayers.orElse(0) && fighters <= maxPlayers.orElse(Integer.MAX_VALUE);
        }

        /** Whether either player-count bound is set (so the fighters must be counted). */
        public boolean countsPlayers() {
            return minPlayers.isPresent() || maxPlayers.isPresent();
        }

        /** Its {@code when.behind} rule, if any. */
        public Optional<Behind> behind() {
            return when.flatMap(When::behind);
        }
    }

    /** Conditions on the players around the mob that an ability needs (all must hold). */
    public record When(Optional<Behind> behind) {
        public static final Codec<When> CODEC = RecordCodecBuilder.create(i -> i.group(
                Behind.CODEC.optionalFieldOf("behind").forGetter(When::behind)
        ).apply(i, When::new));
    }

    /**
     * At least {@code players} fighting players have each stood behind the mob (within {@code range} blocks, the rear
     * {@code angle} degrees, the whole width, centred on its back, and {@code height} blocks above or below its feet,
     * so a player on a ledge does not count) for {@code seconds} without a break.
     */
    public record Behind(int players, double seconds, double range, double angle, double height) {
        public static final Codec<Behind> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 64).optionalFieldOf("players", 1).forGetter(Behind::players),
                Codec.doubleRange(0.0, 600.0).optionalFieldOf("for", 0.0).forGetter(Behind::seconds),
                Codec.doubleRange(0.5, 64.0).optionalFieldOf("range", 5.0).forGetter(Behind::range),
                Codec.doubleRange(1.0, 360.0).optionalFieldOf("angle", 120.0).forGetter(Behind::angle),
                Codec.doubleRange(0.0, 64.0).optionalFieldOf("height", 3.0).forGetter(Behind::height)
        ).apply(i, Behind::new));
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

    /**
     * Who an ability aims at (or movement follows): {@code current} (the threat target, normally the tank),
     * {@code nearest}, {@code farthest}, {@code random} player, {@code lowest_threat} (the player with the least threat;
     * ties prefer someone other than the tank), {@code top_damage} (whoever dealt it the most damage in the last 10 s;
     * else the threat top), {@code slot:<name>} (whoever that target slot holds now), or {@code summoner} (the
     * living owner of a bound add).
     *
     * @param slot the slot's name, for {@link Kind#SLOT}
     */
    public record TargetRule(Kind kind, Optional<String> slot) {
        public static final TargetRule CURRENT = new TargetRule(Kind.CURRENT, Optional.empty());
        public static final TargetRule NEAREST = new TargetRule(Kind.NEAREST, Optional.empty());
        public static final TargetRule FARTHEST = new TargetRule(Kind.FARTHEST, Optional.empty());
        public static final TargetRule RANDOM = new TargetRule(Kind.RANDOM, Optional.empty());
        public static final TargetRule LOWEST_THREAT = new TargetRule(Kind.LOWEST_THREAT, Optional.empty());
        public static final TargetRule TOP_DAMAGE = new TargetRule(Kind.TOP_DAMAGE, Optional.empty());
        private static final String SLOT_PREFIX = "slot:";

        public static final Codec<TargetRule> CODEC = Codec.STRING.comapFlatMap(TargetRule::parse, TargetRule::serialized);

        public static TargetRule slot(String name) {
            return new TargetRule(Kind.SLOT, Optional.of(name));
        }

        private static DataResult<TargetRule> parse(String text) {
            if (text.startsWith(SLOT_PREFIX)) {
                String name = text.substring(SLOT_PREFIX.length());
                return name.isEmpty() ? DataResult.error(() -> "'slot:' needs a target slot name, e.g. slot:right_head")
                        : DataResult.success(slot(name));
            }
            for (Kind kind : Kind.values()) {
                if (kind != Kind.SLOT && kind.name.equals(text)) {
                    return DataResult.success(new TargetRule(kind, Optional.empty()));
                }
            }
            return DataResult.error(() -> "Unknown target '" + text
                    + "' (expected current, nearest, farthest, random, lowest_threat, top_damage, slot:<name> or summoner)");
        }

        public String serialized() {
            return kind == Kind.SLOT ? SLOT_PREFIX + slot.orElse("") : kind.name;
        }

        public enum Kind {
            CURRENT("current"),
            NEAREST("nearest"),
            FARTHEST("farthest"),
            RANDOM("random"),
            LOWEST_THREAT("lowest_threat"),
            TOP_DAMAGE("top_damage"),
            SLOT("slot"),
            SUMMONER("summoner");

            private final String name;

            Kind(String name) {
                this.name = name;
            }
        }
    }

    /**
     * A secondary target the mob keeps besides its main one (the Wither's side heads), picked by a rule from the
     * players around it and re-picked on a timer or as soon as its holder is lost. The host adapter decides how a
     * slot shows (the Wither's {@code right_head} / {@code left_head} turn those heads); abilities, {@code look} and
     * {@code projectile} events can aim at it as {@code slot:<name>}.
     *
     * @param rule        how it picks (see {@link SlotPicker})
     * @param range       candidates within this many blocks of the mob
     * @param lineOfSight candidates must be in sight of the slot's head (and a holder out of sight for
     *                    {@code lostAfter} seconds is replaced)
     * @param repick      picks again this often (rolled per pick)
     * @param offset      keeps its re-picks this long after those of the first slot listed without an offset (its
     *                    lead), for the whole fight, so the two never switch together; must be shorter than the
     *                    lead's {@code repick}. Without a lead it only delays the first scheduled re-pick
     * @param lostAfter   seconds out of sight before the holder is replaced
     */
    public record TargetSlot(SlotPicker.Rule rule, double range, boolean lineOfSight, Seconds repick, double offset,
                             double lostAfter) {
        /** Shortest re-pick period (each pick casts a sight line to every player in range). */
        private static final double MIN_REPICK = 0.5;

        private static final Codec<SlotPicker.Rule> RULE = Codec.STRING.comapFlatMap(name -> {
            SlotPicker.Rule rule = SlotPicker.Rule.byName(name);
            return rule != null ? DataResult.success(rule)
                    : DataResult.error(() -> "Unknown target slot rule '" + name + "' (expected lowest_threat or spread)");
        }, SlotPicker.Rule::serializedName);

        public static final Codec<TargetSlot> CODEC = RecordCodecBuilder.<TargetSlot>create(i -> i.group(
                RULE.fieldOf("rule").forGetter(TargetSlot::rule),
                Codec.doubleRange(1.0, 128.0).optionalFieldOf("range", 30.0).forGetter(TargetSlot::range),
                Codec.BOOL.optionalFieldOf("line_of_sight", true).forGetter(TargetSlot::lineOfSight),
                Seconds.CODEC.optionalFieldOf("repick", Seconds.of(10.0)).forGetter(TargetSlot::repick),
                Codec.doubleRange(0.0, 600.0).optionalFieldOf("offset", 0.0).forGetter(TargetSlot::offset),
                Codec.doubleRange(0.0, 600.0).optionalFieldOf("lost_after", 2.0).forGetter(TargetSlot::lostAfter)
        ).apply(i, TargetSlot::new)).validate(slot -> slot.repick().min() < MIN_REPICK
                ? DataResult.error(() -> "A target slot re-picks at least every " + MIN_REPICK + " s, got " + slot.repick())
                : DataResult.success(slot));
    }

    /** Helpless window after e.g. a charge into a wall: the clip holds while the mob does nothing. */
    /**
     * The helpless window after a dash hits a wall.
     *
     * @param recover seconds after the window during which no ability starts (the mob moves on its own again), so a
     *                ready ability does not fire the moment it gets up; 0 = at once, as before
     */
    public record Stagger(String clip, double duration, double recover) {
        public static final Codec<Stagger> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("clip", "stagger").forGetter(Stagger::clip),
                Codec.DOUBLE.fieldOf("duration").forGetter(Stagger::duration),
                Codec.doubleRange(0.0, 60.0).optionalFieldOf("recover", 0.0).forGetter(Stagger::recover)
        ).apply(i, Stagger::new));
    }

    /**
     * A stage of the fight, active from {@code belowHealth} (fraction of max health) down to the next phase.
     *
     * @param abilities   names usable in this phase; empty = every ability
     * @param transition  played once on entering the phase (not for the first phase)
     * @param cooldowns   per-ability cooldown overrides (seconds; a range is rolled at each use)
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
            Map<String, Seconds> cooldowns,
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
                Codec.unboundedMap(Codec.STRING, Seconds.CODEC).optionalFieldOf("cooldowns", Map.of()).forGetter(Phase::cooldowns),
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

        /**
         * The product of the {@code damage_taken} rules that match {@code source}; a rule with {@code while_adds} only
         * while {@code liveAdd} says the mob has a live add of that entity type.
         */
        /** Whether a {@code while_adds} rule (an add shield) is what takes this source's damage to 0 now. */
        public boolean shieldedByAdds(DamageSource source, Predicate<ResourceLocation> liveAdd) {
            for (DamageTaken rule : damageTaken) {
                if (rule.multiplier() <= 0.0 && rule.whileAdds().isPresent() && rule.kind().matches(source)
                        && liveAdd.test(rule.whileAdds().get())) {
                    return true;
                }
            }
            return false;
        }

        public double damageMultiplier(DamageSource source, Predicate<ResourceLocation> liveAdd) {
            double multiplier = 1.0;
            for (DamageTaken rule : damageTaken) {
                if (rule.kind().matches(source) && rule.whileAdds().map(liveAdd::test).orElse(true)) {
                    multiplier *= rule.multiplier();
                }
            }
            return multiplier;
        }
    }

    /**
     * @param whileAdds only while one of the mob's own adds (its summons) of this entity type is alive (the Warden's
     *                  Resonators halve its damage taken while any stands); empty = always
     */
    public record DamageTaken(Kind kind, double multiplier, Optional<ResourceLocation> whileAdds) {
        public static final Codec<DamageTaken> CODEC = RecordCodecBuilder.create(i -> i.group(
                Kind.CODEC.fieldOf("kind").forGetter(DamageTaken::kind),
                Codec.DOUBLE.fieldOf("multiplier").forGetter(DamageTaken::multiplier),
                ResourceLocation.CODEC.optionalFieldOf("while_adds").forGetter(DamageTaken::whileAdds)
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
     * @param wardDamage  outgoing damage bonus while enough linked adds are actively warding it
     * @param recovery    seconds the boss holds position after a named ability or chain ends
     */
    public record Boss(
            Optional<BossBar> bar,
            double leash,
            double resetAfter,
            Optional<SoftEnrage> softEnrage,
            EngageOn engageOn,
            Optional<WardDamage> wardDamage,
            double recovery
    ) {
        public static final Codec<Boss> CODEC = RecordCodecBuilder.create(i -> i.group(
                BossBar.CODEC.optionalFieldOf("bar").forGetter(Boss::bar),
                Codec.DOUBLE.optionalFieldOf("leash", 0.0).forGetter(Boss::leash),
                Codec.DOUBLE.optionalFieldOf("reset_after", 15.0).forGetter(Boss::resetAfter),
                SoftEnrage.CODEC.optionalFieldOf("soft_enrage").forGetter(Boss::softEnrage),
                EngageOn.CODEC.optionalFieldOf("engage_on", EngageOn.ANY).forGetter(Boss::engageOn),
                WardDamage.CODEC.optionalFieldOf("ward_damage").forGetter(Boss::wardDamage),
                Codec.doubleRange(0.0, 60.0).optionalFieldOf("recovery", 0.0).forGetter(Boss::recovery)
        ).apply(i, Boss::new));
    }

    public record WardDamage(int minWards, double multiplier) {
        public static final Codec<WardDamage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 256).fieldOf("min_wards").forGetter(WardDamage::minWards),
                Codec.doubleRange(1.0, 100.0).fieldOf("multiplier").forGetter(WardDamage::multiplier)
        ).apply(i, WardDamage::new));
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
