package com.jayemceekay.shadowedhearts.client.gui

import com.jayemceekay.shadowedhearts.client.ModShaders
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gui.GuiGraphics
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/** The infuser's quiet, upward-flowing currents, confined to its center viewport. */
class UmbraInfuserBackground {
    private var lastFrameNanos = 0L
    private var flowSeconds = 0.0
    private var lastComplete: Boolean? = null
    private var completedAtNanos = 0L

    fun reset() {
        lastFrameNanos = 0L
        flowSeconds = 0.0
        lastComplete = null
        completedAtNanos = 0L
    }

    fun render(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        energy: Float,
        progress: Float,
        active: Boolean,
        occupied: Boolean,
        complete: Boolean
    ) {
        if (width <= 0 || height <= 0) return
        val now = System.nanoTime()
        val activity = if (active) 0.85F + 0.15F * progress.coerceIn(0F, 1F) else 0.12F
        val frameGap = if (lastFrameNanos == 0L) 0.0 else (now - lastFrameNanos) / 1_000_000_000.0
        // Integrate speed instead of multiplying absolute time, so start/stop never jumps phase.
        // Cap the first delta after the inventory drawer hides this viewport.
        if (lastFrameNanos != 0L) {
            val delta = frameGap.coerceIn(0.0, 0.25)
            flowSeconds += delta * (0.28 + 0.72 * activity)
        }
        lastFrameNanos = now
        val visiblyComplete = complete && occupied && !active
        if (lastComplete == false && visiblyComplete && frameGap <= 0.25) completedAtNanos = now
        if (!visiblyComplete || frameGap > 0.25) completedAtNanos = 0L
        lastComplete = visiblyComplete
        // A newly opened completed chamber starts with no pulse.
        val pulseAge = if (completedAtNanos == 0L) -1F else (now - completedAtNanos) / 1_000_000_000F

        // Finish prior GUI batches before capturing their state and issuing an immediate quad.
        graphics.flush()
        val previousShader = RenderSystem.getShader()
        val previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        val previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        val previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        val depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        val cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
        val sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        val equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)
        val equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)
        try {
            RenderSystem.disableDepthTest()
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            RenderSystem.depthMask(false)
            GL11.glDepthMask(false)
            RenderSystem.disableBlend()
            GL11.glDisable(GL11.GL_BLEND)
            val shader = ModShaders.UMBRA_INFUSER_BACKGROUND
            if (shader == null) {
                graphics.fill(x, y, x + width, y + height, 0xFF08090B.toInt())
                graphics.flush()
                return
            }
            shader.getUniform("ViewportSize")?.set(width.toFloat(), height.toFloat())
            shader.getUniform("FlowTime")?.set(flowSeconds.toFloat())
            shader.getUniform("Energy")?.set(energy.coerceIn(0F, 1F))
            shader.getUniform("Activity")?.set(activity)
            shader.getUniform("Occupied")?.set(if (occupied) 1F else 0F)
            shader.getUniform("PulseAge")?.set(pulseAge)
            RenderSystem.setShader { shader }
            val pose = graphics.pose().last().pose()
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
            buffer.addVertex(pose, x.toFloat(), (y + height).toFloat(), 0F).setUv(0F, 1F)
            buffer.addVertex(pose, (x + width).toFloat(), (y + height).toFloat(), 0F).setUv(1F, 1F)
            buffer.addVertex(pose, (x + width).toFloat(), y.toFloat(), 0F).setUv(1F, 0F)
            buffer.addVertex(pose, x.toFloat(), y.toFloat(), 0F).setUv(0F, 0F)
            BufferUploader.drawWithShader(buffer.buildOrThrow())
        } finally {
            // Match the entry state, including raw state when another renderer bypassed vanilla caches.
            RenderSystem.depthMask(depthWrite)
            GL11.glDepthMask(depthWrite)
            RenderSystem.depthFunc(depthFunction)
            GL11.glDepthFunc(depthFunction)
            if (depthEnabled) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            if (depthEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
            if (cullEnabled) RenderSystem.enableCull() else RenderSystem.disableCull()
            if (cullEnabled) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)
            RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha)
            GL14.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha)
            RenderSystem.blendEquation(equationRgb)
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha)
            if (blendEnabled) RenderSystem.enableBlend() else RenderSystem.disableBlend()
            if (blendEnabled) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
            BufferUploader.invalidate()
            GL30.glBindVertexArray(previousVertexArray)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer)
            RenderSystem.setShader { previousShader }
            GL20.glUseProgram(previousProgram)
        }
    }
}
