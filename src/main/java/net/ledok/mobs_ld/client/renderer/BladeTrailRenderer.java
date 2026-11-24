package net.ledok.mobs_ld.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.ledok.mobs_ld.mob.LdMob;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Brief ribbons sampled from two live weapon markers after GeckoLib has posed the model. */
public final class BladeTrailRenderer {
    private static final double FADE_TICKS = 4.0;
    private static final int MAX_SAMPLES = 18;
    private static final Map<Integer, Trail> ACTIVE = new HashMap<>();
    private static ClientLevel world;

    private BladeTrailRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(BladeTrailRenderer::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> switchWorld(null));
    }

    public static void start(LdMob mob, String near, String far, double ticks, int color) {
        if (mob.level() instanceof ClientLevel level) {
            switchWorld(level);
            ACTIVE.put(mob.getId(), new Trail(mob.currentAction(), near, far, level.getGameTime() + ticks,
                    color, new ArrayDeque<>()));
        }
    }

    public static void sample(LdMob mob, float partialTick) {
        Trail trail = ACTIVE.get(mob.getId());
        if (trail == null || mob.level() != world || !mob.isAlive() || !mob.currentAction().equals(trail.action())) {
            return;
        }
        double now = world.getGameTime() + partialTick;
        if (now > trail.endsAt()) {
            return;
        }
        Vec3 near = mob.markerPosition(trail.near());
        Vec3 far = mob.markerPosition(trail.far());
        if (near == null || far == null) {
            return;
        }
        Sample previous = trail.samples().peekLast();
        if (previous != null && (now <= previous.time()
                || near.distanceToSqr(previous.near()) + far.distanceToSqr(previous.far()) < 1.0e-4)) {
            return;
        }
        if (previous != null && (now - previous.time() > FADE_TICKS || far.distanceToSqr(previous.far()) > 16.0)) {
            trail.samples().clear();
        }
        if (trail.samples().size() >= MAX_SAMPLES) {
            trail.samples().removeFirst();
        }
        trail.samples().addLast(new Sample(near, far, now));
    }

    private static void switchWorld(ClientLevel level) {
        if (world != level) {
            ACTIVE.clear();
            world = level;
        }
    }

    private static void render(WorldRenderContext context) {
        switchWorld(context.world());
        if (world == null || ACTIVE.isEmpty() || context.consumers() == null || context.matrixStack() == null) {
            return;
        }
        double now = world.getGameTime() + context.tickCounter().getGameTimeDeltaPartialTick(false);
        ACTIVE.values().removeIf(trail -> now > trail.endsAt() + FADE_TICKS);
        VertexConsumer buffer = context.consumers().getBuffer(RenderType.debugQuads());
        Matrix4f matrix = context.matrixStack().last().pose();
        Vec3 camera = context.camera().getPosition();
        for (Trail trail : ACTIVE.values()) {
            trail.samples().removeIf(sample -> now > sample.time() + FADE_TICKS);
            Sample previous = null;
            for (Sample sample : trail.samples()) {
                if (previous != null) {
                    vertex(buffer, matrix, previous.near().subtract(camera), color(trail.rgb(), previous, now, 0.08));
                    vertex(buffer, matrix, previous.far().subtract(camera), color(trail.rgb(), previous, now, 0.8));
                    vertex(buffer, matrix, sample.far().subtract(camera), color(trail.rgb(), sample, now, 0.8));
                    vertex(buffer, matrix, sample.near().subtract(camera), color(trail.rgb(), sample, now, 0.08));
                }
                previous = sample;
            }
        }
    }

    private static int color(int rgb, Sample sample, double now, double opacity) {
        double fade = Mth.clamp(1.0 - (now - sample.time()) / FADE_TICKS, 0.0, 1.0);
        return (int) (255 * fade * opacity) << 24 | rgb;
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 position, int color) {
        buffer.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z).setColor(color);
    }

    private record Trail(String action, String near, String far, double endsAt, int rgb, Deque<Sample> samples) {
    }

    private record Sample(Vec3 near, Vec3 far, double time) {
    }
}
