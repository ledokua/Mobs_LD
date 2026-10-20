package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.LdMob;
import net.ledok.mobs_ld.mob.behaviour.HitTracks;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.MobCombat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Weapon hitbox that follows the animation: from {@code from} to {@code to} a capsule of {@code radius} blocks is
 * swept along the bones in {@code path} (e.g. wrist to blade tip), using the clip's baked {@link HitTracks}; anyone
 * it passes through takes {@code hit} once. No ground telegraph - players read the swing itself. Fast swings are
 * sub-stepped, so a blade cannot skip past someone between two ticks.
 *
 * @param clip    which clip's track to read; empty = the ability's own clip
 * @param aimSide re-aim when the window opens so a strike line this far to the mob's right meets the target
 */
public record TraceEvent(double from, double to, List<String> path, double radius, Optional<String> clip,
                         Optional<Double> aimSide, HitSpec hit) implements AbilityEvent {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> WARNED = new HashSet<>();
    /** Sub-steps per tick when sweeping (enough for a 4-block blade turning ~90 degrees a tick). */
    private static final int SUBSTEPS = 4;

    public static final MapCodec<TraceEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("from").forGetter(TraceEvent::from),
            Codec.DOUBLE.fieldOf("to").forGetter(TraceEvent::to),
            Codec.STRING.listOf().fieldOf("path").forGetter(TraceEvent::path),
            Codec.DOUBLE.optionalFieldOf("radius", 0.6).forGetter(TraceEvent::radius),
            Codec.STRING.optionalFieldOf("clip").forGetter(TraceEvent::clip),
            Codec.DOUBLE.optionalFieldOf("aim_side").forGetter(TraceEvent::aimSide),
            HitSpec.CODEC.optionalFieldOf("hit", HitSpec.DEFAULT).forGetter(TraceEvent::hit)
    ).apply(i, TraceEvent::new));

    @Override
    public String type() {
        return "trace";
    }

    @Override
    public double begin() {
        return from;
    }

    @Override
    public double end() {
        return Math.max(from, to);
    }

    @Override
    public Instance start(AbilityRun run) {
        LdMob mob = run.mob();
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        String track = clip.orElse(run.ability().clipOr(run.name()));
        if (!HitTracks.has(mobId, track)) {
            String key = mobId + "/" + track;
            synchronized (WARNED) {
                if (WARNED.add(key)) {
                    LOGGER.warn("trace event on {} has no baked hit track for clip '{}' (run /mobs_ldc bake)", mobId, track);
                }
            }
            run.combat().trace("trace: no hit track for " + track + " (bake it)");
            return new Instance() {
            };
        }
        Set<UUID> struck = new HashSet<>();
        return new Instance() {
            private double last = from;

            @Override
            public void tick(AbilityRun run) {
                sweep(run, Math.min(run.seconds(), to));
            }

            @Override
            public void finish(AbilityRun run) {
                sweep(run, to);
            }

            private void sweep(AbilityRun run, double now) {
                if (now <= last) {
                    return;
                }
                for (int step = 1; step <= SUBSTEPS; step++) {
                    double t = last + (now - last) * step / SUBSTEPS;
                    List<Vec3> points = new ArrayList<>(path.size());
                    for (String bone : path) {
                        Vec3 local = HitTracks.at(mobId, track, bone, t);
                        if (local == null) {
                            return;
                        }
                        points.add(toWorld(mob, run.yaw(), local));
                    }
                    for (int s = 0; s + 1 < points.size(); s++) {
                        strike(run, points.get(s), points.get(s + 1));
                    }
                }
                last = now;
            }

            private void strike(AbilityRun run, Vec3 a, Vec3 b) {
                if (MobCombat.isDebug()) {
                    showSegment(run, a, b);
                }
                AABB around = new AABB(a, b).inflate(radius + 1.0);
                for (LivingEntity victim : run.enemiesNear(around)) {
                    if (struck.contains(victim.getUUID())) {
                        continue;
                    }
                    AABB body = victim.getBoundingBox().inflate(radius);
                    if (body.contains(a) || body.contains(b) || body.clip(a, b).isPresent()) {
                        struck.add(victim.getUUID());
                        hit.apply(mob, victim, mob.position());
                    }
                }
            }
        };
    }

    /** Mob-local (forward, up, right) to world, using the ability's locked facing. */
    private static Vec3 toWorld(LdMob mob, float yawDegrees, Vec3 local) {
        return mob.position().add(AbilityRun.forward(yawDegrees).scale(local.x)).add(0.0, local.y, 0.0)
                .add(AbilityRun.right(yawDegrees).scale(local.z));
    }

    private static void showSegment(AbilityRun run, Vec3 a, Vec3 b) {
        int dots = Math.max(2, (int) Math.ceil(a.distanceTo(b) / 0.4));
        for (int i = 0; i <= dots; i++) {
            Vec3 p = a.lerp(b, (double) i / dots);
            run.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
