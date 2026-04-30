package net.ledok.mobs_ld.entity.boss;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public abstract class AbilityDefinition {
    public abstract String id();

    public abstract int priority();

    public abstract boolean isPassive();

    public abstract TriggerCondition trigger();

    public abstract int cooldownTicks();

    public AttackZone zone() {
        return null;
    }

    public int windupTicks() {
        return 0;
    }

    public int damagePersistTicks() {
        return 0;
    }

    public AttackDisplayConfig displayConfig() {
        return null;
    }

    public boolean grantsImmunityDuringWindup() {
        return false;
    }

    public void onWindupStart(ServerLevel world, BaseBossMob boss) {
    }

    public void onActivate(ServerLevel world, BaseBossMob boss, Vec3 zoneOrigin, float yawDegrees) {
        if (zone() == null) {
            return;
        }
        float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
        boss.applyZoneDamage(zone(), zoneOrigin, yawDegrees, damage);
    }

    public void onEnd(ServerLevel world, BaseBossMob boss) {
    }
}
