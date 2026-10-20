// Made with Blockbench 5.1.4
// Exported for Minecraft version 1.17 or later with Mojang mappings

package net.ledok.mobs_ld.client.model;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.client.animation.MummyAnimations;
import net.ledok.mobs_ld.entity.mob.Mummy;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

public class MummyModel extends HierarchicalModel<Mummy> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "mummy"),
			"main"
	);
	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart head;

	public MummyModel(ModelPart root) {
		this.root = root;
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-3.7842F, -11.9363F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 12.0F, 0.0F));

		PartDefinition bandage_waist = body.addOrReplaceChild("bandage_waist", CubeListBuilder.create().texOffs(44, 0).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 9.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.2158F, 0.0637F, 2.5F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.2158F, -11.9363F, 0.0F));

		PartDefinition arm_right = body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(40, 16).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(5.2158F, -9.9363F, 0.0F));

		PartDefinition bandage_arm_right = arm_right.addOrReplaceChild("bandage_arm_right", CubeListBuilder.create(), PartPose.offset(2.0F, 8.0F, -1.5F));

		PartDefinition bandage_arm_right_r1 = bandage_arm_right.addOrReplaceChild("bandage_arm_right_r1", CubeListBuilder.create().texOffs(44, 0).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 8.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -0.1719F, -0.0075F, 1.5708F, 0.0F, -1.5272F));

		PartDefinition arm_left = body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(32, 48).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.7842F, -9.9363F, 0.0F));

		PartDefinition bandage_arm_left = arm_left.addOrReplaceChild("bandage_arm_left", CubeListBuilder.create(), PartPose.offset(-2.05F, 10.0F, -1.5F));

		PartDefinition bandage_arm_left_r1 = bandage_arm_left.addOrReplaceChild("bandage_arm_left_r1", CubeListBuilder.create().texOffs(44, 0).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 8.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 1.5708F, 0.0F, 0.0F));

		PartDefinition leg_right = partdefinition.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, 12.0F, 0.0F));

		PartDefinition leg_left = partdefinition.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, 12.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void setupAnim(Mummy entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.root().getAllParts().forEach(ModelPart::resetPose);
		this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
		this.head.xRot = headPitch * ((float) Math.PI / 180F);

		// While the swing is playing, let it own the whole pose. Layering walk underneath it adds the walk's
		// vertical body bob on top of the attack, which makes the mummy bounce up and down mid-swing.
		if (entity.attackAnimationState.isStarted()) {
			this.animate(entity.attackAnimationState, MummyAnimations.ATTACK_MELEE, ageInTicks);
			return;
		}

		// Idle and walk drive the same bones (arms held up), so only play one at a time.
		if (limbSwingAmount > 0.01F) {
			this.animateWalk(MummyAnimations.WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);
		} else {
			this.animate(entity.idleAnimationState, MummyAnimations.IDLE, ageInTicks);
		}
	}

	@Override
	public ModelPart root() {
		return this.root;
	}
}
