package com.jayemceekay.shadowedhearts.client.ball;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Development-only, bounded GPU diagnostics for the Dark Ball screen-space
 * pipeline. Both outputs are opt-in so their extra splat draw, atlas refresh,
 * and screenshot readback do not contaminate production performance.
 *
 * <p>The source attachments are sampled only after the density pass has ended
 * and the final composite has succeeded. HUD mode refreshes a small GPU atlas
 * at a bounded rate and reuses it between refreshes. Deep-trace capture mode
 * synchronously reads back that same fixed-size atlas at phase-relative
 * interior samples; only the PNG encoding and disk write are asynchronous.</p>
 */
public final class DarkBallFboDebugPreview {

    static final String HUD_PROPERTY =
            "shadowedhearts.darkBallFboPreviewHud";
    static final String HUD_PROPERTY_ALIAS =
            "shadowedhearts.darkBallFboPreview";
    static final String CAPTURE_PROPERTY =
            "shadowedhearts.darkBallFboCaptureMidpoints";
    static final String CAPTURE_SAMPLES_PROPERTY =
            "shadowedhearts.darkBallFboCaptureSamplesPerPhase";

    private static final boolean HUD_ENABLED =
            Boolean.parseBoolean(System.getProperty(
                    HUD_PROPERTY,
                    System.getProperty(HUD_PROPERTY_ALIAS, "false")));
    private static final boolean PHASE_CAPTURES_ENABLED =
            Boolean.parseBoolean(System.getProperty(
                    CAPTURE_PROPERTY, "false"));
    private static final int CAPTURE_SAMPLES_PER_PHASE =
            DarkBallFboPreviewSchedule.clampSamplesPerPhase(
                    Integer.getInteger(
                            CAPTURE_SAMPLES_PROPERTY,
                            DarkBallFboPreviewSchedule
                                    .DEFAULT_DEEP_TRACE_SAMPLES_PER_PHASE));

    private static final int ATLAS_WIDTH = 960;
    private static final int ATLAS_HEIGHT = 720;
    private static final int ATLAS_COLUMNS = 4;
    private static final int ATLAS_ROWS = 4;
    private static final int MAGNIFIED_TILE_START = 12;
    private static final int MAGNIFIED_CROP_PADDING_PIXELS = 24;
    private static final float TILE_LABEL_SCALE = 0.55f;
    private static final int TILE_LABEL_PADDING_X = 3;
    private static final int TILE_LABEL_PADDING_Y = 2;
    private static final float LIVE_UPDATE_INTERVAL_SECONDS = 1.0f / 12.0f;
    private static final String SESSION_TOKEN =
            Long.toUnsignedString(System.currentTimeMillis(), 36);
    private static final Gson MANIFEST_GSON = new Gson();
    private static final Object MANIFEST_WRITE_LOCK = new Object();

    private static final String[] TILE_LABELS = {
            "final composite", "raw RGB", "body R", "siphon G",
            "storage B signed", "surface A signed",
            "edge-resolved RGB", "exact mask RGB",
            "proxy-front depth", "scene depth",
            "pre-merge splat identities",
            "pre-merge splat overlap",
            "focus final composite", "focus raw RGB",
            "focus edge-resolved RGB", "focus exact mask RGB"
    };
    private static final String[] TILE_SEMANTICS = {
            "main framebuffer immediately after the Dark Ball composite",
            "raw screen-space material RGBA",
            "raw material body coverage R",
            "raw material siphon coverage G",
            "raw signed storage or radius payload B",
            "raw signed first-surface distance A",
            "edge-resolved screen-space material",
            "persistent frozen textured reference mask",
            "texture-exact proxy-front hardware depth",
            "copied full-scene hardware depth",
            "individual deformed splat footprints before field resolve; "
                    + "stable colors identify contributors",
            "continuous overlap heat from the pre-resolve splat diagnostic "
                    + "alpha accumulation",
            "magnified projected-effect crop of the main framebuffer",
            "magnified projected-effect crop of raw material RGBA",
            "magnified projected-effect crop of edge-resolved material",
            "magnified projected-effect crop of the frozen reference mask"
    };
    private static final int[] TILE_PREVIEW_MODES = {
            7, 0, 1, 2,
            3, 4, 0, 0,
            5, 6, 9, 10,
            7, 0, 0, 0
    };

    private static final DarkBallFboPreviewSchedule.Tracker
            CAPTURE_TRACKER = new DarkBallFboPreviewSchedule.Tracker(
                    CAPTURE_SAMPLES_PER_PHASE);
    private static final
            DarkBallFboPreviewSchedule.DirectRendererActivationTracker
            DIRECT_RENDERER_ACTIVATION_TRACKER =
            new DarkBallFboPreviewSchedule.DirectRendererActivationTracker();

    private static RenderTarget atlasTarget;
    private static RenderTarget splatTileExportTarget;
    private static LivePreview livePreview;
    private static long outputCaptureKey = Long.MIN_VALUE;
    private static float outputPreviousAge;
    private static int nextOutputShotNumber = 1;

    private DarkBallFboDebugPreview() {
    }

    static boolean isEnabled() {
        return HUD_ENABLED || PHASE_CAPTURES_ENABLED;
    }

    /**
     * Consumes a fresh post-composite attachment snapshot. This must be called
     * from the render thread after {@link DarkBallDensityFBO#composite()}.
     */
    static void afterCompositeFrame(
            DarkBallCaptureVfx.FboPreviewCapture capture,
            DarkBallDensityFBO.PreviewFrame frame) {
        if (!isEnabled()) {
            return;
        }
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        afterCompositeFrame(
                capture,
                frame,
                target.getColorTextureId(),
                target.width,
                target.height);
    }

