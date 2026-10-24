package net.ledok.mobs_ld.mob.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Ground area an ability event affects, in blocks, relative to an origin and a facing. Doubles as the telegraph
 * drawn before the hit (via {@link #zone()}).
 */
public sealed interface Shape permits Shape.Rectangle, Shape.Cone, Shape.Circle, Shape.Rays {
    Map<String, MapCodec<? extends Shape>> TYPES = Map.of(
            "rectangle", Rectangle.CODEC,
            "cone", Cone.CODEC,
            "circle", Circle.CODEC,
            "rays", Rays.CODEC
    );

    /** An unknown {@code type} is a decoding error (the file is reported and skipped), never an exception. */
    Codec<Shape> CODEC = Codec.STRING.partialDispatch("type", shape -> DataResult.success(shape.type()), type -> {
        MapCodec<? extends Shape> codec = TYPES.get(type);
        return codec != null ? DataResult.success(codec)
                : DataResult.error(() -> "Unknown shape type '" + type + "' (expected one of " + TYPES.keySet() + ")");
    });

    String type();

    /** The telegraph zone drawn for this shape. */
    AttackZone zone();

    /**
     * Whether a point, given as {@code forward}/{@code right} distances from the origin along the facing, is inside.
     */
    boolean contains(double forward, double right);

    /** Furthest distance from the origin the shape reaches; used for cheap entity searches. */
    double reach();

    /** A strip {@code width} wide, from {@code offset} to {@code offset + length} ahead of the origin. */
    record Rectangle(double width, double length, double offset) implements Shape {
        static final MapCodec<Rectangle> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("width").forGetter(Rectangle::width),
                Codec.DOUBLE.fieldOf("length").forGetter(Rectangle::length),
                Codec.DOUBLE.optionalFieldOf("offset", 0.0).forGetter(Rectangle::offset)
        ).apply(i, Rectangle::new));

        @Override
        public String type() {
            return "rectangle";
        }

        @Override
        public AttackZone zone() {
            return new AttackZone.Rectangle((float) width, (float) length, (float) offset);
        }

        @Override
        public boolean contains(double forward, double right) {
            return forward >= offset && forward <= offset + length && Math.abs(right) <= width / 2.0;
        }

        @Override
        public double reach() {
            return Math.hypot(offset + length, width / 2.0);
        }
    }

    /** A wedge {@code angle} degrees wide and {@code radius} long, centred on the facing. */
    record Cone(double radius, double angle) implements Shape {
        static final MapCodec<Cone> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("radius").forGetter(Cone::radius),
                Codec.DOUBLE.fieldOf("angle").forGetter(Cone::angle)
        ).apply(i, Cone::new));

        @Override
        public String type() {
            return "cone";
        }

        @Override
        public AttackZone zone() {
            return new AttackZone.Cone((float) radius, (float) angle);
        }

        @Override
        public boolean contains(double forward, double right) {
            double distance = Math.hypot(forward, right);
            if (distance > radius) {
                return false;
            }
            if (distance < 1.0e-6) {
                return true; // standing on the origin counts as inside
            }
            return Math.toDegrees(Math.acos(forward / distance)) <= angle / 2.0;
        }

        @Override
        public double reach() {
            return radius;
        }
    }

    record Circle(double radius) implements Shape {
        static final MapCodec<Circle> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("radius").forGetter(Circle::radius)
        ).apply(i, Circle::new));

        @Override
        public String type() {
            return "circle";
        }

        @Override
        public AttackZone zone() {
            return new AttackZone.Circle((float) radius);
        }

        @Override
        public boolean contains(double forward, double right) {
            return forward * forward + right * right <= radius * radius;
        }

        @Override
        public double reach() {
            return radius;
        }
    }

    /** {@code count} lines radiating from the origin (an even count; opposite rays form one line). */
    record Rays(int count, double length, double width) implements Shape {
        static final MapCodec<Rays> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("count").forGetter(Rays::count),
                Codec.DOUBLE.fieldOf("length").forGetter(Rays::length),
                Codec.DOUBLE.fieldOf("width").forGetter(Rays::width)
        ).apply(i, Rays::new));

        @Override
        public String type() {
            return "rays";
        }

        @Override
        public AttackZone zone() {
            return new AttackZone.CircleRays(count, (float) length, (float) width);
        }

        @Override
        public boolean contains(double forward, double right) {
            AttackZone.CircleRays rays = (AttackZone.CircleRays) zone(); // normalised (even) count
            int lines = Math.max(1, rays.rayCount() / 2);
            for (int i = 0; i < lines; i++) {
                double yaw = Math.toRadians(i * (180.0 / lines));
                Vec3 along = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
                double alongRay = Math.abs(forward * along.x + right * along.z);
                double acrossRay = Math.abs(-forward * along.z + right * along.x);
                if (alongRay <= length && acrossRay <= width / 2.0) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public double reach() {
            return Math.hypot(length, width / 2.0);
        }
    }
}
