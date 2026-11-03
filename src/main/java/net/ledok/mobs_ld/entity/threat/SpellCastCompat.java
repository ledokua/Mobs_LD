package net.ledok.mobs_ld.entity.threat;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Soft-dependency bridge to Spell Engine's casting state: whether a player is casting a spell with a cast time (or
 * channelling one) right now. Spell Engine casts do not use the vanilla item-use state, so the Warden's Listening
 * Silence asks here as well. Instant spells have no cast in progress (they are caught by the damage they deal).
 *
 * <p>Looked up by name once ({@code SpellCaster.Entity#isCastingSpell}, which Spell Engine mixes into every player),
 * so this compiles and answers false when Spell Engine is not installed or changed that method.
 */
public final class SpellCastCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CASTER = "net.spell_engine.internals.casting.SpellCaster$Entity";
    private static Class<?> caster;
    private static Method isCasting;
    private static boolean resolved;

    private SpellCastCompat() {
    }

    /** True if Spell Engine reports {@code player} casting a spell now; false without Spell Engine. */
    public static boolean isCasting(Player player) {
        if (!resolved) {
            resolve();
        }
        if (isCasting == null || !caster.isInstance(player)) {
            return false;
        }
        try {
            return (Boolean) isCasting.invoke(player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Spell Engine casting check failed; ignoring spell casts from now on", e);
            isCasting = null;
            return false;
        }
    }

    private static void resolve() {
        resolved = true;
        try {
            caster = Class.forName(CASTER);
            isCasting = caster.getMethod("isCastingSpell");
        } catch (ReflectiveOperationException | LinkageError e) {
            caster = null;
            isCasting = null; // Spell Engine is not installed (or moved its API)
        }
    }
}