    /**
     * Iris-aware form of the ordinary post-composite callback. The
     * final-composite diagnostic must
     * sample the color target that actually received the effect, not
     * Minecraft's vanilla target when Iris owns world rendering.
     */
    static void afterCompositeFrame(
            DarkBallCaptureVfx.FboPreviewCapture capture,
            DarkBallDensityFBO.PreviewFrame frame,
            int compositeTexture,
            int compositeWidth,
            int compositeHeight) {
        if (!isEnabled()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        if (capture == null
                || frame == null
                || !frame.isUsable()
                || !DarkBallDensityFBO.isPreviewFrameCurrent(frame)
                || compositeTexture <= 0
                || compositeWidth <= 0
                || compositeHeight <= 0) {
            livePreview = null;
            return;
        }
        CompositeSource compositeSource = new CompositeSource(
                compositeTexture, compositeWidth, compositeHeight);

        ensureOutputSequence(capture.captureKey(), capture.age());
        DarkBallFboPreviewSchedule.CapturePoint pendingCapture = null;
        List<DarkBallFboPreviewSchedule.DirectRendererActivationCapture>
                activationCaptures =
                DIRECT_RENDERER_ACTIVATION_TRACKER.observe(
                capture.captureKey(),
                capture.age(),
                capture.presentationAuthority());
        if (PHASE_CAPTURES_ENABLED) {
            reportNewlySkippedCaptures(
                    capture,
                    CAPTURE_TRACKER.observe(
                            capture.captureKey(), capture.age()));
            pendingCapture = CAPTURE_TRACKER.pendingCapture();
        }

        for (DarkBallFboPreviewSchedule.DirectRendererActivationCapture
                activationCapture : activationCaptures) {
            if (activationCapture.kind().previousFrame()) {
                submitRetainedPreActivationScreenshot(
                        capture, activationCapture);
            }
        }

        boolean hasCurrentActivationCapture =
                activationCaptures.stream().anyMatch(
                        activationCapture ->
                                !activationCapture.kind().previousFrame());
        boolean refreshHud = shouldRefreshHud(
                capture, frame, compositeSource);
        if (!refreshHud
                && pendingCapture == null
                && !hasCurrentActivationCapture) {
            return;
        }
        if (!buildAtlas(frame, compositeSource.texture())) {
            livePreview = null;
            return;
        }

        livePreview = new LivePreview(capture, frame, compositeSource);
        for (DarkBallFboPreviewSchedule.DirectRendererActivationCapture
                activationCapture : activationCaptures) {
            if (!activationCapture.kind().previousFrame()) {
                submitDirectRendererActivationScreenshot(
                        capture, frame, compositeSource, activationCapture);
            }
        }
        if (pendingCapture != null
                && submitCaptureScreenshot(
                capture, frame, compositeSource, pendingCapture)) {
            CAPTURE_TRACKER.markSubmitted(pendingCapture);
        }
    }

    public static void renderHud(GuiGraphics graphics) {
        if (!HUD_ENABLED || graphics == null) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        LivePreview preview = livePreview;
        if (minecraft == null
                || minecraft.options.hideGui
                || DarkBallCaptureVfx.getActiveInstances().isEmpty()
                || atlasTarget == null
                || preview == null
                || ModShaders.DARK_BALL_FBO_PREVIEW == null) {
            return;
        }

        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        if (guiWidth <= 0 || guiHeight <= 0) {
            return;
        }
        int previewWidth = Math.min(
                Math.max(1, guiWidth - 12),
                Math.min(
                        720,
                        Math.max(240, Math.round(guiWidth * 0.56f))));
        int previewHeight = Math.round(
                previewWidth * (ATLAS_HEIGHT / (float) ATLAS_WIDTH));
        int maximumHeight = Math.max(
                1,
                Math.min(
                        Math.max(135, Math.round(guiHeight * 0.62f)),
                        guiHeight - 33));
        if (previewHeight > maximumHeight) {
            previewHeight = maximumHeight;
            previewWidth = Math.round(
                    previewHeight * (ATLAS_WIDTH / (float) ATLAS_HEIGHT));
        }

        int x = Math.max(4, guiWidth - previewWidth - 8);
        int y = 29;
        String header = String.format(
                Locale.ROOT,
                "Dark Ball FBO | %s | %.3fs | %s",
                preview.capture().renderPath()
                        + " / "
                        + preview.capture().presentationAuthority(),
                preview.capture().age(),
                DarkBallFboPreviewSchedule.phaseAt(preview.capture().age()));
        String detail = String.format(
                Locale.ROOT,
                "source %dx%d | resolve %s%s %.2f/%.2f | "
                        + "sweep %s %.2f | atlas %dx%d | "
                        + "live %.0f Hz | trace %d/phase",
                preview.frame().width(),
                preview.frame().height(),
                preview.frame().reducedCompositePolicy(),
                preview.frame().reducedCompositeUsed() ? "*" : "",
                preview.frame().projectedBodyAreaFraction(),
                preview.frame().mediumReducedCompositeAreaThreshold(),
                preview.capture().sweepPath(),
                preview.capture().sweepStrength(),
                ATLAS_WIDTH,
                ATLAS_HEIGHT,
                1.0f / LIVE_UPDATE_INTERVAL_SECONDS,
                CAPTURE_SAMPLES_PER_PHASE);

        graphics.fill(
                x - 3, y - 25,
                x + previewWidth + 3,
                y + previewHeight + 3,
                0xD0000000);
        graphics.drawString(
                minecraft.font,
                fitText(minecraft, header, previewWidth),
                x, y - 22, 0xFFE6D7FF, true);
        graphics.drawString(
                minecraft.font,
                fitText(minecraft, detail, previewWidth),
                x, y - 12, 0xFFBFB6CA, true);
        graphics.flush();

        if (!drawHudAtlas(
                atlasTarget.getColorTextureId(),
                x, y, previewWidth, previewHeight,
                guiWidth, guiHeight)) {
            return;
        }

        int tileWidth = previewWidth / ATLAS_COLUMNS;
        int tileHeight = previewHeight / ATLAS_ROWS;
        for (int index = 0; index < TILE_LABELS.length; index++) {
            int column = index % ATLAS_COLUMNS;
            int row = index / ATLAS_COLUMNS;
            int tileX = x + column * tileWidth;
            int tileY = y + row * tileHeight;
            int availableRenderedWidth = Math.max(
                    1, tileWidth - TILE_LABEL_PADDING_X * 2);
            int availableUnscaledWidth = Math.max(
                    1,
                    (int) Math.floor(
                            availableRenderedWidth / TILE_LABEL_SCALE));
            String label = fitText(
                    minecraft,
                    tileLabel(index, preview.frame()),
                    availableUnscaledWidth);
            int renderedLabelWidth = Math.min(
                    availableRenderedWidth,
                    Math.max(
                            1,
                            (int) Math.ceil(
                                    minecraft.font.width(label)
                                            * TILE_LABEL_SCALE)));
            int renderedLabelHeight = Math.max(
                    1,
                    (int) Math.ceil(
                            minecraft.font.lineHeight * TILE_LABEL_SCALE));
            graphics.fill(
                    tileX + 1, tileY + 1,
                    Math.min(
                            tileX + tileWidth - 1,
                            tileX + renderedLabelWidth
                                    + TILE_LABEL_PADDING_X * 2),
                    tileY + renderedLabelHeight
                            + TILE_LABEL_PADDING_Y * 2,
                    0xB0000000);
            graphics.pose().pushPose();
            graphics.pose().translate(
                    tileX + TILE_LABEL_PADDING_X,
                    tileY + TILE_LABEL_PADDING_Y,
                    0.0f);
            graphics.pose().scale(
                    TILE_LABEL_SCALE, TILE_LABEL_SCALE, 1.0f);
            graphics.drawString(
                    minecraft.font, label,
                    0, 0,
                    0xFFFFFFFF, true);
            graphics.pose().popPose();
        }
    }

    public static void destroy() {
        if (atlasTarget != null) {
            atlasTarget.destroyBuffers();
            atlasTarget = null;
        }
        if (splatTileExportTarget != null) {
            splatTileExportTarget.destroyBuffers();
            splatTileExportTarget = null;
        }
        livePreview = null;
        outputCaptureKey = Long.MIN_VALUE;
        outputPreviousAge = 0.0f;
        nextOutputShotNumber = 1;
    }

    private static void ensureOutputSequence(
            long captureKey,
            float age) {
        if (outputCaptureKey != captureKey
                || age + 0.0001f < outputPreviousAge) {
            outputCaptureKey = captureKey;
            nextOutputShotNumber = 1;
        }
        outputPreviousAge = Math.max(0.0f, age);
    }

    private static boolean shouldRefreshHud(
            DarkBallCaptureVfx.FboPreviewCapture capture,
            DarkBallDensityFBO.PreviewFrame frame,
            CompositeSource compositeSource) {
        if (!HUD_ENABLED) {
            return false;
        }
        LivePreview previous = livePreview;
        return previous == null
                || atlasTarget == null
                || previous.capture().captureKey() != capture.captureKey()
                || capture.age() + 0.0001f < previous.capture().age()
                || capture.age() - previous.capture().age()
                >= LIVE_UPDATE_INTERVAL_SECONDS
                || previous.frame().targetGeneration()
                != frame.targetGeneration()
                || previous.frame().width() != frame.width()
                || previous.frame().height() != frame.height()
                || previous.compositeSource().texture()
                != compositeSource.texture()
                || previous.compositeSource().width()
                != compositeSource.width()
                || previous.compositeSource().height()
                != compositeSource.height();
    }

    private static String fitText(
            Minecraft minecraft,
            String text,
            int maximumWidth) {
        if (minecraft.font.width(text) <= maximumWidth) {
            return text;
        }
        String ellipsis = "...";
        int availableWidth = Math.max(
                0, maximumWidth - minecraft.font.width(ellipsis));
        int end = text.length();
        while (end > 0
                && minecraft.font.width(text.substring(0, end))
                > availableWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    private static String tileLabel(
            int index,
            DarkBallDensityFBO.PreviewFrame frame) {
        if (!tileAvailable(index, frame)) {
            return TILE_LABELS[index] + " [n/a]";
        }
        return TILE_LABELS[index];
    }

    private static boolean tileAvailable(
            int index,
            DarkBallDensityFBO.PreviewFrame frame) {
        return switch (index) {
            case 6, 14 -> frame.resolvedMaterialValid()
                    && frame.resolvedMaterialTexture() != 0;
            case 8 -> frame.proxyFrontValid()
                    && frame.proxyFrontTexture() != 0;
            case 10, 11 -> frame.splatDiagnosticValid()
                    && frame.splatDiagnosticTexture() != 0;
            default -> true;
        };
    }

    private static boolean buildAtlas(
            DarkBallDensityFBO.PreviewFrame frame,
            int compositeTexture) {
        ShaderInstance shader = ModShaders.DARK_BALL_FBO_PREVIEW;
        if (shader == null) {
            return false;
        }

        GlState state = GlState.capture();
        try {
            ensureAtlasTarget();
            if (atlasTarget == null) {
                return false;
            }

            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderSystem.colorMask(true, true, true, true);

            atlasTarget.clear(Minecraft.ON_OSX);
            atlasTarget.bindWrite(false);
            RenderSystem.viewport(0, 0, ATLAS_WIDTH, ATLAS_HEIGHT);

            drawAtlasTile(
                    shader,
                    compositeTexture,
                    0, 7);
            drawAtlasTile(shader, frame.rawMaterialTexture(), 1, 0);
            drawAtlasTile(shader, frame.rawMaterialTexture(), 2, 1);
            drawAtlasTile(shader, frame.rawMaterialTexture(), 3, 2);
            drawAtlasTile(shader, frame.rawMaterialTexture(), 4, 3);
            drawAtlasTile(shader, frame.rawMaterialTexture(), 5, 4);
            drawOptionalAtlasTile(
                    shader,
                    frame.resolvedMaterialTexture(),
                    frame.resolvedMaterialValid(),
                    frame.rawMaterialTexture(),
                    6,
                    0);
            drawAtlasTile(shader, frame.exactMaskTexture(), 7, 0);
            drawOptionalAtlasTile(
                    shader,
                    frame.proxyFrontTexture(),
                    frame.proxyFrontValid(),
                    frame.rawMaterialTexture(),
                    8,
                    5);
            drawAtlasTile(shader, frame.sceneDepthTexture(), 9, 6);
            drawOptionalAtlasTile(
                    shader,
                    frame.splatDiagnosticTexture(),
                    frame.splatDiagnosticValid(),
                    frame.rawMaterialTexture(),
                    10,
                    9);
            drawOptionalAtlasTile(
                    shader,
                    frame.splatDiagnosticTexture(),
                    frame.splatDiagnosticValid(),
                    frame.rawMaterialTexture(),
                    11,
                    10);
            DarkBallFboPreviewSchedule.UvCrop focusCrop =
                    magnifiedCrop(frame);
            drawAtlasTile(
                    shader,
                    compositeTexture,
                    12,
                    7,
                    focusCrop);
            drawAtlasTile(
                    shader,
                    frame.rawMaterialTexture(),
                    13,
                    0,
                    focusCrop);
            drawOptionalAtlasTile(
                    shader,
                    frame.resolvedMaterialTexture(),
                    frame.resolvedMaterialValid(),
                    frame.rawMaterialTexture(),
                    14,
                    0,
                    focusCrop);
            drawAtlasTile(
                    shader,
                    frame.exactMaskTexture(),
                    15,
                    0,
                    focusCrop);
            return true;
        } finally {
            try {
                shader.clear();
            } finally {
                state.restore();
            }
        }
    }

    private static void ensureAtlasTarget() {
        if (atlasTarget != null
                && atlasTarget.width == ATLAS_WIDTH
                && atlasTarget.height == ATLAS_HEIGHT) {
            return;
        }
        if (atlasTarget != null) {
            atlasTarget.destroyBuffers();
        }
        atlasTarget = new TextureTarget(
                ATLAS_WIDTH, ATLAS_HEIGHT,
                false, Minecraft.ON_OSX);
        atlasTarget.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        atlasTarget.setFilterMode(GL11.GL_LINEAR);
    }

    private static void drawAtlasTile(
            ShaderInstance shader,
            int textureId,
            int tileIndex,
            int previewMode) {
        drawAtlasTile(
                shader,
                textureId,
                tileIndex,
                previewMode,
                new DarkBallFboPreviewSchedule.UvCrop(
                        0.0f, 0.0f, 1.0f, 1.0f));
    }

    private static void drawAtlasTile(
            ShaderInstance shader,
            int textureId,
            int tileIndex,
            int previewMode,
            DarkBallFboPreviewSchedule.UvCrop sourceCrop) {
        if (textureId == 0) {
            return;
        }
        int column = tileIndex % ATLAS_COLUMNS;
        int row = tileIndex / ATLAS_COLUMNS;
        int x = column * (ATLAS_WIDTH / ATLAS_COLUMNS);
        int y = row * (ATLAS_HEIGHT / ATLAS_ROWS);
        drawTexture(
                shader,
                textureId,
                previewMode,
                x, y,
                ATLAS_WIDTH / ATLAS_COLUMNS,
                ATLAS_HEIGHT / ATLAS_ROWS,
                ATLAS_WIDTH,
                ATLAS_HEIGHT,
                sourceCrop);
    }

    private static void drawOptionalAtlasTile(
            ShaderInstance shader,
            int textureId,
            boolean available,
            int fallbackTextureId,
            int tileIndex,
            int previewMode) {
        drawOptionalAtlasTile(
                shader,
                textureId,
                available,
                fallbackTextureId,
                tileIndex,
                previewMode,
                new DarkBallFboPreviewSchedule.UvCrop(
                        0.0f, 0.0f, 1.0f, 1.0f));
    }

    private static void drawOptionalAtlasTile(
            ShaderInstance shader,
            int textureId,
            boolean available,
            int fallbackTextureId,
            int tileIndex,
            int previewMode,
            DarkBallFboPreviewSchedule.UvCrop sourceCrop) {
        boolean sourceAvailable = available && textureId != 0;
        drawAtlasTile(
                shader,
                sourceAvailable
                        ? textureId
                        : fallbackTextureId,
                tileIndex,
                sourceAvailable
                        ? previewMode
                        : 8,
                sourceAvailable
                        ? sourceCrop
                        : new DarkBallFboPreviewSchedule.UvCrop(
                        0.0f, 0.0f, 1.0f, 1.0f));
    }

    private static boolean drawHudAtlas(
            int textureId,
            int x, int y, int width, int height,
            int guiWidth, int guiHeight) {
        ShaderInstance shader = ModShaders.DARK_BALL_FBO_PREVIEW;
        if (textureId == 0 || shader == null) {
            return false;
        }
        GlState state = GlState.capture();
        try {
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderSystem.colorMask(true, true, true, true);
            drawTexture(
                    shader, textureId, 7,
                    x, y, width, height,
                    guiWidth, guiHeight,
                    new DarkBallFboPreviewSchedule.UvCrop(
                            0.0f, 0.0f, 1.0f, 1.0f));
            return true;
        } finally {
            try {
                shader.clear();
            } finally {
                state.restore();
            }
        }
    }

    private static void drawTexture(
            ShaderInstance shader,
            int textureId,
            int previewMode,
            int x, int y, int width, int height,
            int canvasWidth, int canvasHeight,
            DarkBallFboPreviewSchedule.UvCrop sourceCrop) {
        RenderSystem.setShaderTexture(0, textureId);
        shader.setSampler("Sampler0", textureId);
        Uniform mode = shader.getUniform("PreviewMode");
        if (mode != null) {
            mode.set(previewMode);
        }
        Uniform uvMinimum = shader.getUniform("PreviewUvMin");
        if (uvMinimum != null) {
            uvMinimum.set(sourceCrop.minU(), sourceCrop.minV());
        }
        Uniform uvMaximum = shader.getUniform("PreviewUvMax");
        if (uvMaximum != null) {
            uvMaximum.set(sourceCrop.maxU(), sourceCrop.maxV());
        }
        RenderSystem.setShader(() -> shader);

        float minimumX = x / (float) canvasWidth * 2.0f - 1.0f;
        float maximumX =
                (x + width) / (float) canvasWidth * 2.0f - 1.0f;
        float maximumY = 1.0f - y / (float) canvasHeight * 2.0f;
        float minimumY =
                1.0f - (y + height) / (float) canvasHeight * 2.0f;

        var builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(minimumX, minimumY, 0.0f).setUv(0.0f, 0.0f);
        builder.addVertex(maximumX, minimumY, 0.0f).setUv(1.0f, 0.0f);
        builder.addVertex(maximumX, maximumY, 0.0f).setUv(1.0f, 1.0f);
        builder.addVertex(minimumX, maximumY, 0.0f).setUv(0.0f, 1.0f);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static DarkBallFboPreviewSchedule.UvCrop magnifiedCrop(
            DarkBallDensityFBO.PreviewFrame frame) {
        return DarkBallFboPreviewSchedule.magnifiedCrop(
                frame.effectMinU(),
                frame.effectMinV(),
                frame.effectMaxU(),
                frame.effectMaxV(),
                frame.width(),
                frame.height(),
                ATLAS_WIDTH / ATLAS_COLUMNS,
                ATLAS_HEIGHT / ATLAS_ROWS,
                MAGNIFIED_CROP_PADDING_PIXELS);
    }

    private static boolean submitCaptureScreenshot(
            DarkBallCaptureVfx.FboPreviewCapture capture,
            DarkBallDensityFBO.PreviewFrame frame,
            CompositeSource compositeSource,
            DarkBallFboPreviewSchedule.CapturePoint point) {
        return submitCaptureScreenshot(
                capture,
                frame,
                compositeSource,
                CaptureDescriptor.phase(point),
                capture.age());
    }

    private static void submitRetainedPreActivationScreenshot(
            DarkBallCaptureVfx.FboPreviewCapture currentCapture,
            DarkBallFboPreviewSchedule.DirectRendererActivationCapture
                    transition) {
        LivePreview retained = livePreview;
        if (atlasTarget == null
                || retained == null
                || retained.capture().captureKey()
                != currentCapture.captureKey()
                || !"exact-mask".equals(
                retained.capture().presentationAuthority())) {
            Shadowedhearts.LOGGER.warn(
                    "[ShadowedHearts] Dark Ball FBO deep trace could not "
                            + "retain the pre-activation atlas for pokemon {} "
                            + "(authority={}, age={})",
                    currentCapture.pokemonId(),
                    retained == null
                            ? "unavailable"
                            : retained.capture().presentationAuthority(),
                    currentCapture.age());
            return;
        }
        DarkBallFboPreviewSchedule.DirectRendererActivationCapture
                retainedTransition = transition.withTargetAge(
                        retained.capture().age());
        submitCaptureScreenshot(
                retained.capture(),
                retained.frame(),
                retained.compositeSource(),
                CaptureDescriptor.transition(retainedTransition),
                retained.capture().age());
    }

    private static void submitDirectRendererActivationScreenshot(
            DarkBallCaptureVfx.FboPreviewCapture capture,
            DarkBallDensityFBO.PreviewFrame frame,
            CompositeSource compositeSource,
            DarkBallFboPreviewSchedule.DirectRendererActivationCapture
                    transition) {
        submitCaptureScreenshot(
                capture,
                frame,
                compositeSource,
                CaptureDescriptor.transition(transition),
                capture.age());
    }

    private static boolean submitCaptureScreenshot(
            DarkBallCaptureVfx.FboPreviewCapture capture,
            DarkBallDensityFBO.PreviewFrame frame,
            CompositeSource compositeSource,
            CaptureDescriptor descriptor,
            float actualAge) {
        if (atlasTarget == null) {
            return false;
        }
        int shotNumber = nextOutputShotNumber;
        String filename;
        if (descriptor.phasePoint() != null) {
            filename = DarkBallFboPreviewSchedule.captureFilename(
                    SESSION_TOKEN,
                    capture.pokemonId(),
                    capture.ballId(),
                    CAPTURE_TRACKER.runOrdinal(),
                    capture.renderPath(),
                    capture.presentationAuthority(),
                    shotNumber,
                    descriptor.slug(),
                    descriptor.phasePoint(),
                    actualAge);
        } else {
            filename =
                    DarkBallFboPreviewSchedule.transitionCaptureFilename(
                            SESSION_TOKEN,
                            capture.pokemonId(),
                            capture.ballId(),
                            CAPTURE_TRACKER.runOrdinal(),
                            capture.renderPath(),
                            capture.presentationAuthority(),
                            shotNumber,
                            descriptor.transition(),
                            actualAge);
        }
        String manifestFilename =
                DarkBallFboPreviewSchedule.manifestFilename(
                        SESSION_TOKEN,
                        capture.pokemonId(),
                        capture.ballId(),
                        CAPTURE_TRACKER.runOrdinal());
        CaptureAttempt attempt = new CaptureAttempt(
                filename,
                manifestFilename,
                shotNumber,
                CAPTURE_TRACKER.runOrdinal(),
                CAPTURE_TRACKER.capturePointCount(),
                compositeSource.width(),
                compositeSource.height(),
                capture,
                frame,
                descriptor,
                actualAge);
        GlState screenshotState = GlState.capture();
        try {
            if (!ensureScreenshotArtifactParent(filename)) {
                return false;
            }
            Screenshot.grab(
                    Minecraft.getInstance().gameDirectory,
                    filename,
                    atlasTarget,
                    result -> reportScreenshotResult(attempt, result));
            queueSplatTileSeries(attempt);
            nextOutputShotNumber++;
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Queued Dark Ball FBO deep-trace atlas "
                            + "{} (shot={}, capture={}, phase={}, sample={}/{}, "
                            + "targetAge={}, actualAge={}, manifest={}, "
                            + "layout=final/raw/body/siphon | storage/"
                            + "surface/resolved/mask | proxy/scene-depth/"
                            + "splat-identities/splat-overlap | focus-final/"
                            + "focus-raw/focus-resolved/focus-mask; "
                            + "individualSplatTiles={})",
                    filename,
                    shotNumber,
                    descriptor.label(),
                    descriptor.phaseLabel(actualAge),
                    descriptor.sampleNumber(),
                    descriptor.sampleCount(),
                    descriptor.targetAge(),
                    actualAge,
                    manifestFilename,
                    frame.splatDiagnosticValid());
            return true;
        } catch (Throwable failure) {
            Shadowedhearts.LOGGER.error(
                    "[ShadowedHearts] Failed to queue Dark Ball FBO "
                            + "deep-trace atlas {}",
                    filename,
                    failure);
            return false;
        } finally {
            screenshotState.restore();
        }
    }

    /**
     * Saves the two pre-merge splat diagnostics as full-resolution image
     * sequences synchronized to the atlas shot number. The source diagnostic
     * remains unavailable during exact-mask/pending frames, so those shots
     * intentionally produce no misleading stale tile files.
     */
    private static void queueSplatTileSeries(CaptureAttempt atlasAttempt) {
        DarkBallDensityFBO.PreviewFrame frame = atlasAttempt.frame();
        if (!frame.splatDiagnosticValid()
                || frame.splatDiagnosticTexture() == 0
                || frame.width() <= 0
                || frame.height() <= 0) {
            return;
        }
        ShaderInstance shader = ModShaders.DARK_BALL_FBO_PREVIEW;
        if (shader == null
                || !ensureSplatTileExportTarget(
                frame.width(), frame.height())) {
            return;
        }

        queueSplatTileScreenshot(
                atlasAttempt,
                shader,
                11,
                "pre-merge splat identities",
                "ids",
                9);
        queueSplatTileScreenshot(
                atlasAttempt,
                shader,
                12,
                "pre-merge splat overlap",
                "overlap",
                10);
    }

    private static void queueSplatTileScreenshot(
            CaptureAttempt atlasAttempt,
            ShaderInstance shader,
            int tileNumber,
            String tileLabel,
            String filenameSlug,
            int previewMode) {
        String filename = tileSeriesFilename(
                atlasAttempt.filename(),
                tileNumber,
                filenameSlug);
        GlState tileState = GlState.capture();
        try {
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderSystem.colorMask(true, true, true, true);

            splatTileExportTarget.clear(Minecraft.ON_OSX);
            splatTileExportTarget.bindWrite(false);
            RenderSystem.viewport(
                    0, 0,
                    splatTileExportTarget.width,
                    splatTileExportTarget.height);
            drawTexture(
                    shader,
                    atlasAttempt.frame().splatDiagnosticTexture(),
                    previewMode,
                    0, 0,
                    splatTileExportTarget.width,
                    splatTileExportTarget.height,
                    splatTileExportTarget.width,
                    splatTileExportTarget.height,
                    new DarkBallFboPreviewSchedule.UvCrop(
                            0.0f, 0.0f, 1.0f, 1.0f));

            SplatTileCaptureAttempt tileAttempt =
                    new SplatTileCaptureAttempt(
                            atlasAttempt,
                            filename,
                            tileNumber,
                            tileLabel,
                            previewMode);
            Screenshot.grab(
                    Minecraft.getInstance().gameDirectory,
                    filename,
                    splatTileExportTarget,
                    result -> reportSplatTileScreenshotResult(
                            tileAttempt, result));
        } catch (Throwable failure) {
            Shadowedhearts.LOGGER.error(
                    "[ShadowedHearts] Failed to queue Dark Ball FBO "
                            + "tile {} series image {}",
                    tileNumber,
                    filename,
                    failure);
        } finally {
            try {
                shader.clear();
            } finally {
                tileState.restore();
            }
        }
    }

    private static boolean ensureSplatTileExportTarget(
            int width,
            int height) {
        if (splatTileExportTarget != null
                && splatTileExportTarget.width == width
                && splatTileExportTarget.height == height) {
            return true;
        }
        if (splatTileExportTarget != null) {
            splatTileExportTarget.destroyBuffers();
            splatTileExportTarget = null;
        }
        try {
            splatTileExportTarget = new TextureTarget(
                    width, height,
                    false, Minecraft.ON_OSX);
            splatTileExportTarget.setClearColor(
                    0.0f, 0.0f, 0.0f, 1.0f);
            splatTileExportTarget.setFilterMode(GL11.GL_NEAREST);
            return true;
        } catch (RuntimeException allocationFailure) {
            if (splatTileExportTarget != null) {
                splatTileExportTarget.destroyBuffers();
                splatTileExportTarget = null;
            }
            Shadowedhearts.LOGGER.error(
                    "[ShadowedHearts] Failed to allocate the {}x{} Dark Ball "
                            + "pre-merge splat tile export target",
                    width,
                    height,
                    allocationFailure);
            return false;
        }
    }

    private static String tileSeriesFilename(
            String atlasFilename,
            int tileNumber,
            String slug) {
        String suffix = ".png";
        String stem = atlasFilename.endsWith(suffix)
                ? atlasFilename.substring(
                0, atlasFilename.length() - suffix.length())
                : atlasFilename;
        return stem
                + String.format(
                Locale.ROOT,
                "-t%02d-%s.png",
                tileNumber,
                slug);
    }

    private static void reportNewlySkippedCaptures(
            DarkBallCaptureVfx.FboPreviewCapture capture,
            List<DarkBallFboPreviewSchedule.CapturePoint>
                    skippedCaptures) {
        if (skippedCaptures.isEmpty()) {
            return;
        }
        String manifestFilename =
                DarkBallFboPreviewSchedule.manifestFilename(
                        SESSION_TOKEN,
                        capture.pokemonId(),
                        capture.ballId(),
                        CAPTURE_TRACKER.runOrdinal());
        List<JsonObject> manifestEntries =
                new ArrayList<>(skippedCaptures.size());
        for (DarkBallFboPreviewSchedule.CapturePoint point
                : skippedCaptures) {
            Shadowedhearts.LOGGER.warn(
                    "[ShadowedHearts] Skipping stale Dark Ball FBO "
                            + "deep-trace atlas for pokemon {} "
                            + "(shot={}/{}, phase={}, sample={}/{}, "
                            + "targetAge={}, actualAge={}, "
                            + "maximumLateness={})",
                    capture.pokemonId(),
                    point.shotNumber(),
                    CAPTURE_TRACKER.capturePointCount(),
                    point.phase().label(),
                    point.sampleNumber(),
                    point.sampleCount(),
                    point.targetAge(),
                    capture.age(),
                    DarkBallFboPreviewSchedule
                            .MAX_CAPTURE_LATENESS_SECONDS);
            manifestEntries.add(baseManifestEntry(
                    capture,
                    CaptureDescriptor.phase(point),
                    capture.age(),
                    CAPTURE_TRACKER.runOrdinal(),
                    CAPTURE_TRACKER.capturePointCount(),
                    "skipped-stale",
                    null));
        }
        appendManifestAsync(manifestFilename, manifestEntries);
    }

    private static void reportScreenshotResult(
            CaptureAttempt attempt,
            Component result) {
        String resultKey = screenshotResultKey(result);
        String resultText =
                result != null ? result.getString() : "unknown";
        String status = switch (resultKey) {
            case "screenshot.success" -> "saved";
            case "screenshot.failure" -> "save-failed";
            default -> "screenshot-result-unknown";
        };
        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball FBO deep-trace atlas result "
                        + "for {} (pokemon={}, ball={}, shot={}, phase={}, "
                        + "sample={}/{}): {}",
                attempt.filename(),
                attempt.capture().pokemonId(),
                attempt.capture().ballId(),
                attempt.shotNumber(),
                attempt.descriptor().phaseLabel(attempt.actualAge()),
                attempt.descriptor().sampleNumber(),
                attempt.descriptor().sampleCount(),
                resultText);
        JsonObject entry = baseManifestEntry(
                attempt.capture(),
                attempt.descriptor(),
                attempt.actualAge(),
                attempt.runOrdinal(),
                attempt.scheduledPhaseShotCount(),
                status,
                attempt.shotNumber());
        entry.addProperty("screenshotFilename", attempt.filename());
        entry.addProperty("screenshotMessageKey", resultKey);
        entry.addProperty(
                "screenshotSaved",
                "screenshot.success".equals(resultKey));
        entry.addProperty("result", resultText);
        entry.addProperty(
                "completedAtEpochMillis",
                System.currentTimeMillis());
        entry.addProperty("atlasWidth", ATLAS_WIDTH);
        entry.addProperty("atlasHeight", ATLAS_HEIGHT);
        entry.addProperty(
                "densityTargetWidth",
                attempt.frame().width());
        entry.addProperty(
                "densityTargetHeight",
                attempt.frame().height());
        entry.addProperty(
                "mainFramebufferWidth",
                attempt.mainFramebufferWidth());
        entry.addProperty(
                "mainFramebufferHeight",
                attempt.mainFramebufferHeight());
        entry.addProperty(
                "sourceFrameSerial",
                attempt.frame().frameSerial());
        entry.addProperty(
                "sourceTargetGeneration",
                attempt.frame().targetGeneration());
        entry.addProperty(
                "rawCompositeSource",
                attempt.frame().rawCompositeSource());
        entry.addProperty(
                "reducedCompositeUsed",
                attempt.frame().reducedCompositeUsed());
        entry.addProperty(
                "reducedCompositePolicy",
                attempt.frame().reducedCompositePolicy());
        entry.addProperty(
                "projectedBodyAreaFraction",
                attempt.frame().projectedBodyAreaFraction());
        entry.addProperty(
                "mediumReducedCompositeAreaThreshold",
                attempt.frame()
                        .mediumReducedCompositeAreaThreshold());
        DarkBallFboPreviewSchedule.UvCrop focusCrop =
                magnifiedCrop(attempt.frame());
        entry.addProperty("focusCropMinU", focusCrop.minU());
        entry.addProperty("focusCropMinV", focusCrop.minV());
        entry.addProperty("focusCropMaxU", focusCrop.maxU());
        entry.addProperty("focusCropMaxV", focusCrop.maxV());
        entry.add(
                "tiles",
                tileManifest(
                        attempt.capture(),
                        attempt.frame(),
                        attempt.mainFramebufferWidth(),
                        attempt.mainFramebufferHeight()));
        appendManifest(attempt.manifestFilename(), entry);

        Minecraft minecraft = Minecraft.getInstance();
        int shotNumber = attempt.shotNumber();
        boolean finalScheduledPhaseCapture =
                attempt.descriptor().phasePoint() != null
                        && attempt.descriptor().phasePoint().shotNumber()
                        == attempt.scheduledPhaseShotCount();
        if (minecraft != null
                && (shotNumber == 1
                || shotNumber % 5 == 0
                || finalScheduledPhaseCapture)) {
            minecraft.execute(() -> {
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(
                            Component.literal("[ShadowedHearts] ")
                                    .append(result != null
                                            ? result
                                            : Component.literal(
                                                    resultText)),
                            false);
                }
            });
        }
    }

    private static void reportSplatTileScreenshotResult(
            SplatTileCaptureAttempt attempt,
            Component result) {
        String resultKey = screenshotResultKey(result);
        String resultText =
                result != null ? result.getString() : "unknown";
        String status = switch (resultKey) {
            case "screenshot.success" -> "tile-saved";
            case "screenshot.failure" -> "tile-save-failed";
            default -> "tile-screenshot-result-unknown";
        };
        CaptureAttempt atlasAttempt = attempt.atlasAttempt();
        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball pre-merge tile {} result for {} "
                        + "(shot={}, phase={}, sample={}/{}): {}",
                attempt.tileNumber(),
                attempt.filename(),
                atlasAttempt.shotNumber(),
                atlasAttempt.descriptor().phaseLabel(
                        atlasAttempt.actualAge()),
                atlasAttempt.descriptor().sampleNumber(),
                atlasAttempt.descriptor().sampleCount(),
                resultText);

        JsonObject entry = baseManifestEntry(
                atlasAttempt.capture(),
                atlasAttempt.descriptor(),
                atlasAttempt.actualAge(),
                atlasAttempt.runOrdinal(),
                atlasAttempt.scheduledPhaseShotCount(),
                status,
                atlasAttempt.shotNumber());
        entry.addProperty("artifactKind", "individual-tile-series");
        entry.addProperty(
                "manifestArtifactSortKey",
                String.format(
                        Locale.ROOT,
                        "%05d-t%02d",
                        atlasAttempt.shotNumber(),
                        attempt.tileNumber()));
        entry.addProperty(
                "parentAtlasScreenshotFilename",
                atlasAttempt.filename());
        entry.addProperty("tileNumber", attempt.tileNumber());
        entry.addProperty("tileLabel", attempt.tileLabel());
        entry.addProperty("previewMode", attempt.previewMode());
        entry.addProperty("screenshotFilename", attempt.filename());
        entry.addProperty("screenshotMessageKey", resultKey);
        entry.addProperty(
                "screenshotSaved",
                "screenshot.success".equals(resultKey));
        entry.addProperty("result", resultText);
        entry.addProperty(
                "completedAtEpochMillis",
                System.currentTimeMillis());
        entry.addProperty(
                "sourceFrameSerial",
                atlasAttempt.frame().frameSerial());
        entry.addProperty(
                "sourceTargetGeneration",
                atlasAttempt.frame().targetGeneration());
        entry.addProperty(
                "sourceWidth",
                atlasAttempt.frame().width());
        entry.addProperty(
                "sourceHeight",
                atlasAttempt.frame().height());
        appendManifest(atlasAttempt.manifestFilename(), entry);
    }

