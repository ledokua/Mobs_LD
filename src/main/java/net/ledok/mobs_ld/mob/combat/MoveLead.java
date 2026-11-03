package net.ledok.mobs_ld.mob.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Where a fighter is heading, for zones placed ahead of a moving player ({@code hit.origin: each_fighter} with
 * {@code lead}): the fighters' positions over the last {@value #WINDOW_SECONDS} s, sampled once per tick (with the
 * game time of each sample), but only while {@link MobCombat} may need them. The prediction and the trail bookkeeping
 * are pure, so they are unit-tested.
 */
public final class MoveLead {
    /** Seconds of positions the movement is measured over. */
    public static final double WINDOW_SECONDS = 0.5;
    /** A predicted spot is never further than this from where the player stands now (blocks). */
    public static final double MAX_LEAD_BLOCKS = 4.0;
    private static final long WINDOW_TICKS = Math.round(WINDOW_SECONDS * 20.0);
    /** Samples kept per fighter: the window plus one, so a full window is still there a tick after the last sample. */
    private static final int SAMPLES = (int) WINDOW_TICKS + 2;

    private final Map<UUID, ArrayDeque<Sample>> trails = new HashMap<>();

    /** Records where each fighter stands at game time {@code tick}; players no longer fighting are dropped. */
    public void sample(List<Player> fighters, long tick) {
        Set<UUID> present = new HashSet<>();
        for (Player fighter : fighters) {
            present.add(fighter.getUUID());
            sample(fighter.getUUID(), fighter.position(), tick);
        }
        trails.keySet().retainAll(present);
    }

    /** Records where {@code id} stands at game time {@code tick}. */
    public void sample(UUID id, Vec3 position, long tick) {
        ArrayDeque<Sample> trail = trails.computeIfAbsent(id, key -> new ArrayDeque<>(SAMPLES));
        if (trail.size() == SAMPLES) {
            trail.removeFirst();
        }
        trail.addLast(new Sample(position, tick));
    }

    public boolean isEmpty() {
        return trails.isEmpty();
    }

    public void clear() {
        trails.clear();
    }

    /**
     * Where {@code entity} will be {@code lead} seconds after game time {@code now} if it keeps its horizontal
     * movement of the last half second; where it stands without a trail (not sampled, or just joined) or a lead.
     */
    public Vec3 predict(LivingEntity entity, double lead, long now) {
        return predict(entity.getUUID(), entity.position(), lead, now);
    }

    /**
     * As {@link #predict(LivingEntity, double, long)} for {@code id} standing at {@code position}: the movement is
     * measured from its oldest sample within the window (or the newest older one) to {@code position}, over the real
     * ticks between them (samples are taken at the end of a tick, a zone may lock at the start of the next one).
     */
    public Vec3 predict(UUID id, Vec3 position, double lead, long now) {
        ArrayDeque<Sample> trail = trails.get(id);
        if (lead <= 0.0 || trail == null || trail.isEmpty()) {
            return position;
        }
        Sample from = null;
        for (Iterator<Sample> it = trail.iterator(); it.hasNext(); ) {
            Sample sample = it.next();
            if (now - sample.tick() <= WINDOW_TICKS) {
                from = sample;
                break;
            }
            from = sample; // older than the window: kept only if nothing newer is in it
        }
        if (from == null || now <= from.tick()) {
            return position;
        }
        return predict(from.position(), position, (now - from.tick()) / 20.0, lead, MAX_LEAD_BLOCKS);
    }

    /**
     * {@code newest} moved on by {@code lead} seconds at the horizontal velocity from {@code oldest} to {@code newest}
     * over {@code spanSeconds}, the move capped at {@code cap} blocks; the height stays {@code newest}'s.
     */
    public static Vec3 predict(Vec3 oldest, Vec3 newest, double spanSeconds, double lead, double cap) {
        if (spanSeconds <= 0.0 || lead <= 0.0) {
            return newest;
        }
        double dx = (newest.x - oldest.x) / spanSeconds * lead;
        double dz = (newest.z - oldest.z) / spanSeconds * lead;
        double length = Math.hypot(dx, dz);
        if (length > cap && length > 0.0) {
            dx *= cap / length;
            dz *= cap / length;
        }
        return new Vec3(newest.x + dx, newest.y, newest.z + dz);
    }

    private record Sample(Vec3 position, long tick) {
    }
}
