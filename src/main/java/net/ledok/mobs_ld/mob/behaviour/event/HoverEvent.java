package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Flies the body to a height and holds it there from {@code from} to {@code until}: every tick the velocity is set
 * toward a point {@code height} blocks above the reference, at most {@code rate} blocks per tick, so a flyer is pinned
 * in place whatever its move control does with gravity. Horizontally it holds still, unless {@code retreatTo} is
 * set: then it also backs off until it is that far from the aim. Made for flying hosts (the Wither, flying LdMobs);
 * a walker would float.
 *
 * @param relativeTo what {@code height} is measured from (see {@link Reference})
 * @param rate       top speed in blocks per tick (faster with the phase's attack speed, so it keeps to the timeline)
 * @param retreatTo  horizontal distance from the aim it backs off to, if closer
 * @param home       also fly horizontally to the mob's home (where it first ticked: an Arenas_LD arena's spawn point),
 *                   instead of holding still or backing off
 */
public record HoverEvent(double from, double until, double height, Reference relativeTo, double rate,
                         Optional<Double> retreatTo, boolean home) implements AbilityEvent {
    public static final MapCodec<HoverEvent> CODEC = RecordCodecBuilder.<HoverEvent>mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("from").forGetter(HoverEvent::from),
            Codec.DOUBLE.fieldOf("until").forGetter(HoverEvent::until),
            Codec.doubleRange(-64.0, 64.0).fieldOf("height").forGetter(HoverEvent::height),
            Reference.CODEC.optionalFieldOf("relative_to", Reference.GROUND).forGetter(HoverEvent::relativeTo),
            Codec.doubleRange(0.01, 4.0).optionalFieldOf("rate", 0.4).forGetter(HoverEvent::rate),
            Codec.doubleRange(0.0, 64.0).optionalFieldOf("retreat_to").forGetter(HoverEvent::retreatTo),
            Codec.BOOL.optionalFieldOf("home", false).forGetter(HoverEvent::home)
    ).apply(i, HoverEvent::new)).validate(HoverEvent::validate);

    private static DataResult<HoverEvent> validate(HoverEvent event) {
        return event.until < event.from
                ? DataResult.error(() -> "A hover event ends (until " + event.until + ") before it starts (from " + event.from + ")")
                : DataResult.success(event);
    }

    /** What a hover's height is measured from. */
    public enum Reference implements StringRepresentable {
        /** The floor under the mob (deep scan: {@code movement.ground_scan}); over the void it keeps its altitude. */
        GROUND("ground"),
        /** The aim's feet: tracked until {@code track_until}, then where it stood. */
        AIM("aim"),
        /** The mob's own height when the hover began (e.g. rise 2 blocks). */
        SELF("self");

        public static final Codec<Reference> CODEC = StringRepresentable.fromEnum(Reference::values);
        private final String name;

        Reference(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @Override
    public String type() {
        return "hover";
    }

    @Override
    public double begin() {
        return from;
    }

    @Override
    public double end() {
        return until;
    }

    @Override
    public Instance start(AbilityRun run) {
        Mob mob = run.mob();
        double startY = mob.getY();
        return new Instance() {
            @Override
            public void tick(AbilityRun run) {
                Vec3 position = mob.position();
                double targetY = switch (relativeTo) {
                    // Scanned from the feet down: the blocks the body itself fills are not its floor.
                    case GROUND -> run.groundBelow(position, 0).map(ground -> ground.y + height).orElse(position.y);
                    case AIM -> run.aimPosition().y + height;
                    case SELF -> startY + height;
                };
                double targetX = position.x;
                double targetZ = position.z;
                Vec3 homePoint = home ? run.combat().home() : null;
                if (homePoint != null) {
                    targetX = homePoint.x;
                    targetZ = homePoint.z;
                } else if (retreatTo.isPresent()) {
                    Vec3 aim = run.aimPosition();
                    double dx = position.x - aim.x;
                    double dz = position.z - aim.z;
                    double distance = Math.hypot(dx, dz);
                    if (distance < retreatTo.get()) {
                        if (distance < 1.0e-3) {
                            // Right above the aim: back off the way it faces away from.
                            Vec3 back = AbilityRun.forward(run.yaw()).scale(-1.0);
                            dx = back.x;
                            dz = back.z;
                            distance = 1.0;
                        }
                        targetX = aim.x + dx / distance * retreatTo.get();
                        targetZ = aim.z + dz / distance * retreatTo.get();
                    }
                }
                Vec3 delta = new Vec3(targetX - position.x, targetY - position.y, targetZ - position.z);
                double limit = rate * run.speed();
                double length = delta.length();
                Vec3 velocity = length > limit ? delta.scale(limit / length) : delta;
                run.moveBody(velocity.x, velocity.y, velocity.z);
            }
        };
    }
}
