package com.jayemceekay.shadowedhearts.client.render.armor;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class SnagMachineAdvancedModel<T extends Entity> extends EntityModel<T> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "snag_machine_advanced"), "main");
	public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "textures/armor/snag_machine_advanced.png");
	private final ModelPart bone2;

	public SnagMachineAdvancedModel(ModelPart root) {
		this.bone2 = root.getChild("bone2");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition bone2 = partdefinition.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(0, 6).addBox(-3.5708F, 8.0782F, -2.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(12, 6).addBox(-3.6708F, 8.0782F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offset(-3.4292F, -0.0782F, 0.0F));

		PartDefinition cube_r1 = bone2.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(16, 4).addBox(-0.75F, 0.0782F, -1.1792F, 1.5F, 1.0F, 1.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 7.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

		PartDefinition cube_r2 = bone2.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(12, 14).addBox(5.4292F, -4.9218F, -0.9292F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 7.0F, -6.0F, 0.0F, -1.5708F, 0.0F));

		PartDefinition RightArm_r1 = bone2.addOrReplaceChild("RightArm_r1", CubeListBuilder.create().texOffs(16, 0).addBox(-0.9208F, 1.1766F, 1.1766F, 0.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 7.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

		PartDefinition RightArm_r2 = bone2.addOrReplaceChild("RightArm_r2", CubeListBuilder.create().texOffs(0, 18).addBox(-1.3973F, 4.8547F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(-3.0F, 7.0F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition RightArm_r3 = bone2.addOrReplaceChild("RightArm_r3", CubeListBuilder.create().texOffs(16, 17).addBox(-1.8416F, 4.8547F, 0.2797F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(-3.0F, 7.0F, 0.0F, 0.0F, 0.7854F, -0.1745F));

		PartDefinition RightArm_r4 = bone2.addOrReplaceChild("RightArm_r4", CubeListBuilder.create().texOffs(16, 14).addBox(-1.8743F, 4.8547F, -1.2205F, 1.0F, 2.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(-3.0F, 7.0F, 0.0F, 0.0F, -0.829F, -0.1745F));

		PartDefinition RightArm_r5 = bone2.addOrReplaceChild("RightArm_r5", CubeListBuilder.create().texOffs(0, 14).addBox(0.1684F, -4.4955F, -1.5F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.3316F, -6.6955F, -2.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(-3.0F, 7.0F, 0.0F, 0.0F, 0.0F, -0.2182F));

		return LayerDefinition.create(meshdefinition, 32, 32);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		bone2.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}
