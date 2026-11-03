package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Where an area event is anchored: on the mob, where its aim target stood when the event began, or (a {@code hit}
 * only) under each fighting player.
 */
public enum Origin implements StringRepresentable {
    SELF("self"),
    TARGET("target"),
    /** One zone under each fighting player (see {@link net.ledok.mobs_ld.mob.combat.Fighters}); {@code hit} only. */
    EACH_FIGHTER("each_fighter");

    public static final Codec<Origin> CODEC = StringRepresentable.fromEnum(Origin::values);
    private final String name;

    Origin(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
