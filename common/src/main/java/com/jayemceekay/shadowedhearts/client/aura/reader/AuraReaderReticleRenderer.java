package com.jayemceekay.shadowedhearts.client.aura.reader;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource;
import com.jayemceekay.shadowedhearts.config.IClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.clamp01;
import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.fill;

/** Draws the Reader's animated scanner rings with caller-supplied frame opacity. */
final class AuraReaderReticleRenderer {
    private static final int SCAN_RING_MIDDLE_WIDTH = 100;
    private static final int SCAN_RING_MIDDLE_HEIGHT = 1;
    private static final int SCAN_RING_OUTER_DIAMETER = AuraReaderHudGeometry.SCANNER_RETICLE_DIAMETER;
    private static final int SCAN_RING_INNER_DIAMETER = 84;
    private static final ResourceLocation SCAN_RING_OUTER = cobblemon("textures/gui/pokedex/scan/scan_ring_outer.png");
    private static final ResourceLocation SCAN_RING_MIDDLE = cobblemon("textures/gui/pokedex/scan/scan_ring_middle.png");
    private static final ResourceLocation SCAN_RING_INNER = cobblemon("textures/gui/pokedex/scan/scan_ring_inner.png");

    private AuraReaderReticleRenderer() {
    }

    private static ResourceLocation cobblemon(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon", path);
    }

    static void render(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            float scale,
            AuraReaderClientState.ReadingSnapshot focused,
            IClientConfig config,
            float frameAlpha) {
        if (!(scale > 0.0f) || !Float.isFinite(scale)) {
            return;
        }

        boolean locked = focused != null
                && (focused.locked() || focused.source() == AuraReadingSource.LOCKED);
        float pulseProgress = AuraReaderClientState.getPulseAnimationProgress();
        boolean pulseActive = pulseProgress > 0.0f;
        float ringAlpha = locked ? 1.0f : focused != null ? 0.96f : pulseActive ? 0.94f : 0.82f;
        float rotation = config.auraReaderReducedMotion()
                ? 0.0f
                : AuraReaderClientState.getUsageIntervals() % 360.0f;
        float innerRotation = config.auraReaderReducedMotion()
                ? 0.0f
                : AuraReaderClientState.getInnerRingRotation();

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);

        graphics.pose().pushPose();
        graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(rotation * -0.5f)));
        blitNative(
                graphics,
                SCAN_RING_OUTER,
                -(SCAN_RING_OUTER_DIAMETER / 2.0f),
                -(SCAN_RING_OUTER_DIAMETER / 2.0f),
                SCAN_RING_OUTER_DIAMETER,
                SCAN_RING_OUTER_DIAMETER,
                0,
                0,
                SCAN_RING_OUTER_DIAMETER,
                SCAN_RING_OUTER_DIAMETER,
                ringAlpha,
                frameAlpha
        );
        graphics.pose().popPose();

        float progressOpacity = ringAlpha;
        int middleSegments = 40;
        float scanProgress = AuraReaderClientState.getReticleScanProgress();
        if (scanProgress > 0.0f) {
            if (scanProgress < 20.0f) {
                progressOpacity = Math.max(0.0f, ringAlpha - scanProgress * 0.05f);
            } else {
                middleSegments = Math.max(0, Math.min(40, (int) Math.floor((scanProgress - 20.0f) / 2.0f)));
            }
        }
        for (int index = 0; index < middleSegments; index++) {
            graphics.pose().pushPose();
            float segmentRotation = index * 4.5f + rotation * 0.5f;
            graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(segmentRotation)));
            blitNative(
                    graphics,
                    SCAN_RING_MIDDLE,
                    -(SCAN_RING_MIDDLE_WIDTH / 2.0f),
                    -(SCAN_RING_MIDDLE_HEIGHT / 2.0f),
                    SCAN_RING_MIDDLE_WIDTH,
                    SCAN_RING_MIDDLE_HEIGHT,
                    0,
                    0,
                    SCAN_RING_MIDDLE_WIDTH,
                    SCAN_RING_MIDDLE_HEIGHT,
                    progressOpacity,
                    frameAlpha
            );
            graphics.pose().popPose();
        }

        graphics.pose().pushPose();
        graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-innerRotation)));
        blitNative(
                graphics,
                SCAN_RING_INNER,
                -(SCAN_RING_INNER_DIAMETER / 2.0f),
                -(SCAN_RING_INNER_DIAMETER / 2.0f),
                SCAN_RING_INNER_DIAMETER,
                SCAN_RING_INNER_DIAMETER,
                0,
                0,
                SCAN_RING_INNER_DIAMETER,
                SCAN_RING_INNER_DIAMETER,
                ringAlpha,
                frameAlpha
        );
        graphics.pose().popPose();

        drawCooldownRing(graphics, frameAlpha);

        graphics.pose().popPose();
    }

    private static void drawCooldownRing(GuiGraphics graphics, float frameAlpha) {
        float readyFraction = 1.0f - AuraReaderClientState.getPulseCooldownFraction();
        int litSegments = Math.round(24 * readyFraction);
        for (int index = 0; index < 24; index++) {
            graphics.pose().pushPose();
            graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(index * 15.0f)));
            int color = index < litSegments
                    ? AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.CYAN, 0.72f)
                    : AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.CYAN_DIM, 0.24f);
            fill(graphics, -1, -57, 1, -53, color, frameAlpha);
            graphics.pose().popPose();
        }
    }

    private static void blitNative(
            GuiGraphics graphics,
            ResourceLocation texture,
            float x,
            float y,
            float width,
            float height,
            int uOffset,
            int vOffset,
            int textureWidth,
            int textureHeight,
            float alpha,
            float frameAlpha) {
        if (!(width > 0.0f) || !(height > 0.0f)) {
            return;
        }
        GuiUtilsKt.blitk(
                graphics.pose(),
                texture,
                x,
                y,
                height,
                width,
                uOffset,
                vOffset,
                textureWidth,
                textureHeight,
                0,
                1.0f,
                1.0f,
                1.0f,
                clamp01(alpha * frameAlpha),
                true,
                1.0f
        );
    }
}
