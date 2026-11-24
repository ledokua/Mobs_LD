package net.ledok.mobs_ld.client.vfx;

import net.ledok.mobs_ld.network.SpellVfxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side lifecycle manager for active procedural 3D spell effects.
 * Enforces a hard active cap so large boss encounters never degrade frame rate, and clears cleanly on disconnect.
 */
public final class SpellVfxManager {
    private static final int MAX_ACTIVE = 128;
    private static final List<SpellVfxInstance> ACTIVE = new ArrayList<>();

    private SpellVfxManager() {
    }

    public static void spawn(SpellVfxPayload payload) {
        if (ACTIVE.size() >= MAX_ACTIVE) {
            ACTIVE.removeFirst();
        }
        ACTIVE.add(new SpellVfxInstance(payload));
    }

    public static void cancel(int id) {
        for (SpellVfxInstance effect : ACTIVE) {
            if (effect.id == id) {
                effect.beginCancel();
                return;
            }
        }
    }

    public static void tick(Minecraft client) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        ClientLevel level = client.level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }
        if (client.isPaused()) {
            return;
        }
        Iterator<SpellVfxInstance> it = ACTIVE.iterator();
        while (it.hasNext()) {
            if (!it.next().tick(level)) {
                it.remove();
            }
        }
    }

    public static void reset() {
        ACTIVE.clear();
    }

    static List<SpellVfxInstance> active() {
        return ACTIVE;
    }
}