    private static String screenshotResultKey(Component result) {
        if (result != null
                && result.getContents()
                instanceof TranslatableContents contents) {
            return contents.getKey();
        }
        return "unknown";
    }

    private static JsonObject baseManifestEntry(
            DarkBallCaptureVfx.FboPreviewCapture capture,
            CaptureDescriptor descriptor,
            float actualAge,
            int runOrdinal,
            int scheduledPhaseShotCount,
            String status,
            Integer shotNumber) {
        JsonObject entry = new JsonObject();
        entry.addProperty("schemaVersion", 9);
        entry.addProperty("status", status);
        entry.addProperty("manifestRecordOrder", "completion-order");
        entry.addProperty("manifestSortKey", "shotNumber");
        entry.addProperty("sessionToken", SESSION_TOKEN);
        entry.addProperty("captureKey", capture.captureKey());
        entry.addProperty("pokemonId", capture.pokemonId());
        entry.addProperty("ballId", capture.ballId());
        entry.addProperty("runOrdinal", runOrdinal);
        entry.addProperty(
                "captureDirectory",
                DarkBallFboPreviewSchedule.captureDirectoryName(
                        SESSION_TOKEN,
                        capture.pokemonId(),
                        capture.ballId(),
                        runOrdinal));
        if (shotNumber == null) {
            entry.add("shotNumber", JsonNull.INSTANCE);
        } else {
            entry.addProperty("shotNumber", shotNumber);
        }
        entry.addProperty(
                "scheduledPhaseShotCount",
                scheduledPhaseShotCount);
        entry.addProperty(
                "directRendererActivationEventCapacity",
                DarkBallFboPreviewSchedule
                        .DirectRendererActivationCaptureKind
                        .values().length);
        // Retain the original field as a compatibility alias for existing
        // diagnostic-manifest consumers.
        entry.addProperty(
                "meshActivationEventCapacity",
                DarkBallFboPreviewSchedule
                        .DirectRendererActivationCaptureKind
                        .values().length);
        entry.addProperty("renderPath", capture.renderPath());
        entry.addProperty("selectionPath", capture.renderPath());
        entry.addProperty(
                "vfxDurationSeconds",
                DarkBallCaptureVfx.VFX_END);
        entry.addProperty(
                "choreographyDeltaSeconds",
                capture.choreographyDeltaSeconds());
        entry.addProperty(
                "turbulenceAmplitudeBlend",
                capture.turbulenceAmplitudeBlend());
        entry.addProperty(
                "turbulenceComplexityBlend",
                capture.turbulenceComplexityBlend());
        entry.addProperty("spikeFlowTime", capture.spikeFlowTime());
        entry.addProperty("siphonProgress", capture.siphonProgress());
        entry.addProperty(
                "bodyReleaseFront", capture.bodyReleaseFront());
        entry.addProperty(
                "bodyPresentationGate",
                capture.bodyPresentationGate());
        entry.addProperty("finalCollapse", capture.finalCollapse());
        entry.addProperty("siphonRootGate", capture.siphonRootGate());
        entry.addProperty("siphonTipGate", capture.siphonTipGate());
        addNullableNumber(
                entry, "voxelReadyAgeSeconds",
                capture.voxelReadyAge());
        addNullableNumber(
                entry, "voxelBuildMillis",
                capture.voxelBuildMillis());
        addNullableNumber(
                entry, "surfaceReadyAgeSeconds",
                capture.surfaceReadyAge());
        addNullableNumber(
                entry, "surfaceBuildMillis",
                capture.surfaceBuildMillis());
        addNullableNumber(
                entry, "directRendererActivationAgeSeconds",
                capture.directRendererActivationAge());
        addNullableNumber(
                entry, "splatRequestedBudget",
                capture.splatRequestedBudget());
        addNullableNumber(
                entry, "splatSampleCount",
                capture.splatSampleCount());
        addNullableBoolean(
                entry, "splatBodySubmittedCurrentFrame",
                capture.splatBodySubmittedCurrentFrame());
        addNullableNumber(
                entry, "splatLastSectionBodyOwnership",
                capture.splatLastSectionBodyOwnership());
        addNullableBoolean(
                entry, "splatUploadOccurredCurrentFrame",
                capture.splatUploadOccurredCurrentFrame());
        addNullableNumber(
                entry, "splatUploadGeneration",
                capture.splatUploadGeneration());
        addNullableNumber(
                entry, "splatPreparationUploadCpuMicros",
                capture.splatPreparationUploadCpuMicros());
        entry.addProperty(
                "splatPreparationUploadTimingScope",
                capture.splatPreparationUploadCpuMicros() == null
                        ? "not-available"
                        : Boolean.TRUE.equals(
                        capture.splatUploadOccurredCurrentFrame())
                        ? "current-frame-one-time-upload"
                        : "historical-latest-successful-upload");
        addNullableNumber(
                entry, "splatDrawCpuMicros",
                capture.splatDrawCpuMicros());
        entry.addProperty(
                "splatDrawSubmittedCurrentFrame",
                Boolean.TRUE.equals(
                        capture.splatBodySubmittedCurrentFrame()));
        entry.addProperty(
                "splatDrawTimingState",
                splatDrawTimingState(capture));
        addNullableNumber(
                entry, "splatResolveCpuMicros",
                capture.splatResolveCpuMicros());
        entry.addProperty(
                "splatResolveSubmittedCurrentFrame",
                capture.splatResolveCpuMicros() != null);
        entry.addProperty(
                "splatResolveTimingState",
                splatResolveTimingState(capture));
        addNullableNumber(
                entry, "siphonBuildCpuMicros",
                capture.siphonBuildCpuMicros());
        addNullableNumber(
                entry, "siphonDrawCpuMicros",
                capture.siphonDrawCpuMicros());
        addNullableNumber(
                entry, "depthRestoreCpuMicros",
                capture.depthRestoreCpuMicros());
        addNullableNumber(
                entry, "splatDrawGpuMicros",
                capture.splatDrawGpuMicros());
        addNullableNumber(
                entry, "splatResolveGpuMicros",
                capture.splatResolveGpuMicros());
        addNullableNumber(
                entry, "siphonDrawGpuMicros",
                capture.siphonDrawGpuMicros());
        addNullableNumber(
                entry, "depthRestoreGpuMicros",
                capture.depthRestoreGpuMicros());
        entry.addProperty(
                "gpuTimingMode",
                "asynchronous-time-elapsed-query");
        if (capture.quality() == null) {
            entry.add("quality", JsonNull.INSTANCE);
        } else {
            entry.addProperty("quality", capture.quality());
        }
        addNullableNumber(
                entry, "rawTargetWidth",
                capture.rawTargetWidth());
        addNullableNumber(
                entry, "rawTargetHeight",
                capture.rawTargetHeight());
        entry.addProperty(
                "presentationAuthority",
                capture.presentationAuthority());
        entry.addProperty(
                "presentationActivationBlend",
                capture.presentationActivationBlend());
        entry.addProperty("sweepMode", capture.sweepMode());
        entry.addProperty("sweepPath", capture.sweepPath());
        entry.addProperty(
                "sweepStrength", capture.sweepStrength());
        entry.addProperty(
                "sweepNodeCount", capture.sweepNodeCount());
        entry.addProperty(
                "sweepSliceCount", capture.sweepSliceCount());
        entry.addProperty(
                "sweepPinchCenter", capture.sweepPinchCenter());
        entry.addProperty(
                "sweepPinchStrength",
                capture.sweepPinchStrength());
        entry.addProperty(
                "sweepMinimumSectionScale",
                capture.sweepMinimumSectionScale());
        entry.addProperty(
                "sweepMaximumSectionScale",
                capture.sweepMaximumSectionScale());
        entry.addProperty(
                "sweepAuthority", capture.sweepAuthority());
        addNullableBoolean(
                entry,
                "bridgePresentationActive",
                capture.bridgePresentationActive());
        addNullableNumber(
                entry,
                "bridgeMaterialActivation",
                capture.bridgeMaterialActivation());
        addNullableNumber(
                entry,
                "bridgeRetractionProgress",
                capture.bridgeRetractionProgress());
        addNullableNumber(
                entry,
                "bridgeRetractionFront",
                capture.bridgeRetractionFront());
        addNullableNumber(
                entry,
                "bridgeFirstActiveRing",
                capture.bridgeFirstActiveRing());
        addNullableNumber(
                entry,
                "bridgeLastActiveRing",
                capture.bridgeLastActiveRing());
        addNullableNumber(
                entry,
                "bridgeActiveRingCount",
                capture.bridgeActiveRingCount());
        addNullableNumber(
                entry,
                "bridgePathLength",
                capture.bridgePathLength());
        addNullableNumber(
                entry,
                "bridgePathStartRadius",
                capture.bridgePathStartRadius());
        addNullableNumber(
                entry,
                "bridgePathShoulderRadius",
                capture.bridgePathShoulderRadius());
        addNullableNumber(
                entry,
                "bridgeInletRadius",
                capture.bridgeInletRadius());
        addNullableNumber(
                entry,
                "bridgeProjectedPixelAreaFraction",
                capture.bridgeProjectedPixelAreaFraction());
        addNullableNumber(
                entry,
                "projectedPixelAreaFraction",
                capture.projectedPixelAreaFraction());
        addNullableBoolean(
                entry,
                "reducedCompositeUsed",
                capture.reducedCompositeUsed());
        entry.addProperty("captureKind", descriptor.kind());
        entry.addProperty("captureSlug", descriptor.slug());
        if (descriptor.transition() != null) {
            entry.addProperty(
                    "directRendererActivationDetectedAgeSeconds",
                    descriptor.transition().activationAge());
            entry.addProperty(
                    "directRendererActivationObservedPreviousAgeSeconds",
                    descriptor.transition().observedPreviousAge());
            entry.addProperty(
                    "meshActivationDetectedAgeSeconds",
                    descriptor.transition().activationAge());
            entry.addProperty(
                    "meshActivationObservedPreviousAgeSeconds",
                    descriptor.transition().observedPreviousAge());
        } else {
            entry.add(
                    "directRendererActivationDetectedAgeSeconds",
                    JsonNull.INSTANCE);
            entry.add(
                    "directRendererActivationObservedPreviousAgeSeconds",
                    JsonNull.INSTANCE);
            entry.add(
                    "meshActivationDetectedAgeSeconds",
                    JsonNull.INSTANCE);
            entry.add(
                    "meshActivationObservedPreviousAgeSeconds",
                    JsonNull.INSTANCE);
        }
        if (descriptor.phasePoint() != null) {
            DarkBallFboPreviewSchedule.CapturePoint point =
                    descriptor.phasePoint();
            entry.addProperty(
                    "scheduledPhaseShotNumber",
                    point.shotNumber());
            entry.addProperty(
                    "scheduledPhaseIndex",
                    point.phase().index() + 1);
            entry.addProperty(
                    "scheduledPhaseSlug",
                    point.phase().slug());
            entry.addProperty(
                    "scheduledPhaseLabel",
                    point.phase().label());
            entry.addProperty(
                    "phaseStartAgeSeconds",
                    point.phase().startAge());
            entry.addProperty(
                    "phaseEndAgeSeconds",
                    point.phase().endAge());
            entry.addProperty(
                    "phaseFraction",
                    (point.sampleIndex() + 0.5f)
                            / point.sampleCount());
        } else {
            entry.add("scheduledPhaseShotNumber", JsonNull.INSTANCE);
            entry.add("scheduledPhaseIndex", JsonNull.INSTANCE);
            entry.add("scheduledPhaseSlug", JsonNull.INSTANCE);
            entry.add("scheduledPhaseLabel", JsonNull.INSTANCE);
            entry.add("phaseStartAgeSeconds", JsonNull.INSTANCE);
            entry.add("phaseEndAgeSeconds", JsonNull.INSTANCE);
            entry.add("phaseFraction", JsonNull.INSTANCE);
        }
        entry.addProperty(
                "actualPhaseLabel",
                DarkBallFboPreviewSchedule.phaseAt(actualAge));
        entry.addProperty(
                "phaseSampleNumber",
                descriptor.sampleNumber());
        entry.addProperty(
                "phaseSampleCount",
                descriptor.sampleCount());
        entry.addProperty(
                "scheduledAgeSeconds",
                descriptor.targetAge());
        entry.addProperty("actualAgeSeconds", actualAge);
        entry.addProperty(
                "latenessSeconds",
                actualAge - descriptor.targetAge());
        return entry;
    }

