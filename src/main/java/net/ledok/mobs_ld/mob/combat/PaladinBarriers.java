package net.ledok.mobs_ld.mob.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** Optional integration through the barrier's registered entity type and vanilla collision contract. */
public final class PaladinBarriers {
    private static final ResourceLocation BARRIER = ResourceLocation.fromNamespaceAndPath("paladins", "barrier");
    private static final ResourceLocation IMPACT = ResourceLocation.fromNamespaceAndPath("paladins", "holy_barrier_impact");

    private PaladinBarriers() {
    }

    public static boolean available() {
        return BuiltInRegistries.ENTITY_TYPE.containsKey(BARRIER);
    }

    static List<Entity> between(AbilityRun run, AABB bounds) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(BARRIER).map(type -> run.level().getEntities(run.mob(), bounds,
                entity -> entity.getType() == type && entity.isAlive() && entity.canCollideWith(run.mob())))
                .orElseGet(List::of);
    }

    public static Optional<DashContact> dashContact(AbilityRun run, AABB body, Vec3 movement) {
        DashContact nearest = null;
        for (Entity barrier : between(run, body.expandTowards(movement).inflate(1.0e-6))) {
            AABB box = barrier.getBoundingBox();
            double fraction = sweptFraction(body, movement, box);
            if (fraction <= 1.0 && (nearest == null || fraction < nearest.fraction())) {
                nearest = new DashContact(fraction, closestPoint(box, body.getCenter().add(movement.scale(fraction))));
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** Sweep the centre of the moving body against the obstacle expanded by the body's half extents. */
    static double sweptFraction(AABB body, Vec3 movement, AABB obstacle) {
        Vec3 center = body.getCenter();
        AABB expanded = obstacle.inflate(body.getXsize() / 2.0, body.getYsize() / 2.0, body.getZsize() / 2.0);
        double[] interval = {0.0, 1.0};
        if (clipAxis(interval, center.x, movement.x, expanded.minX, expanded.maxX)
                && clipAxis(interval, center.y, movement.y, expanded.minY, expanded.maxY)
                && clipAxis(interval, center.z, movement.z, expanded.minZ, expanded.maxZ)) {
            return interval[0];
        }
        return Double.POSITIVE_INFINITY;
    }

    private static boolean clipAxis(double[] interval, double start, double delta, double min, double max) {
        if (delta == 0.0) {
            return start > min && start < max;
        }
        // A body touching the far surface while departing it is free to leave.
        if ((start >= max && delta > 0.0) || (start <= min && delta < 0.0)) {
            return false;
        }
        double a = (min - start) / delta;
        double b = (max - start) / delta;
        interval[0] = Math.max(interval[0], Math.min(a, b));
        interval[1] = Math.min(interval[1], Math.max(a, b));
        return interval[0] <= interval[1];
    }

    static Vec3 closestPoint(AABB bounds, Vec3 point) {
        return new Vec3(Mth.clamp(point.x, bounds.minX, bounds.maxX), Mth.clamp(point.y, bounds.minY, bounds.maxY),
                Mth.clamp(point.z, bounds.minZ, bounds.maxZ));
    }

    public static void playImpact(AbilityRun run, Vec3 point) {
        var sound = BuiltInRegistries.SOUND_EVENT.getOptional(IMPACT).orElse(SoundEvents.SHIELD_BLOCK);
        run.level().playSound(null, point.x, point.y, point.z, sound, run.mob().getSoundSource(), 1.2F, 1.0F);
        run.level().sendParticles(ParticleTypes.ENCHANTED_HIT, point.x, point.y, point.z, 16, 0.3, 0.45, 0.3, 0.15);
    }

    public record DashContact(double fraction, Vec3 point) {
    }
}
