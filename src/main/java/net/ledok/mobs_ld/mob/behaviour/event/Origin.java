package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Where an area event is anchored: on the mob, or where its aim target stood when the event began. */
public enum Origin implements StringRepresentable {
    SELF("self"),
    TARGET("target");

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
