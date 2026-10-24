package net.ledok.mobs_ld.mob.behaviour;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * A time in seconds that may vary: written as a number ({@code 4.0}) or as a range ({@code [4, 5]}), from which a
 * fresh value is rolled every time it is used (each re-pick, each shot), so repeating timers drift apart instead of
 * firing in lockstep.
 */
public record Seconds(double min, double max) {
    /** Longest time any Seconds field accepts (10 minutes). */
    private static final double LIMIT = 600.0;

    public static final Codec<Seconds> CODEC = Codec.either(Codec.DOUBLE, Codec.DOUBLE.listOf()).comapFlatMap(
            Seconds::parse,
            seconds -> seconds.fixed() ? Either.left(seconds.min) : Either.right(List.of(seconds.min, seconds.max)));

    public static Seconds of(double seconds) {
        return new Seconds(seconds, seconds);
    }

    private static DataResult<Seconds> parse(Either<Double, List<Double>> either) {
        return either.map(
                value -> check(value, value),
                range -> range.size() == 2 ? check(range.get(0), range.get(1))
                        : DataResult.error(() -> "A time range must be [min, max], got " + range));
    }

    private static DataResult<Seconds> check(double min, double max) {
        if (!(min >= 0.0) || !(max <= LIMIT)) {
            return DataResult.error(() -> "A time must be between 0 and " + LIMIT + " seconds, got " + describe(min, max));
        }
        if (max < min) {
            return DataResult.error(() -> "A time range [" + min + ", " + max + "] ends before it starts");
        }
        return DataResult.success(new Seconds(min, max));
    }

    /** Whether it always rolls the same value. */
    public boolean fixed() {
        return min == max;
    }

    /** A value in {@code [min, max]}; {@code unit} gives uniform numbers in {@code [0, 1)} (e.g. a mob's random). */
    public double roll(DoubleSupplier unit) {
        return fixed() ? min : min + (max - min) * unit.getAsDouble();
    }

    /** {@link #roll} in whole server ticks (at least 1 for a non-zero time). */
    public int rollTicks(DoubleSupplier unit) {
        double seconds = roll(unit);
        return seconds <= 0.0 ? 0 : Math.max(1, (int) Math.round(seconds * 20.0));
    }

    @Override
    public String toString() {
        return describe(min, max);
    }

    private static String describe(double min, double max) {
        return min == max ? min + " s" : "[" + min + ", " + max + "] s";
    }
}
