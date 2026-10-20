package net.ledok.mobs_ld.mob;

import software.bernie.geckolib.animation.keyframe.event.CustomInstructionKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent;

/**
 * Client-side services the common {@link LdMob} needs while animating. Animation controllers only run on the client,
 * but they are declared in common code; routing through this interface keeps client classes out of {@link LdMob} so
 * a dedicated server never loads them. The client entrypoint installs the real implementation.
 */
public interface MobClientHooks {
    MobClientHooks NONE = new MobClientHooks() {
    };

    /**
     * The exact animation name to play for {@code clip} on this mob ({@code animation.<ns>.<mob>.<clip>} per the
     * model contract, or the bare clip name), or null if the mob's animation file has no such clip — or no file at
     * all, which GeckoLib would otherwise crash on.
     */
    default String animation(LdMob mob, String clip) {
        return null;
    }

    /** Playback speed for the walk loop, matched to how fast the mob is actually moving. */
    default double walkSpeed(LdMob mob) {
        return 1.0;
    }

    default void onSound(SoundKeyframeEvent<LdMob> event) {
    }

    default void onParticle(ParticleKeyframeEvent<LdMob> event) {
    }

    default void onInstruction(CustomInstructionKeyframeEvent<LdMob> event) {
    }

    final class Holder {
        static MobClientHooks hooks = NONE;

        private Holder() {
        }
    }

    static MobClientHooks get() {
        return Holder.hooks;
    }

    static void install(MobClientHooks hooks) {
        Holder.hooks = hooks;
    }
}
