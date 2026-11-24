package net.ledok.mobs_ld.client.vfx;

import net.ledok.mobs_ld.network.SpellVfxKind;
import net.ledok.mobs_ld.network.SpellVfxPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Live client-side state of one procedural 3D spell effect received via {@link SpellVfxPayload}.
 * Tracks smooth sub-tick age, optional early-cancel fadeout, and spawns restrained client-local particles.
 */
final class SpellVfxInstance {
    private static final float CANCEL_FADE_SECONDS = 0.15F;

    final int id;
    final int sourceEntityId;
    final SpellVfxKind kind;
    final double x;
    final double y;
    final double z;
    final float yaw;
    final int primaryColor;
    final int secondaryColor;
    final float windup;
    final float duration;
    final float radius;
    final float length;
    final float width;
    final float height;
    final float angle;
    final float tilt;
    final int count;
    final boolean reverse;
    final boolean particles;

    private int ageTicks;
    private int cancelFadeTicks = -1;
    private boolean impactBurstSpawned;

    SpellVfxInstance(SpellVfxPayload payload) {
        this.id = payload.id();
        this.sourceEntityId = payload.sourceEntityId();
        this.kind = payload.kind();
        this.x = payload.x();
        this.y = payload.y();
        this.z = payload.z();
        this.yaw = payload.yaw();
        this.primaryColor = payload.primaryColor();
        this.secondaryColor = payload.secondaryColor();
        this.windup = Math.max(0.0F, payload.windupSeconds());
        this.duration = Math.max(this.windup + 0.05F, payload.durationSeconds());
        this.radius = Math.max(0.1F, payload.radius());
        this.length = Math.max(0.1F, payload.length());
        this.width = Math.max(0.0F, payload.width());
        this.height = Math.max(0.1F, payload.height());
        this.angle = Mth.clamp(payload.angle(), 1.0F, 360.0F);
        this.tilt = payload.tilt();
        this.count = payload.countUnsigned();
        this.reverse = payload.reverse();
        this.particles = payload.particles();
    }

    /** Marks a charging effect as cancelled so it dissolves rapidly rather than playing its impact. */
    void beginCancel() {
        if (cancelFadeTicks < 0) {
            cancelFadeTicks = 0;
        }
    }

    float ageSeconds(float partialTick) {
        return (ageTicks + partialTick) / 20.0F;
    }

    /** Extra alpha multiplier (1 normally, fading 1 -> 0 over {@link #CANCEL_FADE_SECONDS} when cancelled). */
    float cancelAlpha(float partialTick) {
        if (cancelFadeTicks < 0) {
            return 1.0F;
        }
        float t = (cancelFadeTicks + partialTick) / (CANCEL_FADE_SECONDS * 20.0F);
        return Mth.clamp(1.0F - t, 0.0F, 1.0F);
    }

    boolean isCancelled() {
        return cancelFadeTicks >= 0;
    }

    /** Advances one client tick and spawns local particles; returns false when the effect should be removed. */
    boolean tick(ClientLevel level) {
        ageTicks++;
        if (cancelFadeTicks >= 0) {
            cancelFadeTicks++;
            return cancelFadeTicks < Math.ceil(CANCEL_FADE_SECONDS * 20.0F);
        }

        float elapsed = ageTicks / 20.0F;
        // If the caster dies or vanishes during windup, fade out cleanly rather than detonating an orphan spell.
        if (windup > 0.0F && elapsed < windup && sourceEntityId >= 0) {
            Entity source = level.getEntity(sourceEntityId);
            if (source == null || !source.isAlive()) {
                beginCancel();
                return true;
            }
        }

        if (particles && level != null) {
            spawnParticles(level, elapsed);
        }
        return elapsed < duration;
    }

