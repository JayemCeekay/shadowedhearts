package com.jayemceekay.shadowedhearts.client.trail

import com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository
import com.cobblemon.mod.common.client.particle.ParticleStorm
import com.cobblemon.mod.common.client.render.MatrixWrapper
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

/**
 * Renders evidence node markers (shadow disc + particle effects)
 * at trail node positions. Separated from TrailClientState so
 * node markers can be tuned independently of the ribbon trail.
 */
object TrailEvidenceRenderer {

    private val SHADOW_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/misc/shadow.png")

    /**
     * Spawns Cobblemon sparkle particle effects at each evidence node position.
     * The active hotspot uses a brighter effect; inactive nodes use darker rising sparkles.
     * A shadow disc effect is also spawned at each node for ground-level shadowing.
     */
    fun render(
        level: ClientLevel,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        camera: Camera,
        nodes: List<BlockPos>,
        hotspot: BlockPos?,
        currentNodeIndex: Int,
        tickCounter: Int,
        signalBlackedOut: Boolean
    ) {
        if (signalBlackedOut) return

        val spawnInactive = (tickCounter % 3 == 0)

        val activeEffect = BedrockParticleOptionsRepository.getEffect(
            ResourceLocation.fromNamespaceAndPath("shadowedhearts", "shadow_node_active")
        )
        val inactiveEffect = BedrockParticleOptionsRepository.getEffect(
            ResourceLocation.fromNamespaceAndPath("shadowedhearts", "shadow_node_inactive")
        )
        val vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(SHADOW_TEXTURE))

        nodes.forEachIndexed { idx, nodePos ->
            val isHotspot = (nodePos == hotspot)
            if (isHotspot) {
                val worldX = nodePos.x + 0.5
                val worldY = nodePos.y + 0.02
                val worldZ = nodePos.z + 0.5

                val localX = (worldX - camera.position.x).toFloat()
                val localY = (worldY - camera.position.y).toFloat()
                val localZ = (worldZ - camera.position.z).toFloat()

                val radius = 0.9f
                val alpha = 0.75f

                poseStack.pushPose()
                poseStack.translate(localX.toDouble(), localY.toDouble(), localZ.toDouble())
                poseStack.mulPose(Axis.XP.rotationDegrees(90f))
                poseStack.scale(radius, radius, radius)

                val pose = poseStack.last()

                vertexConsumer.addVertex(pose, -1f, -1f, 0f)
                    .setColor(0f, 0f, 0f, alpha)
                    .setUv(0f, 0f)
                    .setUv1(0, 0)
                    .setNormal(0f, 1f, 0f)
                    .setLight(0)

                vertexConsumer.addVertex(pose, -1f, 1f, 0f)
                    .setColor(0f, 0f, 0f, alpha)
                    .setUv(0f, 1f)
                    .setUv1(0, 1)
                    .setNormal(0f, 1f, 0f)
                    .setLight(0)

                vertexConsumer.addVertex(pose, 1f, 1f, 0f)
                    .setColor(0f, 0f, 0f, alpha)
                    .setUv(1f, 1f)
                    .setUv1(1, 1)
                    .setNormal(0f, 1f, 0f)
                    .setLight(0)

                vertexConsumer.addVertex(pose, 1f, -1f, 0f)
                    .setColor(0f, 0f, 0f, alpha)
                    .setUv(1f, 0f)
                    .setUv1(1, 0)
                    .setNormal(0f, 1f, 0f)
                    .setLight(0)

                poseStack.popPose()

                if (activeEffect != null) {
                    val wrapper = MatrixWrapper()
                    wrapper.updatePosition(Vec3(nodePos.x + 0.5, nodePos.y + 0.1, nodePos.z + 0.5))
                    ParticleStorm(activeEffect, wrapper, wrapper, level).spawn()
                }
            } else if (!isHotspot && spawnInactive && idx >= currentNodeIndex) {
                if (inactiveEffect != null) {
                    val wrapper = MatrixWrapper()
                    wrapper.updatePosition(Vec3(nodePos.x + 0.5, nodePos.y + 0.1, nodePos.z + 0.5))
                    //ParticleStorm(inactiveEffect, wrapper, wrapper, level).spawn()
                }
            }
        }
    }
}
