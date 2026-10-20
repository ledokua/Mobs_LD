package net.ledok.mobs_ld.client.mob;

import com.mojang.logging.LogUtils;
import net.ledok.mobs_ld.client.ScreenShake;
import net.ledok.mobs_ld.mob.LdMob;
import net.ledok.mobs_ld.mob.MobClientHooks;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.keyframe.event.CustomInstructionKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.util.HashSet;
import java.util.Set;

/**
 * Client implementation of {@link MobClientHooks}: resolves clip names against the baked GeckoLib animations and
 * turns the effect keyframes authored in Blockbench into sounds, particles and camera shake (docs/models.md).
 */
public final class LdMobClientHooks implements MobClientHooks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double MIN_WALK_SPEED = 0.25;
    private static final double MAX_WALK_SPEED = 3.0;

    /** Bad effect ids already reported, so a looping clip does not spam the log. */
    private final Set<String> warned = new HashSet<>();

    @Override
    public String animation(LdMob mob, String clip) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        BakedAnimations baked = GeckoLibCache.getBakedAnimations().get(animationFile(id));
        if (baked == null) {
            return null;
        }
        String full = "animation." + id.getNamespace() + "." + id.getPath() + "." + clip;
        if (baked.getAnimation(full) != null) {
            return full;
        }
        return baked.getAnimation(clip) != null ? clip : null;
    }

    @Override
    public double walkSpeed(LdMob mob) {
        double blocksPerCycle = mob.declaration().render().walkBlocksPerCycle();
        String walk = mob.movementClip(this);
        if (blocksPerCycle <= 0.0 || walk == null) {
            return 1.0;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        Animation clip = GeckoLibCache.getBakedAnimations().get(animationFile(id)).getAnimation(walk);
        double cycleSeconds = clip.length() / 20.0;
        double blocksPerSecond = Math.sqrt(Mth.square(mob.getX() - mob.xo) + Mth.square(mob.getZ() - mob.zo)) * 20.0;
        // At authored speed one cycle covers blocksPerCycle in cycleSeconds; scale playback to the real speed.
        return Mth.clamp(blocksPerSecond * cycleSeconds / blocksPerCycle, MIN_WALK_SPEED, MAX_WALK_SPEED);
    }

    @Override
    public void onSound(SoundKeyframeEvent<LdMob> event) {
        LdMob mob = event.getAnimatable();
        ResourceLocation id = parseId(event.getKeyframeData().getSound(), "sound");
        if (id == null) {
            return;
        }
        // Variable-range events work for sounds.json entries that are not in the sound registry too.
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(id).orElseGet(() -> SoundEvent.createVariableRangeEvent(id));
        mob.level().playLocalSound(mob.getX(), mob.getY(), mob.getZ(), sound, mob.getSoundSource(), 1.0F, 1.0F, false);
    }

    @Override
    public void onParticle(ParticleKeyframeEvent<LdMob> event) {
        LdMob mob = event.getAnimatable();
        ResourceLocation id = parseId(event.getKeyframeData().getEffect(), "particle");
        if (id == null) {
            return;
        }
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(id);
        if (!(type instanceof ParticleOptions options)) {
            warnOnce("particle " + id, "Particle keyframe {} on {} is not a simple particle type", id, mob.getType());
            return;
        }
        String locator = event.getKeyframeData().getLocator();
        Vec3 at = locator == null || locator.isEmpty() ? null : mob.markerPosition(locator);
        if (at == null) {
            at = mob.position().add(0.0, mob.getBbHeight() * 0.5, 0.0);
        }
        ParticleScript script = ParticleScript.parse(event.getKeyframeData().script());
        for (int i = 0; i < script.count(); i++) {
            double ox = (mob.getRandom().nextDouble() * 2.0 - 1.0) * script.spread();
            double oy = (mob.getRandom().nextDouble() * 2.0 - 1.0) * script.spread();
            double oz = (mob.getRandom().nextDouble() * 2.0 - 1.0) * script.spread();
            double vx = mob.getRandom().nextGaussian() * script.speed();
            double vy = mob.getRandom().nextGaussian() * script.speed();
            double vz = mob.getRandom().nextGaussian() * script.speed();
            mob.level().addParticle(options, at.x + ox, at.y + oy, at.z + oz, vx, vy, vz);
        }
    }

    @Override
    public void onInstruction(CustomInstructionKeyframeEvent<LdMob> event) {
        LdMob mob = event.getAnimatable();
        // Blockbench ends each instruction with ';' and may put several on one keyframe.
        for (String line : event.getKeyframeData().getInstructions().split("[;\\n]")) {
            String[] args = line.trim().split("\\s+");
            if (args.length == 0 || args[0].isEmpty()) {
                continue;
            }
            if (args[0].equals("shake") && args.length == 3) {
                try {
                    ScreenShake.shakeAt(mob.getX(), mob.getY(), mob.getZ(), Float.parseFloat(args[1]), Double.parseDouble(args[2]));
                } catch (NumberFormatException e) {
                    warnOnce("instruction " + line, "Bad shake instruction '{}' on {}", line.trim(), mob.getType());
                }
            } else {
                warnOnce("instruction " + args[0], "Unknown animation instruction '{}' on {}", line.trim(), mob.getType());
            }
        }
    }

    private static ResourceLocation animationFile(ResourceLocation id) {
        return id.withPath("animations/entity/" + id.getPath() + ".animation.json");
    }

    private ResourceLocation parseId(String raw, String kind) {
        ResourceLocation id = raw == null ? null : ResourceLocation.tryParse(raw.trim());
        if (id == null) {
            warnOnce(kind + " " + raw, "Invalid {} id '{}' in an animation keyframe", kind, raw);
        }
        return id;
    }

    private void warnOnce(String key, String message, Object... args) {
        if (warned.add(key)) {
            LOGGER.warn(message, args);
        }
    }

    /**
     * Optional particle keyframe script ({@code count=12; spread=0.6; speed=0.05}): how many particles to burst,
     * how far around the anchor (blocks) and how fast. Defaults: one still particle at the anchor.
     */
    record ParticleScript(int count, double spread, double speed) {
        static final ParticleScript DEFAULT = new ParticleScript(1, 0.0, 0.0);

        static ParticleScript parse(String script) {
            if (script == null || script.isBlank()) {
                return DEFAULT;
            }
            int count = DEFAULT.count;
            double spread = DEFAULT.spread;
            double speed = DEFAULT.speed;
            for (String part : script.split("[;\\n]")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                try {
                    switch (kv[0].trim()) {
                        case "count" -> count = Mth.clamp(Integer.parseInt(kv[1].trim()), 1, 200);
                        case "spread" -> spread = Math.max(0.0, Double.parseDouble(kv[1].trim()));
                        case "speed" -> speed = Math.max(0.0, Double.parseDouble(kv[1].trim()));
                        default -> {
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // keep the default for a malformed value
                }
            }
            return new ParticleScript(count, spread, speed);
        }
    }
}
