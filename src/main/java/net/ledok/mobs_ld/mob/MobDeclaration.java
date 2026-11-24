package net.ledok.mobs_ld.mob;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.MobCategory;

import java.util.Map;
import java.util.Optional;

/**
 * Startup half of a mob definition: everything that must exist before registries freeze — the entity type, its
 * hitbox and default attributes. Read from {@code mobs_ld/entities/<id>.json} inside the mod jar (see
 * {@link MobDeclarations}); clients load the same jar, so both sides agree on the entity registry.
 * Behaviour (abilities, movement, phases) is a separate, reloadable datapack layer.
 */
public record MobDeclaration(
        ResourceLocation id,
        MobCategory category,
        float width,
        float height,
        Optional<Float> eyeHeight,
        Body body,
        int trackingRange,
        boolean fireImmune,
        boolean pushable,
        int deathTicks,
        Map<ResourceLocation, Double> attributes,
        Optional<SpawnEgg> spawnEgg,
        Render render
) {
    /** How the mob moves through the world; fixes its navigation and move control at construction. */
    public enum Body implements StringRepresentable {
        GROUND("ground"),
        FLYING("flying");

        public static final Codec<Body> CODEC = StringRepresentable.fromEnum(Body::values);
        private final String name;

        Body(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record SpawnEgg(int primaryColor, int secondaryColor) {
        public static final Codec<SpawnEgg> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("primary").forGetter(SpawnEgg::primaryColor),
                Codec.INT.fieldOf("secondary").forGetter(SpawnEgg::secondaryColor)
        ).apply(i, SpawnEgg::new));
    }

    /**
     * Client-side model facts that come from the art, not from gameplay (see docs/models.md).
     *
     * @param walkBlocksPerCycle distance one {@code walk} loop covers; walk playback is scaled to match actual
     *                           ground speed. 0 plays the clip at authored speed.
     * @param scale              uniform model scale applied on top of the authored true-size model
     * @param shadowRadius       ground shadow radius in blocks; empty = half the hitbox width
     */
    public record Render(double walkBlocksPerCycle, float scale, Optional<Float> shadowRadius) {
        public static final Render DEFAULT = new Render(0.0, 1.0F, Optional.empty());
        public static final Codec<Render> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("walk_blocks_per_cycle", 0.0).forGetter(Render::walkBlocksPerCycle),
                Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Render::scale),
                Codec.FLOAT.optionalFieldOf("shadow_radius").forGetter(Render::shadowRadius)
        ).apply(i, Render::new));
    }

    /** Codec for the file body; the id comes from the file name. */
    public static Codec<MobDeclaration> codec(ResourceLocation id) {
        return RecordCodecBuilder.create(i -> i.group(
                MobCategory.CODEC.optionalFieldOf("category", MobCategory.MONSTER).forGetter(MobDeclaration::category),
                Codec.FLOAT.fieldOf("width").forGetter(MobDeclaration::width),
                Codec.FLOAT.fieldOf("height").forGetter(MobDeclaration::height),
                Codec.FLOAT.optionalFieldOf("eye_height").forGetter(MobDeclaration::eyeHeight),
                Body.CODEC.optionalFieldOf("body", Body.GROUND).forGetter(MobDeclaration::body),
                Codec.INT.optionalFieldOf("tracking_range", 10).forGetter(MobDeclaration::trackingRange),
                Codec.BOOL.optionalFieldOf("fire_immune", false).forGetter(MobDeclaration::fireImmune),
                Codec.BOOL.optionalFieldOf("pushable", true).forGetter(MobDeclaration::pushable),
                Codec.INT.optionalFieldOf("death_ticks", 20).forGetter(MobDeclaration::deathTicks),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("attributes", Map.of())
                        .forGetter(MobDeclaration::attributes),
                SpawnEgg.CODEC.optionalFieldOf("spawn_egg").forGetter(MobDeclaration::spawnEgg),
                Render.CODEC.optionalFieldOf("render", Render.DEFAULT).forGetter(MobDeclaration::render)
        ).apply(i, (category, width, height, eyeHeight, body, trackingRange, fireImmune, pushable, deathTicks, attributes,
                    spawnEgg, render) -> new MobDeclaration(id, category, width, height, eyeHeight, body,
                trackingRange, fireImmune, pushable, deathTicks, attributes, spawnEgg, render)));
    }
}
