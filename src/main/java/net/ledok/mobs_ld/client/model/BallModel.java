// Made with Blockbench 5.1.4
// Exported for Minecraft version 1.17 or later with Mojang mappings

package net.ledok.mobs_ld.client.model;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.client.animation.BallAnimations;
import net.ledok.mobs_ld.entity.mob.Ball;
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

public class BallModel extends HierarchicalModel<Ball> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "ball"),
			"main"
	);
	private final ModelPart root;

	public BallModel(ModelPart root) {
		this.root = root;
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition core = partdefinition.addOrReplaceChild("core", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.0397F, 9.5878F, 0.2308F, 0.0F, -1.6144F, 0.0F));

		PartDefinition core_shell = core.addOrReplaceChild("core_shell", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition core_shell_r1 = core_shell.addOrReplaceChild("core_shell_r1", CubeListBuilder.create().texOffs(44, 18).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0397F, 0.0122F, -0.2308F, -0.8399F, -0.6123F, 0.5794F));

		PartDefinition core_glow = core.addOrReplaceChild("core_glow", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition core_glow_r1 = core_glow.addOrReplaceChild("core_glow_r1", CubeListBuilder.create().texOffs(60, 44).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0398F, 0.0122F, -0.2308F, -0.8399F, -0.6123F, 0.5794F));

		PartDefinition plate_hub = partdefinition.addOrReplaceChild("plate_hub", CubeListBuilder.create(), PartPose.offset(0.003F, 9.2962F, 0.127F));

		PartDefinition plate_01 = plate_hub.addOrReplaceChild("plate_01", CubeListBuilder.create(), PartPose.offset(-5.9839F, -3.1962F, -9.5204F));

		PartDefinition plate_01_r1 = plate_01.addOrReplaceChild("plate_01_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-13.2695F, -3.9462F, -6.3363F, 2.0F, 6.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0301F, 4.136F, 9.9527F, -0.3967F, -0.8548F, 0.474F));

		PartDefinition plate_02 = plate_hub.addOrReplaceChild("plate_02", CubeListBuilder.create(), PartPose.offset(-2.3349F, -9.8925F, -5.2714F));

		PartDefinition plate_02_r1 = plate_02.addOrReplaceChild("plate_02_r1", CubeListBuilder.create().texOffs(48, 0).addBox(-13.4617F, -3.9976F, -3.037F, 2.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.3811F, 10.8324F, 5.7037F, -1.3099F, -0.5128F, 1.3396F));

		PartDefinition plate_03 = plate_hub.addOrReplaceChild("plate_03", CubeListBuilder.create(), PartPose.offset(2.7039F, 0.1075F, -11.8942F));

		PartDefinition plate_03_r1 = plate_03.addOrReplaceChild("plate_03_r1", CubeListBuilder.create().texOffs(0, 54).addBox(-13.6071F, -3.9603F, -2.2608F, 2.0F, 8.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6577F, 0.8324F, 12.3264F, 3.049F, -1.289F, 2.9786F));

		PartDefinition plate_04 = plate_hub.addOrReplaceChild("plate_04", CubeListBuilder.create(), PartPose.offset(5.7679F, -6.6841F, -6.2918F));

		PartDefinition plate_04_r1 = plate_04.addOrReplaceChild("plate_04_r1", CubeListBuilder.create().texOffs(24, 18).addBox(-12.6239F, -3.6227F, -4.4372F, 2.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.7217F, 7.624F, 6.724F, -1.7393F, -0.5472F, 2.1539F));

		PartDefinition plate_05 = plate_hub.addOrReplaceChild("plate_05", CubeListBuilder.create(), PartPose.offset(5.6089F, -8.3169F, 3.9003F));

		PartDefinition plate_05_r1 = plate_05.addOrReplaceChild("plate_05_r1", CubeListBuilder.create().texOffs(0, 36).addBox(-12.3153F, -4.0977F, -4.6795F, 2.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.5627F, 9.2568F, -3.4681F, 0.1144F, 0.3666F, 2.1561F));

		PartDefinition plate_06 = plate_hub.addOrReplaceChild("plate_06", CubeListBuilder.create(), PartPose.offset(4.8642F, -2.9812F, 10.8261F));

		PartDefinition plate_06_r1 = plate_06.addOrReplaceChild("plate_06_r1", CubeListBuilder.create().texOffs(16, 54).addBox(-13.0695F, -4.4726F, -3.0242F, 2.0F, 8.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.818F, 3.9211F, -10.3938F, 0.2184F, 1.0567F, 2.3059F));

		PartDefinition plate_07 = plate_hub.addOrReplaceChild("plate_07", CubeListBuilder.create(), PartPose.offset(-3.8579F, -3.3248F, 10.6939F));

		PartDefinition plate_07_r1 = plate_07.addOrReplaceChild("plate_07_r1", CubeListBuilder.create().texOffs(0, 18).addBox(-12.7165F, -4.0361F, -6.08F, 2.0F, 6.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.9041F, 4.2646F, -10.2617F, -0.9974F, 1.0786F, 0.6208F));

		PartDefinition plate_08 = plate_hub.addOrReplaceChild("plate_08", CubeListBuilder.create(), PartPose.offset(-3.5471F, -10.0685F, 3.8783F));

		PartDefinition plate_08_r1 = plate_08.addOrReplaceChild("plate_08_r1", CubeListBuilder.create().texOffs(32, 54).addBox(-13.0682F, -3.8278F, -2.5283F, 2.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5933F, 11.0084F, -3.4461F, -0.9501F, 0.2445F, 1.272F));

		PartDefinition plate_09 = plate_hub.addOrReplaceChild("plate_09", CubeListBuilder.create(), PartPose.offset(-3.8636F, 11.9482F, 0.8183F));

		PartDefinition plate_09_r1 = plate_09.addOrReplaceChild("plate_09_r1", CubeListBuilder.create().texOffs(24, 0).addBox(-12.6751F, -4.0354F, -5.1563F, 2.0F, 6.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.9098F, -11.0083F, -0.3861F, -3.048F, 0.0154F, -1.1851F));

		PartDefinition plate_10 = plate_hub.addOrReplaceChild("plate_10", CubeListBuilder.create(), PartPose.offset(4.782F, 11.843F, 2.0236F));

		PartDefinition plate_10_r1 = plate_10.addOrReplaceChild("plate_10_r1", CubeListBuilder.create().texOffs(48, 54).addBox(-12.8903F, -4.8488F, -2.199F, 2.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.7358F, -10.9032F, -1.5914F, 2.8051F, 0.2343F, -1.8956F));

		PartDefinition plate_11 = plate_hub.addOrReplaceChild("plate_11", CubeListBuilder.create(), PartPose.offset(4.2147F, 6.1871F, 8.7079F));

		PartDefinition plate_11_r1 = plate_11.addOrReplaceChild("plate_11_r1", CubeListBuilder.create().texOffs(20, 36).addBox(-11.6475F, -4.4739F, -4.1626F, 2.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.1685F, -5.2472F, -8.2756F, 2.6164F, 0.8757F, -2.2333F));

		PartDefinition plate_12 = plate_hub.addOrReplaceChild("plate_12", CubeListBuilder.create(), PartPose.offset(-5.0928F, 6.7053F, 8.7573F));

		PartDefinition plate_12_r1 = plate_12.addOrReplaceChild("plate_12_r1", CubeListBuilder.create().texOffs(60, 30).addBox(-12.3046F, -3.9538F, -2.0225F, 2.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.139F, -5.7654F, -8.325F, 3.0306F, 0.9135F, -0.7984F));

		PartDefinition plate_13 = plate_hub.addOrReplaceChild("plate_13", CubeListBuilder.create(), PartPose.offset(-6.0136F, 5.1941F, -8.9734F));

		PartDefinition plate_13_r1 = plate_13.addOrReplaceChild("plate_13_r1", CubeListBuilder.create().texOffs(64, 0).addBox(-12.8331F, -4.5331F, -1.5176F, 2.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0598F, -4.2543F, 9.4057F, 2.0424F, -0.7733F, -0.7273F));

		PartDefinition plate_14 = plate_hub.addOrReplaceChild("plate_14", CubeListBuilder.create(), PartPose.offset(2.7522F, 8.079F, -7.6547F));

		PartDefinition plate_14_r1 = plate_14.addOrReplaceChild("plate_14_r1", CubeListBuilder.create().texOffs(40, 36).addBox(-12.1F, -4.0892F, -3.4435F, 2.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.706F, -7.1391F, 8.087F, -2.5671F, -0.752F, -1.9387F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void setupAnim(Ball entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.root().getAllParts().forEach(ModelPart::resetPose);

		// The plate_burst clip owns the whole pose while it plays (it pulls the plates in, then flings them out).
		if (entity.burstAnimationState.isStarted()) {
			this.animate(entity.burstAnimationState, BallAnimations.PLATE_BURST, ageInTicks);
			return;
		}

		// Idle and roll both drive the core/plate_hub, so only one runs at a time.
		if (limbSwingAmount > 0.01F) {
			this.animateWalk(BallAnimations.ROLL, limbSwing, limbSwingAmount, 2.0F, 2.5F);
		} else {
			this.animate(entity.idleAnimationState, BallAnimations.IDLE, ageInTicks);
		}
	}

	@Override
	public ModelPart root() {
		return this.root;
	}
}
