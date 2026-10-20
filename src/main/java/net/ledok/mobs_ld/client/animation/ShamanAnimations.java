package net.ledok.mobs_ld.client.animation;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Made with Blockbench 5.1.4
 * Exported for Minecraft version 1.19 or later with Mojang mappings
 */
public final class ShamanAnimations {
	private ShamanAnimations() {
	}

	public static final AnimationDefinition ANIMATION = AnimationDefinition.Builder.withLength(100.0F).looping()
		.addAnimation("boss", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(19.2917F, KeyframeAnimations.degreeVec(-0.1222F, 0.0F, 1.0869F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("boss", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(19.2917F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("око", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 8.7848F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("око", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(-0.2997F, -0.0138F, -0.0138F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("маг_круг", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 176.8129F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("R_верх", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-0.3667F, 0.0F, 2.1739F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("R_верх", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("R_низ", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-0.3667F, 0.0F, 2.1739F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("R_низ", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("праве_крило", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-0.2445F, 0.0F, 2.1739F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("праве_крило", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone12", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-1.2224F, -16.304F, -5.4347F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone12", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone15", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-1.2224F, -16.304F, -5.4347F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone15", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("R_рука", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-0.1222F, 0.0F, 1.0869F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("R_рука", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone43", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.5F, KeyframeAnimations.degreeVec(-0.1222F, 0.0F, 1.0869F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone43", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone3", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.5F, KeyframeAnimations.degreeVec(-0.3667F, 0.0F, 2.1739F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone3", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone22", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.5F, KeyframeAnimations.degreeVec(-0.1222F, 0.0F, 1.0869F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone22", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("L_верх", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-0.3667F, 0.0F, 2.1739F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("L_верх", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("L_низ", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-0.3667F, 0.0F, 2.1739F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("L_низ", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("ліве_крило", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.2445F, 0.0F, 2.1739F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("ліве_крило", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone7", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-1.2224F, 16.304F, 5.4347F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone7", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone18", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-1.2224F, 16.304F, 5.4347F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone18", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("L_рука", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(15.0F, KeyframeAnimations.degreeVec(-0.1222F, 0.0F, 1.0869F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("L_рука", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(15.0F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone46", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(5.75F, KeyframeAnimations.degreeVec(-0.1222F, 0.0F, 1.0869F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone46", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(5.75F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone50", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(24.8333F, KeyframeAnimations.degreeVec(-0.3667F, 0.0F, 2.1739F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone50", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(24.8333F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone21", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(12.0417F, KeyframeAnimations.degreeVec(-0.3667F, 0.0F, 2.1739F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("bone21", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(12.0417F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("L_книга", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.3333F, KeyframeAnimations.degreeVec(-0.3667F, 0.0F, 2.1739F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("L_книга", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.3333F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("R_книга", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(5.7917F, KeyframeAnimations.degreeVec(-0.3667F, 0.0F, 2.1739F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("R_книга", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(5.7917F, KeyframeAnimations.posVec(0.0F, 0.9989F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.build();
}