    private static void addNullableNumber(
            JsonObject target,
            String name,
            Number value) {
        if (value == null) {
            target.add(name, JsonNull.INSTANCE);
        } else {
            target.addProperty(name, value);
        }
    }

    private static String splatDrawTimingState(
            DarkBallCaptureVfx.FboPreviewCapture capture) {
        if (!"splat".equals(capture.renderPath())) {
            return "not-applicable";
        }
        if (Boolean.TRUE.equals(
                capture.splatBodySubmittedCurrentFrame())) {
            return capture.splatDrawCpuMicros() == null
                    ? "submitted-timing-unavailable"
                    : "submitted-current-frame";
        }
        if (capture.splatDrawCpuMicros() != null) {
            return "submitted-current-frame";
        }
        if (capture.splatLastSectionBodyOwnership() != null
                && capture.splatLastSectionBodyOwnership()
                <= DarkBallCaptureVfx.SPLAT_BODY_ACTIVITY_EPSILON) {
            return "skipped-body-retired";
        }
        if (capture.splatSampleCount() == null) {
            return "skipped-renderer-not-ready";
        }
        return "skipped-no-body-submission";
    }

    private static String splatResolveTimingState(
            DarkBallCaptureVfx.FboPreviewCapture capture) {
        if (!"splat".equals(capture.renderPath())) {
            return "not-applicable";
        }
        if (capture.splatResolveCpuMicros() != null) {
            return "submitted-current-frame";
        }
        if (capture.splatDrawCpuMicros() == null) {
            return splatDrawTimingState(capture);
        }
        return "skipped-after-draw-failure";
    }

