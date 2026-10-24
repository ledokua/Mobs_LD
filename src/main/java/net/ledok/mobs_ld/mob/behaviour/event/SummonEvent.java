package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.threat.ThreatHolder;
import net.ledok.mobs_ld.entity.threat.ThreatTargetGoal;
import net.ledok.mobs_ld.mixin.MobGoalsAccessor;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.Fighters;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.ledok.mobs_ld.mob.combat.RuneSpots;
import net.ledok.mobs_ld.mob.combat.SummonBond;
import net.ledok.mobs_ld.mob.combat.SummonStats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * Spawns adds. With only the original fields ({@code count} as a number, the offsets, {@code spread},
 * {@code max_alive} as a number) it works as summons always did: {@code count} entities at an offset from the mob
 * (forward/up/right in blocks, along its facing) with up to {@code spread} blocks of random scatter, at {@code at},
 * while fewer than {@code max_alive} of its adds live. Summoned mobs start on the summoner's aim target and are removed
 * when the summoner dies or resets. Spawning happens inside the summoner's own tick, which is how Arenas_LD recognises
 * minions of a run boss.
 *
 * <p>Every other field is optional and off by default (see docs/behaviour.md):
 * <ul>
 *   <li>{@code telegraph}: a rune (a circle) at each add's spot from {@code telegraph.from}, the adds rising at
 *       {@code at}; the wave (how many, where) is decided when the runes appear;</li>
 *   <li>{@code count} / {@code max_alive} as {@link Count} / {@link MaxAlive}: growing with the players fighting the
 *       mob ({@link Fighters});</li>
 *   <li>{@code placement}: spots on valid ground around the mob, its aim or each fighter ({@link Placement},
 *       {@link RuneSpots});</li>
 *   <li>{@code scale}: stats from the summoner's ({@link SummonStats});</li>
 *   <li>{@code loot} / {@code xp} false: the adds drop nothing (tags, so it survives saves);</li>
 *   <li>{@code bound}: the adds leave with their summoner even when it cannot reach them ({@link SummonBond});</li>
 *   <li>{@code target: rune_player}: each add first goes for the player its rune was placed for.</li>
 * </ul>
 *
 * @param maxAlive no more spawn while this many of the summoner's adds are still alive
 */
