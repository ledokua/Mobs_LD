package net.ledok.mobs_ld.mob.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.event.AbilityEvent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The reloadable half of a mob definition: how it moves, what it can do and, for bosses, how the fight evolves.
 * One file per entity type at {@code data/<namespace>/mob_behaviour/<path>.json} (see docs/behaviour.md).
 * All times are seconds, as in the art hand-off notes; distances are blocks.
 */
public record MobBehaviour(
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
        return DataResult.success(behaviour);
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
     */
    public record Movement(Style style, double distance, double speed, TargetRule follow, Optional<Double> reachHeight,
                           Optional<Double> reachDepth) {
        public static final Movement DEFAULT = new Movement(Style.STANDOFF, 2.0, 1.0, TargetRule.CURRENT,
                Optional.empty(), Optional.empty());
        public static final Codec<Movement> CODEC = RecordCodecBuilder.create(i -> i.group(
                Style.CODEC.optionalFieldOf("style", Style.STANDOFF).forGetter(Movement::style),
                Codec.DOUBLE.optionalFieldOf("distance", 2.0).forGetter(Movement::distance),
                Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(Movement::speed),
                TargetRule.CODEC.optionalFieldOf("follow", TargetRule.CURRENT).forGetter(Movement::follow),
                Codec.DOUBLE.optionalFieldOf("reach_height").forGetter(Movement::reachHeight),
                Codec.DOUBLE.optionalFieldOf("reach_depth").forGetter(Movement::reachDepth)
        ).apply(i, Movement::new));

        public enum Style implements StringRepresentable {
            /** Walk up to {@code distance} from the target and hold there, facing it. */
            STANDOFF("standoff"),
            /** Keep about {@code distance} away, backing off when the target closes in. */
            KITE("kite"),
            /** Never move; only turn to face the target. */
            STATIONARY("stationary");

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
            Optional<Movement> movement
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
                Movement.CODEC.optionalFieldOf("movement").forGetter(Phase::movement)
        ).apply(i, Phase::new));

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
     */
    public record Boss(
            Optional<BossBar> bar,
            double leash,
            double resetAfter,
            Optional<SoftEnrage> softEnrage
    ) {
        public static final Codec<Boss> CODEC = RecordCodecBuilder.create(i -> i.group(
                BossBar.CODEC.optionalFieldOf("bar").forGetter(Boss::bar),
                Codec.DOUBLE.optionalFieldOf("leash", 0.0).forGetter(Boss::leash),
                Codec.DOUBLE.optionalFieldOf("reset_after", 15.0).forGetter(Boss::resetAfter),
                SoftEnrage.CODEC.optionalFieldOf("soft_enrage").forGetter(Boss::softEnrage)
        ).apply(i, Boss::new));
    }

    public record BossBar(BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        public static final Codec<BossBar> CODEC = RecordCodecBuilder.create(i -> i.group(
                BossColorCodec.COLOR.optionalFieldOf("color", BossEvent.BossBarColor.RED).forGetter(BossBar::color),
                BossColorCodec.OVERLAY.optionalFieldOf("overlay", BossEvent.BossBarOverlay.PROGRESS).forGetter(BossBar::overlay)
        ).apply(i, BossBar::new));
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
