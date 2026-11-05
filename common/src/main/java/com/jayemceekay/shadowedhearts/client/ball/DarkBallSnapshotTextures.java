package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.render.DarkBallFieldMaskBufferSource.AlphaMode;
import com.jayemceekay.shadowedhearts.client.render.DarkBallFieldMaskBufferSource.CapturedTriangle;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Immutable per-material alpha data; no native image or GPU texture ownership. */
final class DarkBallSnapshotTextures {
    private static final DarkBallTextureAlphaClipper.AlphaField TRANSPARENT =
            new DarkBallTextureAlphaClipper.AlphaField(1, 1, new float[]{0.0f});
    private static final DarkBallTextureAlphaClipper.AlphaField OPAQUE =
            new DarkBallTextureAlphaClipper.AlphaField(1, 1, new float[]{1.0f});

    private final Map<ResourceLocation, DarkBallTextureAlphaClipper.AlphaField> textures;
    private final DarkBallTextureAlphaClipper.AlphaField legacyAlpha;

    DarkBallSnapshotTextures(Map<ResourceLocation, DarkBallTextureAlphaClipper.AlphaField> textures,
                             DarkBallTextureAlphaClipper.AlphaField legacyAlpha) {
        this.textures = Map.copyOf(textures);
        this.legacyAlpha = legacyAlpha;
    }

    static DarkBallSnapshotTextures fromBase(NativeImage image) {
        return new DarkBallSnapshotTextures(Map.of(), image == null ? null : copyAlpha(image, () -> false));
    }

    DarkBallTextureAlphaClipper.AlphaField alphaFor(CapturedTriangle triangle) {
        if (triangle.material() == null) {
            return legacyAlpha;
        }
        if (triangle.material().alphaMode() == AlphaMode.OPAQUE) {
            return OPAQUE;
        }
        // An identified missing overlay must not inherit an opaque base mask.
        return textures.getOrDefault(triangle.material().texture(), TRANSPARENT);
    }

    boolean hasAnyAlpha() {
        return legacyAlpha != null || !textures.isEmpty();
    }

    /**
     * Resolve resources before scheduling work, preserving the selected pack and
     * discrete animated-frame resource. No decoding or native allocation occurs
     * here. Resource reload cancels the owning capture; workers never re-resolve
     * identifiers against a newer resource manager.
     */
    static CapturedResources captureResources(ResourceManager manager,
                                               List<CapturedTriangle> triangles,
                                               ResourceLocation baseTexture) {
        Set<ResourceLocation> requested = new LinkedHashSet<>();
        if (baseTexture != null) {
            requested.add(baseTexture);
        }
        for (CapturedTriangle triangle : triangles) {
            if (triangle.material() != null) {
                requested.add(triangle.material().texture());
            }
        }
        Map<ResourceLocation, Resource> resources = new LinkedHashMap<>();
        Map<ResourceLocation, String> provenance = new LinkedHashMap<>();
        for (ResourceLocation texture : requested) {
            manager.getResource(texture).ifPresent(resource -> {
                resources.put(texture, resource);
                provenance.put(texture, resource.sourcePackId());
            });
        }
        return new CapturedResources(Map.copyOf(resources), Set.copyOf(requested),
                Map.copyOf(provenance), baseTexture);
    }

    record CapturedResources(Map<ResourceLocation, Resource> resources,
                             Set<ResourceLocation> requested,
                             Map<ResourceLocation, String> provenance,
                             ResourceLocation baseTexture) {
        CapturedResources {
            resources = Map.copyOf(resources);
            requested = Set.copyOf(requested);
            provenance = Map.copyOf(provenance);
        }

        DarkBallSnapshotTextures decode(BooleanSupplier cancelled) {
            Map<ResourceLocation, DarkBallTextureAlphaClipper.AlphaField> alpha = new LinkedHashMap<>();
            for (ResourceLocation texture : requested) {
                checkCancelled(cancelled);
                Resource resource = resources.get(texture);
                DarkBallTextureAlphaClipper.AlphaField field = TRANSPARENT;
                if (resource != null) {
                    try (var stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
                        field = copyAlpha(image, cancelled);
                    } catch (IOException | IllegalArgumentException failure) {
                        Shadowedhearts.LOGGER.warn(
                                "[ShadowedHearts] Could not decode Dark Ball material {} from {}; omitting its prepared surface",
                                texture, provenance.get(texture), failure);
                    }
                } else {
                    Shadowedhearts.LOGGER.warn(
                            "[ShadowedHearts] Missing Dark Ball material {}; omitting its prepared surface", texture);
                }
                alpha.put(texture, field);
            }
            checkCancelled(cancelled);
            return new DarkBallSnapshotTextures(alpha,
                    baseTexture == null ? null : alpha.get(baseTexture));
        }
    }

    private static DarkBallTextureAlphaClipper.AlphaField copyAlpha(
            NativeImage image, BooleanSupplier cancelled) {
        int width = image.getWidth();
        int height = image.getHeight();
        float[] alpha = new float[Math.multiplyExact(width, height)];
        for (int y = 0; y < height; y++) {
            checkCancelled(cancelled);
            for (int x = 0; x < width; x++) {
                alpha[y * width + x] = FastColor.ABGR32.alpha(image.getPixelRGBA(x, y)) / 255.0f;
            }
        }
        return new DarkBallTextureAlphaClipper.AlphaField(width, height, alpha);
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        DarkBallPreparationCancellation.check();
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Dark Ball material decode cancelled");
        }
    }
}