public record SummonEvent(double at, ResourceLocation entity, Count count, double forward, double up, double right,
                          double spread, MaxAlive maxAlive, Optional<Runes> telegraph, Optional<Placement> placement,
                          Optional<Scale> scale, boolean loot, boolean xp, boolean bound, AddTarget target)
        implements AbilityEvent {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Runes without a {@code from} appear this long before the adds rise. */
    private static final double DEFAULT_LEAD = 1.0;
    /** A spot may lie this far below its centre's feet (a player's), so adds do not rise at the foot of a cliff. */
    private static final int RUNE_DROP = 6;
    /** Threat a {@code rune_player} add starts with on its player: one point of damage from anyone else beats it. */
    private static final double RUNE_PLAYER_THREAT = 1.0;

    public static final MapCodec<SummonEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(SummonEvent::at),
            ResourceLocation.CODEC.fieldOf("entity").forGetter(SummonEvent::entity),
            Count.CODEC.optionalFieldOf("count", Count.of(1)).forGetter(SummonEvent::count),
            Codec.DOUBLE.optionalFieldOf("forward", 0.0).forGetter(SummonEvent::forward),
            Codec.DOUBLE.optionalFieldOf("up", 0.0).forGetter(SummonEvent::up),
            Codec.DOUBLE.optionalFieldOf("right", 0.0).forGetter(SummonEvent::right),
            Codec.DOUBLE.optionalFieldOf("spread", 1.0).forGetter(SummonEvent::spread),
            MaxAlive.CODEC.optionalFieldOf("max_alive", MaxAlive.of(8)).forGetter(SummonEvent::maxAlive),
            Runes.CODEC.optionalFieldOf("telegraph").forGetter(SummonEvent::telegraph),
            Placement.CODEC.optionalFieldOf("placement").forGetter(SummonEvent::placement),
            Scale.CODEC.optionalFieldOf("scale").forGetter(SummonEvent::scale),
            Codec.BOOL.optionalFieldOf("loot", true).forGetter(SummonEvent::loot),
            Codec.BOOL.optionalFieldOf("xp", true).forGetter(SummonEvent::xp),
            Codec.BOOL.optionalFieldOf("bound", false).forGetter(SummonEvent::bound),
            AddTarget.CODEC.optionalFieldOf("target", AddTarget.AIM).forGetter(SummonEvent::target)
    ).apply(i, SummonEvent::new));

    @Override
    public String type() {
        return "summon";
    }

    @Override
    public double begin() {
        return runesShown() ? telegraph.get().telegraph().start(at, DEFAULT_LEAD) : at;
    }

    @Override
    public double end() {
        return at;
    }

    private boolean runesShown() {
        return telegraph.isPresent() && telegraph.get().telegraph().show();
    }

    // --- The wave ---

    /** How many more adds the summoner may have alive now under {@code max_alive} (for {@code skip_when_full}). */
    public int room(Mob mob, MobCombat combat) {
        int fighters = maxAlive.perExtraPlayer() > 0 ? Fighters.of(mob).size() : 0;
        return Math.max(0, maxAlive.at(fighters) - combat.aliveSummons());
    }

    private boolean needsFighters() {
        return count.perExtraPlayer() > 0 || maxAlive.perExtraPlayer() > 0
                || placement.map(p -> p.around() == Placement.Around.FIGHTERS).orElse(false);
    }

    @Override
    public Instance start(AbilityRun run) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity).orElse(null);
        if (type == null) {
            LOGGER.warn("Mob behaviour summons unknown entity {}", entity);
            return new Instance() {
            };
        }
        // With runes the wave is decided as they appear, so they show exactly how many rise and where.
        Wave wave = runesShown() ? plan(run) : null;
        AbilityRun.Marker marker = wave == null ? null : drawRunes(run, wave);
        return new Instance() {
            @Override
            public void finish(AbilityRun run) {
                if (wave != null) {
                    spawnWave(run, type, wave);
                    run.flash(marker);
                } else if (placement.isPresent()) {
                    spawnWave(run, type, plan(run));
                } else {
                    spawnScattered(run, type);
                }
            }

            @Override
            public void cancel(AbilityRun run) {
                run.clear(marker);
            }
        };
    }

    /** Where the summon's offsets put its anchor (the default spawn point). */
    private Vec3 anchor(AbilityRun run) {
        Vec3 ahead = AbilityRun.forward(run.yaw());
        Vec3 side = new Vec3(-ahead.z, 0.0, ahead.x);
        return run.mob().position().add(ahead.scale(forward)).add(side.scale(right)).add(0.0, up, 0.0);
    }

    /** The default: one at a time around the anchor, while there is room (as summons always worked). */
    private void spawnScattered(AbilityRun run, EntityType<?> type) {
        Mob mob = run.mob();
        int fighters = needsFighters() ? Fighters.of(mob).size() : 0;
        int total = count.roll(fighters, bound -> mob.getRandom().nextInt(bound));
        int cap = maxAlive.at(fighters);
        Vec3 anchor = anchor(run);
        for (int n = 0; n < total && run.combat().aliveSummons() < cap; n++) {
            Entity spawned = type.create(run.level());
            if (spawned == null) {
                return;
            }
            double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
            double scatter = mob.getRandom().nextDouble() * spread;
            spawned.moveTo(anchor.x + Math.cos(angle) * scatter, anchor.y, anchor.z + Math.sin(angle) * scatter,
                    run.yaw(), 0.0F);
            add(run, spawned, run.aim());
        }
    }

    /**
     * Decides a wave: its size (count, limited to the room under max_alive), then one spot per add: dealt to the
     * fighters in turn ({@link Fighters#deal}) or around the mob / its aim, valid ground per the placement (with the
     * fallback next to the summoner's ground shadow), or the default scatter without a placement.
     */
    private Wave plan(AbilityRun run) {
        Mob mob = run.mob();
        IntUnaryOperator random = bound -> mob.getRandom().nextInt(bound);
        List<Player> fighters = needsFighters() ? Fighters.of(mob) : List.of();
        int rolled = count.roll(fighters.size(), random);
        int cap = maxAlive.at(fighters.size());
        int room = Math.max(0, cap - run.combat().aliveSummons());
        int size = Math.min(rolled, room);
        List<Spot> spots = new ArrayList<>();
        if (size <= 0) {
            run.combat().trace("summon: no room (" + run.combat().aliveSummons() + " alive)");
            return new Wave(spots, cap);
        }
        List<LivingEntity> owners = owners(run, size, fighters, random);
        List<Vec3> taken = new ArrayList<>();
        for (LivingEntity owner : owners) {
            Optional<Vec3> spot = placement.isPresent() ? place(run, placement.get(), owner, taken) : scatter(run);
            if (spot.isPresent()) {
                taken.add(spot.get());
                spots.add(new Spot(spot.get(), owner));
            }
        }
        run.combat().trace(String.format("summon %d of %d %s (room %d, %d fighting)", spots.size(), rolled, entity,
                room, fighters.size()));
        return new Wave(spots, cap);
    }

    /** For each add, the player its rune is placed for (and, with {@code rune_player}, whom it goes for first). */
    private List<LivingEntity> owners(AbilityRun run, int size, List<Player> fighters, IntUnaryOperator random) {
        List<LivingEntity> owners = new ArrayList<>(size);
        boolean aroundFighters = placement.map(p -> p.around() == Placement.Around.FIGHTERS).orElse(false);
        if (aroundFighters && !fighters.isEmpty()) {
            List<Player> order = new ArrayList<>(fighters);
            for (int i = order.size() - 1; i > 0; i--) { // who gets the first runes when the room is short: random
                int j = random.applyAsInt(i + 1);
                order.set(i, order.set(j, order.get(i)));
            }
            for (int index : Fighters.deal(size, order.size(), random)) {
                owners.add(order.get(index));
            }
            return owners;
        }
        LivingEntity aim = run.aim();
        for (int i = 0; i < size; i++) {
            owners.add(aim); // nobody fighting yet (a forced test): around the aim
        }
        return owners;
    }

    /** One add's spot per the placement, else next to the summoner's ground shadow; empty over the void. */
    private Optional<Vec3> place(AbilityRun run, Placement rule, LivingEntity owner, List<Vec3> taken) {
        Mob mob = run.mob();
        ServerLevel level = run.level();
        Vec3 centre = switch (rule.around()) {
            case SELF -> rule.ground() ? run.groundBelow(anchor(run), 0).orElse(null) : anchor(run);
            case AIM, FIGHTERS -> owner != null && owner.isAlive() ? owner.position() : run.aimPosition();
        };
        if (centre != null) {
            Optional<Vec3> spot = RuneSpots.around(level, mob.getRandom(), centre, rule.radius().min(),
                    rule.radius().max(), taken, rule.minGap(), rule.clearance(), rule.ground(), RUNE_DROP);
            if (spot.isPresent()) {
                return spot;
            }
        }
        Optional<Vec3> shadow = rule.ground() ? run.groundBelow(mob.position(), 0) : Optional.of(mob.position());
        return shadow.flatMap(floor -> RuneSpots.nearShadow(level, mob.getRandom(), floor, taken, rule.minGap(),
                rule.clearance(), rule.ground(), RUNE_DROP));
    }

    /** The default spot without a placement: around the anchor, at its height. */
    private Optional<Vec3> scatter(AbilityRun run) {
        Mob mob = run.mob();
        Vec3 anchor = anchor(run);
        double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
        double scatter = mob.getRandom().nextDouble() * spread;
        return Optional.of(new Vec3(anchor.x + Math.cos(angle) * scatter, anchor.y, anchor.z + Math.sin(angle) * scatter));
    }

    private AbilityRun.Marker drawRunes(AbilityRun run, Wave wave) {
        AttackZone rune = new AttackZone.Circle((float) telegraph.get().radius());
        List<AbilityRun.Zone> zones = new ArrayList<>();
        for (Spot spot : wave.spots()) {
            zones.add(new AbilityRun.Zone(spot.position(), run.yaw(), rune));
        }
        return run.telegraph(zones, telegraph.get().telegraph(), at);
    }

    /** Raises a decided wave, each add on its spot facing its first target (while still under its max_alive). */
    private void spawnWave(AbilityRun run, EntityType<?> type, Wave wave) {
        for (Spot spot : wave.spots()) {
            if (run.combat().aliveSummons() >= wave.cap()) {
                return;
            }
            Entity spawned = type.create(run.level());
            if (spawned == null) {
                return;
            }
            LivingEntity first = firstTarget(run, spot.owner());
            Vec3 at = spot.position();
            float yaw = first == null ? run.yaw()
                    : (float) Math.toDegrees(Math.atan2(-(first.getX() - at.x), first.getZ() - at.z));
            spawned.moveTo(at.x, at.y, at.z, yaw, 0.0F);
            add(run, spawned, spot.owner());
        }
    }

    /**
     * Keeps a {@code rune_player} add on its player: vanilla target goals (a wither skeleton's nearest-player goal)
     * would retarget it to whoever is closest within half a second. If the add runs threat targeting (Mobs_LD's
     * goal at priority 0, which every vanilla target goal yields to), the player gets a little threat on its table,
     * so it stays on them until someone builds more (hits it, taunts it). An add without threat targeting (the server's
     * threat config leaves that mob type out) only gets the first target.
     */
    private static void holdFirstTarget(Mob add, LivingEntity player) {
        boolean threatTargeting = ((MobGoalsAccessor) add).mobs_ld$targetSelector().getAvailableGoals().stream()
                .anyMatch(goal -> goal.getGoal() instanceof ThreatTargetGoal);
        if (threatTargeting) {
            ((ThreatHolder) add).getThreatTable().add(player.getUUID(), RUNE_PLAYER_THREAT);
        }
    }

    private LivingEntity firstTarget(AbilityRun run, LivingEntity owner) {
        return target == AddTarget.RUNE_PLAYER && owner != null && owner.isAlive() ? owner : run.aim();
    }

    /**
     * Prepares one add and puts it in the level: finalizeSpawn, its first target, the drop and bond marks (all before
     * it is added, so mods that look at new mobs see them), then, once added, its scaled stats.
     */
    private void add(AbilityRun run, Entity spawned, LivingEntity owner) {
        Mob mob = run.mob();
        ServerLevel level = run.level();
        if (spawned instanceof Mob summoned) {
            summoned.finalizeSpawn(level, level.getCurrentDifficultyAt(summoned.blockPosition()),
                    MobSpawnType.MOB_SUMMONED, null);
            LivingEntity first = firstTarget(run, owner);
            if (first != null) {
                summoned.setTarget(first);
                if (target == AddTarget.RUNE_PLAYER && first == owner) {
                    holdFirstTarget(summoned, first);
                }
            }
            if (!loot) {
                summoned.setCanPickUpLoot(false); // whatever it picked up would vanish with it
            }
        }
        if (!loot) {
            spawned.addTag(SummonBond.NO_LOOT_TAG);
        }
        if (!xp) {
            spawned.addTag(SummonBond.NO_XP_TAG);
        }
        if (bound) {
            SummonBond.bind(spawned, mob);
        }
        LivingEntity living = spawned instanceof LivingEntity entity ? entity : null;
        SummonStats.Targets stats = scale.isPresent() && living != null ? SummonStats.plan(scale.get(), mob, living) : null;
        boolean added = level.addFreshEntity(spawned);
        run.combat().trackSummon(spawned);
        if (added && stats != null) {
            // Whatever scaled the add inside addFreshEntity (Dungeon Difficulty, Arenas_LD, both synchronous there) is
            // evened out here, once.
            SummonStats.apply(living, stats);
            run.combat().trace("summon " + entity + " scaled to" + describe(" HP", stats.maxHealth())
                    + describe(" attack", stats.attackDamage()) + describe(" armor", stats.armor())
                    + describe(" damage_dealt", stats.damageDealt()));
        }
    }

    private static String describe(String stat, double target) {
        return Double.isNaN(target) ? "" : String.format("%s %.2f", stat, target);
    }

    /** One add of a decided wave: where it rises and the player its rune was placed for (may be null). */
    private record Spot(Vec3 position, LivingEntity owner) {
    }

    /** A decided wave and the max_alive it was decided under. */
    private record Wave(List<Spot> spots, int cap) {
    }

    // --- Data ---

    /**
     * How many adds one summon raises: a number (always that many), or {@code {min, max, per_extra_player, cap}}: a
     * random count in [min, max], plus {@code per_extra_player} for each fighting player beyond the first, at most
     * {@code cap}.
     */
    public record Count(int min, int max, int perExtraPlayer, int cap) {
        private static final Codec<Count> RANGE = RecordCodecBuilder.<Count>create(i -> i.group(
                Codec.intRange(0, 256).fieldOf("min").forGetter(Count::min),
                Codec.intRange(0, 256).optionalFieldOf("max").forGetter(c -> Optional.of(c.max)),
                Codec.intRange(0, 64).optionalFieldOf("per_extra_player", 0).forGetter(Count::perExtraPlayer),
                Codec.intRange(0, 1024).optionalFieldOf("cap").forGetter(c -> c.cap == Integer.MAX_VALUE
                        ? Optional.empty() : Optional.of(c.cap))
        ).apply(i, (min, max, perExtra, cap) -> new Count(min, max.orElse(min), perExtra, cap.orElse(Integer.MAX_VALUE))))
                .validate(c -> c.max < c.min ? DataResult.error(() -> "A summon count's max " + c.max
                        + " is below its min " + c.min) : DataResult.success(c));
        public static final Codec<Count> CODEC = Codec.either(Codec.INT, RANGE).xmap(
                either -> either.map(Count::of, range -> range),
                c -> c.plain() ? Either.left(c.min) : Either.right(c));

        /** Always {@code n}, whoever is fighting (a plain number, as summons always took). */
        public static Count of(int n) {
            return new Count(n, n, 0, Integer.MAX_VALUE);
        }

        private boolean plain() {
            return min == max && perExtraPlayer == 0 && cap == Integer.MAX_VALUE;
        }

        /** The count for a wave with {@code fighters} players fighting; {@code random}: bound -> [0, bound). */
        public int roll(int fighters, IntUnaryOperator random) {
            int rolled = max > min ? min + random.applyAsInt(max - min + 1) : min;
            return Math.min(cap, rolled + perExtraPlayer * Fighters.extra(fighters));
        }
    }

    /**
     * How many of the summoner's adds may be alive at once: a number, or {@code {base, per_extra_player, cap}}: base
     * plus {@code per_extra_player} for each fighting player beyond the first, at most {@code cap}. A wave only fills the
     * free room.
     */
    public record MaxAlive(int base, int perExtraPlayer, int cap) {
        private static final Codec<MaxAlive> RANGE = RecordCodecBuilder.<MaxAlive>create(i -> i.group(
                Codec.intRange(0, 1024).fieldOf("base").forGetter(MaxAlive::base),
                Codec.intRange(0, 256).optionalFieldOf("per_extra_player", 0).forGetter(MaxAlive::perExtraPlayer),
                Codec.intRange(0, 1024).optionalFieldOf("cap").forGetter(m -> m.cap == Integer.MAX_VALUE
                        ? Optional.empty() : Optional.of(m.cap))
        ).apply(i, (base, perExtra, cap) -> new MaxAlive(base, perExtra, cap.orElse(Integer.MAX_VALUE))));
        public static final Codec<MaxAlive> CODEC = Codec.either(Codec.INT, RANGE).xmap(
                either -> either.map(MaxAlive::of, range -> range),
                m -> m.perExtraPlayer == 0 && m.cap == Integer.MAX_VALUE ? Either.left(m.base) : Either.right(m));

        public static MaxAlive of(int n) {
            return new MaxAlive(n, 0, Integer.MAX_VALUE);
        }

        /** The limit with {@code fighters} players fighting. */
        public int at(int fighters) {
            return Math.min(cap, base + perExtraPlayer * Fighters.extra(fighters));
        }
    }

    /** The runes: a telegraph (from, color, style) drawn as a circle {@code radius} blocks wide at each add's spot. */
    public record Runes(Telegraph telegraph, double radius) {
        public static final Codec<Runes> CODEC = RecordCodecBuilder.create(i -> i.group(
                Telegraph.MAP_CODEC.forGetter(Runes::telegraph),
                Codec.doubleRange(0.1, 8.0).optionalFieldOf("radius", 0.9).forGetter(Runes::radius)
        ).apply(i, Runes::new));
    }

    /**
     * Where the adds rise instead of the scatter around the anchor.
     *
     * @param around    {@code self} (the floor under the anchor), {@code aim} or {@code fighters} (each rune is placed for
     *                  one fighting player: one each in turn for two rounds, the rest at random)
     * @param radius    blocks from that centre, rolled per spot
     * @param minGap    least distance between two runes of the wave
     * @param clearance open blocks needed above the floor
     * @param ground    on the floor (scanned down from the centre); false: at the centre's height in open air
     */
    public record Placement(Around around, Radius radius, double minGap, int clearance, boolean ground) {
        public static final Codec<Placement> CODEC = RecordCodecBuilder.create(i -> i.group(
                Around.CODEC.optionalFieldOf("around", Around.SELF).forGetter(Placement::around),
                Radius.CODEC.optionalFieldOf("radius", new Radius(2.0, 4.0)).forGetter(Placement::radius),
                Codec.doubleRange(0.0, 16.0).optionalFieldOf("min_gap", 1.5).forGetter(Placement::minGap),
                Codec.intRange(1, 16).optionalFieldOf("clearance", 2).forGetter(Placement::clearance),
                Codec.BOOL.optionalFieldOf("ground", true).forGetter(Placement::ground)
        ).apply(i, Placement::new));

        public enum Around implements StringRepresentable {
            SELF("self"),
            AIM("aim"),
            FIGHTERS("fighters");

            public static final Codec<Around> CODEC = StringRepresentable.fromEnum(Around::values);
            private final String name;

            Around(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return name;
            }
        }
    }

    /** A distance in blocks: a number or a {@code [min, max]} range, rolled per use. */
    public record Radius(double min, double max) {
        private static final double LIMIT = 32.0;
        public static final Codec<Radius> CODEC = Codec.either(Codec.DOUBLE, Codec.DOUBLE.listOf()).comapFlatMap(
                either -> either.map(value -> check(value, value), range -> range.size() == 2 ? check(range.get(0), range.get(1))
                        : DataResult.error(() -> "A radius range must be [min, max], got " + range)),
                radius -> radius.min == radius.max ? Either.left(radius.min) : Either.right(List.of(radius.min, radius.max)));

        private static DataResult<Radius> check(double min, double max) {
            if (!(min >= 0.0) || !(max <= LIMIT) || max < min) {
                return DataResult.error(() -> "A radius must be a number or [min, max] between 0 and " + LIMIT
                        + ", got [" + min + ", " + max + "]");
            }
            return DataResult.success(new Radius(min, max));
        }
    }

    /**
     * The adds' stats from the summoner's (see {@link SummonStats}); each part left out stays the add's own.
     *
     * @param maxHealth    the add's max health from the summoner's (see {@link StatScale})
     * @param attackDamage the add's attack damage from the summoner's (see {@link StatScale})
     * @param armor        added to the add's own armor
     * @param damageDealt  {@code copy}: the summoner's {@code loot_stage:damage_dealt} (if that mod is installed)
     */
    public record Scale(Optional<StatScale> maxHealth, Optional<StatScale> attackDamage, Optional<ArmorBonus> armor,
                        Optional<DamageDealt> damageDealt) {
        public static final Codec<Scale> CODEC = RecordCodecBuilder.create(i -> i.group(
                StatScale.CODEC.optionalFieldOf("max_health").forGetter(Scale::maxHealth),
                StatScale.CODEC.optionalFieldOf("attack_damage").forGetter(Scale::attackDamage),
                ArmorBonus.CODEC.optionalFieldOf("armor").forGetter(Scale::armor),
                DamageDealt.CODEC.optionalFieldOf("damage_dealt").forGetter(Scale::damageDealt)
        ).apply(i, Scale::new));

        public boolean copiesDamageDealt() {
            return damageDealt.isPresent();
        }
    }

    /**
     * How one stat of an add follows its summoner's: a number is a factor on the add's own value, times the summoner's
     * live value over the base its behaviour declares (the ratio form); {@code {"of_summoner": f}} is the summoner's live
     * value times {@code f}, whatever the add's own value is (e.g. {@code 0.025}: a fortieth of the boss's max health).
     */
    public record StatScale(double factor, boolean ofSummoner) {
        private static final Codec<Double> AMOUNT = Codec.doubleRange(0.0, 1000.0);
        public static final Codec<StatScale> CODEC = Codec.either(AMOUNT, AMOUNT.fieldOf("of_summoner").codec()).xmap(
                either -> either.map(factor -> new StatScale(factor, false), fraction -> new StatScale(fraction, true)),
                scale -> scale.ofSummoner ? Either.right(scale.factor) : Either.left(scale.factor));
    }

    /**
     * Armor added to the add's own: {@code summoner_bonus} (the summoner's armor above its declared base, e.g. what a
     * level gave it) or a fixed number.
     */
    public record ArmorBonus(boolean summonerBonus, double flat) {
        private static final String SUMMONER_BONUS = "summoner_bonus";
        public static final Codec<ArmorBonus> CODEC = Codec.either(Codec.STRING, Codec.doubleRange(-1024.0, 1024.0))
                .comapFlatMap(either -> either.map(
                        name -> SUMMONER_BONUS.equals(name) ? DataResult.success(new ArmorBonus(true, 0.0))
                                : DataResult.error(() -> "Unknown armor rule '" + name + "' (expected summoner_bonus or a number)"),
                        flat -> DataResult.success(new ArmorBonus(false, flat))),
                        bonus -> bonus.summonerBonus ? Either.left(SUMMONER_BONUS) : Either.right(bonus.flat));

        public double bonus(Mob summoner) {
            return summonerBonus ? SummonStats.bonusAboveBase(summoner, Attributes.ARMOR) : flat;
        }
    }

    public enum DamageDealt implements StringRepresentable {
        COPY("copy");

        public static final Codec<DamageDealt> CODEC = StringRepresentable.fromEnum(DamageDealt::values);
        private final String name;

        DamageDealt(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Whom each add goes for first. */
    public enum AddTarget implements StringRepresentable {
        /** The summoner's aim (the default). */
        AIM("aim"),
        /** The player its rune was placed for (with {@code placement.around: fighters}); else the aim. */
        RUNE_PLAYER("rune_player");

        public static final Codec<AddTarget> CODEC = StringRepresentable.fromEnum(AddTarget::values);
        private final String name;

        AddTarget(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
