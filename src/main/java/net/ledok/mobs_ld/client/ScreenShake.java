package net.ledok.mobs_ld.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Client-side camera-shake state. Effects call {@link #trigger}, {@link #tick} decays it once per client tick,
 * and {@link net.ledok.mobs_ld.mixin.client.CameraMixin} reads {@link #currentIntensity} each frame to jostle the
 * camera. Purely cosmetic and entirely client-local.
 */
public final class ScreenShake {
    /** A slam felt from this far away (blocks) or closer; intensity falls off to zero at the edge. */
    private static final double SLAM_RANGE = 22.0;
    /** Peak camera offset of a point-blank slam, in blocks. */
    private static final float SLAM_PEAK = 0.32F;
    /** How many ticks a slam shake decays over. */
    private static final int SLAM_DURATION = 6;

    private static float magnitude = 0.0F;
    private static int ticksRemaining = 0;
    private static int duration = 1;

    private ScreenShake() {
    }

    /** Shakes the local player's camera for a ground slam at the given world position, with distance falloff. */
    public static void shakeFromSlam(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double distSqr = mc.player.distanceToSqr(x, y, z);
        if (distSqr >= SLAM_RANGE * SLAM_RANGE) {
            return;
        }
        float falloff = 1.0F - (float) (Math.sqrt(distSqr) / SLAM_RANGE);
        trigger(SLAM_PEAK * falloff * falloff, SLAM_DURATION);
    }

    /**
     * Starts (or strengthens) a shake.
     *
     * @param strength       peak camera offset in blocks
     * @param durationTicks  how long it decays to zero over
     */
    public static void trigger(float strength, int durationTicks) {
        if (strength <= magnitude && ticksRemaining > 0) {
            return; // don't let a weaker shake cut a stronger one short
        }
        magnitude = strength;
        duration = Math.max(1, durationTicks);
        ticksRemaining = duration;
    }

    public static void tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
    }

    /** Kills any active shake; a shake in progress must not carry across a disconnect into the next world. */
    public static void reset() {
        magnitude = 0.0F;
        ticksRemaining = 0;
        duration = 1;
    }

    /** Current shake amplitude in blocks, eased so it fades out smoothly; 0 when idle. */
    public static float currentIntensity(float partialTick) {
        if (ticksRemaining <= 0) {
            return 0.0F;
        }
        float remaining = (ticksRemaining - partialTick) / (float) duration;
        return magnitude * Mth.clamp(remaining, 0.0F, 1.0F);
    }
}
