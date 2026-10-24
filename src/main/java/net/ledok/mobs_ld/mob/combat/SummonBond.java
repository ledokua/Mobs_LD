package net.ledok.mobs_ld.mob.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.UUID;

/**
 * A {@code bound} add's tie to its summoner, kept in entity tags so it survives saves: {@value #SUMMON_TAG} plus
 * {@code mobs_ld_summoner:<uuid>}, set before the add enters the level. A bound add leaves in a puff of smoke (no death,
 * no drops) once its summoner is dead or removed, no longer counts it among its adds (a reset while the add was
 * unloaded), or has not been loaded in its level for {@value #MISSING_TICKS} ticks while the add ticks (a restart or
 * chunk unload that kept only the add, or the summoner gone through a portal). Checked every
 * {@value #CHECK_INTERVAL} ticks from the add's own AI tick; only tagged mobs pay for it (one null check otherwise).
 *
 * <p>The summoner's death and reset discard the adds it tracks, as for every summon; this is the safety net for the ones
 * it cannot reach then.
 */
public final class SummonBond {
    public static final String SUMMON_TAG = "mobs_ld_summon";
    public static final String SUMMONER_TAG_PREFIX = "mobs_ld_summoner:";
    /** The add drops nothing when it dies: no loot table, no custom drops, no equipment. */
    public static final String NO_LOOT_TAG = "mobs_ld_no_loot";
    /** The add drops no experience. */
    public static final String NO_XP_TAG = "mobs_ld_no_xp";
    static final int CHECK_INTERVAL = 20;
    /** A summoner missing this long (while the add ticks) is gone for good. */
    static final int MISSING_TICKS = 100;
    private static final long NONE = Long.MIN_VALUE;

    private final UUID summoner;
    /** Game time the summoner was first found missing in the current run of checks; NONE while it is there. */
    private long missingSince = NONE;
    private long lastCheck = NONE;

    private SummonBond(UUID summoner) {
        this.summoner = summoner;
    }

    /** Ties {@code add} to {@code summoner} (call before adding it to the level). */
    public static void bind(Entity add, Entity summoner) {
        add.addTag(SUMMON_TAG);
        add.addTag(SUMMONER_TAG_PREFIX + summoner.getUUID());
    }

    /** The bond {@code entity} carries in its tags, or null if it is not a bound add. */
    public static SummonBond of(Entity entity) {
        if (!entity.getTags().contains(SUMMON_TAG)) {
            return null;
        }
        for (String tag : entity.getTags()) {
            if (tag.startsWith(SUMMONER_TAG_PREFIX)) {
                try {
                    return new SummonBond(UUID.fromString(tag.substring(SUMMONER_TAG_PREFIX.length())));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    public UUID summoner() {
        return summoner;
    }

    /** Whether {@code add} has outlived its summoner (call every tick; it looks every {@value #CHECK_INTERVAL}). */
    public boolean orphaned(Mob add) {
        if ((add.tickCount + add.getId()) % CHECK_INTERVAL != 0 || !(add.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        Entity owner = level.getEntity(summoner);
        if (owner == null) {
            // Not loaded: its chunk may simply load after ours. A gap in our own checks (we were not ticking either)
            // starts the wait over.
            if (missingSince == NONE || now - lastCheck > CHECK_INTERVAL * 3L) {
                missingSince = now;
            }
            lastCheck = now;
            return now - missingSince >= MISSING_TICKS;
        }
        missingSince = NONE;
        lastCheck = now;
        if (!owner.isAlive()) {
            return true;
        }
        MobCombat combat = CombatHost.of(owner);
        return combat == null || !combat.ownsSummon(add);
    }
}
