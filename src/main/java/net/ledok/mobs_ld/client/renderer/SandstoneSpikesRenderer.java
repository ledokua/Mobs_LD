package net.ledok.mobs_ld.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.network.SandstoneSpikesPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Short-lived, collision-free sandstone spires drawn from the local floor. */
public final class SandstoneSpikesRenderer {
    private static final int CLUSTERS = 5;
    private static final float CLUSTER_DELAY = 0.8F;
    private static final float LIFETIME = 13.0F;
    private static final float[] STEPS = {0.0F, 0.28F, 0.53F, 0.74F, 0.90F, 1.0F};
    private static final float[] WIDTHS = {1.0F, 0.82F, 0.63F, 0.43F, 0.23F};
    private static final List<Spike> SPIKES = new ArrayList<>();
    private static ClientLevel level;

    private SandstoneSpikesRenderer() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SandstoneSpikesPayload.TYPE,
                (payload, context) -> add(context.client().level, payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> switchLevel(null));
        WorldRenderEvents.AFTER_ENTITIES.register(SandstoneSpikesRenderer::render);
    }

    private static void switchLevel(ClientLevel current) {
        if (level != current) {
            SPIKES.clear();
            level = current;
        }
    }

    private static void add(ClientLevel current, SandstoneSpikesPayload cue) {
        switchLevel(current);
        if (level == null || !(cue.width() > 0 && cue.length() > 0)) {
            return;
        }
        Vec3 origin = cue.origin();
        float laneWidth = Math.min(cue.width(), 3.0F);
        TerrainDrape floor = new TerrainDrape(level, origin.x, origin.y, origin.z, cue.up(), cue.down(), 0);
        double sectionLength = cue.length() / CLUSTERS;
        for (int i = 0; i < CLUSTERS; i++) {
            double start = cue.offset() + i * sectionLength;
            float side = i % 2 == 0 ? 1 : -1;
            double tallDistance = start + sectionLength * (i == 0 ? 0.70 : 0.30);
            double shortDistance = start + sectionLength * (i == 0 ? 0.91 : 0.70);
            addSpike(cue, floor, tallDistance, -0.075 * laneWidth * side, i * CLUSTER_DELAY,
                    1.45F + (i * 5 % 9) / 16.0F, Math.min(0.69F, laneWidth / 3), i * 23);
            addSpike(cue, floor, shortDistance, 0.095 * laneWidth * side, i * CLUSTER_DELAY,
                    0.82F + (i * 3 % 6) / 16.0F, Math.min(0.50F, laneWidth / 3), i * 23 + 29);
        }
    }

    private static void addSpike(SandstoneSpikesPayload cue, TerrainDrape floor, double distance, double lateral,
                                 float delay, float height, float width, int angle) {
        Vec3 point = AbilityRun.local(cue.origin(), cue.yaw(), distance, lateral);
        double dx = point.x - cue.origin().x;
        double dz = point.z - cue.origin().z;
        float y = floor.exactAt(dx, dz);
        if (!Float.isNaN(y)) {
            SPIKES.add(new Spike(point.add(0, y, 0), level.getGameTime() + delay,
                    height, width, cue.yaw() + angle));
        }
    }

    private static void render(WorldRenderContext context) {
        switchLevel(context.world());
        if (SPIKES.isEmpty()) {
            return;
        }
        PoseStack pose = context.matrixStack();
        MultiBufferSource buffers = context.consumers();
        if (pose == null || buffers == null) {
            return;
        }
        double now = level.getGameTime() + (double) context.tickCounter().getGameTimeDeltaPartialTick(false);
        SPIKES.removeIf(spike -> now >= spike.start() + LIFETIME);
        Vec3 camera = context.camera().getPosition();
        for (Spike spike : SPIKES) {
            float age = (float) (now - spike.start());
            if (age >= 0.0F) {
                draw(spike, age, pose, buffers, camera);
            }
        }
    }

    private static void draw(Spike spike, float age, PoseStack pose, MultiBufferSource buffers, Vec3 camera) {
        float rise = Math.min(1.0F, 0.22F + age * 0.45F);
        float retreat = 1.0F - Mth.clamp((age - 8.0F) / 5.0F, 0.0F, 1.0F);
        float height = spike.height() * rise * retreat;
        Vec3 base = spike.base();
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(base.add(0, 0.05, 0)));
        Minecraft client = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(base.x - camera.x, base.y - camera.y, base.z - camera.z);
        pose.mulPose(Axis.YP.rotationDegrees(spike.yaw()));
        for (int tier = 0; tier < WIDTHS.length; tier++) {
            float width = spike.width() * WIDTHS[tier];
            float depth = width * 0.86F;
            pose.pushPose();
            pose.translate(tier * 0.045F - width / 2, height * STEPS[tier], -tier * 0.043F - depth / 2);
            pose.scale(width, height * (STEPS[tier + 1] - STEPS[tier]), depth);
            client.getBlockRenderer().renderSingleBlock(Blocks.SANDSTONE.defaultBlockState(), pose, buffers,
                    light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }

    private record Spike(Vec3 base, double start, float height, float width, float yaw) {
    }
}