    private static void addNullableBoolean(
            JsonObject target,
            String name,
            Boolean value) {
        if (value == null) {
            target.add(name, JsonNull.INSTANCE);
        } else {
            target.addProperty(name, value);
        }
    }

    private static JsonArray tileManifest(
            DarkBallCaptureVfx.FboPreviewCapture capture,
            DarkBallDensityFBO.PreviewFrame frame,
            int mainFramebufferWidth,
            int mainFramebufferHeight) {
        JsonArray tiles = new JsonArray();
        DarkBallFboPreviewSchedule.UvCrop focusCrop =
                magnifiedCrop(frame);
        for (int index = 0; index < TILE_LABELS.length; index++) {
            boolean available = tileAvailable(index, frame);
            int expectedWidth = tileSourceWidth(
                    index, frame, mainFramebufferWidth);
            int expectedHeight = tileSourceHeight(
                    index, frame, mainFramebufferHeight);
            JsonObject tile = new JsonObject();
            tile.addProperty("tileNumber", index + 1);
            tile.addProperty("label", TILE_LABELS[index]);
            tile.addProperty("semantic", TILE_SEMANTICS[index]);
            tile.addProperty("sourceKind", tileSourceKind(index));
            tile.addProperty(
                    "previewMode",
                    available ? TILE_PREVIEW_MODES[index] : 8);
            tile.addProperty("available", available);
            tile.addProperty(
                    "sourceWidth",
                    available ? expectedWidth : 0);
            tile.addProperty(
                    "sourceHeight",
                    available ? expectedHeight : 0);
            tile.addProperty("expectedSourceWidth", expectedWidth);
            tile.addProperty("expectedSourceHeight", expectedHeight);
            boolean magnified = index >= MAGNIFIED_TILE_START;
            tile.addProperty("magnifiedEffectCrop", magnified);
            if (magnified) {
                tile.addProperty("cropMinU", focusCrop.minU());
                tile.addProperty("cropMinV", focusCrop.minV());
                tile.addProperty("cropMaxU", focusCrop.maxU());
                tile.addProperty("cropMaxV", focusCrop.maxV());
            }
            if (!available) {
                tile.addProperty(
                        "unavailableReason",
                        unavailableReason(index, capture));
            } else if (index == 7 || index == 15) {
                tile.addProperty(
                        "note",
                        "persistent frozen reference; not a visible "
                                + "contribution by itself");
            } else if (index == 9) {
                tile.addProperty(
                        "note",
                        "full-scene depth copied before the Dark Ball pass");
            } else if (index == 10) {
                tile.addProperty(
                        "note",
                        "stable colors identify individual submitted splats; "
                                + "mixed colors indicate footprint overlap");
            } else if (index == 11) {
                tile.addProperty(
                        "note",
                        "continuous heat scale: cyan is low overlap, yellow "
                                + "is moderate, red is heavy overlap");
            }
            tiles.add(tile);
        }
        return tiles;
    }

