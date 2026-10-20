// Made with Blockbench 5.1.4
// Exported for Minecraft version 1.17 or later with Mojang mappings

package net.ledok.mobs_ld.client.model;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.client.animation.SandGolemGuardianAnimations;
import net.ledok.mobs_ld.entity.boss.SandGolemGuardian;
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

public class SandGolemGuardianModel extends HierarchicalModel<SandGolemGuardian> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "sand_golem_guardian"),
			"main"
	);
	private final ModelPart root;
	private final ModelPart head;

	public SandGolemGuardianModel(ModelPart root) {
		this.root = root.getChild("root");
		ModelPart body = this.root.getChild("body");
		this.head = body.getChild("head");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-17.0F, -34.0F, -11.0F, 34.0F, 18.0F, 20.0F, new CubeDeformation(0.0F))
		.texOffs(108, 0).addBox(-14.0F, -18.0F, -7.0F, 28.0F, 12.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(220, 14).addBox(-3.0F, -18.0F, 7.0F, 6.0F, 10.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(0, 38).addBox(-15.0F, -8.0F, -8.0F, 30.0F, 10.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(210, 38).addBox(-5.0F, -6.0F, -10.0F, 10.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(148, 54).addBox(-10.0F, -36.0F, -3.0F, 20.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(200, 98).addBox(-12.0F, -32.0F, 9.0F, 24.0F, 12.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -28.0F, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(196, 0).addBox(-6.0F, 0.0F, -10.0F, 12.0F, 4.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(196, 14).addBox(-3.0F, -25.0F, -7.0F, 6.0F, 8.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(92, 38).addBox(-8.0F, -14.0F, -10.0F, 16.0F, 14.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(148, 38).addBox(-9.0F, -17.0F, -11.0F, 18.0F, 3.0F, 13.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -34.0F, -4.0F));

		PartDefinition arm_right = body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(0, 66).addBox(-8.0F, -6.0F, -8.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(128, 66).addBox(-8.0F, 34.0F, -8.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(0, 98).addBox(-6.0F, 8.0F, -6.0F, 12.0F, 26.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(200, 112).addBox(-5.0F, -14.0F, -5.0F, 10.0F, 8.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(-22.0F, -30.0F, -4.0F));

		PartDefinition arm_left = body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(64, 66).addBox(-8.0F, -6.0F, -8.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(192, 66).addBox(-8.0F, 34.0F, -8.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(48, 98).addBox(-6.0F, 8.0F, -6.0F, 12.0F, 26.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(200, 130).addBox(-5.0F, -14.0F, -5.0F, 10.0F, 8.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(22.0F, -30.0F, -4.0F));

		PartDefinition leg_right = root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(96, 98).addBox(-6.0F, 0.0F, -7.0F, 12.0F, 20.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(0, 136).addBox(-7.0F, 20.0F, -9.0F, 14.0F, 8.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(-10.0F, -28.0F, 0.0F));

		PartDefinition leg_left = root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(148, 98).addBox(-6.0F, 0.0F, -7.0F, 12.0F, 20.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(60, 136).addBox(-7.0F, 20.0F, -9.0F, 14.0F, 8.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(10.0F, -28.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 256, 256);
	}

	@Override
	public void setupAnim(SandGolemGuardian entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.root().getAllParts().forEach(ModelPart::resetPose);
		this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
		this.head.xRot = headPitch * ((float) Math.PI / 180F);

		this.animateWalk(SandGolemGuardianAnimations.WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);
		this.animate(entity.idleAnimationState, SandGolemGuardianAnimations.IDLE, ageInTicks);
		this.animate(entity.attackAnimationState, SandGolemGuardianAnimations.ATTACK_POUND, ageInTicks);
	}

	@Override
	public ModelPart root() {
		return this.root;
	}
}
