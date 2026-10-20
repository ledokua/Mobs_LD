// Made with Blockbench 5.1.4
// Exported for Minecraft version 1.17+ for Yarn
// Paste this class into your mod and generate all required imports

package net.ledok.mobs_ld.client.model;

import net.ledok.mobs_ld.MobsLdMod;
import net.ledok.mobs_ld.entity.mob.CurseShaman;
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
import net.minecraft.util.Mth;

public class ShamanCustomModel extends HierarchicalModel<CurseShaman> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			ResourceLocation.fromNamespaceAndPath(MobsLdMod.MOD_ID, "shaman_custom_model"),
			"main"
	);

	private final ModelPart boss;
	private final ModelPart hitbox;
	private final ModelPart око;
	private final ModelPart маг_круг;
	private final ModelPart праве_крило;
	private final ModelPart ліве_крило;
	private final ModelPart L_книга;
	private final ModelPart R_книга;

	public ShamanCustomModel(ModelPart root) {
		this.boss = root.getChild("boss");
		this.hitbox = this.boss.getChild("hitbox");
		this.hitbox.visible = false;
		this.око = this.boss.getChild("око");
		this.маг_круг = this.boss.getChild("маг_круг");
		this.праве_крило = this.boss.getChild("R_").getChild("праве_крило");
		this.ліве_крило = this.boss.getChild("L_").getChild("ліве_крило");
		this.L_книга = this.boss.getChild("L_книга");
		this.R_книга = this.boss.getChild("R_книга");
	}
	public static LayerDefinition createBodyLayer() {
		MeshDefinition modelData = new MeshDefinition();
		PartDefinition modelPartData = modelData.getRoot();
		PartDefinition boss = modelPartData.addOrReplaceChild("boss", CubeListBuilder.create(), PartPose.offset(0.0F, 19.0F, 0.0F));

		PartDefinition hitbox = boss.addOrReplaceChild("hitbox", CubeListBuilder.create().texOffs(12, 7).addBox(-4.0F, -12.0F, -5.0F, 8.0F, 8.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 8.0F, 0.0F));

		PartDefinition око = boss.addOrReplaceChild("око", CubeListBuilder.create().texOffs(190, 130).addBox(-3.5F, -3.5F, -4.0F, 7.0F, 7.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition маг_круг = boss.addOrReplaceChild("маг_круг", CubeListBuilder.create().texOffs(0, 0).addBox(-25.0F, -25.0F, -10.0F, 50.0F, 50.0F, 20.0F, new CubeDeformation(-10.0F)), PartPose.offset(0.0F, -1.5F, 14.25F));

		PartDefinition R_ = boss.addOrReplaceChild("R_", CubeListBuilder.create().texOffs(124, 218).addBox(-13.1924F, -0.4124F, -5.6F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.78F, 3.6F));

		PartDefinition cube_r1 = R_.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(16, 225).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 10.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-9.3033F, 5.8909F, -3.6F, 0.6981F, 0.0F, 0.7854F));

		PartDefinition cube_r2 = R_.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(24, 225).addBox(0.0F, -0.5F, -1.25F, 1.0F, 10.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.3536F, 10.8406F, -3.6F, 1.2217F, 0.0F, 0.2618F));

		PartDefinition cube_r3 = R_.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(140, 60).addBox(-1.1005F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(164, 60).addBox(7.8995F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-11.0F, 2.78F, -3.6F, 0.0F, 0.0F, 0.7854F));

		PartDefinition bone6 = R_.addOrReplaceChild("bone6", CubeListBuilder.create(), PartPose.offset(-12.1924F, 1.5876F, -3.6F));

		PartDefinition cube_r4 = bone6.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(224, 167).addBox(7.8995F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 208).addBox(-1.1005F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.1924F, -0.8076F, 0.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition cube_r5 = bone6.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(226, 78).addBox(-0.5F, -6.5F, -0.5F, 1.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.8891F, -3.9185F, 0.0F, -0.6981F, 0.0F, -0.7854F));

		PartDefinition cube_r6 = bone6.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(226, 18).addBox(-0.5F, -8.5F, -0.5F, 1.0F, 9.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.5459F, -9.5754F, 0.0F, -0.8727F, 0.0F, -0.2618F));

		PartDefinition R_верх = R_.addOrReplaceChild("R_верх", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.7604F, -2.6224F, -3.6F, 0.0F, 0.0F, -0.6981F));

		PartDefinition cube_r7 = R_верх.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(60, 100).addBox(-9.0F, -9.0F, -6.0F, 18.0F, 18.0F, 12.0F, new CubeDeformation(-6.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0F, 0.0F));

		PartDefinition R_низ = R_.addOrReplaceChild("R_низ", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.9176F, 5.6754F, -3.6F, 0.0F, 0.0F, -0.7854F));

		PartDefinition cube_r8 = R_низ.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(120, 100).addBox(-9.0F, -9.0F, -6.0F, 18.0F, 18.0F, 12.0F, new CubeDeformation(-6.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));

		PartDefinition праве_крило = R_.addOrReplaceChild("праве_крило", CubeListBuilder.create(), PartPose.offset(-16.9588F, 1.33F, 5.4F));

		PartDefinition bone12 = праве_крило.addOrReplaceChild("bone12", CubeListBuilder.create().texOffs(156, 218).addBox(-6.3333F, 9.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.4373F, -10.2307F, -2.7362F, 0.3186F, -0.1451F, 2.5946F));

		PartDefinition cube_r9 = bone12.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(70, 130).addBox(-31.39F, -9.5038F, 0.0F, 35.0F, 11.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.6212F, 3.6223F, 0.0F, 0.0F, 0.0F, -2.0071F));

		PartDefinition bone13 = bone12.addOrReplaceChild("bone13", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.1068F, 10.8943F, 0.0F, 0.0F, 0.0F, -0.3491F));

		PartDefinition cube_r10 = bone13.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(0, 196).addBox(-1.1005F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(218, 140).addBox(7.8995F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.1924F, -0.8076F, 0.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition bone14 = bone12.addOrReplaceChild("bone14", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.1068F, 11.1057F, 0.0F, 0.0F, 0.0F, 0.3491F));

		PartDefinition cube_r11 = bone14.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(172, 218).addBox(7.8995F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 202).addBox(-1.1005F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.1924F, 1.1924F, 0.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition bone15 = праве_крило.addOrReplaceChild("bone15", CubeListBuilder.create().texOffs(188, 218).addBox(-6.3333F, 9.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.2669F, 7.0801F, -2.7622F, 0.3442F, 0.0594F, 1.3193F));

		PartDefinition cube_r12 = bone15.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(140, 0).addBox(-31.39F, -9.5038F, 0.0F, 35.0F, 11.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.6212F, 3.6223F, 0.0F, 0.0F, 0.0F, -2.0071F));

		PartDefinition bone16 = bone15.addOrReplaceChild("bone16", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.1068F, 10.8944F, 0.0F, 0.0F, 0.0F, -0.3491F));

		PartDefinition cube_r13 = bone16.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(78, 203).addBox(-1.1005F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(204, 218).addBox(7.8995F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.1924F, -0.8076F, 0.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition bone17 = bone15.addOrReplaceChild("bone17", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.1068F, 11.1057F, 0.0F, 0.0F, 0.0F, 0.3491F));

		PartDefinition cube_r14 = bone17.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(40, 219).addBox(7.8995F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(102, 203).addBox(-1.1005F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.1924F, 1.1924F, 0.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition R_рука = R_.addOrReplaceChild("R_рука", CubeListBuilder.create().texOffs(226, 30).addBox(-3.0F, -3.0F, -4.0F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.1924F, 6.8376F, -4.1F, 0.541F, 0.6332F, 0.1279F));

		PartDefinition bone43 = R_рука.addOrReplaceChild("bone43", CubeListBuilder.create().texOffs(24, 206).addBox(-5.0F, -5.0F, -4.0F, 10.0F, 10.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, -0.1745F, 0.0F));

		PartDefinition bone3 = bone43.addOrReplaceChild("bone3", CubeListBuilder.create().texOffs(180, 114).addBox(-5.0F, -5.0F, -1.0F, 10.0F, 10.0F, 6.0F, new CubeDeformation(-3.0F)), PartPose.offset(0.0F, 0.0F, -4.0F));

		PartDefinition bone22 = bone43.addOrReplaceChild("bone22", CubeListBuilder.create().texOffs(62, 195).addBox(-0.5F, -5.0F, -10.0F, 1.5F, 8.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.8076F, 0.9424F, -3.5F, 0.0F, -0.1745F, 0.0F));

		PartDefinition bone24 = bone22.addOrReplaceChild("bone24", CubeListBuilder.create().texOffs(214, 226).addBox(-0.75F, 0.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.25F, -5.0F, -8.0F, -0.6127F, -0.0715F, 0.0501F));

		PartDefinition bone38 = bone24.addOrReplaceChild("bone38", CubeListBuilder.create().texOffs(190, 144).addBox(-0.75F, -1.0F, -2.95F, 1.5F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 1.0F, -4.05F, 0.0F, -0.0873F, 0.0F));

		PartDefinition bone26 = bone22.addOrReplaceChild("bone26", CubeListBuilder.create().texOffs(174, 226).addBox(-0.75F, -1.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.25F, -4.0F, -10.0F, -0.0873F, -0.0873F, 0.0F));

		PartDefinition bone39 = bone26.addOrReplaceChild("bone39", CubeListBuilder.create().texOffs(194, 226).addBox(-0.75F, -1.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, -0.0873F, 0.0F));

		PartDefinition bone32 = bone22.addOrReplaceChild("bone32", CubeListBuilder.create().texOffs(124, 226).addBox(-0.75F, -1.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.25F, -2.0F, -10.0F, 0.0F, -0.0873F, 0.0F));

		PartDefinition bone40 = bone32.addOrReplaceChild("bone40", CubeListBuilder.create().texOffs(78, 195).addBox(-0.75F, -1.0F, -5.0F, 1.5F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, -0.0873F, 0.0F));

		PartDefinition bone31 = bone22.addOrReplaceChild("bone31", CubeListBuilder.create().texOffs(144, 226).addBox(-0.75F, -1.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.25F, 0.0F, -10.0F, 0.0873F, -0.0873F, 0.0F));

		PartDefinition bone41 = bone31.addOrReplaceChild("bone41", CubeListBuilder.create().texOffs(204, 226).addBox(-0.75F, -1.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, -0.0873F, 0.0F));

		PartDefinition bone30 = bone22.addOrReplaceChild("bone30", CubeListBuilder.create().texOffs(226, 88).addBox(-0.75F, -1.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.25F, 2.0F, -10.0F, 0.1745F, -0.0873F, 0.0F));

		PartDefinition bone42 = bone30.addOrReplaceChild("bone42", CubeListBuilder.create().texOffs(198, 144).addBox(-0.75F, -1.0F, -3.0F, 1.5F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, -0.0873F, 0.0F));

		PartDefinition L_ = boss.addOrReplaceChild("L_", CubeListBuilder.create().texOffs(56, 224).addBox(9.1924F, -0.4124F, -5.6F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.78F, 3.6F));

		PartDefinition cube_r15 = L_.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(72, 224).addBox(-1.0F, -0.5F, -1.25F, 1.0F, 10.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.3536F, 10.8406F, -3.6F, 1.2217F, 0.0F, -0.2618F));

		PartDefinition cube_r16 = L_.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(32, 225).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 10.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.3033F, 5.8909F, -3.6F, 0.6981F, 0.0F, -0.7854F));

		PartDefinition cube_r17 = L_.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(78, 209).addBox(-7.8995F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(120, 141).addBox(-11.8995F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(11.0F, 2.78F, -3.6F, 0.0F, 0.0F, -0.7854F));

		PartDefinition bone4 = L_.addOrReplaceChild("bone4", CubeListBuilder.create(), PartPose.offset(12.1924F, 1.5876F, -3.6F));

		PartDefinition cube_r18 = bone4.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(218, 132).addBox(-11.8995F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(96, 141).addBox(-7.8995F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.1924F, -0.8076F, 0.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition cube_r19 = bone4.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(226, 56).addBox(-0.5F, -8.5F, -0.5F, 1.0F, 9.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.5459F, -9.5754F, 0.0F, -0.8727F, 0.0F, 0.2618F));

		PartDefinition cube_r20 = bone4.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(226, 68).addBox(-0.5F, -6.5F, -0.5F, 1.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.8891F, -3.9185F, 0.0F, -0.6981F, 0.0F, 0.7854F));

		PartDefinition L_верх = L_.addOrReplaceChild("L_верх", CubeListBuilder.create(), PartPose.offsetAndRotation(3.7604F, -2.6224F, -3.6F, 0.0F, 0.0F, 0.6981F));

		PartDefinition cube_r21 = L_верх.addOrReplaceChild("cube_r21", CubeListBuilder.create().texOffs(60, 70).addBox(-9.0F, -9.0F, -6.0F, 18.0F, 18.0F, 12.0F, new CubeDeformation(-6.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0F, 0.0F));

		PartDefinition L_низ = L_.addOrReplaceChild("L_низ", CubeListBuilder.create(), PartPose.offsetAndRotation(3.9176F, 5.6754F, -3.6F, 0.0F, 0.0F, 0.6981F));

		PartDefinition cube_r22 = L_низ.addOrReplaceChild("cube_r22", CubeListBuilder.create().texOffs(0, 100).addBox(-9.0F, -9.0F, -6.0F, 18.0F, 18.0F, 12.0F, new CubeDeformation(-6.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

		PartDefinition ліве_крило = L_.addOrReplaceChild("ліве_крило", CubeListBuilder.create(), PartPose.offset(16.9588F, 1.33F, 5.4F));

		PartDefinition bone7 = ліве_крило.addOrReplaceChild("bone7", CubeListBuilder.create().texOffs(140, 218).addBox(2.3333F, 9.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.4373F, -10.2307F, -2.7362F, 0.3186F, 0.1451F, -2.5946F));

		PartDefinition cube_r23 = bone7.addOrReplaceChild("cube_r23", CubeListBuilder.create().texOffs(0, 130).addBox(-3.61F, -9.5038F, 0.0F, 35.0F, 11.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6212F, 3.6223F, 0.0F, 0.0F, 0.0F, 2.0071F));

		PartDefinition bone10 = bone7.addOrReplaceChild("bone10", CubeListBuilder.create(), PartPose.offsetAndRotation(5.1068F, 10.8943F, 0.0F, 0.0F, 0.0F, 0.3491F));

		PartDefinition cube_r24 = bone10.addOrReplaceChild("cube_r24", CubeListBuilder.create().texOffs(126, 203).addBox(-7.8995F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(220, 218).addBox(-11.8995F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.1924F, -0.8076F, 0.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition bone11 = bone7.addOrReplaceChild("bone11", CubeListBuilder.create(), PartPose.offsetAndRotation(5.1068F, 11.1057F, 0.0F, 0.0F, 0.0F, -0.3491F));

		PartDefinition cube_r25 = bone11.addOrReplaceChild("cube_r25", CubeListBuilder.create().texOffs(222, 40).addBox(-11.8995F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(150, 203).addBox(-7.8995F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.1924F, 1.1924F, 0.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition bone18 = ліве_крило.addOrReplaceChild("bone18", CubeListBuilder.create().texOffs(222, 48).addBox(2.3333F, 9.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.2669F, 7.0801F, -2.7622F, 0.3442F, -0.0594F, -1.3193F));

		PartDefinition cube_r26 = bone18.addOrReplaceChild("cube_r26", CubeListBuilder.create().texOffs(140, 11).addBox(-3.61F, -9.5038F, 0.0F, 35.0F, 11.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6212F, 3.6223F, 0.0F, 0.0F, 0.0F, 2.0071F));

		PartDefinition bone19 = bone18.addOrReplaceChild("bone19", CubeListBuilder.create(), PartPose.offsetAndRotation(5.1068F, 10.8944F, 0.0F, 0.0F, 0.0F, 0.3491F));

		PartDefinition cube_r27 = bone19.addOrReplaceChild("cube_r27", CubeListBuilder.create().texOffs(174, 203).addBox(-7.8995F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(222, 203).addBox(-11.8995F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.1924F, -0.8076F, 0.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition bone20 = bone18.addOrReplaceChild("bone20", CubeListBuilder.create(), PartPose.offsetAndRotation(5.1068F, 11.1057F, 0.0F, 0.0F, 0.0F, -0.3491F));

		PartDefinition cube_r28 = bone20.addOrReplaceChild("cube_r28", CubeListBuilder.create().texOffs(0, 223).addBox(-11.8995F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(198, 203).addBox(-7.8995F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.1924F, 1.1924F, 0.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition L_рука = L_.addOrReplaceChild("L_рука", CubeListBuilder.create().texOffs(224, 175).addBox(-3.0F, -3.0F, -4.0F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.1924F, 6.8376F, -4.1F, 0.541F, -0.6332F, -0.1279F));

		PartDefinition bone46 = L_рука.addOrReplaceChild("bone46", CubeListBuilder.create().texOffs(24, 196).addBox(-5.0F, -5.0F, -4.0F, 10.0F, 10.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, 0.1745F, 0.0F));

		PartDefinition bone50 = bone46.addOrReplaceChild("bone50", CubeListBuilder.create().texOffs(190, 40).addBox(-5.0F, -5.0F, -1.0F, 10.0F, 10.0F, 6.0F, new CubeDeformation(-3.0F)), PartPose.offset(0.0F, 0.0F, -4.0F));

		PartDefinition bone21 = bone46.addOrReplaceChild("bone21", CubeListBuilder.create().texOffs(46, 195).addBox(-1.0F, -5.0F, -10.0F, 1.5F, 8.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.8076F, 0.9424F, -3.5F, 0.0F, 0.1745F, 0.0F));

		PartDefinition bone23 = bone21.addOrReplaceChild("bone23", CubeListBuilder.create().texOffs(154, 226).addBox(-0.75F, 0.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.25F, -5.0F, -8.0F, -0.6127F, 0.0715F, -0.0501F));

		PartDefinition bone33 = bone23.addOrReplaceChild("bone33", CubeListBuilder.create().texOffs(64, 210).addBox(-0.75F, -1.0F, -2.95F, 1.5F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 1.0F, -4.05F, 0.0F, 0.0873F, 0.0F));

		PartDefinition bone25 = bone21.addOrReplaceChild("bone25", CubeListBuilder.create().texOffs(226, 94).addBox(-0.75F, -1.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.25F, -4.0F, -10.0F, -0.0873F, 0.0873F, 0.0F));

		PartDefinition bone34 = bone25.addOrReplaceChild("bone34", CubeListBuilder.create().texOffs(164, 226).addBox(-0.75F, -1.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, 0.0873F, 0.0F));

		PartDefinition bone27 = bone21.addOrReplaceChild("bone27", CubeListBuilder.create().texOffs(226, 100).addBox(-0.75F, -1.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.25F, -2.0F, -10.0F, 0.0F, 0.0873F, 0.0F));

		PartDefinition bone35 = bone27.addOrReplaceChild("bone35", CubeListBuilder.create().texOffs(222, 211).addBox(-0.75F, -1.0F, -5.0F, 1.5F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, 0.0873F, 0.0F));

		PartDefinition bone28 = bone21.addOrReplaceChild("bone28", CubeListBuilder.create().texOffs(226, 106).addBox(-0.75F, -1.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.25F, 0.0F, -10.0F, 0.0873F, 0.0873F, 0.0F));

		PartDefinition bone36 = bone28.addOrReplaceChild("bone36", CubeListBuilder.create().texOffs(184, 226).addBox(-0.75F, -1.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, 0.0873F, 0.0F));

		PartDefinition bone29 = bone21.addOrReplaceChild("bone29", CubeListBuilder.create().texOffs(134, 226).addBox(-0.75F, -1.0F, -4.0F, 1.5F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.25F, 2.0F, -10.0F, 0.1745F, 0.0873F, 0.0F));

		PartDefinition bone37 = bone29.addOrReplaceChild("bone37", CubeListBuilder.create().texOffs(206, 144).addBox(-0.75F, -1.0F, -3.0F, 1.5F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -4.0F, 0.0F, 0.0873F, 0.0F));

		PartDefinition L_книга = boss.addOrReplaceChild("L_книга", CubeListBuilder.create().texOffs(188, 167).addBox(-2.375F, -2.4583F, -7.0F, 4.75F, 4.75F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(19.9576F, 0.1487F, -9.7029F, 0.2282F, -0.9924F, -0.8836F));

		PartDefinition bone = L_книга.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(210, 0).addBox(-8.25F, -2.0F, 2.5F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F))
		.texOffs(140, 41).addBox(-8.0F, -2.25F, -7.5F, 10.0F, 4.75F, 15.0F, new CubeDeformation(-2.0F))
		.texOffs(142, 209).addBox(-8.25F, -2.0F, -7.75F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F))
		.texOffs(202, 209).addBox(-3.0F, -2.0F, -7.75F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F))
		.texOffs(210, 9).addBox(-3.0F, -2.0F, 2.5F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(-0.375F, 0.0417F, 0.0F, 0.0F, 0.0F, 0.2618F));

		PartDefinition bone44 = bone.addOrReplaceChild("bone44", CubeListBuilder.create().texOffs(50, 141).addBox(-6.2832F, -1.9423F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offset(-1.2756F, -0.4736F, 0.0F));

		PartDefinition bone45 = bone44.addOrReplaceChild("bone45", CubeListBuilder.create(), PartPose.offset(1.5332F, -0.0577F, 0.0F));

		PartDefinition cube_r29 = bone45.addOrReplaceChild("cube_r29", CubeListBuilder.create().texOffs(142, 149).addBox(-7.7707F, -1.8991F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0873F));

		PartDefinition bone47 = bone45.addOrReplaceChild("bone47", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r30 = bone47.addOrReplaceChild("cube_r30", CubeListBuilder.create().texOffs(96, 149).addBox(-7.7264F, -1.9175F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition bone48 = bone47.addOrReplaceChild("bone48", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r31 = bone48.addOrReplaceChild("cube_r31", CubeListBuilder.create().texOffs(50, 159).addBox(-7.6838F, -1.9396F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2618F));

		PartDefinition bone2 = L_книга.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(122, 209).addBox(-2.25F, -2.0F, -7.75F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F))
		.texOffs(140, 22).addBox(-2.0F, -2.25F, -7.5F, 10.0F, 4.75F, 15.0F, new CubeDeformation(-2.0F))
		.texOffs(102, 209).addBox(3.0F, -2.0F, -7.75F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F))
		.texOffs(162, 209).addBox(3.0F, -2.0F, 2.5F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F))
		.texOffs(182, 209).addBox(-2.25F, -2.0F, 2.5F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.375F, 0.0417F, 0.0F, 0.0F, 0.0F, -0.2618F));

		PartDefinition bone5 = bone2.addOrReplaceChild("bone5", CubeListBuilder.create().texOffs(0, 160).addBox(-2.3362F, -2.7699F, -1.875F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offset(0.375F, 0.375F, -5.125F));

		PartDefinition bone49 = bone5.addOrReplaceChild("bone49", CubeListBuilder.create(), PartPose.offset(-0.6362F, -0.7699F, 5.125F));

		PartDefinition cube_r32 = bone49.addOrReplaceChild("cube_r32", CubeListBuilder.create().texOffs(96, 167).addBox(-1.7F, -2.0F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.0873F));

		PartDefinition bone51 = bone49.addOrReplaceChild("bone51", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r33 = bone51.addOrReplaceChild("cube_r33", CubeListBuilder.create().texOffs(142, 167).addBox(-1.7F, -2.0F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition bone52 = bone51.addOrReplaceChild("bone52", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r34 = bone52.addOrReplaceChild("cube_r34", CubeListBuilder.create().texOffs(46, 177).addBox(-1.7F, -2.0F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2618F));

		PartDefinition R_книга = boss.addOrReplaceChild("R_книга", CubeListBuilder.create().texOffs(190, 22).addBox(-2.375F, -2.4583F, -7.0F, 4.75F, 4.75F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(-19.7924F, 0.1487F, -9.7029F, 0.2282F, 0.9924F, 0.8836F));

		PartDefinition bone53 = R_книга.addOrReplaceChild("bone53", CubeListBuilder.create().texOffs(44, 210).addBox(-8.25F, -2.0F, 2.5F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F))
		.texOffs(140, 130).addBox(-8.0F, -2.25F, -7.5F, 10.0F, 4.75F, 15.0F, new CubeDeformation(-2.0F))
		.texOffs(212, 114).addBox(-8.25F, -2.0F, -7.75F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F))
		.texOffs(0, 214).addBox(-3.0F, -2.0F, -7.75F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F))
		.texOffs(64, 215).addBox(-3.0F, -2.0F, 2.5F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(-0.375F, 0.0417F, 0.0F, 0.0F, 0.0F, 0.2618F));

		PartDefinition bone54 = bone53.addOrReplaceChild("bone54", CubeListBuilder.create().texOffs(0, 178).addBox(-6.2832F, -1.9423F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offset(-1.2756F, -0.4736F, 0.0F));

		PartDefinition bone55 = bone54.addOrReplaceChild("bone55", CubeListBuilder.create(), PartPose.offset(1.5332F, -0.0577F, 0.0F));

		PartDefinition cube_r35 = bone55.addOrReplaceChild("cube_r35", CubeListBuilder.create().texOffs(180, 60).addBox(-7.7707F, -1.8991F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0873F));

		PartDefinition bone56 = bone55.addOrReplaceChild("bone56", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r36 = bone56.addOrReplaceChild("cube_r36", CubeListBuilder.create().texOffs(180, 78).addBox(-7.7264F, -1.9175F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition bone57 = bone56.addOrReplaceChild("bone57", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r37 = bone57.addOrReplaceChild("cube_r37", CubeListBuilder.create().texOffs(180, 96).addBox(-7.6838F, -1.9396F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2618F));

		PartDefinition bone58 = R_книга.addOrReplaceChild("bone58", CubeListBuilder.create().texOffs(20, 216).addBox(-2.25F, -2.0F, -7.75F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F))
		.texOffs(0, 141).addBox(-2.0F, -2.25F, -7.5F, 10.0F, 4.75F, 15.0F, new CubeDeformation(-2.0F))
		.texOffs(84, 218).addBox(3.0F, -2.0F, -7.75F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F))
		.texOffs(104, 218).addBox(3.0F, -2.0F, 2.5F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F))
		.texOffs(218, 123).addBox(-2.25F, -2.0F, 2.5F, 5.25F, 4.75F, 5.25F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.375F, 0.0417F, 0.0F, 0.0F, 0.0F, -0.2618F));

		PartDefinition bone59 = bone58.addOrReplaceChild("bone59", CubeListBuilder.create().texOffs(92, 185).addBox(-2.3362F, -2.7699F, -1.875F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offset(0.375F, 0.375F, -5.125F));

		PartDefinition bone60 = bone59.addOrReplaceChild("bone60", CubeListBuilder.create(), PartPose.offset(-0.6362F, -0.7699F, 5.125F));

		PartDefinition cube_r38 = bone60.addOrReplaceChild("cube_r38", CubeListBuilder.create().texOffs(138, 185).addBox(-1.7F, -2.0F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.0873F));

		PartDefinition bone61 = bone60.addOrReplaceChild("bone61", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r39 = bone61.addOrReplaceChild("cube_r39", CubeListBuilder.create().texOffs(184, 185).addBox(-1.7F, -2.0F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition bone62 = bone61.addOrReplaceChild("bone62", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r40 = bone62.addOrReplaceChild("cube_r40", CubeListBuilder.create().texOffs(188, 149).addBox(-1.7F, -2.0F, -7.0F, 9.5F, 4.0F, 14.0F, new CubeDeformation(-2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2618F));
		return LayerDefinition.create(modelData, 256, 256);
	}
	@Override
	public void setupAnim(CurseShaman entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.boss.getAllParts().forEach(ModelPart::resetPose);
		// Match Blockbench Molang: query.anim_time is in seconds and trig uses degrees.
		float animTime = ageInTicks / 20.0F;
		float s120 = sinDeg(animTime * 120.0F);
		float c100 = cosDeg(animTime * 100.0F);
		float s90 = sinDeg(animTime * 90.0F);
		float c90 = cosDeg(animTime * 90.0F);
		float c50 = cosDeg(animTime * 50.0F);
		float c20 = cosDeg(animTime * 20.0F);
		float eyeYaw = netHeadYaw;
		float eyePitch = headPitch;
		var target = entity.getTarget();
		if (target != null) {
			double dx = target.getX() - entity.getX();
			double dz = target.getZ() - entity.getZ();
			double dy = target.getEyeY() - entity.getEyeY();
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			if (horizontal > 1.0e-6) {
				float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				eyeYaw = Mth.wrapDegrees(targetYaw - entity.getYRot());
				eyePitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
			}
		}

		applyBone("boss", s120 * 2.0F, 0.0F, c100 * 2.0F, 0.0F, s90, 0.0F);
		applyBone("око", eyePitch, eyeYaw, c50 * 10.0F, s90 * 0.3F, c90 * 0.3F, c90 * 0.3F);
		applyBone("маг_круг", 0.0F, 0.0F, c20 * 360.0F, 0.0F, 0.0F, 0.0F);

		applyBone("R_верх", s120 * 6.0F, 0.0F, c100 * 4.0F, 0.0F, s90, 0.0F);
		applyBone("R_низ", s120 * 6.0F, 0.0F, c100 * 4.0F, 0.0F, s90, 0.0F);
		applyBone("праве_крило", s120 * 4.0F, 0.0F, c100 * 4.0F, 0.0F, s90, 0.0F);
		applyBone("bone12", s120 * 20.0F, c100 * 30.0F, -c100 * 10.0F, 0.0F, s90, 0.0F);
		applyBone("bone15", s120 * 20.0F, c100 * 30.0F, -c100 * 10.0F, 0.0F, s90, 0.0F);

		applyBone("R_рука", s120 * 2.0F, 0.0F, c100 * 2.0F, 0.0F, s90, 0.0F);
		applyBone("bone43", s120 * 2.0F, 0.0F, c100 * 2.0F, 0.0F, s90, 0.0F);
		applyBone("bone3", s120 * 6.0F, 0.0F, c100 * 4.0F, 0.0F, s90, 0.0F);
		applyBone("bone22", s120 * 2.0F, 0.0F, c100 * 2.0F, 0.0F, s90, 0.0F);

		applyBone("L_верх", s120 * 6.0F, 0.0F, c100 * 4.0F, 0.0F, s90, 0.0F);
		applyBone("L_низ", s120 * 6.0F, 0.0F, c100 * 4.0F, 0.0F, s90, 0.0F);
		applyBone("ліве_крило", -s120 * 4.0F, 0.0F, c100 * 4.0F, 0.0F, s90, 0.0F);
		applyBone("bone7", s120 * 20.0F, -c100 * 30.0F, c100 * 10.0F, 0.0F, s90, 0.0F);
		applyBone("bone18", s120 * 20.0F, -c100 * 30.0F, c100 * 10.0F, 0.0F, s90, 0.0F);
		applyBone("L_рука", s120 * 2.0F, 0.0F, c100 * 2.0F, 0.0F, s90, 0.0F);
		applyBone("bone46", s120 * 2.0F, 0.0F, c100 * 2.0F, 0.0F, s90, 0.0F);
		applyBone("bone50", s120 * 6.0F, 0.0F, c100 * 4.0F, 0.0F, s90, 0.0F);
		applyBone("bone21", s120 * 6.0F, 0.0F, c100 * 4.0F, 0.0F, s90, 0.0F);
		applyBone("L_книга", s120 * 6.0F, 0.0F, c100 * 4.0F, 0.0F, s90, 0.0F);
		applyBone("R_книга", s120 * 6.0F, 0.0F, c100 * 4.0F, 0.0F, s90, 0.0F);
	}

	@Override
	public ModelPart root() {
		return this.boss;
	}

	private void applyBone(String boneName, float rotXDeg, float rotYDeg, float rotZDeg, float offX, float offY, float offZ) {
		this.getAnyDescendantWithName(boneName).ifPresent(part -> {
			part.xRot += (float) Math.toRadians(rotXDeg);
			part.yRot += (float) Math.toRadians(rotYDeg);
			part.zRot += (float) Math.toRadians(rotZDeg);
			part.x += offX;
			part.y += offY;
			part.z += offZ;
		});
	}

	private static float sinDeg(float degrees) {
		return (float) Math.sin(Math.toRadians(degrees));
	}

	private static float cosDeg(float degrees) {
		return (float) Math.cos(Math.toRadians(degrees));
	}
}
