package net.ledok.mobs_ld.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Client-side camera-shake state. Effects call {@link #trigger}, {@link #tick} decays it once per client tick,
 * and {@link net.ledok.mobs_ld.mixin.client.CameraMixin} reads {@link #currentIntensity} each frame to jostle the
 * camera. Purely cosmetic and entirely client-local.
 */
public final class ScreenShake {
    /** Peak camera offset, in blocks, of a strength-1.0 shake felt point-blank. */
    private static final float MAX_PEAK = 0.32F;

    private static float magnitude = 0.0F;
    private static int ticksRemaining = 0;
    private static int duration = 1;

    private ScreenShake() {
    }

    /**
     * Shakes the local player's camera for an impact at a world position (the {@code shake <strength> <radius>}
     * animation instruction). Strength 1.0 is the hardest hit; intensity falls off quadratically to zero at
     * {@code radius} blocks, and stronger shakes also last longer.
     */
    public static void shakeAt(double x, double y, double z, float strength, double radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || strength <= 0.0F || radius <= 0.0) {
            return;
        }
        double distSqr = mc.player.distanceToSqr(x, y, z);
        if (distSqr >= radius * radius) {
            return;
        }
        float falloff = 1.0F - (float) (Math.sqrt(distSqr) / radius);
        float clamped = Math.min(strength, 1.0F);
        trigger(MAX_PEAK * clamped * falloff * falloff, 4 + Math.round(8 * clamped));
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
