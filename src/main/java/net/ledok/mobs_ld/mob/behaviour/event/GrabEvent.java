package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Interactive boss grab: locks a catch zone at {@link #begin()}, seizes up to {@code max_victims} hostile targets at
 * {@code at}, lifts and suspends them safely in front of the boss until {@code until} while applying optional periodic
 * {@code tick_hit} ticks, and finishes with {@code release_hit} + a directional {@code throw}.
 *
 * <p>Teamplay counterplay: when {@code rescue_damage} is configured, allies dealing that much damage to the boss while
 * a victim is held break the grip early, cancelling the finisher damage and throw. Position clamping checks block
 * collisions so a held player is never pushed inside solid terrain.
 */
public record GrabEvent(
        double at,
        double until,
        Shape shape,
        ZoneOffset zoneOffset,
        Telegraph telegraph,
        int maxVictims,
        Hold hold,
        Optional<HitSpec> catchHit,
        Optional<TickHit> tickHit,
        Optional<HitSpec> releaseHit,
        Optional<ThrowSpec> throwSpec,
        Optional<Double> rescueDamage,
        Messages messages
) implements AbilityEvent {
    private static final double DEFAULT_LEAD = 0.6;
    /** Escape distance beyond which a teleported/pearled victim slips out of the grab immediately. */
    private static final double ESCAPE_DISTANCE_SQ = 11.0 * 11.0;

    public static final MapCodec<GrabEvent> CODEC = RecordCodecBuilder.<GrabEvent>mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(GrabEvent::at),
            Codec.DOUBLE.fieldOf("until").forGetter(GrabEvent::until),
            Shape.CODEC.fieldOf("shape").forGetter(GrabEvent::shape),
            ZoneOffset.CODEC.forGetter(GrabEvent::zoneOffset),
            Telegraph.CODEC.optionalFieldOf("telegraph", Telegraph.DEFAULT).forGetter(GrabEvent::telegraph),
            Codec.intRange(1, 6).optionalFieldOf("max_victims", 1).forGetter(GrabEvent::maxVictims),
            Hold.CODEC.optionalFieldOf("hold", Hold.DEFAULT).forGetter(GrabEvent::hold),
            HitSpec.CODEC.optionalFieldOf("catch_hit").forGetter(GrabEvent::catchHit),
            TickHit.CODEC.optionalFieldOf("tick_hit").forGetter(GrabEvent::tickHit),
            HitSpec.CODEC.optionalFieldOf("release_hit").forGetter(GrabEvent::releaseHit),
            ThrowSpec.CODEC.optionalFieldOf("throw").forGetter(GrabEvent::throwSpec),
            Codec.doubleRange(1.0, 1.0e5).optionalFieldOf("rescue_damage").forGetter(GrabEvent::rescueDamage),
            Messages.CODEC.forGetter(GrabEvent::messages)
    ).apply(i, GrabEvent::new)).validate(GrabEvent::validate);

    private static DataResult<GrabEvent> validate(GrabEvent event) {
        if (event.at < 0.0) {
            return DataResult.error(() -> "A grab event's 'at' (" + event.at + ") cannot be negative");
        }
        if (event.until <= event.at) {
            return DataResult.error(() -> "A grab event ends (until " + event.until
                    + ") before or at its catch time (at " + event.at + ")");
        }
        return DataResult.success(event);
    }

    /** Optional catch-zone spatial offsets and vertical bounds (flattened into the grab JSON object). */
    public record ZoneOffset(double offset, double side, Optional<Double> height, Optional<Double> depth) {
        public static final MapCodec<ZoneOffset> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("offset", 0.0).forGetter(ZoneOffset::offset),
                Codec.DOUBLE.optionalFieldOf("side", 0.0).forGetter(ZoneOffset::side),
                Codec.DOUBLE.optionalFieldOf("height").forGetter(ZoneOffset::height),
                Codec.DOUBLE.optionalFieldOf("depth").forGetter(ZoneOffset::depth)
        ).apply(i, ZoneOffset::new));
    }

    /** Optional action-bar translation keys on catch and rescue break (flattened into the grab JSON object). */
    public record Messages(Optional<String> onCatch, Optional<String> onRescue) {
        public static final MapCodec<Messages> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("on_catch_message").forGetter(Messages::onCatch),
                Codec.STRING.optionalFieldOf("on_rescue_message").forGetter(Messages::onRescue)
        ).apply(i, Messages::new));
    }

    public Optional<String> onCatchMessage() {
        return messages.onCatch();
    }

    public Optional<String> onRescueMessage() {
        return messages.onRescue();
    }

    /** Mob-local anchor where grabbed victims are held while lifting smoothly by {@code lift} blocks. */
    public record Hold(double forward, double up, double lift, double side) {
        public static final Hold DEFAULT = new Hold(2.2, 1.4, 0.5, 0.0);
        public static final Codec<Hold> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0.5, 6.0).optionalFieldOf("forward", 2.2).forGetter(Hold::forward),
                Codec.doubleRange(0.0, 5.0).optionalFieldOf("up", 1.4).forGetter(Hold::up),
                Codec.doubleRange(-2.0, 3.0).optionalFieldOf("lift", 0.5).forGetter(Hold::lift),
                Codec.doubleRange(-3.0, 3.0).optionalFieldOf("side", 0.0).forGetter(Hold::side)
        ).apply(i, Hold::new));
    }

    /** Periodic damage/debuff beat applied to held victims every {@code every} seconds during the hold. */
    public record TickHit(double every, HitSpec hit) {
        public static final Codec<TickHit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0.1, 10.0).fieldOf("every").forGetter(TickHit::every),
                HitSpec.CODEC.fieldOf("hit").forGetter(TickHit::hit)
        ).apply(i, TickHit::new));
    }

    /** Release launch impulse relative to the boss's facing. */
    public record ThrowSpec(double forward, double up, double side) {
        public static final Codec<ThrowSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(-2.5, 3.0).optionalFieldOf("forward", 1.25).forGetter(ThrowSpec::forward),
                Codec.doubleRange(-1.5, 2.0).optionalFieldOf("up", 0.5).forGetter(ThrowSpec::up),
                Codec.doubleRange(-2.5, 2.5).optionalFieldOf("side", 0.0).forGetter(ThrowSpec::side)
        ).apply(i, ThrowSpec::new));
    }

    @Override
    public String type() {
        return "grab";
    }

    @Override
    public double begin() {
        if (telegraph.show()) {
            return telegraph.start(at, DEFAULT_LEAD);
        }
        return telegraph.from().map(from -> Math.min(at, from)).orElse(at);
    }

    @Override
    public double end() {
        return until;
    }

    @Override
    public Instance start(AbilityRun run) {
        float lockedYaw = run.yaw();
        Vec3 zoneOrigin = run.groundAt(run.mob().position()
                .add(AbilityRun.forward(lockedYaw).scale(zoneOffset.offset()))
                .add(AbilityRun.right(lockedYaw).scale(zoneOffset.side())));
        AbilityRun.HitZone zone = new AbilityRun.HitZone(shape, zoneOrigin, lockedYaw);
        run.noteGrab(this, zone);
        AbilityRun.Marker marker = (telegraph.show() && begin() < at)
                ? run.telegraph(zoneOrigin, lockedYaw, shape, telegraph, at)
                : null;

        return new Instance() {
            private final List<LivingEntity> held = new ArrayList<>();
            private boolean attemptedCatch;
            private boolean broken;
            private double damageAtCatch;
            private double nextTickAt = Double.POSITIVE_INFINITY;

            @Override
            public void tick(AbilityRun run) {
                double now = run.seconds();
                if (!attemptedCatch) {
                    if (now < at) {
                        return;
                    }
                    attemptedCatch = true;
                    run.flash(marker);
                    capture(run);
                    if (held.isEmpty()) {
                        return;
                    }
                    damageAtCatch = run.damageTaken();
                    if (tickHit.isPresent()) {
                        nextTickAt = at + tickHit.get().every();
                    }
                    messages.onCatch().ifPresent(key -> notifyActionbar(run, key));
                }

                if (broken || held.isEmpty()) {
                    return;
                }

                if (rescueDamage.isPresent() && (run.damageTaken() - damageAtCatch) >= rescueDamage.get()) {
                    broken = true;
                    breakGrip(run);
                    return;
                }

                double progress = Math.max(0.0, Math.min(1.0, (now - at) / Math.max(0.05, until - at)));
                holdVictims(run, progress);

                if (tickHit.isPresent() && now >= nextTickAt && now < until - 0.05) {
                    TickHit pulse = tickHit.get();
                    nextTickAt += pulse.every();
                    for (LivingEntity victim : List.copyOf(held)) {
                        if (victim.isAlive()) {
                            pulse.hit().apply(run, victim, run.mob().position());
                        }
                    }
                }
            }

            @Override
            public void finish(AbilityRun run) {
                if (!attemptedCatch) {
                    run.flash(marker);
                    return;
                }
                if (broken || held.isEmpty()) {
                    return;
                }
                Mob mob = run.mob();
                float yaw = run.yaw();
                Vec3 fwd = AbilityRun.forward(yaw);
                Vec3 right = AbilityRun.right(yaw);
                for (LivingEntity victim : List.copyOf(held)) {
                    if (!victim.isAlive()) {
                        continue;
                    }
                    releaseHit.ifPresent(spec -> spec.apply(run, victim, mob.position()));
                    throwSpec.ifPresent(spec -> {
                        Vec3 vel = fwd.scale(spec.forward()).add(right.scale(spec.side())).add(0.0, spec.up(), 0.0);
                        victim.setDeltaMovement(vel);
                        victim.hurtMarked = true;
                    });
                }
                held.clear();
            }

            @Override
            public void cancel(AbilityRun run) {
                run.clear(marker);
                if (!held.isEmpty()) {
                    for (LivingEntity victim : held) {
                        if (victim.isAlive()) {
                            victim.resetFallDistance();
                            victim.setDeltaMovement(0.0, 0.1, 0.0);
                            victim.hurtMarked = true;
                        }
                    }
                    held.clear();
                }
            }

            private void capture(AbilityRun run) {
                double up = zoneOffset.height().orElse(run.combat().reachUp());
                double down = zoneOffset.depth().orElse(run.combat().reachDown());
                List<LivingEntity> candidates = new ArrayList<>(run.enemiesIn(shape, zoneOrigin, lockedYaw, up, down));
                candidates.removeIf(entity -> entity instanceof Player p && (p.isCreative() || p.isSpectator()));
                Mob mob = run.mob();
                candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mob)));
                for (LivingEntity candidate : candidates) {
                    if (held.size() >= maxVictims) {
                        break;
                    }
                    boolean caught = catchHit.map(spec -> spec.applyChecked(run, candidate, mob.position())).orElse(true);
                    if (!caught || !candidate.isAlive()) {
                        continue;
                    }
                    held.add(candidate);
                    candidate.resetFallDistance();
                    run.combat().trace(run.name() + ": grabbed " + candidate.getName().getString());
                }
            }

            private void holdVictims(AbilityRun run, double progress) {
                Mob mob = run.mob();
                float yaw = run.yaw();
                Vec3 fwd = AbilityRun.forward(yaw);
                Vec3 right = AbilityRun.right(yaw);
                double currentUp = hold.up() + hold.lift() * progress;
                int index = 0;
                for (Iterator<LivingEntity> it = held.iterator(); it.hasNext(); ) {
                    LivingEntity victim = it.next();
                    if (!victim.isAlive() || victim.isRemoved() || victim.level() != mob.level()
                            || victim.distanceToSqr(mob) > ESCAPE_DISTANCE_SQ) {
                        it.remove();
                        continue;
                    }
                    double fanSide = held.size() > 1 ? (index - (held.size() - 1) * 0.5) * 0.85 : 0.0;
                    Vec3 target = safeHoldPoint(run.level(), mob, victim, fwd, right, hold.forward(),
                            hold.side() + fanSide, currentUp);
                    Vec3 delta = target.subtract(victim.position());
                    if (delta.lengthSqr() > 4.5) {
                        victim.teleportTo(target.x, target.y, target.z);
                        victim.setDeltaMovement(Vec3.ZERO);
                    } else {
                        victim.setDeltaMovement(delta.scale(0.58));
                    }
                    victim.resetFallDistance();
                    victim.hurtMarked = true;
                    index++;
                }
            }

            private void breakGrip(AbilityRun run) {
                ServerLevel level = run.level();
                Mob mob = run.mob();
                level.playSound(null, mob.getX(), mob.getY() + 1.5, mob.getZ(),
                        SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 2.0F, 0.85F);
                level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + 2.0, mob.getZ(),
                        18, 0.9, 0.9, 0.9, 0.25);
                for (LivingEntity victim : held) {
                    if (victim.isAlive()) {
                        Vec3 away = victim.position().subtract(mob.position());
                        double horiz = Math.hypot(away.x, away.z);
                        double nx = horiz > 1.0e-3 ? away.x / horiz : 0.0;
                        double nz = horiz > 1.0e-3 ? away.z / horiz : 0.0;
                        victim.setDeltaMovement(nx * 0.45, 0.25, nz * 0.45);
                        victim.resetFallDistance();
                        victim.hurtMarked = true;
                    }
                }
                held.clear();
                messages.onRescue().ifPresent(key -> notifyActionbar(run, key));
                run.combat().trace(run.name() + ": grab broken by rescue damage");
            }

            private void notifyActionbar(AbilityRun run, String key) {
                Component text = Component.translatable(key);
                double radiusSq = 40.0 * 40.0;
                for (ServerPlayer player : run.level().players()) {
                    if (player.distanceToSqr(run.mob()) <= radiusSq) {
                        player.displayClientMessage(text, true);
                    }
                }
            }
        };
    }

    /** Finds the furthest collision-free point from the mob chest out to the authored hold anchor. */
    private static Vec3 safeHoldPoint(ServerLevel level, Mob mob, LivingEntity victim, Vec3 fwd, Vec3 right,
                                      double forward, double side, double up) {
        Vec3 base = mob.position();
        double halfW = victim.getBbWidth() * 0.5;
        double h = victim.getBbHeight();
        for (int step = 5; step >= 1; step--) {
            double frac = step / 5.0;
            Vec3 cand = base.add(fwd.scale(forward * frac)).add(right.scale(side * frac)).add(0.0, up, 0.0);
            AABB box = new AABB(cand.x - halfW, cand.y, cand.z - halfW, cand.x + halfW, cand.y + h, cand.z + halfW);
            if (level.noCollision(victim, box)) {
                return cand;
            }
        }
        return base.add(0.0, Math.min(up, 1.0), 0.0);
    }
}