    private static String tileSourceKind(int tileIndex) {
        return switch (tileIndex) {
            case 0, 12 -> "main-framebuffer";
            case 10, 11 -> "pre-merge-surface-splat-diagnostic";
            default -> "dark-ball-density-target";
        };
    }

    private static int tileSourceWidth(
            int tileIndex,
            DarkBallDensityFBO.PreviewFrame frame,
            int mainFramebufferWidth) {
        return switch (tileIndex) {
            case 0, 12 -> mainFramebufferWidth;
            default -> frame.width();
        };
    }

    private static int tileSourceHeight(
            int tileIndex,
            DarkBallDensityFBO.PreviewFrame frame,
            int mainFramebufferHeight) {
        return switch (tileIndex) {
            case 0, 12 -> mainFramebufferHeight;
            default -> frame.height();
        };
    }

    private static String unavailableReason(
            int tileIndex,
            DarkBallCaptureVfx.FboPreviewCapture capture) {
        return switch (tileIndex) {
            case 6, 14 -> "edge-resolve-not-produced";
            case 8 -> "proxy-front-not-active";
            case 10, 11 -> "pre-merge-splat-diagnostic-not-produced";
            default -> "source-not-available";
        };
    }

    private static void appendManifest(
            String manifestFilename,
            JsonObject entry) {
        Path manifestPath = manifestPath(manifestFilename);
        if (manifestPath != null) {
            appendManifestEntries(manifestPath, List.of(entry));
        }
    }

