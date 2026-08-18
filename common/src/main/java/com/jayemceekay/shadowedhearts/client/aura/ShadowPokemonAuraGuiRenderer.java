package com.jayemceekay.shadowedhearts.client.aura;

import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immediate, GUI-local counterpart to {@link ShadowPokemonAuraSystem}.
 *
 * <p>Preview Pokémon do not have a {@code PokemonEntity}, so they cannot enter the
 * network-driven world aura lifecycle. Instead, this renderer captures a small set
 * of anchors while Cobblemon renders the already-posed GUI model, emits the same
 * broad/core/wisp density material used by the world aura, and composites it before
 * returning to ordinary GUI rendering.</p>
 */
public final class ShadowPokemonAuraGuiRenderer {

    private static final ResourceLocation DENSITY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "shadowedhearts",
            "textures/particle/penumbra_trail_16.png"
    );
    private static final int FULLBRIGHT = 0x00F000F0;
    private static final int MAX_CAPTURED_ANCHORS = 4096;
    private static final int MIN_RENDERED_PUFFS = 72;
    private static final int MAX_RENDERED_PUFFS = 192;
    private static final float PUFF_SIZE_AXIS_SCALE = 0.09f;
    private static final float MIN_PUFF_SIZE = 3.25f;
    private static final float MAX_PUFF_SIZE = 21.0f;
    private static final int COVERAGE_GRID_COLUMNS = 8;
    private static final int COVERAGE_GRID_ROWS = 12;
    private static final float COVERAGE_SIZE_AXIS_SCALE = 0.12f;
    private static final float MIN_COVERAGE_SIZE = 4.5f;
    private static final float MAX_COVERAGE_SIZE = 28.0f;
    private static final float COVERAGE_ALPHA_MIN = 0.18f;
    private static final float COVERAGE_ALPHA_RANGE = 0.04f;
    private static final float GUI_COVERAGE_DENSITY_GAIN = 1.55f;
    private static final float DETAIL_CAMERA_DEPTH_BIAS = 0.00005f;
    private static final float COVERAGE_AWAY_DEPTH_BIAS = 0.000025f;
    private static final float GUI_MASK_FALLOFF = 1.8f;
    private static final float WORLD_MASK_FALLOFF = 4.2f;
    private static final float DEPTH_EPSILON = 0.000001f;
    private static final float TAU = (float) (Math.PI * 2.0);

    private static GuiCapture pendingCapture;
    private static GuiCapture activeCapture;
    private static GuiCapture completedCapture;

    private ShadowPokemonAuraGuiRenderer() {}

    /** Arms the next entity-less Cobblemon profile-model render for anchor capture. */
    public static void beginCapture(RenderablePokemon pokemon) {
        pendingCapture = pokemon == null
                ? null
                : new GuiCapture(pokemon.getSpecies().getResourceIdentifier());
        activeCapture = null;
        completedCapture = null;
    }

    /** Called by the PosableModel mixin immediately before its root bone renders. */
    public static void beginRenderedModelCapture() {
        if (pendingCapture == null || activeCapture != null) {
            return;
        }
        activeCapture = pendingCapture;
        pendingCapture = null;
    }

    /** Called by the ModelPart mixin after the part transform has been applied. */
    public static void captureRenderedModelPart(ModelPart part, PoseStack stack) {
        GuiCapture capture = activeCapture;
        if (capture == null || part == null || stack == null) {
            return;
        }

        Matrix4f pose = stack.last().pose();
        for (ModelPart.Cube cube : part.cubes) {
            float minX = cube.minX / 16.0f;
            float minY = cube.minY / 16.0f;
            float minZ = cube.minZ / 16.0f;
            float maxX = cube.maxX / 16.0f;
            float maxY = cube.maxY / 16.0f;
            float maxZ = cube.maxZ / 16.0f;
            float sizeX = Math.abs(maxX - minX);
            float sizeY = Math.abs(maxY - minY);
            float sizeZ = Math.abs(maxZ - minZ);
            if (Math.max(sizeX, Math.max(sizeY, sizeZ)) <= 0.001f) {
                continue;
            }

            float centerX = (minX + maxX) * 0.5f;
            float centerY = (minY + maxY) * 0.5f;
            float centerZ = (minZ + maxZ) * 0.5f;
            float weight = Math.max(sizeX, Math.max(sizeY, sizeZ));

            addAnchor(capture, pose, centerX, minY, centerZ,
                    centerX, centerY, centerZ);
            addAnchor(capture, pose, centerX, maxY, centerZ,
                    centerX, centerY, centerZ);
            addAnchor(capture, pose, minX, centerY, centerZ,
                    centerX, centerY, centerZ);
            addAnchor(capture, pose, maxX, centerY, centerZ,
                    centerX, centerY, centerZ);
            addAnchor(capture, pose, centerX, centerY, minZ,
                    centerX, centerY, centerZ);
            addAnchor(capture, pose, centerX, centerY, maxZ,
                    centerX, centerY, centerZ);

            if (weight >= 0.30f) {
                addAnchor(capture, pose, minX, minY, minZ,
                        centerX, centerY, centerZ);
                addAnchor(capture, pose, maxX, minY, maxZ,
                        centerX, centerY, centerZ);
                addAnchor(capture, pose, minX, maxY, maxZ,
                        centerX, centerY, centerZ);
                addAnchor(capture, pose, maxX, maxY, minZ,
                        centerX, centerY, centerZ);
            }
        }
    }

    /** Called by the PosableModel mixin after the captured root bone returns. */
    public static void endRenderedModelCapture() {
        if (activeCapture == null) {
            return;
        }
        completedCapture = activeCapture;
        activeCapture = null;
    }

    /**
     * Renders and consumes the most recent GUI capture.
     *
     * @param clipX GUI-scaled left edge of the allowed composite area
     * @param clipY GUI-scaled top edge of the allowed composite area
     * @param clipWidth GUI-scaled clip width
     * @param clipHeight GUI-scaled clip height
     * @param fallbackCenterX fallback center for profile-sprite renders
     * @param fallbackCenterY fallback center for profile-sprite renders
     * @param fallbackZ fallback GUI depth
     * @param fallbackWidth fallback aura width
     * @param fallbackHeight fallback aura height
     */
    public static void render(RenderablePokemon pokemon,
                              float corruption,
                              float partialTicks,
                              float clipX,
                              float clipY,
                              float clipWidth,
                              float clipHeight,
                              float fallbackCenterX,
                              float fallbackCenterY,
                              float fallbackZ,
                              float fallbackWidth,
                              float fallbackHeight) {
        GuiCapture capture = consumeCapture(pokemon);
        float strength = Mth.clamp(corruption, 0.0f, 1.0f);
        if (strength <= 0.001f
                || ModShaders.SHADOW_POKEMON_AURA_DENSITY == null
                || ModShaders.SHADOW_POKEMON_AURA_COMPOSITE == null) {
            return;
        }

        List<GuiAnchor> anchors = capture == null
                ? List.of()
                : capture.anchors;
        if (anchors.isEmpty()) {
            anchors = fallbackAnchors(
                    fallbackCenterX,
                    fallbackCenterY,
                    fallbackZ,
                    fallbackWidth,
                    fallbackHeight
            );
        }
        if (anchors.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        float guiWidth = Math.max(1.0f, mc.getWindow().getGuiScaledWidth());
        float guiHeight = Math.max(1.0f, mc.getWindow().getGuiScaledHeight());
        float left = Mth.clamp(clipX, 0.0f, guiWidth);
        float top = Mth.clamp(clipY, 0.0f, guiHeight);
        float right = Mth.clamp(clipX + Math.max(0.0f, clipWidth), 0.0f, guiWidth);
        float bottom = Mth.clamp(clipY + Math.max(0.0f, clipHeight), 0.0f, guiHeight);
        if (right <= left || bottom <= top) {
            return;
        }

        float animationTicks = (float) ((System.nanoTime() / 50_000_000.0 + partialTicks) % 1200.0);
        boolean densityPassStarted = ShadowPokemonAuraFBO.beginDensityPass(true, true);
        if (!densityPassStarted) {
            return;
        }

        try {
            // ModelWidget's scissor is expressed in full-resolution framebuffer
            // coordinates, while the density target is deliberately half-size.
            // The bounded composite below provides the correct GUI-space clip.
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            renderDensitySplats(anchors, strength, animationTicks,
                    capture == null ? 0 : capture.species.hashCode());
        } finally {
            ShadowPokemonAuraFBO.endDensityPass();
        }

        float minimumU = left / guiWidth;
        float maximumU = right / guiWidth;
        float minimumV = 1.0f - bottom / guiHeight;
        float maximumV = 1.0f - top / guiHeight;
        Matrix4f guiProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        try {
            ShadowPokemonAuraFBO.blur();
            ShadowPokemonAuraFBO.composite(
                    minimumU,
                    minimumV,
                    maximumU,
                    maximumV,
                    animationTicks
            );
        } finally {
            // DensityFboPipeline's fullscreen helpers are world-oriented and
            // currently restore DISTANCE_TO_ORIGIN. These entrypoints always
            // run inside an orthographic GUI, so restore that sorting contract.
            RenderSystem.setProjectionMatrix(guiProjection, VertexSorting.ORTHOGRAPHIC_Z);
        }
    }

    private static GuiCapture consumeCapture(RenderablePokemon pokemon) {
        ResourceLocation species = pokemon == null
                ? null
                : pokemon.getSpecies().getResourceIdentifier();
        GuiCapture result = completedCapture;
        if (result == null) {
            result = activeCapture != null ? activeCapture : pendingCapture;
        }

        pendingCapture = null;
        activeCapture = null;
        completedCapture = null;

        if (result == null || species == null || !species.equals(result.species)) {
            return null;
        }
        return result;
    }

    private static void addAnchor(GuiCapture capture,
                                  Matrix4f pose,
                                  float x,
                                  float y,
                                  float z,
                                  float centerX,
                                  float centerY,
                                  float centerZ) {
        Vector3f transformed = pose.transformPosition(x, y, z, new Vector3f());
        Vector3f transformedCenter = pose.transformPosition(
                centerX,
                centerY,
                centerZ,
                new Vector3f()
        );
        if (!Float.isFinite(transformed.x)
                || !Float.isFinite(transformed.y)
                || !Float.isFinite(transformed.z)
                || !Float.isFinite(transformedCenter.x)
                || !Float.isFinite(transformedCenter.y)
                || !Float.isFinite(transformedCenter.z)) {
            return;
        }
        Vector3f outward = normalizedOutward(
                transformed.x - transformedCenter.x,
                transformed.y - transformedCenter.y,
                transformed.z - transformedCenter.z
        );
        GuiAnchor anchor = new GuiAnchor(
                transformed.x,
                transformed.y,
                transformed.z,
                outward.x,
                outward.y,
                outward.z
        );

        int seen = ++capture.seenAnchorCount;
        if (capture.anchors.size() < MAX_CAPTURED_ANCHORS) {
            capture.anchors.add(anchor);
            return;
        }

        // Deterministic reservoir sampling keeps the memory bound while still
        // representing late-traversed bones on complex Cobblemon models.
        int slot = Math.floorMod(
                mixHash(capture.species.hashCode() ^ seen * 0x45d9f3b),
                seen
        );
        if (slot < MAX_CAPTURED_ANCHORS) {
            capture.anchors.set(slot, anchor);
        }
    }

    private static List<GuiAnchor> fallbackAnchors(float centerX,
                                                    float centerY,
                                                    float z,
                                                    float width,
                                                    float height) {
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY) || !Float.isFinite(z)) {
            return List.of();
        }
        float radiusX = Math.max(8.0f, Math.abs(width) * 0.5f);
        float radiusY = Math.max(12.0f, Math.abs(height) * 0.5f);
        List<GuiAnchor> anchors = new ArrayList<>(42);
        for (int i = 0; i < 42; i++) {
            float unit = (i + 0.5f) / 42.0f;
            float radius = (float) Math.sqrt(unit);
            float angle = i * 2.3999632f;
            float x = centerX + (float) Math.cos(angle) * radiusX * radius;
            float y = centerY + (float) Math.sin(angle) * radiusY * radius;
            Vector3f outward = normalizedOutward(x - centerX, y - centerY, 0.0f);
            anchors.add(new GuiAnchor(
                    x,
                    y,
                    z,
                    outward.x,
                    outward.y,
                    outward.z
            ));
        }
        return anchors;
    }

    private static void renderDensitySplats(List<GuiAnchor> anchors,
                                             float strength,
                                             float animationTicks,
                                             int speciesSeed) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE
        );
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.disableCull();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        var shader = ModShaders.SHADOW_POKEMON_AURA_DENSITY;
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, DENSITY_TEXTURE);

        GuiBounds bounds = GuiBounds.of(anchors);
        float longestAxis = Math.max(bounds.width(), bounds.height());
        ProjectionContext projection = ProjectionContext.capture();
        List<ProjectedGuiAnchor> coverageAnchors = frontmostCoverageAnchors(
                anchors,
                projection
        );

        Uniform gameTime = shader.getUniform("GameTime");
        if (gameTime != null) {
            gameTime.set(animationTicks / 1200.0f);
        }
        Uniform cameraPos = shader.getUniform("CameraPos");
        if (cameraPos != null) {
            cameraPos.set(0.0f, 0.0f, 0.0f);
        }
        Uniform noiseScale = shader.getUniform("AuraNoiseScale");
        if (noiseScale != null) {
            noiseScale.set(Mth.clamp(2.0f / longestAxis, 0.008f, 0.08f));
        }

        float baseSize = basePuffSize(longestAxis);
        float coverageSize = coveragePuffSize(longestAxis);
        int coverageCellCount = coverageAnchors.isEmpty()
                ? Math.min(
                        anchors.size(),
                        COVERAGE_GRID_COLUMNS * COVERAGE_GRID_ROWS)
                : coverageAnchors.size();
        int puffCount = renderedPuffCount(coverageCellCount);

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.PARTICLE
        );
        renderCoveragePuffs(
                buffer,
                coverageAnchors,
                bounds,
                projection,
                coverageSize,
                strength,
                speciesSeed
        );

        for (int i = 0; i < puffCount; i++) {
            GuiAnchor anchor;
            int anchorSeed;
            int cohort;
            if (!coverageAnchors.isEmpty()) {
                int anchorIndex = distributedIndex(
                        i,
                        puffCount,
                        coverageAnchors.size()
                );
                ProjectedGuiAnchor projected = coverageAnchors.get(anchorIndex);
                anchor = projected.anchor;
                anchorSeed = projected.cell;
                cohort = coverageAnchors.size() >= puffCount
                        ? 0
                        : i / coverageAnchors.size();
            } else {
                int anchorIndex = distributedIndex(i, puffCount, anchors.size());
                anchor = anchors.get(anchorIndex);
                anchorSeed = anchorIndex;
                cohort = anchors.size() >= puffCount ? 0 : i / anchors.size();
            }
            int detailKey = mixHash(
                    anchorSeed * 0x45d9f3b ^ cohort * 0x27d4eb2d);
            float randomA = hash01(
                    speciesSeed * 31 + detailKey * 0x27d4eb2d);
            float randomB = hash01(
                    speciesSeed * 17 + detailKey * 0x165667b1);
            float cycle = fract(animationTicks * (0.0075f + randomA * 0.0045f) + randomB);
            float lifeEnvelope = lifeEnvelope(cycle);

            GuiPuffType type = GuiPuffType.forIndex(detailKey);
            float phase = animationTicks * (0.045f + randomB * 0.025f) + randomA * TAU;
            float sway = (float) Math.sin(phase)
                    * baseSize * (0.10f + randomB * 0.20f);
            float rise = cycle * baseSize * type.riseScale;
            Vector3f screenOutward = normalizedScreenOutward(
                    anchor.x,
                    anchor.y,
                    bounds.centerX(),
                    bounds.centerY()
            );
            Vector3f outward = normalizedOutward(
                    anchor.outwardX + screenOutward.x * 0.35f,
                    anchor.outwardY + screenOutward.y * 0.35f,
                    anchor.outwardZ
            );
            float outwardPush = baseSize
                    * cycle
                    * (0.70f + randomB * 0.60f)
                    * type.outwardScale();
            Vector3f cameraBias = projection.depthBias(
                    anchor,
                    DETAIL_CAMERA_DEPTH_BIAS
                            * cycle * (0.65f + randomA * 0.35f),
                    true
            );
            float x = anchor.x + outward.x * outwardPush + cameraBias.x + sway;
            float y = anchor.y + outward.y * outwardPush + cameraBias.y - rise;
            float z = anchor.z + outward.z * outwardPush + cameraBias.z;
            float size = baseSize * type.sizeScale * (0.72f + randomA * 0.58f);
            float alpha = strength * type.alphaScale * lifeEnvelope;
            float rotation = phase * type.rotationScale;

            drawPuffQuad(
                    buffer,
                    x,
                    y,
                    z,
                    size * type.stretchX,
                    size * type.stretchY,
                    rotation,
                    type.broadWeight,
                    type.sparkWeight,
                    type.wispWeight,
                    alpha
            );
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            Uniform maskFalloff = shader.getUniform("AuraMaskFalloff");
            if (maskFalloff != null) {
                maskFalloff.set(GUI_MASK_FALLOFF);
            }
            try {
                BufferUploader.drawWithShader(mesh);
            } finally {
                // ShaderInstance uniforms persist between draw calls. Restore
                // the accepted world material value even though the world
                // path also sets it explicitly before rendering its puffs.
                if (maskFalloff != null) {
                    maskFalloff.set(WORLD_MASK_FALLOFF);
                }
            }
        }
    }

    private static void renderCoveragePuffs(BufferBuilder buffer,
                                            List<ProjectedGuiAnchor> anchors,
                                            GuiBounds bounds,
                                            ProjectionContext projection,
                                            float coverageSize,
                                            float strength,
                                            int speciesSeed) {
        for (int i = 0; i < anchors.size(); i++) {
            ProjectedGuiAnchor projected = anchors.get(i);
            GuiAnchor anchor = projected.anchor;
            float randomA = hash01(
                    speciesSeed * 43 + projected.cell * 0x45d9f3b);
            float randomB = hash01(
                    speciesSeed * 29 + projected.cell * 0x27d4eb2d);
            float size = coverageSize * (0.90f + randomB * 0.20f);
            float alpha = coverageAlpha(strength, randomA);
            Vector3f outward = normalizedScreenOutward(
                    anchor.x,
                    anchor.y,
                    bounds.centerX(),
                    bounds.centerY()
            );
            float outwardPush = coverageOutwardPush(coverageSize, randomB);
            // Keep the steady sheet behind the captured surface so copied
            // model depth masks its interior while its shifted footprint can
            // fill the silhouette edge. This is deliberately an away-bias;
            // only animated detail receives the camera-ward depth offset.
            Vector3f awayBias = projection.depthBias(
                    anchor,
                    COVERAGE_AWAY_DEPTH_BIAS,
                    false
            );
            drawPuffQuad(
                    buffer,
                    anchor.x + outward.x * outwardPush + awayBias.x,
                    anchor.y + outward.y * outwardPush + awayBias.y,
                    anchor.z + awayBias.z,
                    size,
                    size * (1.02f + randomA * 0.16f),
                    randomB * TAU,
                    1.00f,
                    0.00f,
                    0.05f,
                    alpha
            );
        }
    }

    private static List<ProjectedGuiAnchor> frontmostCoverageAnchors(
            List<GuiAnchor> anchors,
            ProjectionContext projection) {
        List<ProjectedGuiAnchor> projectedAnchors = new ArrayList<>(anchors.size());
        for (int i = 0; i < anchors.size(); i++) {
            ProjectedGuiAnchor projected = projection.project(anchors.get(i), i);
            if (projected == null) {
                continue;
            }
            projectedAnchors.add(projected);
        }
        if (projectedAnchors.isEmpty()) {
            return List.of();
        }

        float[] projectedX = new float[projectedAnchors.size()];
        float[] projectedY = new float[projectedAnchors.size()];
        float[] projectedDepth = new float[projectedAnchors.size()];
        for (int i = 0; i < projectedAnchors.size(); i++) {
            ProjectedGuiAnchor projected = projectedAnchors.get(i);
            projectedX[i] = projected.ndcX;
            projectedY[i] = projected.ndcY;
            projectedDepth[i] = projected.windowDepth;
        }
        int[] selected = selectFrontmostCells(
                projectedX,
                projectedY,
                projectedDepth,
                COVERAGE_GRID_COLUMNS,
                COVERAGE_GRID_ROWS,
                projection.depthOrder == DepthOrder.GREATER,
                projection.depthOrder != DepthOrder.UNORDERED
        );

        List<ProjectedGuiAnchor> result = new ArrayList<>(selected.length);
        for (int cell = 0; cell < selected.length; cell++) {
            int winner = selected[cell];
            if (winner >= 0) {
                result.add(projectedAnchors.get(winner).withCell(cell));
            }
        }
        return result;
    }

    private static void drawPuffQuad(BufferBuilder buffer,
                                     float centerX,
                                     float centerY,
                                     float z,
                                     float halfWidth,
                                     float halfHeight,
                                     float rotation,
                                     float broadWeight,
                                     float sparkWeight,
                                     float wispWeight,
                                     float alpha) {
        float cos = (float) Math.cos(rotation);
        float sin = (float) Math.sin(rotation);
        float rightX = cos * halfWidth;
        float rightY = sin * halfWidth;
        float upX = -sin * halfHeight;
        float upY = cos * halfHeight;

        buffer.addVertex(centerX - rightX - upX, centerY - rightY - upY, z)
                .setUv(0.0f, 1.0f)
                .setColor(broadWeight, sparkWeight, wispWeight, alpha)
                .setLight(FULLBRIGHT);
        buffer.addVertex(centerX + rightX - upX, centerY + rightY - upY, z)
                .setUv(1.0f, 1.0f)
                .setColor(broadWeight, sparkWeight, wispWeight, alpha)
                .setLight(FULLBRIGHT);
        buffer.addVertex(centerX + rightX + upX, centerY + rightY + upY, z)
                .setUv(1.0f, 0.0f)
                .setColor(broadWeight, sparkWeight, wispWeight, alpha)
                .setLight(FULLBRIGHT);
        buffer.addVertex(centerX - rightX + upX, centerY - rightY + upY, z)
                .setUv(0.0f, 0.0f)
                .setColor(broadWeight, sparkWeight, wispWeight, alpha)
                .setLight(FULLBRIGHT);
    }

    private static float hash01(int value) {
        return (mixHash(value) & 0x00FFFFFF) / 16777216.0f;
    }

    private static int mixHash(int value) {
        int x = value;
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    static int renderedPuffCount(int anchorCount) {
        if (anchorCount <= 0) {
            return 0;
        }
        return Math.min(
                MAX_RENDERED_PUFFS,
                Math.max(
                        MIN_RENDERED_PUFFS,
                        anchorCount * 2
                )
        );
    }

    static float guiMaskFalloff() {
        return GUI_MASK_FALLOFF;
    }

    static int distributedIndex(int itemIndex, int itemCount, int poolSize) {
        if (itemIndex < 0 || itemCount <= 0 || poolSize <= 0) {
            return 0;
        }
        if (poolSize >= itemCount) {
            return Math.min(
                    poolSize - 1,
                    (int) (((long) itemIndex * poolSize) / itemCount)
            );
        }
        return Math.floorMod(itemIndex, poolSize);
    }

    static float basePuffSize(float longestAxis) {
        return Mth.clamp(
                longestAxis * PUFF_SIZE_AXIS_SCALE,
                MIN_PUFF_SIZE,
                MAX_PUFF_SIZE
        );
    }

    static float coveragePuffSize(float longestAxis) {
        return Mth.clamp(
                longestAxis * COVERAGE_SIZE_AXIS_SCALE,
                MIN_COVERAGE_SIZE,
                MAX_COVERAGE_SIZE
        );
    }

    static float coverageAlpha(float strength, float random) {
        return Mth.clamp(strength, 0.0f, 1.0f)
                * (COVERAGE_ALPHA_MIN
                + Mth.clamp(random, 0.0f, 1.0f) * COVERAGE_ALPHA_RANGE)
                * GUI_COVERAGE_DENSITY_GAIN;
    }

    static float coverageOutwardPush(float coverageSize, float random) {
        return Math.max(0.0f, coverageSize)
                * (0.18f + Mth.clamp(random, 0.0f, 1.0f) * 0.10f);
    }

    static float lifeEnvelope(float cycle) {
        return (float) Math.sin(Math.PI * Mth.clamp(cycle, 0.0f, 1.0f));
    }

    static Vector3f normalizedScreenOutward(float x,
                                            float y,
                                            float centerX,
                                            float centerY) {
        Vector3f outward = new Vector3f(
                x - centerX,
                y - centerY,
                0.0f
        );
        if (outward.lengthSquared() <= 0.000001f) {
            return new Vector3f(0.0f, -1.0f, 0.0f);
        }
        return outward.normalize();
    }

    static Vector3f normalizedOutward(float x, float y, float z) {
        Vector3f outward = new Vector3f(x, y, z);
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || outward.lengthSquared() <= 0.000001f) {
            return new Vector3f(0.0f, -1.0f, 0.0f);
        }
        return outward.normalize();
    }

    static int[] selectFrontmostCells(float[] projectedX,
                                      float[] projectedY,
                                      float[] windowDepth,
                                      int columns,
                                      int rows,
                                      boolean greaterDepthIsFront,
                                      boolean compareDepth) {
        if (projectedX == null || projectedY == null || windowDepth == null
                || columns <= 0 || rows <= 0) {
            return new int[0];
        }
        int sampleCount = Math.min(
                projectedX.length,
                Math.min(projectedY.length, windowDepth.length)
        );
        int[] selected = new int[columns * rows];
        Arrays.fill(selected, -1);

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < sampleCount; i++) {
            if (!Float.isFinite(projectedX[i])
                    || !Float.isFinite(projectedY[i])
                    || !Float.isFinite(windowDepth[i])) {
                continue;
            }
            minX = Math.min(minX, projectedX[i]);
            minY = Math.min(minY, projectedY[i]);
            maxX = Math.max(maxX, projectedX[i]);
            maxY = Math.max(maxY, projectedY[i]);
        }
        if (!Float.isFinite(minX) || !Float.isFinite(minY)
                || !Float.isFinite(maxX) || !Float.isFinite(maxY)) {
            return selected;
        }

        float width = Math.max(DEPTH_EPSILON, maxX - minX);
        float height = Math.max(DEPTH_EPSILON, maxY - minY);
        for (int i = 0; i < sampleCount; i++) {
            float x = projectedX[i];
            float y = projectedY[i];
            float depth = windowDepth[i];
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(depth)) {
                continue;
            }
            int column = Mth.clamp(
                    (int) (((x - minX) / width) * columns),
                    0,
                    columns - 1
            );
            int row = Mth.clamp(
                    (int) (((y - minY) / height) * rows),
                    0,
                    rows - 1
            );
            int cell = row * columns + column;
            int current = selected[cell];
            if (current < 0 || (compareDepth && isCloserWindowDepth(
                    depth,
                    windowDepth[current],
                    greaterDepthIsFront))) {
                selected[cell] = i;
            }
        }
        return selected;
    }

    private static boolean isCloserWindowDepth(float candidate,
                                               float current,
                                               boolean greaterDepthIsFront) {
        return greaterDepthIsFront
                ? candidate > current + DEPTH_EPSILON
                : candidate < current - DEPTH_EPSILON;
    }

    static float shiftedWindowDepth(float windowDepth,
                                    float depthRangeNear,
                                    float depthRangeFar,
                                    boolean greaterDepthIsFront,
                                    boolean towardCamera,
                                    float distance) {
        float minimum = Math.min(depthRangeNear, depthRangeFar);
        float maximum = Math.max(depthRangeNear, depthRangeFar);
        float acceptedDirection = greaterDepthIsFront ? 1.0f : -1.0f;
        float signedDistance = Math.max(0.0f, distance)
                * acceptedDirection
                * (towardCamera ? 1.0f : -1.0f);
        return Mth.clamp(windowDepth + signedDistance, minimum, maximum);
    }

    private static final class GuiCapture {
        private final ResourceLocation species;
        private final List<GuiAnchor> anchors = new ArrayList<>();
        private int seenAnchorCount;

        private GuiCapture(ResourceLocation species) {
            this.species = species;
        }
    }

    private record GuiAnchor(float x,
                             float y,
                             float z,
                             float outwardX,
                             float outwardY,
                             float outwardZ) {}

    private record ProjectedGuiAnchor(GuiAnchor anchor,
                                      float ndcX,
                                      float ndcY,
                                      float windowDepth,
                                      int sourceIndex,
                                      int cell) {
        private ProjectedGuiAnchor withCell(int newCell) {
            return new ProjectedGuiAnchor(
                    anchor,
                    ndcX,
                    ndcY,
                    windowDepth,
                    sourceIndex,
                    newCell
            );
        }
    }

    private enum DepthOrder {
        SMALLER,
        GREATER,
        UNORDERED
    }

    private static final class ProjectionContext {
        private final Matrix4f modelViewProjection;
        private final Matrix4f inverseModelViewProjection;
        private final float depthRangeNear;
        private final float depthRangeFar;
        private final DepthOrder depthOrder;

        private ProjectionContext(Matrix4f modelViewProjection,
                                  Matrix4f inverseModelViewProjection,
                                  float depthRangeNear,
                                  float depthRangeFar,
                                  DepthOrder depthOrder) {
            this.modelViewProjection = modelViewProjection;
            this.inverseModelViewProjection = inverseModelViewProjection;
            this.depthRangeNear = depthRangeNear;
            this.depthRangeFar = depthRangeFar;
            this.depthOrder = depthOrder;
        }

        private static ProjectionContext capture() {
            Matrix4f modelViewProjection = new Matrix4f(
                    RenderSystem.getProjectionMatrix())
                    .mul(RenderSystem.getModelViewMatrix());
            float determinant = modelViewProjection.determinant();
            Matrix4f inverse = null;
            // Orthographic GUI projections legitimately have very small
            // determinants because screen width, height, and GUI depth all
            // contribute scale factors. Only reject a truly singular matrix.
            if (Float.isFinite(determinant) && Math.abs(determinant) > 1.0e-12f) {
                Matrix4f candidate = new Matrix4f(modelViewProjection).invert();
                if (candidate.isFinite()) {
                    inverse = candidate;
                }
            }

            FloatBuffer depthRange = BufferUtils.createFloatBuffer(2);
            GL11.glGetFloatv(GL11.GL_DEPTH_RANGE, depthRange);
            float depthRangeNear = depthRange.get(0);
            float depthRangeFar = depthRange.get(1);
            if (!Float.isFinite(depthRangeNear)
                    || !Float.isFinite(depthRangeFar)
                    || Math.abs(depthRangeFar - depthRangeNear) <= DEPTH_EPSILON) {
                depthRangeNear = 0.0f;
                depthRangeFar = 1.0f;
                inverse = null;
            }

            int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            DepthOrder order = switch (depthFunction) {
                case GL11.GL_GREATER, GL11.GL_GEQUAL -> DepthOrder.GREATER;
                case GL11.GL_LESS, GL11.GL_LEQUAL -> DepthOrder.SMALLER;
                default -> DepthOrder.UNORDERED;
            };
            return new ProjectionContext(
                    modelViewProjection,
                    inverse,
                    depthRangeNear,
                    depthRangeFar,
                    order
            );
        }

        private ProjectedGuiAnchor project(GuiAnchor anchor, int sourceIndex) {
            Vector4f clip = modelViewProjection.transform(
                    new Vector4f(anchor.x, anchor.y, anchor.z, 1.0f)
            );
            if (!Float.isFinite(clip.x)
                    || !Float.isFinite(clip.y)
                    || !Float.isFinite(clip.z)
                    || !Float.isFinite(clip.w)
                    || clip.w <= DEPTH_EPSILON) {
                return null;
            }

            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            float ndcZ = clip.z / clip.w;
            float windowDepth = depthRangeNear
                    + (ndcZ + 1.0f) * 0.5f
                    * (depthRangeFar - depthRangeNear);
            if (!Float.isFinite(ndcX)
                    || !Float.isFinite(ndcY)
                    || !Float.isFinite(windowDepth)) {
                return null;
            }
            return new ProjectedGuiAnchor(
                    anchor,
                    ndcX,
                    ndcY,
                    windowDepth,
                    sourceIndex,
                    -1
            );
        }

        private Vector3f depthBias(GuiAnchor anchor,
                                   float distance,
                                   boolean towardCamera) {
            if (inverseModelViewProjection == null
                    || depthOrder == DepthOrder.UNORDERED
                    || distance <= 0.0f) {
                return new Vector3f();
            }
            ProjectedGuiAnchor projected = project(anchor, -1);
            if (projected == null) {
                return new Vector3f();
            }

            float targetWindowDepth = shiftedWindowDepth(
                    projected.windowDepth,
                    depthRangeNear,
                    depthRangeFar,
                    depthOrder == DepthOrder.GREATER,
                    towardCamera,
                    distance
            );
            float depthSpan = depthRangeFar - depthRangeNear;
            float targetNdcZ = ((targetWindowDepth - depthRangeNear)
                    / depthSpan) * 2.0f - 1.0f;
            Vector4f unprojected = inverseModelViewProjection.transform(
                    new Vector4f(
                            projected.ndcX,
                            projected.ndcY,
                            targetNdcZ,
                            1.0f
                    )
            );
            if (!Float.isFinite(unprojected.x)
                    || !Float.isFinite(unprojected.y)
                    || !Float.isFinite(unprojected.z)
                    || !Float.isFinite(unprojected.w)
                    || Math.abs(unprojected.w) <= DEPTH_EPSILON) {
                return new Vector3f();
            }

            float inverseW = 1.0f / unprojected.w;
            Vector3f bias = new Vector3f(
                    unprojected.x * inverseW - anchor.x,
                    unprojected.y * inverseW - anchor.y,
                    unprojected.z * inverseW - anchor.z
            );
            if (!Float.isFinite(bias.x)
                    || !Float.isFinite(bias.y)
                    || !Float.isFinite(bias.z)) {
                return new Vector3f();
            }
            return bias;
        }
    }

    private record GuiBounds(float minX, float minY, float minZ,
                             float maxX, float maxY, float maxZ) {
        private static GuiBounds of(List<GuiAnchor> anchors) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (GuiAnchor anchor : anchors) {
                minX = Math.min(minX, anchor.x);
                minY = Math.min(minY, anchor.y);
                minZ = Math.min(minZ, anchor.z);
                maxX = Math.max(maxX, anchor.x);
                maxY = Math.max(maxY, anchor.y);
                maxZ = Math.max(maxZ, anchor.z);
            }
            if (!Float.isFinite(minX) || !Float.isFinite(minY)
                    || !Float.isFinite(minZ) || !Float.isFinite(maxX)
                    || !Float.isFinite(maxY) || !Float.isFinite(maxZ)) {
                return new GuiBounds(0.0f, 0.0f, 0.0f,
                        1.0f, 1.0f, 1.0f);
            }
            return new GuiBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        private float width() {
            return Math.max(1.0f, maxX - minX);
        }

        private float height() {
            return Math.max(1.0f, maxY - minY);
        }

        private float centerX() {
            return (minX + maxX) * 0.5f;
        }

        private float centerY() {
            return (minY + maxY) * 0.5f;
        }

        private float centerZ() {
            return (minZ + maxZ) * 0.5f;
        }
    }

    private enum GuiPuffType {
        BROAD_HAZE(1.00f, 0.00f, 0.05f, 1.18f, 0.46f, 1.00f, 1.00f, 0.45f, 0.08f),
        CORE_HAZE(0.72f, 0.06f, 0.25f, 0.92f, 0.58f, 0.92f, 1.08f, 0.70f, 0.10f),
        WISP(0.10f, 0.18f, 1.00f, 0.76f, 0.60f, 0.62f, 1.48f, 1.80f, 0.18f),
        HOT_FLECK(0.00f, 1.00f, 0.25f, 0.34f, 0.40f, 0.42f, 1.38f, 1.00f, 0.30f);

        private final float broadWeight;
        private final float sparkWeight;
        private final float wispWeight;
        private final float sizeScale;
        private final float alphaScale;
        private final float stretchX;
        private final float stretchY;
        private final float riseScale;
        private final float rotationScale;

        GuiPuffType(float broadWeight,
                    float sparkWeight,
                    float wispWeight,
                    float sizeScale,
                    float alphaScale,
                    float stretchX,
                    float stretchY,
                    float riseScale,
                    float rotationScale) {
            this.broadWeight = broadWeight;
            this.sparkWeight = sparkWeight;
            this.wispWeight = wispWeight;
            this.sizeScale = sizeScale;
            this.alphaScale = alphaScale;
            this.stretchX = stretchX;
            this.stretchY = stretchY;
            this.riseScale = riseScale;
            this.rotationScale = rotationScale;
        }

        private static GuiPuffType forIndex(int index) {
            return switch (Math.floorMod(index, 10)) {
                case 0, 1 -> BROAD_HAZE;
                case 2, 3, 4, 5 -> CORE_HAZE;
                case 6, 7, 8 -> WISP;
                default -> HOT_FLECK;
            };
        }

        private float outwardScale() {
            return switch (this) {
                case BROAD_HAZE -> 0.35f;
                case CORE_HAZE -> 0.50f;
                case WISP -> 0.90f;
                case HOT_FLECK -> 0.70f;
            };
        }
    }
}
