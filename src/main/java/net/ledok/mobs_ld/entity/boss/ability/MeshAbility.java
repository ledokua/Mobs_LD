package net.ledok.mobs_ld.entity.boss.ability;

import net.ledok.mobs_ld.entity.attack.AttackDisplayConfig;
import net.ledok.mobs_ld.entity.attack.AttackZone;
import net.ledok.mobs_ld.entity.attack.AttackZoneDisplay;
import net.ledok.mobs_ld.entity.boss.AbilityDefinition;
import net.ledok.mobs_ld.entity.boss.BaseBossMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class MeshAbility extends AbilityDefinition {
    private final List<AttackZoneDisplay> meshDisplays = new ArrayList<>();

    protected abstract int linesPerAxis();

    protected abstract float spacing();

    protected abstract float lineWidth();

    @Override
    public AttackZone zone() {
        return null;
    }

    @Override
    public void onWindupStart(ServerLevel world, BaseBossMob boss) {
        clearDisplays();

        float totalSize = spacing() * linesPerAxis();
        float half = totalSize * 0.5F;
        float lineLength = totalSize + spacing();
        float halfLen = lineLength * 0.5F;
        Vec3 origin = boss.position();
        AttackDisplayConfig cfg = displayConfig() != null ? displayConfig() : AttackDisplayConfig.DEFAULT;

        for (int i = 0; i < linesPerAxis(); i++) {
            float offsetZ = -half + spacing() * i + spacing() * 0.5F;
            Vec3 lineOrigin = new Vec3(origin.x, origin.y, origin.z + offsetZ);
            meshDisplays.add(AttackZoneDisplay.spawn(
                    world,
                    lineOrigin,
                    90.0F,
                    new AttackZone.Rectangle(lineWidth(), lineLength, -halfLen),
                    cfg,
                    Math.max(1, windupTicks())
            ));
        }

        for (int i = 0; i < linesPerAxis(); i++) {
            float offsetX = -half + spacing() * i + spacing() * 0.5F;
            Vec3 lineOrigin = new Vec3(origin.x + offsetX, origin.y, origin.z);
            meshDisplays.add(AttackZoneDisplay.spawn(
                    world,
                    lineOrigin,
                    0.0F,
                    new AttackZone.Rectangle(lineWidth(), lineLength, -halfLen),
                    cfg,
                    Math.max(1, windupTicks())
            ));
        }
    }

    @Override
    public void onWindupTick(ServerLevel world, BaseBossMob boss, int windupTimer) {
        for (AttackZoneDisplay display : meshDisplays) {
            display.update(windupTimer, Math.max(1, windupTicks()));
        }
    }

    @Override
    public void onActivate(ServerLevel world, BaseBossMob boss, Vec3 origin, float yawDegrees) {
        Set<ServerPlayer> alreadyHit = new HashSet<>();
        float totalSize = spacing() * linesPerAxis();
        float half = totalSize * 0.5F;
        float lineLength = totalSize + spacing();
        float halfLength = lineLength * 0.5F;
        float halfWidth = lineWidth() * 0.5F;
        float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE);

        for (int i = 0; i < linesPerAxis(); i++) {
            float offsetZ = -half + spacing() * i + spacing() * 0.5F;
            AABB box = new AABB(
                    origin.x - halfLength,
                    origin.y - 0.5,
                    origin.z + offsetZ - halfWidth,
                    origin.x + halfLength,
                    origin.y + 2.0,
                    origin.z + offsetZ + halfWidth
            );
            world.getEntitiesOfClass(ServerPlayer.class, box).stream()
                    .filter(alreadyHit::add)
                    .forEach(player -> player.hurt(world.damageSources().mobAttack(boss), damage));
        }

        for (int i = 0; i < linesPerAxis(); i++) {
            float offsetX = -half + spacing() * i + spacing() * 0.5F;
            AABB box = new AABB(
                    origin.x + offsetX - halfWidth,
                    origin.y - 0.5,
                    origin.z - halfLength,
                    origin.x + offsetX + halfWidth,
                    origin.y + 2.0,
                    origin.z + halfLength
            );
            world.getEntitiesOfClass(ServerPlayer.class, box).stream()
                    .filter(alreadyHit::add)
                    .forEach(player -> player.hurt(world.damageSources().mobAttack(boss), damage));
        }
    }

    @Override
    public void onEnd(ServerLevel world, BaseBossMob boss) {
        clearDisplays();
    }

    private void clearDisplays() {
        for (AttackZoneDisplay display : meshDisplays) {
            display.remove();
        }
        meshDisplays.clear();
    }
}
