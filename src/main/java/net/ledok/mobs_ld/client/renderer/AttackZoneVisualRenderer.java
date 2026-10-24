package net.ledok.mobs_ld.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
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

    /** Inside-test for a zone, evaluated in the zone's local frame (+Z forward, +X right). */
    private interface ZoneTest {
        boolean inside(float localX, float localZ);
    }

    public AttackZoneVisualRenderer(EntityRendererProvider.Context context) {
        super(context);
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
                // A circle has no length to sweep along: it grows instead.
                case SWEEP -> isCircle(entity) ? displayProgress : 1.0F;
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
        for (int ix = 0; ix < cellsX; ix++) {
            float x0 = minX + ix * PIXEL_SIZE;
            float x1 = Math.min(x0 + PIXEL_SIZE, maxX);
            float cx = (x0 + x1) * 0.5F;
            for (int iz = 0; iz < cellsZ; iz++) {
                float z0 = minZ + iz * PIXEL_SIZE;
                float z1 = Math.min(z0 + PIXEL_SIZE, maxZ);
                if (!test.inside(cx, (z0 + z1) * 0.5F)) {
                    continue;
                }
                addQuad(matrix, buffer, color,
                        rotateX(x0, z0, yaw), rotateZ(x0, z0, yaw),
                        rotateX(x1, z0, yaw), rotateZ(x1, z0, yaw),
                        rotateX(x1, z1, yaw), rotateZ(x1, z1, yaw),
                        rotateX(x0, z1, yaw), rotateZ(x0, z1, yaw));
            }
        }
    }

    private void addQuad(
            Matrix4f matrix,
            VertexConsumer buffer,
            int argb,
            float x1, float z1,
            float x2, float z2,
            float x3, float z3,
            float x4, float z4
    ) {
        float y = 0.02F;
        buffer.addVertex(matrix, x1, y, z1).setColor(argb);
        buffer.addVertex(matrix, x2, y, z2).setColor(argb);
        buffer.addVertex(matrix, x3, y, z3).setColor(argb);
        buffer.addVertex(matrix, x4, y, z4).setColor(argb);
    }

    private float rotateX(float localX, float localZ, float yaw) {
        return (float) (localX * Math.cos(yaw) - localZ * Math.sin(yaw));
    }

    private float rotateZ(float localX, float localZ, float yaw) {
        return (float) (localX * Math.sin(yaw) + localZ * Math.cos(yaw));
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
        return (dx * dx + dy * dy + dz * dz) < (32.0 * 32.0);
    }

    /** Circle and target-circle zones (kinds 2 and 3). */
    private static boolean isCircle(AttackZoneVisualEntity entity) {
        int kind = entity.getZoneKind();
        return kind == 2 || kind == 3;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

}
