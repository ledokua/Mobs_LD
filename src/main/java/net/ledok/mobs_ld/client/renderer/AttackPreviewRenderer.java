package net.ledok.mobs_ld.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.network.AttackPreviewPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Private wireframe of the server's locked hit shape. Red boxes show victims at the impact tick. */
public final class AttackPreviewRenderer {
    private static final int LOWER = 0xFF42D8DE;
    private static final int UPPER = 0xFFF9CA65;
    private static final int VERTICAL = 0xFFADC9EF;
    private static final int AFTER_IMPACT_TICKS = 60;
    private static final Map<UUID, Preview> ACTIVE = new HashMap<>();
    private static ClientLevel world;

    private AttackPreviewRenderer() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AttackPreviewPayload.TYPE,
                (payload, context) -> receive(context.client().level, payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> switchWorld(null));
        WorldRenderEvents.AFTER_ENTITIES.register(AttackPreviewRenderer::render);
    }

    private static void switchWorld(ClientLevel level) {
        if (world != level) {
            ACTIVE.clear();
            world = level;
        }
    }

    private static void receive(ClientLevel level, AttackPreviewPayload payload) {
        switchWorld(level);
        if (world == null) {
            return;
        }
        long now = level.getGameTime();
        switch (payload.stage()) {
            case LOCKED -> ACTIVE.put(payload.id(), new Preview(edges(payload.zones()), List.of(),
                    now + Math.max(0, payload.windupTicks()) + AFTER_IMPACT_TICKS));
            case IMPACT -> {
                Preview previous = ACTIVE.get(payload.id());
                List<Edge> edges = previous == null ? List.of() : previous.edges();
                ACTIVE.put(payload.id(), new Preview(edges, payload.hitboxes(), now + AFTER_IMPACT_TICKS));
            }
            case CANCELLED -> ACTIVE.remove(payload.id());
            case CLEAR -> ACTIVE.clear();
        }
    }

    private static void render(WorldRenderContext context) {
        switchWorld(context.world());
        if (world == null || ACTIVE.isEmpty()) {
            return;
        }
        ACTIVE.values().removeIf(preview -> preview.expiresAt() <= world.getGameTime());
        if (ACTIVE.isEmpty() || context.consumers() == null || context.matrixStack() == null) {
            return;
        }
        VertexConsumer buffer = context.consumers().getBuffer(RenderType.lines());
        PoseStack stack = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        for (Preview preview : ACTIVE.values()) {
            for (Edge edge : preview.edges()) {
                drawLine(buffer, stack.last(), edge.from().subtract(camera), edge.to().subtract(camera), edge.color());
            }
            for (AABB hit : preview.hits()) {
                LevelRenderer.renderLineBox(stack, buffer, hit.inflate(0.025).move(camera.scale(-1.0)),
                        1.0F, 0.25F, 0.21F, 1.0F);
            }
        }
    }

    private static void drawLine(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, int color) {
        Vec3 direction = to.subtract(from).normalize();
        if (direction.lengthSqr() < 1.0e-6) {
            return;
        }
        vertex(buffer, pose, from, direction, color);
        vertex(buffer, pose, to, direction, color);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 point, Vec3 direction, int color) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(color)
                .setNormal(pose, (float) direction.x, (float) direction.y, (float) direction.z);
    }

    private static List<Edge> edges(List<AttackPreviewPayload.Zone> zones) {
        List<Edge> edges = new ArrayList<>();
        for (AttackPreviewPayload.Zone zone : zones) {
            for (List<Point> contour : contours(zone.shape())) {
                int stride = Math.max(1, contour.size() / 8);
                for (int i = 0; i < contour.size(); i++) {
                    Point first = contour.get(i);
                    Point second = contour.get((i + 1) % contour.size());
                    Vec3 a = AbilityRun.local(zone.origin(), zone.yaw(), first.forward(), first.right());
                    Vec3 b = AbilityRun.local(zone.origin(), zone.yaw(), second.forward(), second.right());
                    Vec3 low = a.add(0, -zone.down(), 0);
                    Vec3 high = a.add(0, zone.up(), 0);
                    edges.add(new Edge(low, b.add(0, -zone.down(), 0), LOWER));
                    edges.add(new Edge(high, b.add(0, zone.up(), 0), UPPER));
                    if (i % stride == 0) {
                        edges.add(new Edge(low, high, VERTICAL));
                    }
                }
            }
        }
        return List.copyOf(edges);
    }

    private static List<List<Point>> contours(Shape shape) {
        return switch (shape) {
            case Shape.Rectangle r -> {
                double start = r.offset();
                double end = start + r.length();
                double width = r.width() / 2.0;
                yield List.of(List.of(new Point(start, -width), new Point(end, -width),
                        new Point(end, width), new Point(start, width)));
            }
            case Shape.Circle c -> List.of(circle(c.radius()));
            case Shape.Ring r -> List.of(circle(r.outer()), circle(r.inner()));
            case Shape.Cone c -> List.of(cone(c.radius(), c.angle()));
            case Shape.Rays r -> rays(r);
        };
    }

    private static List<Point> circle(double radius) {
        List<Point> points = new ArrayList<>(64);
        for (int i = 0; i < 64; i++) {
            double angle = Math.PI * i / 32.0;
            points.add(new Point(radius * Math.cos(angle), radius * Math.sin(angle)));
        }
        return points;
    }

    private static List<Point> cone(double radius, double width) {
        if (width >= 360.0) {
            return circle(radius);
        }
        double half = Math.toRadians(width / 2.0);
        int steps = Math.max(2, (int) Math.ceil(width / 5.625));
        List<Point> points = new ArrayList<>(steps + 2);
        points.add(new Point(0, 0));
        for (int i = 0; i <= steps; i++) {
            double angle = -half + i * 2.0 * half / steps;
            points.add(new Point(radius * Math.cos(angle), radius * Math.sin(angle)));
        }
        return points;
    }

    private static List<List<Point>> rays(Shape.Rays rays) {
        // Same normalized ray count and lane width as the hit test. Each ray has two feet at its centre edge.
        int count = ((AttackZone.CircleRays) rays.zone()).rayCount();
        double near = rays.widthAt(0.0) / 2.0;
        double far = rays.widthAt(rays.length()) / 2.0;
        List<List<Point>> contours = new ArrayList<>();
        for (int i = 0; i < Math.min(count, 128); i++) {
            double angle = 2.0 * Math.PI * i / count;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            contours.add(List.of(new Point(near * sin, -near * cos),
                    new Point(rays.length() * cos + far * sin, rays.length() * sin - far * cos),
                    new Point(rays.length() * cos - far * sin, rays.length() * sin + far * cos),
                    new Point(-near * sin, near * cos)));
        }
        return contours;
    }

    private record Point(double forward, double right) {
    }

    private record Edge(Vec3 from, Vec3 to, int color) {
    }

    private record Preview(List<Edge> edges, List<AABB> hits, long expiresAt) {
    }
}
