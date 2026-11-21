package net.ledok.mobs_ld.mob.damage;

/**
 * The cap {@link AttackShaping} found for the hit a living entity is taking (NaN: none), carried from the start of its
 * {@code hurt} to its {@code actuallyHurt}. Implemented on LivingEntity by a mixin.
 */
public interface HitCapHolder {
    double mobs_ld$hitCap();

    void mobs_ld$setHitCap(double cap);
}
