package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Spawns {@code count} entities at an offset from the mob (forward/up/right in blocks, along its facing), with up to
 * {@code spread} blocks of random scatter. Summoned mobs start on the summoner's aim target and are removed when the
 * summoner dies or resets. Spawning happens inside the summoner's own tick, which is how Arenas_LD recognises
 * minions of a run boss.
 *
 * @param maxAlive no more spawn while this many of the summoner's adds are still alive
 */
public record SummonEvent(double at, ResourceLocation entity, int count, double forward, double up, double right,
                          double spread, int maxAlive) implements AbilityEvent.Instant {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final MapCodec<SummonEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(SummonEvent::at),
            ResourceLocation.CODEC.fieldOf("entity").forGetter(SummonEvent::entity),
            Codec.INT.optionalFieldOf("count", 1).forGetter(SummonEvent::count),
            Codec.DOUBLE.optionalFieldOf("forward", 0.0).forGetter(SummonEvent::forward),
            Codec.DOUBLE.optionalFieldOf("up", 0.0).forGetter(SummonEvent::up),
            Codec.DOUBLE.optionalFieldOf("right", 0.0).forGetter(SummonEvent::right),
            Codec.DOUBLE.optionalFieldOf("spread", 1.0).forGetter(SummonEvent::spread),
            Codec.INT.optionalFieldOf("max_alive", 8).forGetter(SummonEvent::maxAlive)
    ).apply(i, SummonEvent::new));

    @Override
    public String type() {
        return "summon";
    }

    @Override
    public void fire(AbilityRun run) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity).orElse(null);
        if (type == null) {
            LOGGER.warn("Mob behaviour summons unknown entity {}", entity);
            return;
        }
        Mob mob = run.mob();
        ServerLevel level = run.level();
        float yaw = run.yaw();
        Vec3 ahead = AbilityRun.forward(yaw);
        Vec3 side = new Vec3(-ahead.z, 0.0, ahead.x);
        Vec3 anchor = mob.position().add(ahead.scale(forward)).add(side.scale(right)).add(0.0, up, 0.0);
        for (int n = 0; n < count && run.combat().aliveSummons() < maxAlive; n++) {
            Entity spawned = type.create(level);
            if (spawned == null) {
                return;
            }
            double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
            double scatter = mob.getRandom().nextDouble() * spread;
            spawned.moveTo(anchor.x + Math.cos(angle) * scatter, anchor.y, anchor.z + Math.sin(angle) * scatter,
                    yaw, 0.0F);
            if (spawned instanceof Mob summoned) {
                summoned.finalizeSpawn(level, level.getCurrentDifficultyAt(summoned.blockPosition()),
                        MobSpawnType.MOB_SUMMONED, null);
                if (run.aim() != null) {
                    summoned.setTarget(run.aim());
                }
            }
            level.addFreshEntity(spawned);
            run.combat().trackSummon(spawned);
        }
    }
}
