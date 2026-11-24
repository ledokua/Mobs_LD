package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.CombatTargets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Optional;

/** A bound add reduces its summoner's incoming damage while channeling within range and line of sight. */
public record SummonerWardEvent(double at, Optional<Double> until, double range, double multiplier,
                                Optional<String> onDisconnect) implements AbilityEvent {
    public static final MapCodec<SummonerWardEvent> CODEC = RecordCodecBuilder.<SummonerWardEvent>mapCodec(i -> i.group(
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("at").forGetter(SummonerWardEvent::at),
            Codec.doubleRange(0.0, Double.MAX_VALUE).optionalFieldOf("until").forGetter(SummonerWardEvent::until),
            Codec.doubleRange(0.1, 32.0).fieldOf("range").forGetter(SummonerWardEvent::range),
            Codec.doubleRange(0.05, 0.99).fieldOf("multiplier").forGetter(SummonerWardEvent::multiplier),
            Codec.STRING.optionalFieldOf("on_disconnect").forGetter(SummonerWardEvent::onDisconnect)
    ).apply(i, SummonerWardEvent::new)).validate(event -> event.until().isPresent() && event.until().get() <= event.at()
            ? DataResult.error(() -> "summoner_ward needs until greater than at") : DataResult.success(event));

    @Override
    public String type() {
        return "summoner_ward";
    }

    @Override
    public double begin() {
        return at;
    }

    @Override
    public double end() {
        // A sustained ward can end as soon as it opens if it never finds a connection.
        return until.orElse(at);
    }

    @Override
    public List<String> chains() {
        return onDisconnect.stream().toList();
    }

    public boolean inRange(Mob add, LivingEntity summoner) {
        return add.distanceToSqr(summoner) <= range * range;
    }

    @Override
    public Instance start(AbilityRun run) {
        return new Instance() {
            private boolean disconnected;

            @Override
            public boolean readyToFinish(AbilityRun run, double scheduledEnd) {
                Mob add = run.mob();
                LivingEntity aim = run.aim();
                if (until.isEmpty() || onDisconnect.isPresent()) {
                    disconnected = aim == null || CombatTargets.summoner(add) != aim
                            || !inRange(add, aim) || !add.getSensing().hasLineOfSight(aim);
                }
                return disconnected || until.isPresent() && run.seconds() >= scheduledEnd;
            }

            @Override
            public void finish(AbilityRun run) {
                if (disconnected) {
                    onDisconnect.ifPresent(run::chain);
                }
            }
        };
    }
}
