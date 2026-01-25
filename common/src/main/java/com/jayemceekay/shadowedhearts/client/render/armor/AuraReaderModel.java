package com.jayemceekay.shadowedhearts.client.render.armor;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class AuraReaderModel<T extends Entity> extends EntityModel<T> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_reader"), "main");
	public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "textures/armor/aura_reader.png");
	private final ModelPart group;

	public AuraReaderModel(ModelPart root) {
		this.group = root.getChild("group");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();

		meshdefinition.getRoot().addOrReplaceChild("group", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-6.0F, -7.49F, -6.0F, 13.0F, 2.99F, 13.0F, new CubeDeformation(0.51F))
						.texOffs(10, 17).addBox(7F, -6.5F, -4.0F, 1.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
						.texOffs(-1, 15).addBox(7F, -5.5F, -7.0F, 1.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
						.texOffs(20, 19).addBox(7F, -4.5F, -7.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.15F))
						.texOffs(19, 15).addBox(1.25F, -4.5F, -6.5F, 6.0F, 3.0F, 1.0F, new CubeDeformation(0.15F)),
				PartPose.offset(0.0F, 0.0F, 0.0F));

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
