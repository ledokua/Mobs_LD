package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.entity.threat.SpellCastCompat;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.CombatTargets;
import net.ledok.mobs_ld.mob.combat.Fighters;
import net.ledok.mobs_ld.mob.combat.ListenRules;
import net.ledok.mobs_ld.mob.combat.ListenWindows;
import net.ledok.mobs_ld.mob.host.Head;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A listening window (the Warden's Listening Silence, "be still"): from {@code from} to {@code until} every fighting
 * player within {@code range} who gives themselves away (see {@link ListenRules.Trigger}) takes {@code hit} at once,
 * once per window, with a line of particles from the mob's chest to them. At {@code chain_at} (default
 * {@code until}), if any of them is still alive, in the level and an enemy, the ability hands over to {@code chain_to},
 * aimed at the first such one; otherwise it does not hand over.
 *
 * <p>The players watched are those fighting it and within range when the window opens; their state is read every tick
 * of the window only (nothing is polled outside it). A player who was hurt or pushed is not caught by the jump or move
 * rule for a moment after it (see {@link ListenRules.Tracker}). Damage and instant item uses come from the game's
 * events (see {@link ListenWindows}).
 *
 * @param particles drawn every block from the mob's chest to each offender (a particle type needing no settings)
 * @param sound     played at the mob for each offender
 */
