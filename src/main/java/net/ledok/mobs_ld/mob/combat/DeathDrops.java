package net.ledok.mobs_ld.mob.combat;

/**
 * Every living entity, through LivingEntityCombatMixin: whether it is in the middle of its own death as a
 * {@code loot: false} add (see {@link SummonBond#NO_LOOT_TAG}). Whatever it drops then is a death drop and is held
 * back (EntityDropsMixin), even from subclass overrides and other mods' death hooks that bypass the loot rules.
 * Prefixed {@code mobs_ld$} because the mixin adds it to every living entity class.
 */
public interface DeathDrops {
    boolean mobs_ld$blocksDeathDrops();
}
