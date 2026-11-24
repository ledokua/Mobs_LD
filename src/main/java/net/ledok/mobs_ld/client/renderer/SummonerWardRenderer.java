package net.ledok.mobs_ld.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ledok.mobs_ld.mob.LdMob;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** A flowing ember ribbon between a channeling add and the summoner it protects. */
public final class SummonerWardRenderer {
    private static final int SEGMENTS = 18;
    private static final int EDGE_COLOR = 0x20EB4814;

    private SummonerWardRenderer() {
    }

    public static void render(LdMob add, float partialTick, PoseStack poses, MultiBufferSource buffers) {
        int targetId = add.wardTargetId();
        if (targetId < 0 || !add.isAlive()) {
            return;
        }
        Entity target = add.level().getEntity(targetId);
        if (target == null || !target.isAlive()) {
            return;
        }

        Vec3 origin = add.getPosition(partialTick);
        Vec3 start = endpoint(add, origin, "fx_coal");
        Vec3 end = endpoint(target, target.getPosition(partialTick), "fx_core");
        Vec3 length = end.subtract(start);
        if (length.lengthSqr() < 1.0e-4) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 side = length.cross(camera.subtract(start.lerp(end, 0.5))).normalize();
        if (side.lengthSqr() < 1.0e-4) {
            side = length.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        }
        if (side.lengthSqr() < 1.0e-4) {
            side = new Vec3(1.0, 0.0, 0.0);
        }

        VertexConsumer buffer = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f pose = poses.last().pose();
        Vec3 localStart = start.subtract(origin);
        double time = (add.level().getGameTime() % 1200 + partialTick) * 0.27;
        Point previous = point(0.0, localStart, length, side, time);
        for (int i = 1; i <= SEGMENTS; i++) {
            Point next = point((double) i / SEGMENTS, localStart, length, side, time);
            vertex(buffer, pose, previous.pos.subtract(previous.side), EDGE_COLOR);
            vertex(buffer, pose, next.pos.subtract(next.side), EDGE_COLOR);
            vertex(buffer, pose, next.pos, next.core);
            vertex(buffer, pose, previous.pos, previous.core);
            vertex(buffer, pose, previous.pos, previous.core);
            vertex(buffer, pose, next.pos, next.core);
            vertex(buffer, pose, next.pos.add(next.side), EDGE_COLOR);
            vertex(buffer, pose, previous.pos.add(previous.side), EDGE_COLOR);
            previous = next;
        }
    }

    private static Vec3 endpoint(Entity entity, Vec3 origin, String name) {
        Vec3 fallback = origin.add(0.0, entity.getBbHeight() * 0.68, 0.0);
        if (entity instanceof LdMob mob) {
            Vec3 marker = mob.markerPosition(name);
            // Use the body position if an offscreen summoner has no recent marker.
            if (marker != null && marker.distanceToSqr(fallback) <= 1.0) {
                return marker;
            }
        }
        return fallback;
    }

    private static Point point(double t, Vec3 start, Vec3 length, Vec3 side, double time) {
        double envelope = Math.sin(Math.PI * t);
        double wave = Math.sin(27.0 * t - time);
        double glint = 0.5 + 0.5 * Math.sin(35.0 * t - time * 2.0);
        Vec3 center = start.add(length.scale(t)).add(side.scale(0.08 * envelope * wave));
        Vec3 width = side.scale((0.035 + 0.049 * envelope) * (0.72 + 0.28 * glint));
        int green = 106 + (int) (125.0 * glint);
        int blue = 27 + (int) (120.0 * glint);
        int core = 0xFFFF0000 | (green << 8) | blue;
        return new Point(center, width, core);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vec3 at, int color) {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(color);
    }

    private record Point(Vec3 pos, Vec3 side, int core) {
    }
}