    private static void appendManifestAsync(
            String manifestFilename,
            List<JsonObject> entries) {
        Path manifestPath = manifestPath(manifestFilename);
        if (manifestPath == null || entries.isEmpty()) {
            return;
        }
        List<JsonObject> immutableEntries = List.copyOf(entries);
        try {
            CompletableFuture.runAsync(
                    () -> appendManifestEntries(
                            manifestPath, immutableEntries),
                    Util.ioPool());
        } catch (RuntimeException failure) {
            Shadowedhearts.LOGGER.error(
                    "[ShadowedHearts] Failed to queue Dark Ball FBO "
                            + "deep-trace manifest append {}",
                    manifestPath,
                    failure);
        }
    }

    private static Path manifestPath(String manifestFilename) {
        return screenshotArtifactPath(manifestFilename);
    }

    private static boolean ensureScreenshotArtifactParent(
            String relativeArtifactPath) {
        Path artifactPath = screenshotArtifactPath(relativeArtifactPath);
        if (artifactPath == null || artifactPath.getParent() == null) {
            return false;
        }
        try {
            Files.createDirectories(artifactPath.getParent());
            return true;
        } catch (IOException | SecurityException failure) {
            Shadowedhearts.LOGGER.error(
                    "[ShadowedHearts] Failed to create Dark Ball FBO capture "
                            + "directory for {}",
                    artifactPath,
                    failure);
            return false;
        }
    }

