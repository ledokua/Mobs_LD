package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Draws a shape on the ground that fills until {@code at} and deals nothing: a warning for something else on the
 * timeline (the paths a {@code projectile} event with {@code directions} will fly). Laid out like a {@code hit}: on the
 * mob, {@code offset} blocks ahead, turned by {@code rotate} degrees from the facing the ability locked (unset: the
 * current facing).
 */
public record TelegraphEvent(double at, Shape shape, double offset, Optional<Double> rotate, Telegraph telegraph,
                             Optional<Repeat> repeat) implements AbilityEvent {
    private static final double DEFAULT_LEAD = 0.8;

    public static final MapCodec<TelegraphEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(TelegraphEvent::at),
            Shape.CODEC.fieldOf("shape").forGetter(TelegraphEvent::shape),
            Codec.DOUBLE.optionalFieldOf("offset", 0.0).forGetter(TelegraphEvent::offset),
            Codec.DOUBLE.optionalFieldOf("rotate").forGetter(TelegraphEvent::rotate),
            Telegraph.CODEC.optionalFieldOf("telegraph", Telegraph.DEFAULT).forGetter(TelegraphEvent::telegraph),
            Repeat.CODEC.optionalFieldOf("repeat").forGetter(TelegraphEvent::repeat)
    ).apply(i, TelegraphEvent::new));

    /**
     * {@code count} copies of the shape, copy {@code k} turned {@code k x rotate} degrees further (positive =
     * clockwise from above) and drawn {@code k x step} seconds later, each filling until its own impact: lines
     * swept around the mob one after another.
     */
    public record Repeat(int count, double rotate, double step) {
        public static final Codec<Repeat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 128).fieldOf("count").forGetter(Repeat::count),
                Codec.DOUBLE.fieldOf("rotate").forGetter(Repeat::rotate),
                Codec.doubleRange(0.0, 10.0).optionalFieldOf("step", 0.0).forGetter(Repeat::step)
        ).apply(i, Repeat::new));
    }

    @Override
    public String type() {
        return "telegraph";
    }

    @Override
    public double begin() {
        return telegraph.start(at, DEFAULT_LEAD);
    }

    @Override
    public double end() {
        return at + repeat.map(copies -> (copies.count() - 1) * copies.step()).orElse(0.0);
    }

    @Override
    public Instance start(AbilityRun run) {
        float yaw = rotate.map(degrees -> (float) (run.lockedYaw() + degrees)).orElse(run.yaw());
        int count = repeat.map(Repeat::count).orElse(1);
        double turn = repeat.map(Repeat::rotate).orElse(0.0);
        double step = repeat.map(Repeat::step).orElse(0.0);
        double lead = at - begin();
        return new Instance() {
            private final AbilityRun.Marker[] markers = new AbilityRun.Marker[count];
            private final boolean[] flashed = new boolean[count];

            @Override
            public void tick(AbilityRun run) {
                double now = run.seconds();
                for (int k = 0; k < count; k++) {
                    double impact = at + k * step;
                    if (markers[k] == null && now >= impact - lead) {
                        float copyYaw = (float) (yaw + k * turn);
                        Vec3 origin = run.groundAt(run.mob().position().add(AbilityRun.forward(copyYaw).scale(offset)));
                        markers[k] = run.telegraph(List.of(AbilityRun.zoneOf(origin, copyYaw, shape, telegraph)),
                                telegraph, impact);
                    }
                    if (markers[k] != null && !flashed[k] && now >= impact) {
                        flashed[k] = true;
                        run.flash(markers[k]);
                    }
                }
            }

            @Override
            public void finish(AbilityRun run) {
                tick(run);
            }

            @Override
            public void cancel(AbilityRun run) {
                for (AbilityRun.Marker marker : markers) {
                    run.clear(marker);
                }
            }
        };
    }
}
