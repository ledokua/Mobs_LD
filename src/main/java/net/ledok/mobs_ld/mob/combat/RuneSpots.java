package net.ledok.mobs_ld.mob.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Optional;

/**
 * Where a summon's adds may rise ({@code placement}): a spot with a solid floor, {@code clearance} open blocks above it
 * (no collision, no fluid) and no fluid on the way down to it, at least {@code min_gap} from the other runes of the wave.
 * The floor is found by scanning down from one block above the centre's feet (so a step up is fine, but a tunnel's
 * roof is never mistaken for the floor), at most {@code movement.ground_scan} blocks; nothing within that is the void.
 * The rules themselves are pure (see {@link Terrain}), so they are unit-tested.
 */
public final class RuneSpots {
    /** Random spots tried around a rune's centre before it falls back to the summoner's ground shadow. */
    public static final int TRIES = 8;
    /** How far from the summoner's ground shadow the fallback spot lies. */
    private static final double SHADOW_MIN = 1.5;
    private static final double SHADOW_MAX = 3.0;

    private RuneSpots() {
    }

    /** What the spot rules need to know about one block. */
    public interface Terrain {
        /** The block has a collision shape (something to stand on, or to bump into). */
        boolean solid(int x, int y, int z);

        /** Top of the block's collision shape above its own y (only asked where {@link #solid}). */
        double top(int x, int y, int z);

        /** The block holds a fluid (water, lava, a waterlogged block). */
        boolean fluid(int x, int y, int z);
    }

    // --- Rules (pure) ---

    /**
     * The feet height of the spot in column ({@code x}, {@code z}): scanning down from {@code fromY} (at most
     * {@code scan} blocks), the first solid block is the floor; its spot is valid if no fluid was met on the way or is
     * in it, and the {@code clearance} blocks above it are open. NaN: no valid spot (over fluid, too cramped, or
     * nothing to stand on within the scan, e.g. the void).
     */
    public static double floor(Terrain terrain, int x, int z, int fromY, int scan, int clearance) {
        for (int y = fromY; y >= fromY - scan; y--) {
            if (terrain.fluid(x, y, z)) {
                return Double.NaN; // a lake, a lava sea or a waterlogged floor: nobody rises there
            }
            if (terrain.solid(x, y, z)) {
                return open(terrain, x, y + 1, z, clearance) ? y + terrain.top(x, y, z) : Double.NaN;
            }
        }
        return Double.NaN;
    }

    /** Whether the {@code clearance} blocks from {@code y} up are free of collision and fluid. */
    public static boolean open(Terrain terrain, int x, int y, int z, int clearance) {
        for (int up = 0; up < clearance; up++) {
            if (terrain.solid(x, y + up, z) || terrain.fluid(x, y + up, z)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a spot is at least {@code minGap} blocks from every spot already {@code taken}. */
    public static boolean apart(List<Vec3> taken, Vec3 spot, double minGap) {
        for (Vec3 other : taken) {
            if (other.distanceToSqr(spot) < minGap * minGap) {
                return false;
            }
        }
        return true;
    }

    // --- In the level ---

    /**
     * A valid spot around {@code centre}: {@value #TRIES} tries at a random angle and a distance in
     * [{@code minRadius}, {@code maxRadius}]. With {@code ground} the spot lies on the floor below; without, at the
     * centre's height in open air.
     */
    public static Optional<Vec3> around(ServerLevel level, RandomSource random, Vec3 centre, double minRadius,
                                        double maxRadius, List<Vec3> taken, double minGap, int clearance, boolean ground,
                                        int scan) {
        for (int attempt = 0; attempt < TRIES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = minRadius + random.nextDouble() * Math.max(0.0, maxRadius - minRadius);
            Optional<Vec3> spot = at(level, centre.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance),
                    clearance, ground, scan);
            if (spot.isPresent() && apart(taken, spot.get(), minGap)) {
                return spot;
            }
        }
        return Optional.empty();
    }

    /**
     * The fallback: a valid spot next to {@code shadow} (the summoner's ground shadow), keeping {@code minGap} from the
     * other runes if it can; empty if there is none (the summoner hangs over the void, a lava sea, a cramped hole).
     */
    public static Optional<Vec3> nearShadow(ServerLevel level, RandomSource random, Vec3 shadow, List<Vec3> taken,
                                            double minGap, int clearance, boolean ground, int scan) {
        Vec3 crowded = null;
        for (int attempt = 0; attempt < TRIES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = SHADOW_MIN + random.nextDouble() * (SHADOW_MAX - SHADOW_MIN);
            Optional<Vec3> spot = at(level, shadow.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance),
                    clearance, ground, scan);
            if (spot.isEmpty()) {
                continue;
            }
            if (apart(taken, spot.get(), minGap)) {
                return spot;
            }
            if (crowded == null) {
                crowded = spot.get();
            }
        }
        return Optional.ofNullable(crowded);
    }

    /**
     * The spot in the block column of {@code point}, centred on it (so an add as wide as a block fits between walls),
     * if it is valid (see {@link #floor}); only in loaded chunks inside the world border.
     */
    public static Optional<Vec3> at(ServerLevel level, Vec3 point, int clearance, boolean ground, int scan) {
        int x = Mth.floor(point.x);
        int z = Mth.floor(point.z);
        int feet = Mth.floor(point.y);
        if (!level.getWorldBorder().isWithinBounds(x, z) || !level.hasChunkAt(new BlockPos(x, feet, z))) {
            return Optional.empty();
        }
        Terrain terrain = new LevelTerrain(level);
        if (!ground) {
            return open(terrain, x, feet, z, clearance) ? Optional.of(new Vec3(x + 0.5, point.y, z + 0.5)) : Optional.empty();
        }
        int from = Math.min(level.getMaxBuildHeight() - 1, feet + 1);
        int depth = Math.min(scan, from - level.getMinBuildHeight());
        double floor = floor(terrain, x, z, from, depth, clearance);
        return Double.isNaN(floor) ? Optional.empty() : Optional.of(new Vec3(x + 0.5, floor, z + 0.5));
    }

    /** {@link Terrain} over the real blocks. */
    private record LevelTerrain(ServerLevel level, BlockPos.MutableBlockPos cursor) implements Terrain {
        LevelTerrain(ServerLevel level) {
            this(level, new BlockPos.MutableBlockPos());
        }

        private BlockState state(int x, int y, int z) {
            return level.getBlockState(cursor.set(x, y, z));
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return !state(x, y, z).getCollisionShape(level, cursor).isEmpty();
        }

        @Override
        public double top(int x, int y, int z) {
            VoxelShape shape = state(x, y, z).getCollisionShape(level, cursor);
            return shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y);
        }

        @Override
        public boolean fluid(int x, int y, int z) {
            return !state(x, y, z).getFluidState().isEmpty();
        }
    }
}
