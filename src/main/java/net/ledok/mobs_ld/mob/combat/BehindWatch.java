package net.ledok.mobs_ld.mob.combat;

import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who has stood behind a mob, and since when, for an ability's {@code when.behind} (the Warden's Sculk Backlash
 * punishes players stacking at its back): a player counts once they have stood within {@code range} of it and inside
 * the rear {@code angle} for {@code for} seconds without a break. Fed by {@link MobCombat} only while such an ability
 * is allowed and off cooldown. Times are game ticks; the geometry and the bookkeeping are pure, so they are
 * unit-tested.
 */
public final class BehindWatch {
    /** When each player behind it now got there (game time). */
    private final Map<UUID, Long> since = new HashMap<>();

    /**
     * Whether a point {@code dx}/{@code dy}/{@code dz} blocks (world axes) from the mob's feet is behind it: at most
     * {@code range} blocks away horizontally, at most {@code height} above or below its feet, and within {@code angle}
     * degrees (the whole width) centred on its back, for a body facing {@code bodyYaw}. Standing right on it counts as
     * behind.
     */
    public static boolean isBehind(double dx, double dy, double dz, float bodyYaw, double range, double angle,
                                   double height) {
        double distance = Math.hypot(dx, dz);
        if (distance > range || Math.abs(dy) > height) {
            return false;
        }
        if (distance < 1.0e-6) {
            return true;
        }
        Vec3 back = AbilityRun.forward(bodyYaw).scale(-1.0);
        double cos = (dx * back.x + dz * back.z) / distance;
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos)))) <= angle / 2.0;
    }

    /** One look at the players: {@code behind} are those behind it now; anyone else's streak is broken. */
    public void update(Set<UUID> behind, long now) {
        since.keySet().retainAll(behind);
        for (UUID id : behind) {
            since.putIfAbsent(id, now);
        }
    }

    /** Players who have been behind it for at least {@code forTicks} at {@code now}. */
    public int count(long now, long forTicks) {
        int count = 0;
        for (long at : since.values()) {
            if (now - at >= forTicks) {
                count++;
            }
        }
        return count;
    }

    public void clear() {
        since.clear();
    }
}
