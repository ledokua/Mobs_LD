package net.ledok.mobs_ld.mob.host;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.EnumSet;
import java.util.Set;

/**
 * A head of a host, as {@code look}, {@code fx} and {@code projectile} events name them. A host with one head (most
 * mobs) has only {@link #CENTER}, at its eyes; the Wither has three. Right and left are the host's own (its right
 * when it faces you is on your left).
 */
public enum Head {
    CENTER,
    RIGHT,
    LEFT;

    /** A set of heads as written in events: {@code all} (or {@code heads}), {@code side}, {@code center}, {@code right}, {@code left}. */
    public enum Selector {
        ALL("all", EnumSet.allOf(Head.class)),
        SIDE("side", EnumSet.of(Head.RIGHT, Head.LEFT)),
        CENTER("center", EnumSet.of(Head.CENTER)),
        RIGHT("right", EnumSet.of(Head.RIGHT)),
        LEFT("left", EnumSet.of(Head.LEFT));

        public static final Codec<Selector> CODEC = Codec.STRING.comapFlatMap(Selector::parse, Selector::serializedName);
        private final String name;
        private final Set<Head> heads;

        Selector(String name, Set<Head> heads) {
            this.name = name;
            this.heads = heads;
        }

        private static DataResult<Selector> parse(String name) {
            if (name.equals("heads")) {
                return DataResult.success(ALL);
            }
            for (Selector selector : values()) {
                if (selector.name.equals(name)) {
                    return DataResult.success(selector);
                }
            }
            return DataResult.error(() -> "Unknown heads '" + name + "' (expected all, heads, side, center, right or left)");
        }

        public String serializedName() {
            return name;
        }

        /** The heads selected, in the order center, right, left. */
        public Set<Head> heads() {
            return heads;
        }
    }
}
