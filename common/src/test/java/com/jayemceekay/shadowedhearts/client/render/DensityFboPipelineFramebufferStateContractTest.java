package com.jayemceekay.shadowedhearts.client.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DensityFboPipelineFramebufferStateContractTest {

    @Test
    void densityTransactionPreservesSplitIrisFramebufferState()
            throws IOException {
        String source = pipelineSource();
        String capture = sourceBetween(
                source,
                "private void captureCurrentRenderState()",
                "private void restoreCapturedRenderState()");
        String restore = sourceBetween(
                source,
                "private void restoreCapturedRenderState()",
                "private static void drawFullScreenQuad()");

        assertTrue(source.contains(
                "private int restoreDrawFramebufferId = -1;"));
        assertTrue(source.contains(
                "private int restoreReadFramebufferId = -1;"));
        assertTrue(source.contains(
                "private boolean renderStateCaptured;"));
        assertTrue(capture.contains("RenderTransactionState.capture()"));
        assertTrue(capture.contains("baseState.drawFramebuffer()"));
        assertTrue(capture.contains("baseState.readFramebuffer()"));
        assertTrue(capture.contains("renderStateCaptured = true;"));
        assertTrue(restore.contains("restoreRenderState.restore()"),
                "Iris needs its program, samplers, color mask and fixed"
                        + " function state restored with its framebuffer");
        assertTrue(restore.contains("restoreRenderState = null;"));
        assertTrue(restore.contains("if (!renderStateCaptured)"),
                "an unmatched end call must not replace Iris' active target");
    }

    @Test
    void depthCopyUsesCapturedDrawViewportAndAcceptsDefaultFramebuffer()
            throws IOException {
        String source = pipelineSource();
        String depthCopy = sourceBetween(
                source,
                "private boolean blitSourceDepthToTarget(RenderTarget target)",
                "private void captureCurrentRenderState()");

        assertTrue(depthCopy.contains("if (renderStateCaptured)"));
        assertTrue(depthCopy.contains(
                "sourceFbo = restoreDrawFramebufferId;"));
        assertTrue(depthCopy.contains("sourceX = restoreViewportX;"));
        assertTrue(depthCopy.contains("sourceY = restoreViewportY;"));
        assertTrue(depthCopy.contains(
                "sourceX + sourceWidth, sourceY + sourceHeight"));
        assertFalse(depthCopy.contains("sourceFbo <= 0"),
                "framebuffer zero is a valid captured depth source");
        assertTrue(depthCopy.contains(
                "GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer"));
        assertTrue(depthCopy.contains(
                "GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer"));
        assertTrue(depthCopy.contains("GL30.glCheckFramebufferStatus("));
        assertTrue(depthCopy.contains(
                "copied = GL11.glGetError() == GL11.GL_NO_ERROR;"),
                "an incomplete or rejected Iris depth blit must be reported"
                        + " instead of silently using stale depth");
    }

    @Test
    void externalTransactionRestoresIrisProgramTexturesAndColorState()
            throws IOException {
        String source = pipelineSource();
        String transactionState = sourceBetween(
                source,
                "private record RenderTransactionState(",
                "private record FullscreenPassState(");

        assertTrue(transactionState.contains("GL20.GL_CURRENT_PROGRAM"));
        assertTrue(transactionState.contains("RenderSystem.getShader()"));
        assertTrue(transactionState.contains("GL13.GL_ACTIVE_TEXTURE"));
        assertTrue(transactionState.contains("GL11.GL_TEXTURE_BINDING_2D"));
        assertTrue(transactionState.contains("GL11.GL_COLOR_WRITEMASK"));
        assertTrue(transactionState.contains("baseState.restore();"));
        assertTrue(transactionState.contains("GL20.glUseProgram(currentProgram)"));
        assertTrue(transactionState.contains("RenderSystem.setShaderColor("));
        assertTrue(transactionState.contains("RenderSystem.colorMask("));
    }

    @Test
    void logicalAndRawSamplerBindingsAreRestoredForUnitsZeroThroughFour()
            throws IOException {
        String source = pipelineSource();
        String transactionState = sourceBetween(
                source,
                "private record RenderTransactionState(",
                "private record FullscreenPassState(");
        String compactState = transactionState.replaceAll("\\s+", "");

        assertTrue(compactState.contains(
                "int[]shaderTextures,int[]textureBindings"),
                "logical RenderSystem sampler ids must be kept separately"
                        + " from the raw GL bindings");
        assertTrue(compactState.contains(
                "privatestaticfinalintPRESERVED_TEXTURE_UNITS=5;"),
                "Dark Ball currently consumes sampler units zero through"
                        + " four");
        assertTrue(compactState.contains(
                "int[]shaderTextures=newint[PRESERVED_TEXTURE_UNITS];"));
        assertTrue(compactState.contains(
                "int[]textureBindings=newint[PRESERVED_TEXTURE_UNITS];"));
        assertTrue(compactState.contains(
                "shaderTextures[unit]=RenderSystem.getShaderTexture(unit);"),
                "capture must preserve RenderSystem's logical sampler cache");
        assertTrue(compactState.contains(
                "textureBindings[unit]=GL11.glGetInteger("
                        + "GL11.GL_TEXTURE_BINDING_2D);"),
                "capture must also preserve Iris' raw texture binding");

        int restoreLogical = compactState.indexOf(
                "RenderSystem.setShaderTexture(unit,shaderTextures[unit]);");
        int restoreActiveUnit = compactState.indexOf(
                "RenderSystem.activeTexture(GL13.GL_TEXTURE0+unit);",
                compactState.indexOf("voidrestore()"));
        int restoreRaw = compactState.indexOf(
                "RenderSystem.bindTexture(textureBindings[unit]);");
        assertTrue(restoreLogical >= 0,
                "restore must repair the logical sampler cache");
        assertTrue(restoreActiveUnit > restoreLogical);
        assertTrue(restoreRaw > restoreActiveUnit,
                "the corresponding raw binding must be restored on the same"
                        + " unit after its logical sampler id");
    }

    @Test
    void blurDepthTextureUsesDeclaredSamplerInsteadOfUniformLookup()
            throws IOException {
        String source = pipelineSource();
        String blur = sourceBetween(
                source,
                "public int blur(ShaderInstance blurShader,"
                        + " Consumer<ShaderInstance> uniformSetup)",
                "public boolean processDensityToTemp(ShaderInstance shader,");
        String material = Files.readString(
                Path.of("src/main/resources/assets/shadowedhearts/shaders/"
                        + "core/aura/penumbra_blur.json"),
                StandardCharsets.UTF_8);
        String samplerBlock = sourceBetween(
                material,
                "\"samplers\"",
                "\"uniforms\"");

        assertTrue(samplerBlock.contains("\"name\": \"Sampler1\""),
                "the shader material must register the depth sampler");
        assertTrue(blur.contains(
                "RenderSystem.setShaderTexture(1, densityDepthTexture);"));
        assertTrue(blur.contains(
                "blurShader.setSampler(\"Sampler1\", densityDepthTexture);"));
        assertFalse(blur.contains("getUniform(\"Sampler1\")"),
                "samplers are tracked separately from ordinary uniforms");
        assertTrue(blur.contains(
                "RenderTransactionState savedState = RenderTransactionState.capture();"),
                "blur must preserve the complete caller GL transaction");
        assertTrue(blur.contains(
                "VertexSorting savedVertexSorting = RenderSystem.getVertexSorting();"));
        assertTrue(blur.contains(
                "savedProj, savedVertexSorting"),
                "GUI and world callers must regain their original vertex sorting");
        assertTrue(blur.contains("finally {"));
        assertTrue(blur.contains("savedState.restore();"),
                "blur must restore caller state even when a draw fails");
        assertFalse(blur.contains("clearTargetUnscissored(densityTarget)"),
                "vertical passes must not erase the copied scene depth");
        assertTrue(blur.contains("clearColorAttachmentUnscissored();"),
                "vertical passes should clear only density color");
    }

    @Test
    void failedBeginAndDestroyRestoreCapturedCallerState()
            throws IOException {
        String source = pipelineSource();
        String begin = sourceBetween(
                source,
                "public boolean beginDensityPass(boolean clearTarget,"
                        + " boolean copyMainDepth)",
                "/**\n     * Restores the framebuffer");
        String destroy = sourceBetween(
                source,
                "public void destroy()",
                "private boolean ensureTargets(");

        assertTrue(begin.contains("catch (RuntimeException | Error"));
        assertTrue(begin.contains("restoreCapturedRenderState();"));
        assertTrue(destroy.contains("restoreCapturedRenderState();"));
    }

    private static String pipelineSource() throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/render/DensityFboPipeline.java"),
                StandardCharsets.UTF_8);
    }

    private static String sourceBetween(
            String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        assertTrue(start >= 0, "missing source token " + startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue(end > start, "missing source token " + endToken);
        return source.substring(start, end);
    }
}