    private static Path screenshotArtifactPath(
            String relativeArtifactPath) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null
                || relativeArtifactPath == null
                || relativeArtifactPath.isBlank()) {
            return null;
        }
        Path screenshotRoot = minecraft.gameDirectory.toPath()
                .resolve("screenshots")
                .normalize();
        Path artifactPath = screenshotRoot
                .resolve(relativeArtifactPath)
                .normalize();
        if (!artifactPath.startsWith(screenshotRoot)) {
            Shadowedhearts.LOGGER.error(
                    "[ShadowedHearts] Rejected Dark Ball FBO capture path "
                            + "outside the screenshots directory: {}",
                    relativeArtifactPath);
            return null;
        }
        return artifactPath;
    }

    private static void appendManifestEntries(
            Path manifestPath,
            List<JsonObject> entries) {
        try {
            StringBuilder jsonLines = new StringBuilder();
            for (JsonObject entry : entries) {
                jsonLines.append(MANIFEST_GSON.toJson(entry))
                        .append(System.lineSeparator());
            }
            synchronized (MANIFEST_WRITE_LOCK) {
                Files.createDirectories(manifestPath.getParent());
                Files.writeString(
                        manifestPath,
                        jsonLines,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND);
            }
        } catch (IOException | SecurityException failure) {
            Shadowedhearts.LOGGER.error(
                    "[ShadowedHearts] Failed to append Dark Ball FBO "
                            + "deep-trace manifest {}",
                    manifestPath,
                    failure);
        }
    }

    private record LivePreview(
            DarkBallCaptureVfx.FboPreviewCapture capture,
            DarkBallDensityFBO.PreviewFrame frame,
            CompositeSource compositeSource) {
    }

    private record CompositeSource(
            int texture,
            int width,
            int height) {
    }

    private record CaptureAttempt(
            String filename,
            String manifestFilename,
            int shotNumber,
            int runOrdinal,
            int scheduledPhaseShotCount,
            int mainFramebufferWidth,
            int mainFramebufferHeight,
            DarkBallCaptureVfx.FboPreviewCapture capture,
            DarkBallDensityFBO.PreviewFrame frame,
            CaptureDescriptor descriptor,
            float actualAge) {
    }

    private record SplatTileCaptureAttempt(
            CaptureAttempt atlasAttempt,
            String filename,
            int tileNumber,
            String tileLabel,
            int previewMode) {
    }

    private record CaptureDescriptor(
            String kind,
            String slug,
            String label,
            DarkBallFboPreviewSchedule.CapturePoint phasePoint,
            DarkBallFboPreviewSchedule.DirectRendererActivationCapture
                    transition) {

        static CaptureDescriptor phase(
                DarkBallFboPreviewSchedule.CapturePoint point) {
            return new CaptureDescriptor(
                    "phase-sample",
                    "phase-sample",
                    "phase sample",
                    point,
                    null);
        }

        static CaptureDescriptor transition(
                DarkBallFboPreviewSchedule.DirectRendererActivationCapture
                        transition) {
            return new CaptureDescriptor(
                    "direct-renderer-activation-event",
                    transition.kind().slug(),
                    transition.kind().label(),
                    null,
                    transition);
        }

        float targetAge() {
            return phasePoint != null
                    ? phasePoint.targetAge()
                    : transition.targetAge();
        }

        int sampleNumber() {
            return phasePoint != null
                    ? phasePoint.sampleNumber()
                    : 0;
        }

        int sampleCount() {
            return phasePoint != null
                    ? phasePoint.sampleCount()
                    : 0;
        }

        String phaseLabel(float actualAge) {
            return phasePoint != null
                    ? phasePoint.phase().label()
                    : DarkBallFboPreviewSchedule.phaseAt(actualAge);
        }
    }

    private record GlState(
            int drawFramebuffer,
            int readFramebuffer,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            boolean blendEnabled,
            boolean depthTestEnabled,
            boolean cullEnabled,
            boolean scissorEnabled,
            boolean depthMask,
            boolean colorMaskRed,
            boolean colorMaskGreen,
            boolean colorMaskBlue,
            boolean colorMaskAlpha,
            int blendSourceRgb,
            int blendDestinationRgb,
            int blendSourceAlpha,
            int blendDestinationAlpha,
            int blendEquationRgb,
            int blendEquationAlpha,
            int activeTexture,
            int activeTextureBinding,
            int textureZeroBinding,
            int packAlignment,
            int textureZero,
            int currentProgram,
            ShaderInstance shader,
            float shaderRed,
            float shaderGreen,
            float shaderBlue,
            float shaderAlpha) {

        static GlState capture() {
            IntBuffer viewport = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            float[] shaderColor = RenderSystem.getShaderColor();
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int activeTextureBinding =
                    GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            int textureZeroBinding =
                    GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            RenderSystem.activeTexture(activeTexture);
            return new GlState(
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    viewport.get(0),
                    viewport.get(1),
                    viewport.get(2),
                    viewport.get(3),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    colorMask.get(0) != 0,
                    colorMask.get(1) != 0,
                    colorMask.get(2) != 0,
                    colorMask.get(3) != 0,
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
                    activeTexture,
                    activeTextureBinding,
                    textureZeroBinding,
                    GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT),
                    RenderSystem.getShaderTexture(0),
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    RenderSystem.getShader(),
                    shaderColor[0],
                    shaderColor[1],
                    shaderColor[2],
                    shaderColor[3]);
        }

        void restore() {
            GL30.glBindFramebuffer(
                    GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(
                    GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(
                    viewportX, viewportY,
                    viewportWidth, viewportHeight);

            GlStateManager._blendFuncSeparate(
                    blendSourceRgb, blendDestinationRgb,
                    blendSourceAlpha, blendDestinationAlpha);
            GL20.glBlendEquationSeparate(
                    blendEquationRgb, blendEquationAlpha);
            if (blendEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (depthTestEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (cullEnabled) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            RenderSystem.depthMask(depthMask);
            RenderSystem.colorMask(
                    colorMaskRed,
                    colorMaskGreen,
                    colorMaskBlue,
                    colorMaskAlpha);
            GL11.glPixelStorei(
                    GL11.GL_PACK_ALIGNMENT, packAlignment);
            RenderSystem.setShaderTexture(0, textureZero);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(textureZeroBinding);
            RenderSystem.activeTexture(activeTexture);
            if (activeTexture != GL13.GL_TEXTURE0) {
                RenderSystem.bindTexture(activeTextureBinding);
            }
            RenderSystem.setShaderColor(
                    shaderRed, shaderGreen,
                    shaderBlue, shaderAlpha);
            RenderSystem.setShader(() -> shader);
            GL20.glUseProgram(currentProgram);
        }
    }
}
