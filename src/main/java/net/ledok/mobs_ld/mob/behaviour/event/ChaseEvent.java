package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.CombatHost;
import net.ledok.mobs_ld.mob.combat.CombatTargets;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Homing run at the aim target from {@code at} for up to {@code maxTime}: the mob turns toward the target at most
 * {@code turnRate} degrees per tick (so it can be side-stepped) and moves {@code speed} blocks per tick in a straight
 * line (so it can be baited into walls). Getting within {@code reach} hands over to the {@code onReach} ability,
 * running out of time to {@code onTimeout}; hitting a wall staggers it (or just stops). Optional {@code hit} tramples
 * anyone the body runs through, once each.
 *
 * @param clip action clip while running (usually a loop); empty keeps the ability's clip
 */
public record ChaseEvent(double at, String clip, double maxTime, double speed, double turnRate, double reach,
                         Optional<String> onReach, Optional<String> onTimeout, DashEvent.WallRule onWall,
                         double width, Optional<HitSpec> hit) implements AbilityEvent {
    /** Ticks of running before a collision counts as hitting a wall (the first steps can graze the start spot). */
    private static final int WALL_GRACE_TICKS = 3;

    public static final MapCodec<ChaseEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(ChaseEvent::at),
            Codec.STRING.optionalFieldOf("clip", "").forGetter(ChaseEvent::clip),
            Codec.DOUBLE.fieldOf("max_time").forGetter(ChaseEvent::maxTime),
            Codec.DOUBLE.fieldOf("speed").forGetter(ChaseEvent::speed),
            Codec.DOUBLE.optionalFieldOf("turn_rate", 6.0).forGetter(ChaseEvent::turnRate),
            Codec.DOUBLE.optionalFieldOf("reach", 3.0).forGetter(ChaseEvent::reach),
            Codec.STRING.optionalFieldOf("on_reach").forGetter(ChaseEvent::onReach),
            Codec.STRING.optionalFieldOf("on_timeout").forGetter(ChaseEvent::onTimeout),
            DashEvent.WallRule.CODEC.optionalFieldOf("on_wall", DashEvent.WallRule.STAGGER).forGetter(ChaseEvent::onWall),
            Codec.DOUBLE.optionalFieldOf("width", 2.0).forGetter(ChaseEvent::width),
            HitSpec.CODEC.optionalFieldOf("hit").forGetter(ChaseEvent::hit)
    ).apply(i, ChaseEvent::new));

    @Override
    public String type() {
        return "chase";
    }

    @Override
    public double begin() {
        return at;
    }

    @Override
    public double end() {
        return at + maxTime;
    }

    @Override
    public List<String> chains() {
        List<String> names = new ArrayList<>();
        onReach.ifPresent(names::add);
        onTimeout.ifPresent(names::add);
        return names;
    }

    @Override
    public Instance start(AbilityRun run) {
        Mob mob = run.mob();
        if (!clip.isEmpty()) {
            ((CombatHost) mob).mobs_ld$playAction(clip, 1.0);
        }
        Set<UUID> struck = new HashSet<>();
        return new Instance() {
            private int running;
            private boolean stopped;

            @Override
            public void tick(AbilityRun run) {
                if (stopped) {
                    return;
                }
                LivingEntity aim = run.aim();
                if (aim == null || !aim.isAlive()) {
                    halt();
                    onTimeout.ifPresent(run::chain);
                    return;
                }
                if (CombatTargets.distance(mob, aim) <= reach) {
                    halt();
                    onReach.ifPresent(run::chain);
                    return;
                }
                if (mob.horizontalCollision && running > WALL_GRACE_TICKS) {
                    halt();
                    if (onWall == DashEvent.WallRule.STAGGER) {
                        run.stagger();
                    }
                    return;
                }
                double dx = aim.getX() - mob.getX();
                double dz = aim.getZ() - mob.getZ();
                float yaw = Mth.approachDegrees(run.yaw(), (float) Math.toDegrees(Math.atan2(-dx, dz)), (float) turnRate);
                run.setYaw(yaw);
                Vec3 direction = AbilityRun.forward(yaw);
                mob.setDeltaMovement(direction.x * speed, mob.getDeltaMovement().y, direction.z * speed);
                mob.hasImpulse = true;
                running++;
                hit.ifPresent(spec -> {
                    for (LivingEntity victim : run.enemiesNear(mob.getBoundingBox().inflate(width / 2.0, 0.5, width / 2.0))) {
                        if (struck.add(victim.getUUID())) {
                            spec.apply(run, victim, mob.position());
                        }
                    }
                });
            }

            @Override
            public void finish(AbilityRun run) {
                if (!stopped) {
                    halt();
                    onTimeout.ifPresent(run::chain); // ran out of time without reaching anyone
                }
            }

            @Override
            public void cancel(AbilityRun run) {
                halt();
            }

            private void halt() {
                if (!stopped) {
                    stopped = true;
                    mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
                }
            }
        };
    }
}
