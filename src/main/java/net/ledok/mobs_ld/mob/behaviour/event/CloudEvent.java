package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Leaves a lingering area-effect cloud (like a lingering potion) on the mob or where its aim target stood. It only
 * affects players and their summons (see {@code AreaEffectCloudMixin}), never the mob's own side.
 */
public record CloudEvent(double at, Origin origin, double radius, double duration, List<HitSpec.EffectSpec> effects)
        implements AbilityEvent.Instant {
    /** Entity tag marking a cloud that must only touch players' side. */
    public static final String HOSTILE_TAG = "mobs_ld_hostile";

    public static final MapCodec<CloudEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(CloudEvent::at),
            Origin.CODEC.optionalFieldOf("origin", Origin.SELF).forGetter(CloudEvent::origin),
            Codec.DOUBLE.fieldOf("radius").forGetter(CloudEvent::radius),
            Codec.DOUBLE.fieldOf("duration").forGetter(CloudEvent::duration),
            HitSpec.EffectSpec.CODEC.listOf().fieldOf("effects").forGetter(CloudEvent::effects)
    ).apply(i, CloudEvent::new));

    @Override
    public String type() {
        return "cloud";
    }

    @Override
    public void fire(AbilityRun run) {
        Vec3 at = run.groundAt(origin == Origin.TARGET ? run.aimPosition() : run.mob().position());
        AreaEffectCloud cloud = new AreaEffectCloud(run.level(), at.x, at.y, at.z);
        cloud.setOwner(run.mob());
        cloud.setRadius((float) radius);
        cloud.setDuration((int) Math.round(duration * 20.0));
        cloud.setWaitTime(0);
        cloud.addTag(HOSTILE_TAG);
        for (HitSpec.EffectSpec effect : effects) {
            effect.instance().ifPresent(cloud::addEffect);
        }
        run.level().addFreshEntity(cloud);
    }
}
