package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Ground warning drawn before an event lands.
 *
 * @param from  seconds into the ability when it appears; empty = a default lead time before the impact
 * @param color warning colour as {@code #RRGGBB}; the impact flash is a brighter shade of it
 */
public record Telegraph(boolean show, Optional<Double> from, int color) {
    public static final int DEFAULT_COLOR = 0xB01818;
    public static final Telegraph DEFAULT = new Telegraph(true, Optional.empty(), DEFAULT_COLOR);
    public static final Telegraph NONE = new Telegraph(false, Optional.empty(), DEFAULT_COLOR);

    private static final Codec<Integer> HEX = Codec.STRING.comapFlatMap(text -> {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        try {
            return DataResult.success(Integer.parseInt(hex, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Bad telegraph colour '" + text + "', expected #RRGGBB");
        }
    }, value -> String.format("#%06X", value));

    public static final Codec<Telegraph> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("show", true).forGetter(Telegraph::show),
            Codec.DOUBLE.optionalFieldOf("from").forGetter(Telegraph::from),
            HEX.optionalFieldOf("color", DEFAULT_COLOR).forGetter(Telegraph::color)
    ).apply(i, Telegraph::new));

    /** When the warning appears for an impact at {@code at}, given a default lead time. */
    public double start(double at, double defaultLead) {
        return Math.min(at, from.orElse(Math.max(0.0, at - defaultLead)));
    }

    public int prepareColor() {
        return 0xFF000000 | color;
    }

    public int invokeColor() {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 0x50);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 0x30);
        int b = Math.min(255, (color & 0xFF) + 0x30);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