public record ListenEvent(double from, double until, double range, List<ListenRules.Trigger> triggers,
                          double moveTolerance, HitSpec hit, Optional<String> chainTo, Optional<Double> chainAt,
                          SimpleParticleType particles, Optional<FxEvent.Sound> sound) implements AbilityEvent {
    public static final MapCodec<ListenEvent> CODEC = RecordCodecBuilder.<ListenEvent>mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("from").forGetter(ListenEvent::from),
            Codec.DOUBLE.fieldOf("until").forGetter(ListenEvent::until),
            Codec.doubleRange(0.0, 128.0).optionalFieldOf("range", 40.0).forGetter(ListenEvent::range),
            ListenRules.Trigger.CODEC.listOf().optionalFieldOf("triggers", List.of(ListenRules.Trigger.values()))
                    .forGetter(ListenEvent::triggers),
            Codec.doubleRange(0.0, 64.0).optionalFieldOf("move_tolerance", 0.6).forGetter(ListenEvent::moveTolerance),
            HitSpec.CODEC.optionalFieldOf("hit", HitSpec.DEFAULT).forGetter(ListenEvent::hit),
            Codec.STRING.optionalFieldOf("chain_to").forGetter(ListenEvent::chainTo),
            Codec.DOUBLE.optionalFieldOf("chain_at").forGetter(ListenEvent::chainAt),
            FxEvent.Particles.TYPE.optionalFieldOf("particles", ParticleTypes.SONIC_BOOM).forGetter(ListenEvent::particles),
            FxEvent.Sound.CODEC.optionalFieldOf("sound").forGetter(ListenEvent::sound)
    ).apply(i, ListenEvent::new)).validate(ListenEvent::validate);

    private static DataResult<ListenEvent> validate(ListenEvent event) {
        if (event.until < event.from) {
            return DataResult.error(() -> "A listen window ends (until " + event.until + ") before it starts (from "
                    + event.from + ")");
        }
        if (event.chainAt.isPresent() && event.chainAt.get() < event.until) {
            return DataResult.error(() -> "A listen window's chain_at (" + event.chainAt.get() + ") is before its end ("
                    + event.until + ")");
        }
        if (event.triggers.isEmpty()) {
            return DataResult.error(() -> "A listen window needs at least one trigger");
        }
        return DataResult.success(event);
    }

    @Override
    public String type() {
        return "listen";
    }

    @Override
    public double begin() {
        return from;
    }

    /** The hand-over time: the window itself closes at {@code until}. */
    @Override
    public double end() {
        return chainAt.orElse(until);
    }

    @Override
    public List<String> chains() {
        return chainTo.map(List::of).orElse(List.of());
    }

    @Override
    public Instance start(AbilityRun run) {
        Set<ListenRules.Trigger> watched = EnumSet.copyOf(triggers);
        Mob mob = run.mob();
        long now = run.level().getGameTime();
        List<Watch> watches = new ArrayList<>();
        for (Player fighter : Fighters.of(mob)) {
            if (fighter.distanceToSqr(mob) <= range * range) {
                watches.add(new Watch(fighter, new ListenRules.Tracker(sample(fighter), now)));
            }
        }
        run.combat().trace(String.format("%s: listening to %d player(s) until %.1f s", run.name(), watches.size(), until));
        Window window = new Window(run, watched, watches, now);
        ListenWindows.open(window);
        return window;
    }

    /** How the listener sees {@code player} this tick. */
    private static ListenRules.Sample sample(Player player) {
        boolean using = player.isUsingItem() || SpellCastCompat.isCasting(player);
        int jumps = player instanceof ServerPlayer server ? server.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)) : 0;
        // Hurt time runs 10 ticks after any hit; hurtMarked / hasImpulse: the server set its velocity (a knockback).
        boolean pushed = player.hurtTime > 0 || player.hurtMarked || player.hasImpulse;
        return new ListenRules.Sample(player.getX(), player.getZ(), using, player.isSprinting(), jumps, pushed);
    }

    /** One watched player and its tracker. */
    private record Watch(Player player, ListenRules.Tracker tracker) {
    }

    /** An item use reported before it happened: the stack and its count then. */
    private record Use(Player player, ItemStack stack, Item item, int countBefore) {
        /** Whether the use did something: the stack shrank, the item went on cooldown, or a use started. */
        boolean happened() {
            return stack.getCount() < countBefore || player.getCooldowns().isOnCooldown(item) || player.isUsingItem();
        }
    }

    /** The open window of one run. */
    private final class Window implements Instance, ListenWindows.Window {
        private final AbilityRun run;
        private final Set<ListenRules.Trigger> watched;
        private final List<Watch> watches;
        private final long openedAt;
        /** Offenders in the order they gave themselves away (the first is the chain's aim). */
        private final Set<Player> offenders = new LinkedHashSet<>();
        /** Players reported by the damage event since the last tick (punished on the run's tick, not inside it). */
        private final Set<Player> damaged = new LinkedHashSet<>();
        /** Item uses reported since the last tick, judged on the run's tick once they have happened. */
        private final List<Use> uses = new ArrayList<>();
        private boolean closed;

        private Window(AbilityRun run, Set<ListenRules.Trigger> watched, List<Watch> watches, long openedAt) {
            this.run = run;
            this.watched = watched;
            this.watches = watches;
            this.openedAt = openedAt;
        }

        @Override
        public Mob mob() {
            return run.mob();
        }

        @Override
        public boolean listening() {
            return !closed && !run.cutOff() && run.seconds() < until;
        }

        @Override
        public long openedAt() {
            return openedAt;
        }

        @Override
        public void damagedBy(Player player) {
            if (watched.contains(ListenRules.Trigger.DAMAGE) && inRange(player)) {
                damaged.add(player);
            }
        }

        @Override
        public void usedItem(Player player, ItemStack stack) {
            if (watched.contains(ListenRules.Trigger.USE_ITEM) && inRange(player)) {
                uses.add(new Use(player, stack, stack.getItem(), stack.getCount()));
            }
        }

        private boolean inRange(Player player) {
            return player.level() == run.level() && player.distanceToSqr(run.mob()) <= range * range;
        }

        @Override
        public void tick(AbilityRun run) {
            for (Player player : List.copyOf(damaged)) {
                offend(player, ListenRules.Trigger.DAMAGE);
            }
            damaged.clear();
            for (Use use : List.copyOf(uses)) {
                if (use.happened()) {
                    offend(use.player(), ListenRules.Trigger.USE_ITEM);
                }
            }
            uses.clear();
            if (closed || run.seconds() >= until) {
                close();
                return;
            }
            long now = run.level().getGameTime();
            for (Watch watch : watches) {
                Player player = watch.player();
                if (offenders.contains(player) || !player.isAlive() || player.isRemoved()) {
                    continue;
                }
                ListenRules.Trigger trigger = watch.tracker().observe(watched, sample(player), now, moveTolerance);
                if (trigger != null) {
                    offend(player, trigger);
                }
            }
        }

        /** {@code player} gave themselves away: they take the hit once, with the line of particles. */
        private void offend(Player player, ListenRules.Trigger trigger) {
            Mob mob = run.mob();
            if (offenders.contains(player) || !player.isAlive() || !CombatTargets.isEnemy(mob, player)) {
                return;
            }
            offenders.add(player);
            run.combat().trace(String.format("%s: %s gave themselves away (%s)", run.name(), player.getName().getString(),
                    trigger.getSerializedName()));
            Vec3 chest = run.combat().adapter().muzzle(mob, Head.CENTER)
                    .orElse(mob.position().add(0.0, mob.getEyeHeight(), 0.0));
            Vec3 target = player.position().add(0.0, player.getBbHeight() / 2.0, 0.0);
            Vec3 line = target.subtract(chest);
            int points = Math.max(1, (int) Math.ceil(line.length()));
            for (int step = 1; step <= points; step++) {
                Vec3 point = chest.add(line.scale((double) step / points));
                run.level().sendParticles(particles, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            sound.ifPresent(cue -> run.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), cue.event(),
                    SoundSource.HOSTILE, cue.volume(), cue.pitch()));
            hit.apply(run, player, mob.position());
        }

        /**
         * At {@code chain_at}: hands over to {@code chain_to} aimed at the first offender who is still alive, in the
         * level and an enemy; with nobody like that it does not hand over at all.
         */
        @Override
        public void finish(AbilityRun run) {
            close();
            if (chainTo.isEmpty()) {
                return;
            }
            Mob mob = run.mob();
            for (Player offender : offenders) {
                if (offender.isAlive() && !offender.isRemoved() && offender.level() == run.level()
                        && CombatTargets.isEnemy(mob, offender)) {
                    run.chain(chainTo.get(), offender, false);
                    return;
                }
            }
        }

        @Override
        public void cancel(AbilityRun run) {
            close();
        }

        private void close() {
            if (!closed) {
                closed = true;
                damaged.clear();
                uses.clear();
                ListenWindows.close(this);
            }
        }
    }
}
