package net.ledok.mobs_ld.mob.behaviour.event;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.mobs_ld.mixin.HurtingProjectileAccessor;
import net.ledok.mobs_ld.mob.behaviour.Shape;
import net.ledok.mobs_ld.mob.combat.AbilityRun;
import net.ledok.mobs_ld.mob.combat.ProjectileFlight;
import net.ledok.mobs_ld.mob.combat.Skulls;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fires a real projectile (any mod's: an arrow, a blaze's small fireball, a ghast's fireball) along a line drawn on
 * the ground first. When the telegraph appears the line locks: from the mob's eyes to the middle of the aim's body as
 * it stands then, drawn on the floor as a {@code width}-wide strip with an arrowhead, {@code distance + overshoot}
 * long (at most {@code range}), cut where the shot's path would first meet a block. At {@code at} the shot flies down exactly that line at a constant {@code speed}
 * (arrows without gravity unless {@code gravity}), so a player who steps off the line dodges it; {@code shots} shots
 * go {@code interval} seconds apart. A {@code fan} gives each shot several lanes released together.
 * The projectile does its own damage (an arrow's
 * {@code damage} sets its base damage), with the mob as its owner, so damage_dealt, attack profiles and threat count
 * it as the mob's.
 */
public record ShootEvent(double at, ResourceLocation projectile, double speed, int shots, double interval,
                         double width, double range, double overshoot, Optional<Double> damage, boolean gravity,
                         Telegraph telegraph, Optional<Fan> fan, List<HitSpec.EffectSpec> effects) implements AbilityEvent {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> WARNED = new HashSet<>();
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("arrow");
    private static final ResourceLocation SPECTRAL_ARROW = ResourceLocation.withDefaultNamespace("spectral_arrow");
    private static final double DEFAULT_LEAD = 0.8;
    private static final Telegraph DEFAULT_TELEGRAPH = new Telegraph(true, Optional.empty(), Telegraph.DEFAULT_COLOR,
            Telegraph.Style.SWEEP, true);

    public static final MapCodec<ShootEvent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("at").forGetter(ShootEvent::at),
            ResourceLocation.CODEC.fieldOf("projectile").forGetter(ShootEvent::projectile),
            Codec.doubleRange(0.05, 5.0).optionalFieldOf("speed", 1.6).forGetter(ShootEvent::speed),
            Codec.intRange(1, 16).optionalFieldOf("shots", 1).forGetter(ShootEvent::shots),
            Codec.doubleRange(0.0, 10.0).optionalFieldOf("interval", 0.3).forGetter(ShootEvent::interval),
            Codec.doubleRange(0.1, 8.0).optionalFieldOf("width", 0.8).forGetter(ShootEvent::width),
            Codec.doubleRange(1.0, 64.0).optionalFieldOf("range", 24.0).forGetter(ShootEvent::range),
            Codec.doubleRange(0.0, 32.0).optionalFieldOf("overshoot", 2.0).forGetter(ShootEvent::overshoot),
            Codec.doubleRange(0.0, 1000.0).optionalFieldOf("damage").forGetter(ShootEvent::damage),
            Codec.BOOL.optionalFieldOf("gravity", false).forGetter(ShootEvent::gravity),
            Telegraph.CODEC.optionalFieldOf("telegraph", DEFAULT_TELEGRAPH).forGetter(ShootEvent::telegraph),
            Fan.CODEC.optionalFieldOf("fan").forGetter(ShootEvent::fan),
            HitSpec.EffectSpec.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(ShootEvent::effects)
    ).apply(i, ShootEvent::new));

    /** Equally spaced horizontal headings over the total arc, using the original shot's pitch. */
    public record Fan(int count, double angle) {
        public static final Codec<Fan> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(2, 9).fieldOf("count").forGetter(Fan::count),
                Codec.doubleRange(0.1, 120.0).fieldOf("angle").forGetter(Fan::angle)
        ).apply(i, Fan::new));

        List<Lane> lanes(Vec3 direction, float yaw) {
            List<Lane> lanes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                float offset = (float) (angle * ((double) i / (count - 1) - 0.5));
                lanes.add(new Lane(direction.yRot((float) -Math.toRadians(offset)), yaw + offset));
            }
            return lanes;
        }
    }

    record Lane(Vec3 direction, float yaw) {
    }

    @Override
    public String type() {
        return "shoot";
    }

    @Override
    public double begin() {
        return telegraph.show() ? telegraph.start(at, DEFAULT_LEAD) : at;
    }

    @Override
    public double end() {
        return at + (shots - 1) * interval;
    }

    @Override
    public Instance start(AbilityRun run) {
        Mob mob = run.mob();
        if ((mob.getType() == EntityType.SKELETON || mob.getType() == EntityType.BOGGED)
                && (projectile.equals(ARROW) || projectile.equals(SPECTRAL_ARROW))
                && mob.getMainHandItem().isEmpty() && !mob.getOffhandItem().is(Items.BOW)) {
            // Arena spawners can construct an archer without running its usual equipment setup.
            mob.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
            mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        Vec3 from = mob.getEyePosition();
        LivingEntity target = run.aim();
        Vec3 point = target != null && target.isAlive() ? Skulls.aimPoint(target) : run.aimPosition().add(0.0, 1.0, 0.0);
        Vec3 toward = point.subtract(from);
        Vec3 direction = toward.lengthSqr() < 1.0e-6 ? AbilityRun.forward(run.yaw()) : toward.normalize();
        float yaw = toward.horizontalDistanceSqr() < 1.0e-6 ? run.yaw()
                : (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
        run.steer(yaw);
        List<Lane> lanes = fan.map(pattern -> pattern.lanes(direction, yaw))
                .orElseGet(() -> List.of(new Lane(direction, yaw)));
        AbilityRun.Marker marker = null;
        if (telegraph.show()) {
            Vec3 ground = run.groundAt(mob.position());
            List<AbilityRun.Zone> zones = new ArrayList<>(lanes.size());
            for (Lane lane : lanes) {
                double length = laneLength(mob, from, toward, lane.direction());
                Shape shape = new Shape.Rectangle(width, length, 0.0);
                zones.add(AbilityRun.zoneOf(ground, lane.yaw(), shape, telegraph));
            }
            marker = run.telegraph(zones, telegraph, at);
        }
        AbilityRun.Marker drawn = marker;
        return new Instance() {
            private int fired;
            private boolean drawingBow;

            @Override
            public void tick(AbilityRun run) {
                if (fired < shots && run.seconds() < at + fired * interval) {
                    // The vanilla bow goal normally syncs these two flags. The scripted shot owns the windup here.
                    if (!drawingBow && mob instanceof AbstractSkeleton && mob.getMainHandItem().is(Items.BOW)
                            && !mob.isUsingItem()) {
                        mob.startUsingItem(InteractionHand.MAIN_HAND);
                        mob.setAggressive(true);
                        drawingBow = true;
                    }
                    if (drawingBow) {
                        mob.getLookControl().setLookAt(point);
                    }
                }
                while (fired < shots && run.seconds() >= at + fired * interval) {
                    if (drawingBow) {
                        lowerBow();
                        mob.swing(InteractionHand.MAIN_HAND);
                    }
                    fire(run, from, lanes, fired++);
                    if (fired == 1) {
                        run.flash(drawn);
                    }
                }
            }

            @Override
            public void finish(AbilityRun run) {
                tick(run);
            }

            @Override
            public void cancel(AbilityRun run) {
                lowerBow();
                run.clear(drawn);
            }

            private void lowerBow() {
                if (drawingBow) {
                    mob.stopUsingItem();
                    mob.setAggressive(false);
                    drawingBow = false;
                }
            }
        };
    }

    /**
     * How far along the ground the line is drawn: to the aim plus {@code overshoot}, at most {@code range}, and no
     * further than where the shot's own path (a straight ray from the mob's eyes, the way the projectile flies) first
     * meets a block. A low wall under the path does not cut the line; a taller wall, a ceiling, or the floor where a
     * downward shot comes down does.
     */
    private double laneLength(Mob mob, Vec3 from, Vec3 toward, Vec3 direction) {
        double reach = Math.min(range, Math.sqrt(toward.horizontalDistanceSqr()) + overshoot);
        double level = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (level > 1.0e-3) {
            Vec3 end = from.add(direction.scale(reach / level));
            BlockHitResult blocked = mob.level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, mob));
            if (blocked.getType() != HitResult.Type.MISS) {
                Vec3 stop = blocked.getLocation();
                reach = Math.min(reach, Math.hypot(stop.x - mob.getX(), stop.z - mob.getZ()));
            }
        }
        return Math.max(1.0, reach);
    }

    /** One shot or fan from the locked muzzle, all in the same mob tick. */
    private void fire(AbilityRun run, Vec3 from, List<Lane> lanes, int shot) {
        Mob mob = run.mob();
        ServerLevel level = run.level();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(projectile).orElse(null);
        boolean first = true;
        for (Lane lane : lanes) {
            Entity entity = type == null ? null : type.create(level);
            if (!(entity instanceof Projectile shotEntity)) {
                if (entity != null) {
                    entity.discard();
                }
                synchronized (WARNED) {
                    if (WARNED.add(projectile)) {
                        LOGGER.warn("shoot event: {} is not a projectile entity; nothing is fired", projectile);
                    }
                }
                return;
            }
            launch(mob, from, lane.direction(), shotEntity);
            if (first) {
                playShotSound(mob, level, shotEntity);
                first = false;
            }
            level.addFreshEntity(shotEntity);
        }
        run.combat().trace(String.format("%s: shot %d/%d of %s (%d lanes)", run.name(), shot + 1, shots, projectile, lanes.size()));
    }

    private void launch(Mob mob, Vec3 from, Vec3 direction, Projectile shotEntity) {
        shotEntity.setOwner(mob);
        shotEntity.moveTo(from.x, from.y, from.z, mob.getYRot(), mob.getXRot());
        shotEntity.shoot(direction.x, direction.y, direction.z, (float) speed, 0.0F);
        if (shotEntity instanceof AbstractArrow arrow) {
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setNoGravity(!gravity);
            damage.ifPresent(arrow::setBaseDamage);
            if (arrow instanceof Arrow tipped) {
                for (HitSpec.EffectSpec effect : effects) {
                    effect.instance().ifPresent(tipped::addEffect);
                }
            }
        } else if (shotEntity instanceof AbstractHurtingProjectile hurting) {
            // Held at its launch speed against its drag, so it flies the drawn line at a constant pace.
            double inertia = ((HurtingProjectileAccessor) hurting).mobs_ld$inertia();
            hurting.accelerationPower = ProjectileFlight.accelerationPower(speed, inertia);
            hurting.setDeltaMovement(direction.scale(speed));
        }
    }

    private static void playShotSound(Mob mob, ServerLevel level, Projectile projectile) {
        if (projectile instanceof AbstractArrow) {
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE,
                    1.0F, 1.0F / (mob.getRandom().nextFloat() * 0.4F + 0.8F));
        } else if (projectile instanceof AbstractHurtingProjectile) {
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE,
                    1.0F, 1.0F);
        }
    }
}
