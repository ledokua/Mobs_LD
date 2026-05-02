package net.ledok.mobs_ld.entity.boss;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public abstract class AbilityDefinition {
    public abstract String id();

    public abstract int priority();

    public abstract boolean isPassive();

    public abstract TriggerCondition trigger();

    public abstract int cooldownTicks();

    // Override to set a cooldown when this ability first becomes available.
    public int initialCooldown() {
        return 0;
    }

    public boolean canUse(ServerLevel world, BaseBossMob boss) {
        return boss.getTarget() != null;
    }

    public Vec3 resolveTargetOrigin(LivingEntity target) {
        return target.position();
    }

    public AttackZone zone() {
        return null;
    }

    public int windupTicks() {
        return 0;
    }

    public int damagePersistTicks() {
        return 0;
    }

    public double damageScale() {
        return 1.0;
    }

    public boolean canMoveWhilePersisting() {
        return false;
    }

    public boolean startsCooldownOnActivate() {
        return true;
    }

    public AttackDisplayConfig displayConfig() {
        return null;
    }

    public boolean grantsImmunityDuringWindup() {
        return false;
    }

    public void onWindupStart(ServerLevel world, BaseBossMob boss) {
    }

    public void onWindupTick(ServerLevel world, BaseBossMob boss, int windupTimer) {
    }

    public void onActivate(ServerLevel world, BaseBossMob boss, Vec3 zoneOrigin, float yawDegrees) {
        if (zone() == null) {
            return;
        }
        float damage = (float) (boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * damageScale());
        boss.applyZoneDamage(zone(), zoneOrigin, yawDegrees, damage);
    }

    public void onPersistTick(ServerLevel world, BaseBossMob boss, int persistTimer) {
    }

    public boolean keepDamageImmuneAfterEnd() {
        return false;
    }

    public void onEnd(ServerLevel world, BaseBossMob boss) {
    }
}
