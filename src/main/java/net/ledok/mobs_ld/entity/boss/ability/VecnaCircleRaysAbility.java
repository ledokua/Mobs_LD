package net.ledok.mobs_ld.entity.boss.ability;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.boss.AbilityDefinition;
import net.ledok.mobs_ld.entity.boss.BaseBossMob;
import net.ledok.mobs_ld.entity.boss.TriggerCondition;
import net.ledok.mobs_ld.entity.boss.mob.VecnaTheSecond;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VecnaCircleRaysAbility extends AbilityDefinition {
    private final int rayCount;
    private final int cooldown;
    private Vec3 castOrigin = Vec3.ZERO;
    private float castYaw = 0.0F;
    private final Set<UUID> alreadyHit = new HashSet<>();

    public VecnaCircleRaysAbility(int rayCount, int cooldown) {
        int normalizedRayCount = Math.max(2, rayCount);
        this.rayCount = normalizedRayCount % 2 == 0 ? normalizedRayCount : normalizedRayCount + 1;
        this.cooldown = Math.max(0, cooldown);
    }

    @Override
    public String id() {
        return "rays_" + rayCount;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean isPassive() {
        return false;
    }

    @Override
    public TriggerCondition trigger() {
        return new TriggerCondition.OnTimer(cooldown);
    }

    @Override
    public int cooldownTicks() {
        return cooldown;
    }

    @Override
    public int initialCooldown() {
        return cooldown;
    }

    @Override
    public int windupTicks() {
        return 15;
    }

    @Override
    public int damagePersistTicks() {
        return 5;
    }

    @Override
    public void onActivate(ServerLevel world, BaseBossMob boss, Vec3 zoneOrigin, float yawDegrees) {
        // Persist phase handles damage checks so the visible active window matches hit timing.
        castOrigin = zoneOrigin;
        castYaw = yawDegrees;
        alreadyHit.clear();
    }

    @Override
    public void onPersistTick(ServerLevel world, BaseBossMob boss, int persistTimer) {
        float damage = (float) (boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * damageScale());
        float reach = zone().maxForwardReach();
        AABB broad = new AABB(castOrigin, castOrigin).inflate(reach + 1.0F);
        Vec3 forward = new Vec3(
                -Math.sin(Math.toRadians(castYaw)),
                0.0,
                Math.cos(Math.toRadians(castYaw))
        );
        for (ServerPlayer player : world.getEntitiesOfClass(ServerPlayer.class, broad)) {
            UUID id = player.getUUID();
            if (alreadyHit.contains(id)) {
                continue;
            }
            if (boss.isInAttackZone(player.position(), zone(), castOrigin, forward)) {
                player.hurt(world.damageSources().mobAttack(boss), damage);
                alreadyHit.add(id);
            }
        }
    }

    @Override
    public void onEnd(ServerLevel world, BaseBossMob boss) {
        alreadyHit.clear();
    }

    @Override
    public double damageScale() {
        return 2;
    }

    @Override
    public boolean canMoveWhilePersisting() {
        return false;
    }

    @Override
    public AttackZone zone() {
        return new AttackZone.CircleRays(rayCount, 20.0F, 0.75F);
    }

    @Override
    public AttackDisplayConfig displayConfig() {
        return new AttackDisplayConfig(
                AttackDisplayConfig.AnimationStyle.SWEEP,
                0xFF1A0033,
                0xFF6600CC
        );
    }

    @Override
    public boolean canUse(ServerLevel world, BaseBossMob boss) {
        return boss instanceof VecnaTheSecond vecna && !vecna.isUnderground();
    }
}
