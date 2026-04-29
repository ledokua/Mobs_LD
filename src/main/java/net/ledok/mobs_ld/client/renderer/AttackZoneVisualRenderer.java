package net.ledok.mobs_ld.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZoneVisualEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class AttackZoneVisualRenderer extends EntityRenderer<AttackZoneVisualEntity> {
    private static final int BRIGHT_WINDOW_TICKS = 2;

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

        int totalWindup = Math.max(1, entity.getTotalWindup());
        int windupTimer = entity.getWindupTimer();
        float progress = clamp01(1.0F - ((float) windupTimer / totalWindup));

        AttackDisplayConfig.AnimationStyle style = AttackDisplayConfig.AnimationStyle.values()[
                Math.max(0, Math.min(AttackDisplayConfig.AnimationStyle.values().length - 1, entity.getStyle()))
        ];

        float scaleMult = switch (style) {
            case APPEAR -> 1.0F;
            case GROW -> progress;
            case PULSE -> 1.0F + 0.1F * (float) Math.sin(progress * Math.PI * 6.0);
            case FADE_IN -> 1.0F;
        };

        int color = lerpColor(entity.getPrepareColor(), entity.getInvokeColor(), progress);
        if (style == AttackDisplayConfig.AnimationStyle.FADE_IN) {
            int alpha = Math.round(progress * 255.0F);
            color = (alpha << 24) | (color & 0x00FFFFFF);
        }
        if (entity.isForceInvoke() || windupTimer <= BRIGHT_WINDOW_TICKS) {
            color = entity.getInvokeColor();
        }

        switch (entity.getZoneKind()) {
            case 0 -> renderRectangle(entity, matrix, buffer, color, scaleMult);
            case 1 -> renderCone(entity, matrix, buffer, color, scaleMult);
            case 2, 3 -> renderCircle(entity, matrix, buffer, color, scaleMult);
            default -> {
            }
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private void renderRectangle(AttackZoneVisualEntity entity, Matrix4f matrix, VertexConsumer buffer, int color, float scale) {
        float width = entity.getParamA();
        float length = entity.getParamB();
        float offsetForward = entity.getParamC();
        float yaw = (float) Math.toRadians(entity.getYawDegrees());

        float halfWidth = (width * scale) * 0.5F;
        float scaledLength = length * scale;

        float backF = offsetForward;
        float frontF = offsetForward + scaledLength;

        addQuad(matrix, buffer, color,
                rotateX(-halfWidth, backF, yaw), rotateZ(-halfWidth, backF, yaw),
                rotateX(halfWidth, backF, yaw), rotateZ(halfWidth, backF, yaw),
                rotateX(halfWidth, frontF, yaw), rotateZ(halfWidth, frontF, yaw),
                rotateX(-halfWidth, frontF, yaw), rotateZ(-halfWidth, frontF, yaw));
    }

    private void renderCone(AttackZoneVisualEntity entity, Matrix4f matrix, VertexConsumer buffer, int color, float scale) {
        float dist = entity.getParamA() * scale;
        float angle = entity.getParamB();
        float yaw = (float) Math.toRadians(entity.getYawDegrees());
        float halfAngle = (float) Math.toRadians(angle * 0.5F);

        float llx = (float) (-Math.sin(halfAngle) * dist);
        float llz = (float) (Math.cos(halfAngle) * dist);
        float rlx = (float) (Math.sin(halfAngle) * dist);
        float rlz = (float) (Math.cos(halfAngle) * dist);

        float lx = rotateX(llx, llz, yaw);
        float lz = rotateZ(llx, llz, yaw);
        float rx = rotateX(rlx, rlz, yaw);
        float rz = rotateZ(rlx, rlz, yaw);

        float y = 0.02F;
        buffer.addVertex(matrix, 0.0F, y, 0.0F).setColor(color);
        buffer.addVertex(matrix, 0.0F, y, 0.0F).setColor(color);
        buffer.addVertex(matrix, lx, y, lz).setColor(color);
        buffer.addVertex(matrix, rx, y, rz).setColor(color);
    }

    private void renderCircle(AttackZoneVisualEntity entity, Matrix4f matrix, VertexConsumer buffer, int color, float scale) {
        float radius = entity.getParamA() * scale;
        int segments = 32;
        float y = 0.02F;
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (2.0 * Math.PI * i / segments);
            float a2 = (float) (2.0 * Math.PI * (i + 1) / segments);

            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;
            float x2 = (float) Math.cos(a2) * radius;
            float z2 = (float) Math.sin(a2) * radius;

            buffer.addVertex(matrix, 0.0F, y, 0.0F).setColor(color);
            buffer.addVertex(matrix, x1, y, z1).setColor(color);
            buffer.addVertex(matrix, x2, y, z2).setColor(color);
            buffer.addVertex(matrix, x2, y, z2).setColor(color);
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
        return true;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int lerpColor(int from, int to, float t) {
        int a = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerp((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = lerp((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = lerp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }
}
