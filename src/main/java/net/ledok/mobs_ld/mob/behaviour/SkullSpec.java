package net.ledok.mobs_ld.mob.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.event.HitSpec;

import java.util.Optional;

/**
 * A named kind of skull a host fires (the Wither's {@code host.skulls}): a vanilla wither skull whose hits Mobs_LD
 * decides. The heads fire the specs named {@code center} and {@code side} on their own cadence between abilities; a
 * {@code projectile} event fires any spec by name. The spec is looked up again when a skull lands, so a reload or a
 * phase change applies to skulls already in flight.
 *
 * @param every  cadence of a head firing it on its own (rolled per shot); empty = only fired by events
 * @param speed  flight speed in blocks per tick, constant from launch (vanilla's skull settles at 1.9)
 * @param range  a head only fires it at targets within this many blocks
 * @param hit    what a direct hit does (its {@code amount} times R; effects only when the hit lands); knockback is
 *               vanilla's, the spec's {@code knockback} / {@code knock_up} are not used
 * @param splash damage to the enemies around the impact, other than the one hit directly; empty = none
 */
public record SkullSpec(Optional<Seconds> every, double speed, double range, HitSpec hit, Optional<Splash> splash) {
    /** Vanilla wither skull speed once it has settled. */
    public static final double VANILLA_SPEED = 1.9;

    /** Shortest cadence a head may fire a spec at. */
    private static final double MIN_EVERY = 0.25;

    public static final Codec<SkullSpec> CODEC = RecordCodecBuilder.<SkullSpec>create(i -> i.group(
            Seconds.CODEC.optionalFieldOf("every").forGetter(SkullSpec::every),
            // Above 3.9 blocks per tick the spawn packet clamps the velocity, and clients would see it lag behind.
            Codec.doubleRange(0.05, 3.9).optionalFieldOf("speed", VANILLA_SPEED).forGetter(SkullSpec::speed),
            Codec.doubleRange(1.0, 128.0).optionalFieldOf("range", 30.0).forGetter(SkullSpec::range),
            HitSpec.CODEC.fieldOf("hit").forGetter(SkullSpec::hit),
            Splash.CODEC.optionalFieldOf("splash").forGetter(SkullSpec::splash)
    ).apply(i, SkullSpec::new)).validate(spec -> spec.every().filter(every -> every.min() < MIN_EVERY).isPresent()
            ? DataResult.error(() -> "A skull's 'every' must be at least " + MIN_EVERY + " s, got " + spec.every().get())
            : DataResult.success(spec));

    /**
     * The blast where a skull lands, in place of vanilla's block-breaking explosion: {@code amount} (times R, like
     * {@code hit.amount}) to every enemy within {@code radius} blocks of the impact that the blast can see, dealt as
     * vanilla's skull blast (an explosion caused by the owner).
     */
    public record Splash(double radius, double amount) {
        public static final Codec<Splash> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0.0, 16.0).optionalFieldOf("radius", 1.5).forGetter(Splash::radius),
                Codec.doubleRange(0.0, 1.0e6).fieldOf("amount").forGetter(Splash::amount)
        ).apply(i, Splash::new));
    }
}
