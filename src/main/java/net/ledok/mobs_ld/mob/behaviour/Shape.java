package net.ledok.mobs_ld.mob.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Points every {@code every} blocks along the shape's centre line(s), as {@code {forward, right}} from the origin
     * (an {@code fx} with particles {@code at: along}): a rectangle's or cone's middle line, every ray of {@code rays},
     * a circle's rim.
     */
    List<double[]> centreLine(double every);

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

        @Override
        public List<double[]> centreLine(double every) {
            List<double[]> points = new ArrayList<>();
            for (double s = offset + every; s <= offset + length + 1.0e-6; s += every) {
                points.add(new double[]{s, 0.0});
            }
            return points;
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

        @Override
        public List<double[]> centreLine(double every) {
            List<double[]> points = new ArrayList<>();
            for (double s = every; s <= radius + 1.0e-6; s += every) {
                points.add(new double[]{s, 0.0});
            }
            return points;
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

        @Override
        public List<double[]> centreLine(double every) {
            int count = Math.max(6, (int) Math.ceil(2.0 * Math.PI * radius / every));
            List<double[]> points = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                double angle = 2.0 * Math.PI * i / count;
                points.add(new double[]{radius * Math.cos(angle), radius * Math.sin(angle)});
            }
            return points;
        }
    }

    /**
     * {@code count} lines radiating from the origin (an even count; opposite rays form one line), the first on the
     * facing. A lane is {@code width} wide at the origin and {@code widen} blocks wider per block along it (0 = straight
     * lanes; drawn and tested as trapezoids).
     */
    record Rays(int count, double length, double width, double widen) implements Shape {
        /** Longest widening lane (each way), so its telegraph stays cheap to draw. */
        public static final double MAX_WIDENED_LENGTH = 48.0;
        static final MapCodec<Rays> CODEC = RecordCodecBuilder.<Rays>mapCodec(i -> i.group(
                Codec.INT.fieldOf("count").forGetter(Rays::count),
                Codec.DOUBLE.fieldOf("length").forGetter(Rays::length),
                Codec.DOUBLE.fieldOf("width").forGetter(Rays::width),
                Codec.doubleRange(0.0, 16.0).optionalFieldOf("widen", 0.0).forGetter(Rays::widen)
        ).apply(i, Rays::new)).validate(rays -> rays.widen() > 0.0 && rays.length() > MAX_WIDENED_LENGTH
                ? DataResult.error(() -> "Widening rays reach at most " + MAX_WIDENED_LENGTH + " blocks each way, got "
                + rays.length())
                : DataResult.success(rays));

        /** Straight lanes (no {@code widen}). */
        public Rays(int count, double length, double width) {
            this(count, length, width, 0.0);
        }

        @Override
        public String type() {
            return "rays";
        }

        @Override
        public AttackZone zone() {
            return new AttackZone.CircleRays(count, (float) length, (float) width, (float) widen);
        }

        /** The lane's full width {@code distance} blocks along it from the origin. */
        public double widthAt(double distance) {
            return width + widen * Math.abs(distance);
        }

        @Override
        public boolean contains(double forward, double right) {
            int lines = lines();
            for (int i = 0; i < lines; i++) {
                double yaw = Math.toRadians(i * (180.0 / lines));
                Vec3 along = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
                double alongRay = Math.abs(forward * along.x + right * along.z);
                double acrossRay = Math.abs(-forward * along.z + right * along.x);
                if (alongRay <= length && acrossRay <= widthAt(alongRay) / 2.0) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public double reach() {
            return Math.hypot(length, widthAt(length) / 2.0);
        }

        /** Lines through the origin (half the normalised, even ray count). */
        private int lines() {
            return Math.max(1, ((AttackZone.CircleRays) zone()).rayCount() / 2);
        }

        @Override
        public List<double[]> centreLine(double every) {
            int rays = lines() * 2;
            List<double[]> points = new ArrayList<>();
            for (int i = 0; i < rays; i++) {
                double angle = Math.toRadians(i * (360.0 / rays));
                for (double s = every; s <= length + 1.0e-6; s += every) {
                    points.add(new double[]{s * Math.cos(angle), s * Math.sin(angle)});
                }
            }
            return points;
        }
    }
}
