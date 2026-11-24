package net.ledok.mobs_ld.client.renderer;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The floor under a telegraph, so it can be laid on the terrain instead of one flat sheet: for any point of the zone,
 * the height of the surface a player would stand on there, relative to the zone's own floor (its origin), if that
 * surface lies inside the hit's height band (the band the hit test uses on the server); none otherwise, so floors the
 * hit cannot reach stay bare. Stairs, slabs and other partial blocks count by their exact collision box at that point,
 * fluids by their surface. A surface with something solid right on top of it (inside a wall, under a block) is
 * skipped and the scan goes on below it, so a floor under an overhang is still found.
 *
 * <p>Sampled from the client's own blocks on a {@value #CELL}-block grid, lazily, once per spot for the zone's life
 * (a telegraph lasts a second or two; terrain changes in that time are not followed).
 */
final class TerrainDrape {
    /** Grid step of the samples, the renderer's pixel size. */
    static final float CELL = 0.25F;
    /** Samples are cached within this many blocks of the origin; further points are sampled every time. */
    private static final double MAX_CACHED_REACH = 64.0;
    private static final float UNKNOWN = Float.MAX_VALUE;
    private static final double EPSILON = 1.0e-3;

    private final Level level;
    private final double originX;
    private final double originZ;
    private final double ground;
    private final double up;
    private final double down;
    private final int half;
    private final float[] cache;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    TerrainDrape(Level level, double originX, double ground, double originZ, double up, double down, double reach) {
        this.level = level;
        this.originX = originX;
        this.originZ = originZ;
        this.ground = ground;
        this.up = up;
        this.down = down;
        this.half = Mth.ceil(Math.min(reach + 1.0, MAX_CACHED_REACH) / CELL);
        this.cache = new float[(2 * half) * (2 * half)];
        java.util.Arrays.fill(cache, UNKNOWN);
    }

    /** Whether this drape was sampled for the same spot and band (a zone that follows its mob moves). */
    boolean matches(double originX, double ground, double originZ, double up, double down) {
        return this.originX == originX && this.ground == ground && this.originZ == originZ && this.up == up
                && this.down == down;
    }

    /** The floor at {@code dx}/{@code dz} blocks (world axes) from the origin, relative to the origin's floor; NaN = none. */
    float at(float dx, float dz) {
        int ix = Mth.floor(dx / CELL) + half;
        int iz = Mth.floor(dz / CELL) + half;
        if (ix < 0 || iz < 0 || ix >= 2 * half || iz >= 2 * half) {
            return sample(originX + dx, originZ + dz);
        }
        int index = iz * 2 * half + ix;
        float value = cache[index];
        if (value == UNKNOWN) {
            // The centre of the grid cell, so neighbouring points of one cell agree.
            value = sample(originX + (ix - half + 0.5) * CELL, originZ + (iz - half + 0.5) * CELL);
            cache[index] = value;
        }
        return value;
    }

    /** Floor at an exact position, for isolated effects that do not use the telegraph's grid. */
    float exactAt(double dx, double dz) {
        return sample(originX + dx, originZ + dz);
    }

    private float sample(double x, double z) {
        double top = ground + up;
        double bottom = ground - down;
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        double fx = x - bx;
        double fz = z - bz;
        for (int y = Mth.floor(top); y >= Mth.floor(bottom) - 1; y--) {
            double surface = surface(bx, y, bz, fx, fz);
            if (Double.isNaN(surface)) {
                continue;
            }
            if (surface > top + EPSILON) {
                continue; // above the band (a wall, a ceiling): someone could only stand on it out of reach
            }
            if (surface < bottom - EPSILON) {
                return Float.NaN; // the first floor down is deeper than the band reaches
            }
            if (covered(bx, bz, fx, fz, surface)) {
                continue; // inside a wall or under a block: nobody stands there
            }
            return (float) (surface - ground);
        }
        return Float.NaN;
    }

    /** The top of block {@code y}'s collision box (or fluid) at that point of the column; NaN = nothing there. */
    private double surface(int bx, int y, int bz, double fx, double fz) {
        cursor.set(bx, y, bz);
        VoxelShape shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
        double best = Double.NaN;
        if (!shape.isEmpty()) {
            for (AABB box : shape.toAabbs()) {
                if (fx >= box.minX && fx <= box.maxX && fz >= box.minZ && fz <= box.maxZ
                        && (Double.isNaN(best) || box.maxY > best)) {
                    best = box.maxY;
                }
            }
        }
        if (!Double.isNaN(best)) {
            return y + best;
        }
        FluidState fluid = level.getFluidState(cursor);
        return fluid.isEmpty() ? Double.NaN : y + fluid.getHeight(level, cursor);
    }

    /** Whether a collision box sits right on top of the surface at that point (the surface is not open to stand on). */
    private boolean covered(int bx, int bz, double fx, double fz, double surface) {
        double probe = surface + 0.05;
        int y = Mth.floor(probe);
        cursor.set(bx, y, bz);
        VoxelShape shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
        if (shape.isEmpty()) {
            return false;
        }
        double local = probe - y;
        for (AABB box : shape.toAabbs()) {
            if (fx >= box.minX && fx <= box.maxX && fz >= box.minZ && fz <= box.maxZ
                    && local >= box.minY && local <= box.maxY) {
                return true;
            }
        }
        return false;
    }
}
