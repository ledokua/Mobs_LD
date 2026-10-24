package net.ledok.mobs_ld.mob.combat;

import net.ledok.mobs_ld.mixin.HurtingProjectileAccessor;
import net.ledok.mobs_ld.mob.behaviour.SkullSpec;
import net.ledok.mobs_ld.mob.behaviour.event.HitSpec;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mobs_LD's skulls: vanilla {@link WitherSkull} entities (vanilla look, sound and client code) that carry the tag
 * {@code mobs_ld_skull:<spec>} and are owned by the mob that fired them. When one lands, its spec is looked up again
 * in the owner's current host options (see {@link SkullSpec}) and WitherSkullMixin deals that instead of vanilla's
 * hit: amount x R, the spec's effects, a splash in place of the block-breaking blast, no heal for the owner, and it
 * flies through the owner's adds and undead allies.
 */
public final class Skulls {
    public static final String TAG_PREFIX = "mobs_ld_skull:";
    /** The Wither's shoot sound, as a level event (what vanilla plays per skull). */
    private static final int SHOOT_SOUND = 1024;
    /** Splash is measured this far back along the flight from the impact, so the point is in open air. */
    private static final double SPLASH_BACKOFF = 0.25;

    private Skulls() {
    }

    /** A skull of a hosted mob, resolved when it lands: who fired it and the spec it deals now. */
    public record Shot(Mob owner, MobCombat combat, String name, SkullSpec spec) {
    }

    /**
     * Fires one skull of spec {@code spec} from {@code from} toward {@code toward} at a constant {@code speed} (blocks
     * per tick), inside the owner's tick. {@code sound}: play the shoot sound (once per volley is enough).
     */
    public static WitherSkull fire(Mob owner, Vec3 from, Vec3 toward, String spec, double speed, boolean dangerous,
                                   boolean sound) {
        ServerLevel level = (ServerLevel) owner.level();
        Vec3 direction = toward.subtract(from);
        direction = direction.lengthSqr() < 1.0e-6 ? AbilityRun.forward(owner.getYRot()) : direction.normalize();
        WitherSkull skull = new WitherSkull(level, owner, direction);
        skull.setPos(from.x, from.y, from.z);
        skull.setDangerous(dangerous);
        // Launched at its cruising speed with just the acceleration that holds it there against its drag: constant
        // speed, so flight time = distance / speed (vanilla's skulls start slow and speed up).
        double inertia = ((HurtingProjectileAccessor) skull).mobs_ld$inertia();
        skull.accelerationPower = ProjectileFlight.accelerationPower(speed, inertia);
        skull.setDeltaMovement(direction.scale(speed));
        skull.addTag(TAG_PREFIX + spec);
        if (sound && !owner.isSilent()) {
            level.levelEvent(null, SHOOT_SOUND, owner.blockPosition(), 0);
        }
        level.addFreshEntity(skull);
        return skull;
    }

    /** Where skulls aim on a target: the middle of its body, as vanilla's heads do (no lead). */
    public static Vec3 aimPoint(LivingEntity target) {
        return new Vec3(target.getX(), target.getY() + target.getEyeHeight() * 0.5, target.getZ());
    }

    /** Whether a shot from {@code from} reaches {@code target}'s eyes or the middle of its body without a block in between. */
    public static boolean clearShot(Mob shooter, Vec3 from, LivingEntity target) {
        return clear(shooter, from, target.getEyePosition()) || clear(shooter, from, aimPoint(target));
    }

    private static boolean clear(Mob shooter, Vec3 from, Vec3 to) {
        return shooter.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter))
                .getType() == HitResult.Type.MISS;
    }

    /** The spec name a skull was tagged with, or null (a vanilla skull). */
    public static String specName(Entity skull) {
        for (String tag : skull.getTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                return tag.substring(TAG_PREFIX.length());
            }
        }
        return null;
    }

    /**
     * The shot behind a skull tagged {@code spec}, fired by {@code owner}: null unless the owner is still a hosted mob
     * whose current host options have that spec (else the skull behaves as vanilla, minus block damage).
     */
    public static Shot resolve(String spec, Entity owner) {
        if (!(owner instanceof Mob mob) || owner.isRemoved()) {
            return null;
        }
        MobCombat combat = CombatHost.of(mob);
        if (combat == null || combat.behaviour() == null) {
            return null;
        }
        SkullSpec found = combat.skullSpec(spec);
        return found == null ? null : new Shot(mob, combat, spec, found);
    }

    /** Skulls fly through their owner, its adds and its undead allies (non-player {@code #wither_friends}). */
    public static boolean passesThrough(Shot shot, Entity target) {
        return target == shot.owner() || shot.combat().ownsSummon(target)
                || (!(target instanceof Player) && target.getType().is(EntityTypeTags.WITHER_FRIENDS));
    }

    /** The direct hit landed (WitherSkullMixin): trace it for {@code /mobs_ld debug}. */
    public static void traceHit(Shot shot, Entity victim, float amount, boolean landed) {
        shot.combat().trace(String.format("%s skull %s %s for %.1f", shot.name(), landed ? "hit" : "was blocked by",
                victim.getName().getString(), amount));
    }

    /**
     * In place of the vanilla blast: the spec's splash to every enemy around the impact but the one hit directly,
     * with the source vanilla's skull blast deals (an explosion by the skull, caused by its owner), then vanilla's
     * small explosion puff and sound. No blocks are touched and nobody is pushed.
     */
    public static void splash(Projectile skull, Shot shot, HitResult result) {
        ServerLevel level = (ServerLevel) skull.level();
        Vec3 center = result.getLocation();
        Vec3 motion = skull.getDeltaMovement();
        if (motion.lengthSqr() > 1.0e-6) {
            center = center.subtract(motion.normalize().scale(SPLASH_BACKOFF));
        }
        Entity direct = result instanceof EntityHitResult hit ? hit.getEntity() : null;
        Vec3 point = center;
        shot.spec().splash().ifPresent(splash -> {
            float amount = (float) (splash.amount() * HitSpec.damageScale(shot.owner()));
            if (amount <= 0.0F || splash.radius() <= 0.0) {
                return;
            }
            DamageSource source = Explosion.getDefaultDamageSource(level, skull);
            AABB box = new AABB(point, point).inflate(splash.radius());
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box,
                    candidate -> candidate != direct && CombatTargets.isEnemy(shot.owner(), candidate))) {
                if (distanceToBox(point, victim.getBoundingBox()) <= splash.radius()
                        && Explosion.getSeenPercent(point, victim) > 0.0F) {
                    boolean landed = victim.hurt(source, amount);
                    shot.combat().trace(String.format("%s skull splash %s %s for %.1f", shot.name(),
                            landed ? "hit" : "was blocked by", victim.getName().getString(), amount));
                }
            }
        });
        RandomSource random = level.getRandom();
        level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F,
                (1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F) * 0.7F);
    }

    private static double distanceToBox(Vec3 point, AABB box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
