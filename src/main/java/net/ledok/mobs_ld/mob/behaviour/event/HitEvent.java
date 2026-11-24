package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.AttackPreview;
import net.ledok.mobs_ld.mob.combat.Fighters;
import net.ledok.mobs_ld.mob.combat.MoveLead;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Area attack: telegraphed from {@link Telegraph#start}, lands at {@code at} on everyone hostile inside the shape.
 * Origin and facing are fixed when the telegraph appears, so what is drawn is exactly what hits.
 *
 * @param offset blocks to push the origin along the facing (e.g. a fist landing in front of the body)
 * @param side   blocks to push the origin to the mob's right (under a one-handed weapon)
 * @param aimSide turn so a strike line this far to the right meets the target: aimed when the telegraph appears and
 *                turned into during the last {@link AbilityRun#AIM_LEAD} seconds before the impact (so the body
 *                does not leave a beat that is still swinging); without a telegraph, turned into before the impact
 * @param height how far above the origin a victim's feet may be and still be hit; empty = the mob's reach
 * @param depth  how far below the origin; empty = the mob's reach depth
 * @param rotate degrees added to the facing the ability locked when its tracking ended (positive = to the mob's
 *               right, as yaw grows): the zone and its hit test are turned by it (the Warden's second Circle wave at
 *               22.5). Empty = the current facing, as before
 * @param turnBody when the telegraph appears, the body turns to the zone's facing over {@link #TURN_SECONDS}
 * @param lead   with {@code origin: each_fighter}: each zone goes where its player is heading, this many seconds of
 *               their movement over the last {@value MoveLead#WINDOW_SECONDS} s ahead (at most
 *               {@value MoveLead#MAX_LEAD_BLOCKS} blocks); empty = where they stand
 * @param once   a victim already struck by another {@code once} hit of this ability run is skipped (overlapping
 *               circles of one line strike each player once)
 */
public record HitEvent(double at, Shape shape, Origin origin, double offset, double side, Optional<Double> aimSide,
                       Optional<Double> height, Optional<Double> depth, Telegraph telegraph, HitSpec hit,
                       Optional<Double> rotate, boolean turnBody, Optional<Double> lead, boolean once)
        implements AbilityEvent {
    /** Default warning lead when the data does not say. */
    private static final double DEFAULT_LEAD = 0.8;
    /** How long a {@code turn_body} turn takes (seconds of the ability's clock). */
    public static final double TURN_SECONDS = 0.3;

    public static final MapCodec<HitEvent> CODEC = RecordCodecBuilder.<HitEvent>mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(HitEvent::at),
            Shape.CODEC.fieldOf("shape").forGetter(HitEvent::shape),
            Origin.CODEC.optionalFieldOf("origin", Origin.SELF).forGetter(HitEvent::origin),
            Codec.DOUBLE.optionalFieldOf("offset", 0.0).forGetter(HitEvent::offset),
            Codec.DOUBLE.optionalFieldOf("side", 0.0).forGetter(HitEvent::side),
            Codec.DOUBLE.optionalFieldOf("aim_side").forGetter(HitEvent::aimSide),
            Codec.DOUBLE.optionalFieldOf("height").forGetter(HitEvent::height),
            Codec.DOUBLE.optionalFieldOf("depth").forGetter(HitEvent::depth),
            Telegraph.CODEC.optionalFieldOf("telegraph", Telegraph.DEFAULT).forGetter(HitEvent::telegraph),
            HitSpec.CODEC.optionalFieldOf("hit", HitSpec.DEFAULT).forGetter(HitEvent::hit),
            Codec.doubleRange(-360.0, 360.0).optionalFieldOf("rotate").forGetter(HitEvent::rotate),
            Codec.BOOL.optionalFieldOf("turn_body", false).forGetter(HitEvent::turnBody),
            Codec.doubleRange(0.0, 10.0).optionalFieldOf("lead").forGetter(HitEvent::lead),
            Codec.BOOL.optionalFieldOf("once", false).forGetter(HitEvent::once)
    ).apply(i, HitEvent::new)).validate(event -> event.lead().isPresent() && event.origin() != Origin.EACH_FIGHTER
            ? DataResult.error(() -> "A hit's 'lead' only works with origin each_fighter (got " + event.origin()
            .getSerializedName() + ")")
            : DataResult.success(event));

    @Override
    public String type() {
        return "hit";
    }

    @Override
    public double begin() {
        return telegraph.show() ? telegraph.start(at, DEFAULT_LEAD) : at;
    }

    @Override
    public double end() {
        return at;
    }

    /** Only an untelegraphed hit needs the run to turn ahead of it; a telegraphed one turns itself (see start). */
    @Override
    public Optional<Double> preAim() {
        return telegraphed() ? Optional.empty() : aimSide;
    }

    @Override
    public Instance start(AbilityRun run) {
        // The zone locks here, but the body only turns into it just before the impact: an earlier beat may still be
        // swinging along the old facing. Without a telegraph the run has already been turning; keep following.
        boolean telegraphed = telegraphed();
        float aimed = aimSide.isEmpty() ? run.yaw() : telegraphed ? run.aimFor(aimSide.get()) : run.reaim(aimSide.get());
        float yaw = zoneYaw(aimed, run.lockedYaw());
        List<AbilityRun.HitZone> zones = new ArrayList<>();
        for (Vec3 base : bases(run)) {
            Vec3 point = run.groundAt(base.add(AbilityRun.forward(yaw).scale(offset)).add(AbilityRun.right(yaw).scale(side)));
            zones.add(new AbilityRun.HitZone(shape, point, yaw));
        }
        run.noteZones(this, zones);
        if (turnBody) {
            run.turnBody(yaw, TURN_SECONDS);
        }
        double up = height.orElse(run.combat().reachUp());
        double down = depth.orElse(run.combat().reachDown());
        AttackPreview.Locked preview = AttackPreview.lock(run, zones, up, down, at);
        AbilityRun.Marker marker = null;
        if (telegraphed && !zones.isEmpty()) {
            List<AbilityRun.Zone> drawn = new ArrayList<>(zones.size());
            for (AbilityRun.HitZone zone : zones) {
                drawn.add(AbilityRun.zoneOf(zone.origin(), yaw, shape, telegraph));
            }
            marker = run.telegraph(drawn, telegraph, at, up, down);
        }
        AbilityRun.Marker drawnMarker = marker;
        return new Instance() {
            private boolean turned = !telegraphed || aimSide.isEmpty();

            @Override
            public void tick(AbilityRun run) {
                if (!turned && run.seconds() >= at - AbilityRun.AIM_LEAD) {
                    turned = true;
                    run.steer(yaw);
                }
            }

            @Override
            public void finish(AbilityRun run) {
                // Each victim once per event, even where zones overlap (two fighters standing together).
                Map<UUID, Victim> victims = new LinkedHashMap<>();
                for (AbilityRun.HitZone zone : zones) {
                    for (LivingEntity victim : run.enemiesIn(shape, zone.origin(), yaw, up, down)) {
                        victims.putIfAbsent(victim.getUUID(), new Victim(victim, zone.origin()));
                    }
                }
                List<AttackPreview.Struck> struck = preview != null ? new ArrayList<>() : List.of();
                for (Victim victim : victims.values()) {
                    if (!once || run.strikeOnce(victim.entity())) {
                        LivingEntity target = victim.entity();
                        AABB before = target.getBoundingBox();
                        if (hit.tryApply(run, target, victim.from()) && preview != null) {
                            struck.add(new AttackPreview.Struck(target.getName().getString(), before));
                        }
                    }
                }
                run.flash(drawnMarker);
                if (preview != null) {
                    preview.impact(struck);
                }
            }

            @Override
            public void cancel(AbilityRun run) {
                run.clear(drawnMarker);
                if (preview != null) {
                    preview.cancel();
                }
            }
        };
    }

    /**
     * Where the zones are anchored before {@code offset} and {@code side}: the mob, its aim, or each fighting player
     * (moved ahead by {@code lead}). With nobody fighting yet (a forced test), {@code each_fighter} uses the aim.
     */
    private List<Vec3> bases(AbilityRun run) {
        return switch (origin) {
            case SELF -> List.of(run.mob().position());
            case TARGET -> List.of(run.aimPosition());
            case EACH_FIGHTER -> {
                List<Vec3> bases = new ArrayList<>();
                double seconds = lead.orElse(0.0);
                for (Player fighter : Fighters.of(run.mob())) {
                    bases.add(run.combat().predictedPosition(fighter, seconds));
                }
                if (bases.isEmpty()) {
                    LivingEntity aim = run.aim();
                    bases.add(aim != null && aim.isAlive() ? run.combat().predictedPosition(aim, seconds)
                            : run.aimPosition());
                }
                yield bases;
            }
        };
    }

    /** One victim of a hit and the zone origin it was struck from (its knockback pushes away from there). */
    private record Victim(LivingEntity entity, Vec3 from) {
    }

    /**
     * The facing the zone locks at: {@code rotate} degrees from the ability's locked facing, or (no {@code rotate})
     * {@code aimed}, the current facing or this beat's own re-aim. With {@code aim_side} as well, the rotation is
     * added to the re-aim.
     */
    public float zoneYaw(float aimed, float locked) {
        if (rotate.isEmpty()) {
            return aimed;
        }
        float from = aimSide.isPresent() ? aimed : locked;
        return Mth.wrapDegrees(from + rotate.get().floatValue());
    }

    private boolean telegraphed() {
        return telegraph.show() && begin() < at;
    }
}
