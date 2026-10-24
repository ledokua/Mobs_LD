package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.SkullSpec;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.Skulls;
import net.ledok.mobs_ld.mob.host.Head;
import net.ledok.mobs_ld.mob.host.HostAdapter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fires skulls of a named spec from the host's {@code skulls} ({@link SkullSpec}): {@code waves} waves,
 * {@code interval} seconds apart from {@code at}. Each wave fires one skull from every head in {@code from}, aimed at
 * where the target is at that moment; with several heads they form a flat wall across the line of fire,
 * {@code spread} blocks to either side of the target (the host's right head takes its right end). A host without
 * such heads fires one skull from its eyes. Skulls fly at a constant {@code speed} (the spec's unless set here), so
 * a skull reaches a target {@code d} blocks away in {@code d / speed} ticks.
 *
 * @param dangerous blue (vanilla's "dangerous") skulls: same damage and speed here, just the look
 */
public record ProjectileEvent(double at, String skull, Head.Selector from, EventTarget aim, int waves, double interval,
                              double spread, Optional<Double> speed, boolean dangerous) implements AbilityEvent {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> WARNED = new HashSet<>();

    public static final MapCodec<ProjectileEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(ProjectileEvent::at),
            Codec.STRING.fieldOf("skull").forGetter(ProjectileEvent::skull),
            Head.Selector.CODEC.optionalFieldOf("from", Head.Selector.ALL).forGetter(ProjectileEvent::from),
            EventTarget.CODEC.optionalFieldOf("aim", EventTarget.AIM).forGetter(ProjectileEvent::aim),
            Codec.intRange(1, 64).optionalFieldOf("waves", 1).forGetter(ProjectileEvent::waves),
            Codec.doubleRange(0.0, 60.0).optionalFieldOf("interval", 0.5).forGetter(ProjectileEvent::interval),
            Codec.doubleRange(0.0, 16.0).optionalFieldOf("spread", 0.0).forGetter(ProjectileEvent::spread),
            Codec.doubleRange(0.05, 3.9).optionalFieldOf("speed").forGetter(ProjectileEvent::speed),
            Codec.BOOL.optionalFieldOf("dangerous", false).forGetter(ProjectileEvent::dangerous)
    ).apply(i, ProjectileEvent::new));

    @Override
    public String type() {
        return "projectile";
    }

    @Override
    public double begin() {
        return at;
    }

    @Override
    public double end() {
        return at + (waves - 1) * interval;
    }

    @Override
    public List<String> slots() {
        return aim.slots();
    }

    @Override
    public List<String> skulls() {
        return List.of(skull);
    }

    @Override
    public Instance start(AbilityRun run) {
        return new Instance() {
            private int fired;

            @Override
            public void tick(AbilityRun run) {
                while (fired < waves && run.seconds() >= at + fired * interval) {
                    fireWave(run, fired++);
                }
            }

            @Override
            public void finish(AbilityRun run) {
                tick(run);
            }
        };
    }

    private void fireWave(AbilityRun run, int wave) {
        Mob mob = run.mob();
        SkullSpec spec = run.combat().skullSpec(skull);
        if (spec == null) {
            // Validated at load; only a host whose options lost the spec since (another phase's block) gets here.
            synchronized (WARNED) {
                if (WARNED.add(skull)) {
                    LOGGER.warn("projectile event: {} has no skull '{}' in its current host options; not firing",
                            mob.getType().getDescription().getString(), skull);
                }
            }
            return;
        }
        LivingEntity target = aim.resolve(run);
        Vec3 point = target != null ? Skulls.aimPoint(target) : run.aimPosition().add(0.0, 1.0, 0.0);
        HostAdapter adapter = run.combat().adapter();
        List<Vec3> muzzles = new ArrayList<>();
        for (Head head : from.heads()) {
            adapter.muzzle(mob, head).ifPresent(muzzles::add);
        }
        if (muzzles.isEmpty()) {
            muzzles.add(mob.getEyePosition()); // a host without those heads fires from its eyes
        }
        // The wall runs across the line of fire; the rightmost muzzle (as the mob faces the target) takes its right end.
        Vec3 toTarget = point.subtract(mob.position());
        float yaw = toTarget.horizontalDistanceSqr() < 1.0e-6 ? run.yaw()
                : (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        Vec3 right = AbilityRun.right(yaw);
        muzzles.sort(Comparator.comparingDouble((Vec3 muzzle) -> muzzle.subtract(mob.position()).dot(right)).reversed());
        double flight = speed.orElse(spec.speed());
        int count = muzzles.size();
        for (int k = 0; k < count; k++) {
            double offset = count == 1 ? 0.0 : spread - 2.0 * spread * k / (count - 1);
            Skulls.fire(mob, muzzles.get(k), point.add(right.scale(offset)), skull, flight, dangerous, k == 0);
        }
        run.combat().trace(String.format("%s volley wave %d/%d: %d skull(s) at %s", skull, wave + 1, waves, count,
                target != null ? target.getName().getString() : "the aim's last spot"));
    }
}