    private void spawnParticles(ClientLevel level, float elapsed) {
        RandomSource rand = level.random;
        float yawRad = (float) Math.toRadians(yaw);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double rx = -Math.cos(yawRad);
        double rz = -Math.sin(yawRad);

        boolean inWindup = elapsed < windup;
        float activeDuration = Math.max(0.05F, duration - windup);
        float ap = inWindup ? 0.0F : Mth.clamp((elapsed - windup) / activeDuration, 0.0F, 1.0F);
        boolean justImpacted = !inWindup && !impactBurstSpawned;
        if (justImpacted) {
            impactBurstSpawned = true;
        }

        switch (kind) {
            case LANCE_LUNGE -> {
                if (inWindup) {
                    // Flowing molten embers racing down the locked corridor.
                    for (int i = 0; i < 4; i++) {
                        double d = rand.nextDouble() * length;
                        double s = (rand.nextDouble() - 0.5) * width;
                        double px = x + fx * d + rx * s;
                        double pz = z + fz * d + rz * s;
                        level.addParticle(i == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME,
                                px, y + 0.15 + rand.nextDouble() * 0.4, pz,
                                fx * 0.16, 0.03, fz * 0.16);
                    }
                } else if (ap < 0.75F) {
                    double tip = length * Math.min(1.0F, ap * 2.2F);
                    for (int i = 0; i < (justImpacted ? 14 : 6); i++) {
                        double d = Math.max(0.0, tip - rand.nextDouble() * Math.min(4.0, tip + 1.0));
                        double s = (rand.nextDouble() - 0.5) * width * 0.9;
                        double py = y + 1.4 + (rand.nextDouble() - 0.5) * 1.1;
                        level.addParticle(i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                                x + fx * d + rx * s, py, z + fz * d + rz * s,
                                fx * 0.22 + (rand.nextDouble() - 0.5) * 0.08,
                                0.04 + rand.nextDouble() * 0.08,
                                fz * 0.22 + (rand.nextDouble() - 0.5) * 0.08);
                    }
                }
            }
            case SWEEPING_BEAM -> {
                if (inWindup) {
                    for (int i = 0; i < 3; i++) {
                        double ox = (rand.nextDouble() - 0.5) * 1.4;
                        double oy = (rand.nextDouble() - 0.5) * 1.4;
                        double oz = (rand.nextDouble() - 0.5) * 1.4;
                        level.addParticle(ParticleTypes.FLAME, x + ox, y + oy, z + oz, -ox * 0.12, -oy * 0.12, -oz * 0.12);
                    }
                } else if (ap < 0.9F) {
                    float half = angle * 0.5F;
                    float deg = reverse ? Mth.lerp(ap, half, -half) : Mth.lerp(ap, -half, half);
                    double bRad = Math.toRadians(yaw + deg);
                    double bfx = -Math.sin(bRad);
                    double bfz = Math.cos(bRad);
                    // Sparks and ground scorch along the beam track.
                    for (int i = 0; i < 5; i++) {
                        double frac = 0.18 + rand.nextDouble() * 0.82;
                        double dist = length * frac;
                        double py = y - height * frac + (rand.nextDouble() - 0.5) * 0.5;
                        level.addParticle(i == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                                x + bfx * dist + (rand.nextDouble() - 0.5) * 0.4,
                                py,
                                z + bfz * dist + (rand.nextDouble() - 0.5) * 0.4,
                                (rand.nextDouble() - 0.5) * 0.08, 0.09, (rand.nextDouble() - 0.5) * 0.08);
                    }
                }
            }
            case METEOR_STRIKE -> {
                if (inWindup) {
                    float wp = Mth.clamp(elapsed / Math.max(0.05F, windup), 0.0F, 1.0F);
                    double my = y + height * (1.0 - wp * wp);
                    double mx = x + 1.8 * (1.0 - wp);
                    double mz = z - 1.8 * (1.0 - wp);
                    for (int i = 0; i < 3; i++) {
                        level.addParticle(i == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                                mx + (rand.nextDouble() - 0.5) * 0.6,
                                my + (rand.nextDouble() - 0.5) * 0.6,
                                mz + (rand.nextDouble() - 0.5) * 0.6,
                                0.0, 0.06, 0.0);
                    }
                    // Perimeter embers on the ground sigil.
                    double theta = rand.nextDouble() * Math.PI * 2.0;
                    level.addParticle(ParticleTypes.SMALL_FLAME,
                            x + Math.cos(theta) * radius, y + 0.1, z + Math.sin(theta) * radius,
                            0.0, 0.05, 0.0);
                } else if (justImpacted) {
                    for (int i = 0; i < 18; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        double spd = 0.08 + rand.nextDouble() * 0.22;
                        level.addParticle(i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                                x + Math.cos(theta) * 0.5, y + 0.35, z + Math.sin(theta) * 0.5,
                                Math.cos(theta) * spd, 0.12 + rand.nextDouble() * 0.2, Math.sin(theta) * spd);
                    }
                }
            }
            case MAGMA_FISSURES -> {
                if (inWindup) {
                    float wp = Mth.clamp(elapsed / Math.max(0.05F, windup), 0.0F, 1.0F);
                    for (int i = 0; i < Math.min(count, 6); i++) {
                        double rayRad = Math.toRadians(yaw + i * (360.0 / count));
                        double d = rand.nextDouble() * length * wp;
                        level.addParticle(ParticleTypes.SMALL_FLAME,
                                x - Math.sin(rayRad) * d, y + 0.12, z + Math.cos(rayRad) * d,
                                0.0, 0.05, 0.0);
                    }
                } else if (justImpacted || (ap < 0.65F && (ageTicks & 1) == 0)) {
                    int perRay = justImpacted ? 3 : 1;
                    for (int i = 0; i < count; i++) {
                        double rayRad = Math.toRadians(yaw + i * (360.0 / count));
                        for (int k = 0; k < perRay; k++) {
                            double d = 0.8 + rand.nextDouble() * (length - 0.8);
                            level.addParticle(k == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                                    x - Math.sin(rayRad) * d, y + 0.25 + rand.nextDouble() * height * 0.6,
                                    z + Math.cos(rayRad) * d,
                                    0.0, 0.14 + rand.nextDouble() * 0.12, 0.0);
                        }
                    }
                }
            }
            case NOVA_DOME -> {
                if (inWindup) {
                    for (int i = 0; i < 4; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        double r = radius * (0.4 + rand.nextDouble() * 0.6);
                        double px = x + Math.cos(theta) * r;
                        double pz = z + Math.sin(theta) * r;
                        level.addParticle(ParticleTypes.FLAME, px, y + 0.3 + rand.nextDouble() * 1.5, pz,
                                -Math.cos(theta) * 0.14, 0.05, -Math.sin(theta) * 0.14);
                    }
                } else if (justImpacted) {
                    for (int i = 0; i < 28; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        double elev = rand.nextDouble() * Math.PI * 0.45;
                        double spd = 0.16 + rand.nextDouble() * 0.24;
                        level.addParticle(i % 4 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                                x, y + 1.0, z,
                                Math.cos(theta) * Math.cos(elev) * spd,
                                Math.sin(elev) * spd + 0.08,
                                Math.sin(theta) * Math.cos(elev) * spd);
                    }
                }
            }
            case SHOCKWAVE_RING -> {
                if (inWindup) {
                    for (int i = 0; i < 4; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        double r = Mth.lerp(rand.nextDouble(), width, radius);
                        level.addParticle(ParticleTypes.SMALL_FLAME,
                                x + Math.cos(theta) * r, y + 0.12, z + Math.sin(theta) * r,
                                0.0, 0.04, 0.0);
                    }
                } else if (ap < 0.75F) {
                    double curR = Mth.lerp(Math.min(1.0F, ap * 2.0F), width, radius);
                    for (int i = 0; i < 8; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        level.addParticle(i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                                x + Math.cos(theta) * curR, y + 0.2 + rand.nextDouble() * height * 0.6,
                                z + Math.sin(theta) * curR,
                                Math.cos(theta) * 0.1, 0.12, Math.sin(theta) * 0.1);
                    }
                }
            }
            case FLAME_WAVE -> {
                if (!inWindup && ap < 0.85F) {
                    double dist = length * Math.min(1.0F, ap * 1.45F);
                    for (int i = 0; i < 6; i++) {
                        double deg = (rand.nextDouble() - 0.5) * angle;
                        double wRad = Math.toRadians(yaw + deg);
                        level.addParticle(i == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                                x - Math.sin(wRad) * dist, y + 0.25 + rand.nextDouble() * height * 0.65,
                                z + Math.cos(wRad) * dist,
                                -Math.sin(wRad) * 0.14, 0.09, Math.cos(wRad) * 0.14);
                    }
                }
            }
            case FLAME_PILLAR -> {
                if (!inWindup && ap < 0.85F) {
                    for (int i = 0; i < 6; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        double r = rand.nextDouble() * radius;
                        level.addParticle(i == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                                x + Math.cos(theta) * r, y + rand.nextDouble() * height * 0.85,
                                z + Math.sin(theta) * r,
                                0.0, 0.18 + rand.nextDouble() * 0.1, 0.0);
                    }
                }
            }
            case VORTEX_SLASH -> {
                if (!inWindup && ap < 0.85F) {
                    for (int i = 0; i < 6; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        double r = radius * (0.55 + rand.nextDouble() * 0.45);
                        double tx = -Math.sin(theta) * 0.22;
                        double tz = Math.cos(theta) * 0.22;
                        level.addParticle(i == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                                x + Math.cos(theta) * r, y + 0.3 + rand.nextDouble() * 1.8,
                                z + Math.sin(theta) * r,
                                reverse ? -tx : tx, 0.05, reverse ? -tz : tz);
                    }
                }
            }
            case SLASH_ARC -> {
                if (!inWindup && ap < 0.55F) {
                    float half = angle * 0.5F;
                    float sweep = Math.min(1.0F, ap * 2.6F);
                    float deg = reverse ? Mth.lerp(sweep, half, -half) : Mth.lerp(sweep, -half, half);
                    double sRad = Math.toRadians(yaw + deg);
                    for (int i = 0; i < 3; i++) {
                        double r = radius * (0.65 + rand.nextDouble() * 0.38);
                        level.addParticle(i == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                                x - Math.sin(sRad) * r, y + (rand.nextDouble() - 0.5) * 0.5,
                                z + Math.cos(sRad) * r,
                                (rand.nextDouble() - 0.5) * 0.08, 0.06, (rand.nextDouble() - 0.5) * 0.08);
                    }
                }
            }
            case EYE_CHARGE -> {
                if (ap < 0.85F) {
                    for (int i = 0; i < 3; i++) {
                        double ox = (rand.nextDouble() - 0.5) * radius * 1.8;
                        double oy = (rand.nextDouble() - 0.5) * radius * 1.8;
                        double oz = (rand.nextDouble() - 0.5) * radius * 1.8;
                        level.addParticle(i == 0 ? ParticleTypes.END_ROD : ParticleTypes.FLAME,
                                x + ox, y + oy, z + oz,
                                -ox * 0.14, -oy * 0.14, -oz * 0.14);
                    }
                }
            }
            case AMBER_SPIRE -> {
                if (inWindup && (ageTicks & 1) == 0) {
                    double theta = rand.nextDouble() * Math.PI * 2.0;
                    double r = rand.nextDouble() * radius * 0.7;
                    level.addParticle(ParticleTypes.WAX_ON,
                            x + Math.cos(theta) * r, y + 0.12, z + Math.sin(theta) * r,
                            0.0, 0.03, 0.0);
                } else if (justImpacted) {
                    for (int i = 0; i < 10; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        double spd = 0.05 + rand.nextDouble() * 0.12;
                        level.addParticle(i % 3 == 0 ? ParticleTypes.POOF : (i % 2 == 0 ? ParticleTypes.WAX_ON : ParticleTypes.CRIT),
                                x + Math.cos(theta) * 0.35, y + 0.25 + rand.nextDouble() * 0.6, z + Math.sin(theta) * 0.35,
                                Math.cos(theta) * spd, 0.10 + rand.nextDouble() * 0.14, Math.sin(theta) * spd);
                    }
                }
            }
            case AMBER_GEODE_TRAP -> {
                if (inWindup && (ageTicks & 1) == 0) {
                    double theta = rand.nextDouble() * Math.PI * 2.0;
                    level.addParticle(ParticleTypes.FALLING_HONEY,
                            x + Math.cos(theta) * radius * 0.85, y + 0.15, z + Math.sin(theta) * radius * 0.85,
                            0.0, 0.02, 0.0);
                } else if (justImpacted) {
                    for (int i = 0; i < 12; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        double r = radius * (0.35 + rand.nextDouble() * 0.5);
                        level.addParticle(i % 3 == 0 ? ParticleTypes.CRIT : ParticleTypes.WAX_ON,
                                x + Math.cos(theta) * r, y + 0.3 + rand.nextDouble() * 0.9, z + Math.sin(theta) * r,
                                -Math.cos(theta) * 0.05, 0.12, -Math.sin(theta) * 0.05);
                    }
                }
            }
            case GROUND_FRACTURE -> {
                if (justImpacted) {
                    for (int i = 0; i < 14; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        double r = radius * (0.25 + rand.nextDouble() * 0.7);
                        level.addParticle(i % 2 == 0 ? ParticleTypes.POOF : ParticleTypes.CRIT,
                                x + Math.cos(theta) * r, y + 0.15, z + Math.sin(theta) * r,
                                Math.cos(theta) * 0.08, 0.08 + rand.nextDouble() * 0.1, Math.sin(theta) * 0.08);
                    }
                }
            }
            case SHARD_SPRAY -> {
                if (!inWindup && ap < 0.65F) {
                    double dist = length * Math.min(1.0F, ap * 1.4F);
                    for (int i = 0; i < 3; i++) {
                        double deg = (rand.nextDouble() - 0.5) * angle;
                        double sRad = Math.toRadians(yaw + deg);
                        level.addParticle(i == 0 ? ParticleTypes.FALLING_HONEY : ParticleTypes.CRIT,
                                x - Math.sin(sRad) * dist, y + 0.4 + rand.nextDouble() * 1.1, z + Math.cos(sRad) * dist,
                                -Math.sin(sRad) * 0.1, 0.05, Math.cos(sRad) * 0.1);
                    }
                }
            }
            case RESONANCE_RING -> {
                if (inWindup && (ageTicks & 1) == 0) {
                    double midR = (width + radius) * 0.5;
                    for (int i = 0; i < 3; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        level.addParticle(ParticleTypes.WAX_ON,
                                x + Math.cos(theta) * midR, y + 0.12, z + Math.sin(theta) * midR,
                                0.0, 0.03, 0.0);
                    }
                } else if (justImpacted) {
                    double midR = (width + radius) * 0.5;
                    for (int i = 0; i < 16; i++) {
                        double theta = (i / 16.0) * Math.PI * 2.0 + rand.nextDouble() * 0.2;
                        level.addParticle(i % 2 == 0 ? ParticleTypes.WAX_ON : ParticleTypes.POOF,
                                x + Math.cos(theta) * midR, y + 0.25, z + Math.sin(theta) * midR,
                                Math.cos(theta) * 0.05, 0.12, Math.sin(theta) * 0.05);
                    }
                }
            }
            case CLAW_RAKE -> {
                if (justImpacted) {
                    for (int i = 0; i < 4; i++) {
                        double deg = (rand.nextDouble() - 0.5) * angle;
                        double sRad = Math.toRadians(yaw + deg);
                        level.addParticle(ParticleTypes.CRIT,
                                x - Math.sin(sRad) * radius * 0.85, y + (rand.nextDouble() - 0.5) * 0.4,
                                z + Math.cos(sRad) * radius * 0.85,
                                0.0, 0.04, 0.0);
                    }
                }
            }
            case ROOT_TENDRILS -> {
                if (inWindup && (ageTicks & 1) == 0) {
                    double theta = rand.nextDouble() * Math.PI * 2.0;
                    double r = radius * (0.4 + rand.nextDouble() * 0.55);
                    level.addParticle(ParticleTypes.SMOKE,
                            x + Math.cos(theta) * r, y + 0.1, z + Math.sin(theta) * r,
                            0.0, 0.04, 0.0);
                } else if (justImpacted) {
                    for (int i = 0; i < 12; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        double r = radius * (0.35 + rand.nextDouble() * 0.55);
                        level.addParticle(i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.POOF,
                                x + Math.cos(theta) * r, y + 0.25 + rand.nextDouble() * 0.8, z + Math.sin(theta) * r,
                                -Math.cos(theta) * 0.04, 0.09, -Math.sin(theta) * 0.04);
                    }
                }
            }
            case CINDER_ERUPTION -> {
                int rays = Math.max(1, count);
                if (inWindup) {
                    if ((ageTicks & 1) == 0) {
                        int pick = rand.nextInt(rays);
                        double rRad = Math.toRadians(yaw + pick * (360.0 / rays));
                        double rfx = -Math.sin(rRad);
                        double rfz = Math.cos(rRad);
                        double rrx = -Math.cos(rRad);
                        double rrz = -Math.sin(rRad);
                        double d = rand.nextDouble() * length * 0.85;
                        double s = (rand.nextDouble() - 0.5) * width * 0.7;
                        level.addParticle(ParticleTypes.SMOKE,
                                x + rfx * d + rrx * s, y - 1.65, z + rfz * d + rrz * s,
                                0.0, 0.04, 0.0);
                    }
                } else if (ap < 0.85F) {
                    int total = rays > 1 ? Math.min(rays * 2, 10) : 5;
                    for (int i = 0; i < total; i++) {
                        int ray = i % rays;
                        double rRad = Math.toRadians(yaw + ray * (360.0 / rays));
                        double rfx = -Math.sin(rRad);
                        double rfz = Math.cos(rRad);
                        double rrx = -Math.cos(rRad);
                        double rrz = -Math.sin(rRad);
                        double d = 0.8 + rand.nextDouble() * (length - 0.8);
                        double s = (rand.nextDouble() - 0.5) * width * 0.75;
                        level.addParticle(i == 0 ? ParticleTypes.LAVA : (i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMOKE),
                                x + rfx * d + rrx * s, y + (rand.nextDouble() - 0.5) * 0.9, z + rfz * d + rrz * s,
                                rfx * 0.18, 0.06, rfz * 0.18);
                    }
                }
            }
            case ROLL_TRAIL -> {
                if (inWindup && (ageTicks & 1) == 0) {
                    double d = rand.nextDouble() * length;
                    double s = (rand.nextBoolean() ? 0.36 : -0.36) * width;
                    level.addParticle(ParticleTypes.SMALL_FLAME,
                            x + fx * d + rx * s, y + 0.12, z + fz * d + rz * s,
                            fx * 0.06, 0.03, fz * 0.06);
                } else if (!inWindup && ap < 0.85F) {
                    double front = length * Math.min(1.0F, ap * 1.15F);
                    for (int i = 0; i < 4; i++) {
                        double d = Math.max(0.0, front - rand.nextDouble() * 3.5);
                        double s = (rand.nextDouble() - 0.5) * width * 0.9;
                        level.addParticle(i == 0 ? ParticleTypes.FLAME : ParticleTypes.POOF,
                                x + fx * d + rx * s, y + 0.25 + rand.nextDouble() * 0.7, z + fz * d + rz * s,
                                -fx * 0.05, 0.08 + rand.nextDouble() * 0.06, -fz * 0.05);
                    }
                }
            }
            case BURROW_MOUND -> {
                if (inWindup) {
                    for (int i = 0; i < 3; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        double r = rand.nextDouble() * radius * 0.65;
                        level.addParticle(i == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.POOF,
                                x + Math.cos(theta) * r, y + 0.2, z + Math.sin(theta) * r,
                                Math.cos(theta) * 0.03, 0.07, Math.sin(theta) * 0.03);
                    }
                } else if (justImpacted) {
                    for (int i = 0; i < 16; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        double spd = 0.08 + rand.nextDouble() * 0.14;
                        level.addParticle(i % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.POOF,
                                x + Math.cos(theta) * 0.5, y + 0.3, z + Math.sin(theta) * 0.5,
                                Math.cos(theta) * spd, 0.14 + rand.nextDouble() * 0.16, Math.sin(theta) * spd);
                    }
                }
            }
            case DRUM_TREMOR -> {
                if (justImpacted) {
                    for (int i = 0; i < 10; i++) {
                        double theta = (i / 10.0) * Math.PI * 2.0 + rand.nextDouble() * 0.25;
                        double r = radius * (0.35 + rand.nextDouble() * 0.55);
                        level.addParticle(i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.POOF,
                                x + Math.cos(theta) * r, y + 0.15, z + Math.sin(theta) * r,
                                Math.cos(theta) * 0.06, 0.09, Math.sin(theta) * 0.06);
                    }
                }
            }
            case RIB_FURNACE -> {
                if (inWindup && (ageTicks & 1) == 0) {
                    double theta = rand.nextDouble() * Math.PI * 2.0;
                    double r = radius * 0.75;
                    level.addParticle(ParticleTypes.SMOKE,
                            x + Math.cos(theta) * r, y + 1.2 + rand.nextDouble() * 1.4, z + Math.sin(theta) * r,
                            -Math.cos(theta) * 0.09, 0.02, -Math.sin(theta) * 0.09);
                } else if (justImpacted) {
                    for (int i = 0; i < 20; i++) {
                        double theta = rand.nextDouble() * Math.PI * 2.0;
                        double spd = 0.12 + rand.nextDouble() * 0.18;
                        level.addParticle(i % 3 == 0 ? ParticleTypes.LAVA : (i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMOKE),
                                x, y + 1.7, z,
                                Math.cos(theta) * spd, 0.06 + rand.nextDouble() * 0.12, Math.sin(theta) * spd);
                    }
                }
            }
        }
    }
}
