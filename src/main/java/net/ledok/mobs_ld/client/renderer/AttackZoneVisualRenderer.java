package net.ledok.mobs_ld.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.attack.AttackZoneVisualEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public class AttackZoneVisualRenderer extends EntityRenderer<AttackZoneVisualEntity> {
    private static final int BRIGHT_WINDOW_TICKS = 2;

    /** Edge length of a single "pixel" cell, in blocks — shapes are rasterized onto this grid for a blocky look. */
    private static final float PIXEL_SIZE = 0.25F;
    /** Widening {@code rays} lanes are drawn at most this far each way (the format caps them at 48 too). */
    private static final float MAX_WIDENED_LENGTH = 48.0F;

    /** Inside-test for a zone, evaluated in the zone's local frame (+Z forward, +X right). */
    private interface ZoneTest {
        boolean inside(float localX, float localZ);
    }

    /** Steps lower than this between neighbouring cells draw no wall (rounding, a carpet). */
    private static final float STEP = 0.02F;
    /** Height band used when the server sends none (an older server). */
    private static final float DEFAULT_BAND = 1.5F;

    /** The floor under the zone being drawn now; null draws it flat (as before terrain draping). */
    private TerrainDrape drape;

    public AttackZoneVisualRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * The zone's floor heights, sampled once and kept on the entity (re-sampled if it moved: zones that follow a mob).
     * The entity sits {@link AttackZoneDisplay#PREVIEW_LIFT} / {@link AttackZoneDisplay#FILL_LIFT} above the floor at
     * its origin, which is the reference the hit's height band is measured from.
     */
    private static TerrainDrape drapeFor(AttackZoneVisualEntity entity) {
        double lift = entity.isPreview() ? AttackZoneDisplay.PREVIEW_LIFT : AttackZoneDisplay.FILL_LIFT;
        double ground = entity.getY() - lift;
        double up = entity.getBandUp() > 0.0F ? entity.getBandUp() : DEFAULT_BAND;
        double down = entity.getBandDown() > 0.0F ? entity.getBandDown() : DEFAULT_BAND;
        if (entity.clientDrape instanceof TerrainDrape cached
                && cached.matches(entity.getX(), ground, entity.getZ(), up, down)) {
            return cached;
        }
        TerrainDrape fresh = new TerrainDrape(entity.level(), entity.getX(), ground, entity.getZ(), up, down, reach(entity));
        entity.clientDrape = fresh;
        return fresh;
    }

    @Override
    public void render(
            AttackZoneVisualEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();
        drape = drapeFor(entity);

        if (entity.isPreview()) {
            renderPreview(entity, matrix, buffer);
        } else {
            renderFill(entity, matrix, buffer);
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private void renderPreview(AttackZoneVisualEntity entity, Matrix4f matrix, VertexConsumer buffer) {
        int color = entity.isForceInvoke() ? entity.getInvokeColor() : entity.getPreviewColor();
        dispatchShape(entity, matrix, buffer, color, 1.0F, 1.0F);
    }

    private void renderFill(AttackZoneVisualEntity entity, Matrix4f matrix, VertexConsumer buffer) {
        int totalWindup = Math.max(1, entity.getTotalWindup());
        int windupTimer = entity.getWindupTimer();
        int animationTicks = Math.max(1, totalWindup - BRIGHT_WINDOW_TICKS);
        float progress = clamp01((float) (totalWindup - windupTimer) / (float) animationTicks);
        float displayProgress = (float) Math.pow(progress, 0.4F);

        AttackDisplayConfig.AnimationStyle style = AttackDisplayConfig.AnimationStyle.values()[
                Math.max(0, Math.min(AttackDisplayConfig.AnimationStyle.values().length - 1, entity.getStyle()))
        ];

        float scaleMult = 1.0F;
        float sweepProgress = 1.0F;
        int color;

        if (entity.isForceInvoke() || windupTimer <= BRIGHT_WINDOW_TICKS) {
            color = entity.getInvokeColor();
        } else {
            int alpha = Math.round(displayProgress * 255.0F);
            color = (alpha << 24) | (entity.getPrepareColor() & 0x00FFFFFF);

            scaleMult = switch (style) {
                case GROW -> displayProgress;
                // A circle (or ring) has no length to sweep along: it grows instead.
                case SWEEP -> isCircle(entity) || isRing(entity) ? displayProgress : 1.0F;
                case DEFAULT -> 1.0F;
            };
            sweepProgress = switch (style) {
                case SWEEP -> progress;
                case GROW, DEFAULT -> 1.0F;
            };
        }

        dispatchShape(entity, matrix, buffer, color, scaleMult, sweepProgress);
    }

    private void dispatchShape(
            AttackZoneVisualEntity entity,
            Matrix4f matrix,
            VertexConsumer buffer,
            int color,
            float scaleMult,
            float sweepProgress
    ) {
        switch (entity.getZoneKind()) {
            case 0 -> renderRectangle(entity, matrix, buffer, color, scaleMult, sweepProgress);
            case 1 -> renderCone(entity, matrix, buffer, color, scaleMult, sweepProgress);
            case 2, 3 -> renderCircle(entity, matrix, buffer, color, scaleMult);
            case 4 -> renderCircleRays(entity, matrix, buffer, color, scaleMult, sweepProgress);
            case 5 -> renderStrip(entity, matrix, buffer, color, scaleMult, sweepProgress);
            case 6 -> renderRing(entity, matrix, buffer, color, scaleMult);
            default -> {
            }
        }
    }

    private void renderRectangle(
            AttackZoneVisualEntity entity,
            Matrix4f matrix,
            VertexConsumer buffer,
            int color,
            float scaleMult,
            float sweepProgress
    ) {
        float width = entity.getParamA();
        float length = entity.getParamB();
        float offsetForward = entity.getParamC();
        float yaw = (float) Math.toRadians(entity.getYawDegrees());

        float halfWidth = (width * scaleMult) * 0.5F;
        float scaledLength = length * sweepProgress;

        float backF = offsetForward;
        float frontF = offsetForward + scaledLength;

        fillPixelated(matrix, buffer, color, yaw, -halfWidth, backF, halfWidth, frontF,
                (x, z) -> true);
    }

    /**
     * One segment of a lane: the lane part fills along its length first, then the arrowhead past its end (if any),
     * while the global sweep runs through this segment's window, so the segments of one strip fill in turn. The lane's
     * side edges are drawn exactly at half its width (they are the hit boundary); the arrowhead is wider.
     */
    private void renderStrip(
            AttackZoneVisualEntity entity,
            Matrix4f matrix,
            VertexConsumer buffer,
            int color,
            float scaleMult,
            float sweepProgress
    ) {
        float length = entity.getParamB();
        float head = entity.getParamC();
        float yaw = (float) Math.toRadians(entity.getYawDegrees());
        float from = entity.getSweepFrom();
        float to = entity.getSweepTo();
        float local = to > from ? clamp01((sweepProgress - from) / (to - from)) : (sweepProgress >= to ? 1.0F : 0.0F);
        float front = (length + head) * local;
        float halfWidth = entity.getParamA() * scaleMult * 0.5F;

        fillPixelated(matrix, buffer, color, yaw, -halfWidth, 0.0F, halfWidth, Math.min(front, length),
                (x, z) -> true);
        if (head > 0.0F && front > length) {
            float headHalf = entity.getParamA() * scaleMult * AttackZone.Strip.HEAD_HALF_WIDTH;
            float tip = length + head;
            fillPixelated(matrix, buffer, color, yaw, -headHalf, length, headHalf, front,
                    (x, z) -> Math.abs(x) <= headHalf * (tip - z) / head);
        }
    }

    private void renderCone(
            AttackZoneVisualEntity entity,
            Matrix4f matrix,
            VertexConsumer buffer,
            int color,
            float scaleMult,
            float sweepProgress
    ) {
        float dist = entity.getParamA() * sweepProgress * scaleMult;
        float angle = entity.getParamB();
        float yaw = (float) Math.toRadians(entity.getYawDegrees());
        float halfAngleRad = (float) Math.toRadians(angle * 0.5F);

        fillPixelated(matrix, buffer, color, yaw, -dist, 0.0F, dist, dist, (x, z) -> {
            float r = (float) Math.sqrt(x * x + z * z);
            if (r > dist || r < 1.0e-4F) {
                return false;
            }
            // angle between the cell vector and the +Z forward axis.
            return Math.acos(Mth.clamp(z / r, -1.0F, 1.0F)) <= halfAngleRad;
        });
    }

    private void renderCircle(AttackZoneVisualEntity entity, Matrix4f matrix, VertexConsumer buffer, int color, float scale) {
        float radius = entity.getParamA() * scale;
        float r2 = radius * radius;
        fillPixelated(matrix, buffer, color, 0.0F, -radius, -radius, radius, radius,
                (x, z) -> x * x + z * z <= r2);
    }

    /**
     * A band from the inner to the outer radius. Growing, it fills outward across the band (from the inner edge), so
     * it never shows anywhere the hit does not reach. Drawn on the same {@link #PIXEL_SIZE} grid as the other shapes,
     * one quad per run of cells in a row (a 22-block ring stays cheap).
     */
    private void renderRing(AttackZoneVisualEntity entity, Matrix4f matrix, VertexConsumer buffer, int color, float fill) {
        float inner = entity.getParamA();
        float outer = inner + (entity.getParamB() - inner) * clamp01(fill);
        if (outer <= inner || outer <= 0.0F) {
            return;
        }
        float inner2 = inner * inner;
        float outer2 = outer * outer;
        float min = -outer;
        int cells = Math.max(1, Mth.ceil(2.0F * outer / PIXEL_SIZE));
        GridPainter painter = new GridPainter(matrix, buffer, color, 0.0F, min, outer, cells);
        for (int iz = 0; iz < cells; iz++) {
            float z0 = min + iz * PIXEL_SIZE;
            float z1 = Math.min(z0 + PIXEL_SIZE, outer);
            float zc = (z0 + z1) * 0.5F;
            float zc2 = zc * zc;
            if (zc2 > outer2) {
                painter.skipRow();
                continue;
            }
            float far = (float) Math.sqrt(outer2 - zc2);
            float near = zc2 >= inner2 ? -1.0F : (float) Math.sqrt(inner2 - zc2);
            painter.row(z0, z1, x -> Math.abs(x) <= far && Math.abs(x) >= near);
        }
    }

    private void renderCircleRays(
            AttackZoneVisualEntity entity,
            Matrix4f matrix,
            VertexConsumer buffer,
            int color,
            float scaleMult,
            float sweepProgress
    ) {
        int rayCount = Math.max(2, Math.round(entity.getParamA()));
        int rectCount = Math.max(1, rayCount / 2);
        float length = entity.getParamB();
        float halfWidth = entity.getParamC() * 0.5F;
        float effectiveLength = length * sweepProgress * scaleMult;
        float effectiveHalfWidth = halfWidth * scaleMult;
        float baseYawRad = (float) Math.toRadians(entity.getYawDegrees());
        float widen = entity.getParamD();
        if (widen > 0.0F) {
            // Widening lanes: each line is a pair of trapezoids, half its width at the origin plus half the widening
            // per block along it (the same rule as the hit test; the grow style scales the base width and the
            // length, never the widening rate), out to where the sweep has reached. Lengths are capped for drawing.
            float halfWiden = widen * 0.5F;
            float drawnLength = Math.min(effectiveLength, MAX_WIDENED_LENGTH);
            for (int i = 0; i < rectCount; i++) {
                float yawRad = baseYawRad + (float) Math.toRadians(i * (180.0 / rectCount));
                fillTrapezoidRows(matrix, buffer, color, yawRad, drawnLength, effectiveHalfWidth, halfWiden);
            }
            return;
        }

        for (int i = 0; i < rectCount; i++) {
            float yawRad = baseYawRad + (float) Math.toRadians(i * (180.0 / rectCount));
            fillPixelated(matrix, buffer, color, yawRad,
                    -effectiveHalfWidth, -effectiveLength, effectiveHalfWidth, effectiveLength,
                    (x, z) -> true);
        }
    }

    /**
     * Rasterizes a zone onto a fixed grid of square cells: every {@link #PIXEL_SIZE} cell whose center passes
     * {@code test} (in the zone's local, unrotated frame) is emitted as a filled quad, rotated by {@code yaw}.
     * This gives the indicator a chunky, Minecraft-style pixelated edge instead of a smooth polygon.
     */
    private void fillPixelated(
            Matrix4f matrix,
            VertexConsumer buffer,
            int color,
            float yaw,
            float minX,
            float minZ,
            float maxX,
            float maxZ,
            ZoneTest test
    ) {
        if (maxX <= minX || maxZ <= minZ) {
            return;
        }
        int cellsX = Math.max(1, Mth.ceil((maxX - minX) / PIXEL_SIZE));
        int cellsZ = Math.max(1, Mth.ceil((maxZ - minZ) / PIXEL_SIZE));
        GridPainter painter = new GridPainter(matrix, buffer, color, yaw, minX, maxX, cellsX);
        for (int iz = 0; iz < cellsZ; iz++) {
            float z0 = minZ + iz * PIXEL_SIZE;
            float z1 = Math.min(z0 + PIXEL_SIZE, maxZ);
            float zc = (z0 + z1) * 0.5F;
            painter.row(z0, z1, x -> test.inside(x, zc));
        }
    }

    /**
     * A lane through the origin, {@code length} each way, whose half-width is {@code halfWidth + halfWiden x |z|}:
     * the same blocky {@link #PIXEL_SIZE} grid as {@link #fillPixelated}, but each row of cells is one quad and its
     * run of inside cells is found from the half-width directly, so the cost is one quad per row whatever the width.
     */
    private void fillTrapezoidRows(
            Matrix4f matrix,
            VertexConsumer buffer,
            int color,
            float yaw,
            float length,
            float halfWidth,
            float halfWiden
    ) {
        float maxX = halfWidth + halfWiden * length;
        if (length <= 0.0F || maxX <= 0.0F) {
            return;
        }
        float minX = -maxX;
        int cellsX = Math.max(1, Mth.ceil((maxX - minX) / PIXEL_SIZE));
        int cellsZ = Math.max(1, Mth.ceil(2.0F * length / PIXEL_SIZE));
        GridPainter painter = new GridPainter(matrix, buffer, color, yaw, minX, maxX, cellsX);
        for (int iz = 0; iz < cellsZ; iz++) {
            float z0 = -length + iz * PIXEL_SIZE;
            float z1 = Math.min(z0 + PIXEL_SIZE, length);
            float half = halfWidth + halfWiden * Math.abs((z0 + z1) * 0.5F);
            painter.row(z0, z1, x -> Math.abs(x) <= half);
        }
    }

    /** Which cells of a row are inside the zone, by the cell centre's x in the zone's local frame. */
    private interface RowTest {
        boolean inside(float localX);
    }

    /**
     * Draws a zone row by row on the {@link #PIXEL_SIZE} grid of its local (rotated) frame, each cell at the height of
     * the floor under it ({@link #drape}). Cells with no floor inside the hit's band are left out; a run of cells at
     * one height is one quad, so a zone on flat ground costs one quad per row. Where two neighbouring cells sit at
     * different heights (a step, a stair), a wall joins them, so the shape stays one surface over the terrain.
     */
    private final class GridPainter {
        private final Matrix4f matrix;
        private final VertexConsumer buffer;
        private final int color;
        private final float sin;
        private final float cos;
        private final float minX;
        private final float maxX;
        private final int cellsX;
        private float[] previous;
        private float[] current;
        /** The far edge of the previous row, if the previous row was drawn and touches the next one. */
        private float previousEdge = Float.NaN;

        GridPainter(Matrix4f matrix, VertexConsumer buffer, int color, float yaw, float minX, float maxX, int cellsX) {
            this.matrix = matrix;
            this.buffer = buffer;
            this.color = color;
            this.sin = (float) Math.sin(yaw);
            this.cos = (float) Math.cos(yaw);
            this.minX = minX;
            this.maxX = maxX;
            this.cellsX = cellsX;
            this.previous = new float[cellsX];
            this.current = new float[cellsX];
        }

        private float x(int cell) {
            return Math.min(minX + cell * PIXEL_SIZE, maxX);
        }

        /** A row with nothing inside: the next row has no neighbour behind it. */
        void skipRow() {
            previousEdge = Float.NaN;
        }

        void row(float z0, float z1, RowTest test) {
            float zc = (z0 + z1) * 0.5F;
            boolean any = false;
            for (int ix = 0; ix < cellsX; ix++) {
                float xc = (x(ix) + x(ix + 1)) * 0.5F;
                float height = Float.NaN;
                if (test.inside(xc)) {
                    height = drape == null ? 0.0F : drape.at(worldX(xc, zc), worldZ(xc, zc));
                }
                current[ix] = height;
                any |= !Float.isNaN(height);
            }
            if (!any) {
                skipRow();
                return;
            }
            // Runs of cells at one height.
            int ix = 0;
            while (ix < cellsX) {
                float height = current[ix];
                if (Float.isNaN(height)) {
                    ix++;
                    continue;
                }
                int end = ix;
                while (end + 1 < cellsX && !Float.isNaN(current[end + 1]) && Math.abs(current[end + 1] - height) < STEP) {
                    end++;
                }
                flat(x(ix), z0, x(end + 1), z1, height);
                ix = end + 1;
            }
            // Walls between neighbours in this row, and toward the row before.
            for (int i = 0; i + 1 < cellsX; i++) {
                if (steps(current[i], current[i + 1])) {
                    wall(x(i + 1), z0, x(i + 1), z1, current[i], current[i + 1]);
                }
            }
            if (!Float.isNaN(previousEdge) && Math.abs(previousEdge - z0) < 1.0e-4F) {
                for (int i = 0; i < cellsX; i++) {
                    if (steps(previous[i], current[i])) {
                        wall(x(i), z0, x(i + 1), z0, previous[i], current[i]);
                    }
                }
            }
            float[] swap = previous;
            previous = current;
            current = swap;
            previousEdge = z1;
        }

        private boolean steps(float a, float b) {
            return !Float.isNaN(a) && !Float.isNaN(b) && Math.abs(a - b) >= STEP;
        }

        private float worldX(float localX, float localZ) {
            return localX * cos - localZ * sin;
        }

        private float worldZ(float localX, float localZ) {
            return localX * sin + localZ * cos;
        }

        private void vertex(float localX, float y, float localZ) {
            buffer.addVertex(matrix, worldX(localX, localZ), y + 0.02F, worldZ(localX, localZ)).setColor(color);
        }

        private void flat(float x0, float z0, float x1, float z1, float y) {
            vertex(x0, y, z0);
            vertex(x1, y, z0);
            vertex(x1, y, z1);
            vertex(x0, y, z1);
        }

        /** A vertical quad along the edge (x0, z0) - (x1, z1) from one height to the other. */
        private void wall(float x0, float z0, float x1, float z1, float a, float b) {
            float low = Math.min(a, b);
            float high = Math.max(a, b);
            vertex(x0, low, z0);
            vertex(x1, low, z1);
            vertex(x1, high, z1);
            vertex(x0, high, z0);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(AttackZoneVisualEntity entity) {
        return null;
    }

    @Override
    public boolean shouldRender(AttackZoneVisualEntity entity, Frustum frustum, double x, double y, double z) {
        double dx = entity.getX() - x;
        double dy = entity.getY() - y;
        double dz = entity.getZ() - z;
        // 32 blocks around the origin, or further for a zone long enough that its far end is past that (a 40-block
        // lance strip must show for the player it is aimed at): never less than before for the small zones.
        double range = Math.max(32.0, reach(entity) + 16.0);
        return (dx * dx + dy * dy + dz * dz) < range * range;
    }

    /** How far from its origin a zone reaches, from its synced parameters. */
    private static double reach(AttackZoneVisualEntity entity) {
        return switch (entity.getZoneKind()) {
            case 0 -> entity.getParamC() + entity.getParamB();
            case 1, 2, 3 -> entity.getParamA();
            case 4 -> entity.getParamB();
            case 5 -> entity.getParamB() + entity.getParamC();
            case 6 -> entity.getParamB();
            default -> 0.0;
        };
    }

    /** Circle and target-circle zones (kinds 2 and 3). */
    private static boolean isCircle(AttackZoneVisualEntity entity) {
        int kind = entity.getZoneKind();
        return kind == 2 || kind == 3;
    }

    /** Ring zones (kind 6). */
    private static boolean isRing(AttackZoneVisualEntity entity) {
        return entity.getZoneKind() == 6;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

}
