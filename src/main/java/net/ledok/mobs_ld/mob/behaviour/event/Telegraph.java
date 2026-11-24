package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.minecraft.util.StringRepresentable;

import java.util.Optional;

/**
 * Ground warning drawn before an event lands.
 *
 * @param from  seconds into the ability when it appears; empty = a default lead time before the impact
 * @param color warning colour as {@code #RRGGBB}; the impact flash is a brighter shade of it
 * @param style how it fills up until the impact (see {@link Style})
 * @param arrow rectangles (a {@code hit}'s rectangle, a {@code dash}'s strip) get an arrowhead past their far end that
 *              shows the direction; it is only a marking, nobody standing in it is hit. Other shapes ignore it
 */
public record Telegraph(boolean show, Optional<Double> from, int color, Style style, boolean arrow) {
    public static final int DEFAULT_COLOR = 0xB01818;
    public static final Telegraph DEFAULT = new Telegraph(true, Optional.empty(), DEFAULT_COLOR, Style.GROW, false);
    public static final Telegraph NONE = new Telegraph(false, Optional.empty(), DEFAULT_COLOR, Style.GROW, false);

    public static final Codec<Integer> HEX = Codec.STRING.comapFlatMap(text -> {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        try {
            return DataResult.success(Integer.parseInt(hex, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Bad telegraph colour '" + text + "', expected #RRGGBB");
        }
    }, value -> String.format("#%06X", value));

    /** The fields on their own, for blocks that add their own next to them (a summon's runes add a radius). */
    public static final MapCodec<Telegraph> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("show", true).forGetter(Telegraph::show),
            Codec.DOUBLE.optionalFieldOf("from").forGetter(Telegraph::from),
            HEX.optionalFieldOf("color", DEFAULT_COLOR).forGetter(Telegraph::color),
            Style.CODEC.optionalFieldOf("style", Style.GROW).forGetter(Telegraph::style),
            Codec.BOOL.optionalFieldOf("arrow", false).forGetter(Telegraph::arrow)
    ).apply(i, Telegraph::new));
    public static final Codec<Telegraph> CODEC = MAP_CODEC.codec();

    /** How a telegraph fills between appearing and the impact. */
    public enum Style implements StringRepresentable {
        /** The whole shape is drawn at once and grows from its origin (rectangles widen, circles and cones grow). */
        GROW("grow", AttackDisplayConfig.AnimationStyle.GROW),
        /**
         * Rectangles and dash strips fill along their length, from the origin toward the far end (and the arrowhead);
         * cones grow their radius. Circles behave as {@code grow}.
         */
        SWEEP("sweep", AttackDisplayConfig.AnimationStyle.SWEEP);

        public static final Codec<Style> CODEC = StringRepresentable.fromEnum(Style::values);
        private final String name;
        private final AttackDisplayConfig.AnimationStyle animation;

        Style(String name, AttackDisplayConfig.AnimationStyle animation) {
            this.name = name;
            this.animation = animation;
        }

        public AttackDisplayConfig.AnimationStyle animation() {
            return animation;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

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
