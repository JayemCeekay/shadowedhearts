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

public class SnagMachinePrototypeModel<T extends Entity> extends EntityModel<T> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "snag_machine_prototype"), "main");
	public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "textures/armor/snag_machine_prototype.png");
	private final ModelPart group;

	public SnagMachinePrototypeModel(ModelPart root) {
		this.group = root.getChild("group");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		partdefinition.addOrReplaceChild("group", CubeListBuilder.create().texOffs(0, 0).addBox(-6.763F, -2.1717F, -0.1945F, 9.0F, 6.0F, 4.0F, new CubeDeformation(0.1F))
		.texOffs(0, 10).addBox(-6.754F, -2.1627F, -0.2035F, 9.0F, 6.0F, 4.0F, new CubeDeformation(0.3F))
		.texOffs(26, 0).addBox(-2.772F, -2.3158F, -0.1855F, 5.0F, 3.0F, 4.0F, new CubeDeformation(-0.1F))
		.texOffs(16, 20).addBox(-3.844F, 4.1973F, -0.2035F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.3F))
		.texOffs(0, 20).addBox(-3.853F, 0.1883F, -0.1945F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offset(8.0F, 1.0F, -2.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		group.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}
