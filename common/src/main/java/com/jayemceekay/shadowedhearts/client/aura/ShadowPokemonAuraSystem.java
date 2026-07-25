package com.jayemceekay.shadowedhearts.client.aura;

import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.geom.SphereBuffers;
import com.jayemceekay.shadowedhearts.client.render.rendertypes.AuraRenderTypes;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.mixin.RenderTargetAccessor;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Penumbra-derived FBO aura for shadow Pokemon.
 *
 * <p>The base aura is driven by large density puffs emitted from Cobblemon's
 * posed model bones, with the old hitbox emitter kept only as a fallback.
 * That keeps the aura flexible across very different Pokemon proportions
 * while preserving the soft density-field look of the Penumbra trail.
 */
public final class ShadowPokemonAuraSystem {

    private static final ResourceLocation DENSITY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "shadowedhearts",
            "textures/particle/penumbra_trail.png"
    );

    private static final int FULLBRIGHT = 0x00F000F0;
    private static final int MAX_PUFFS = 15000;
    private static final int MIN_BONE_ANCHORS = 160;
    private static final int MAX_BONE_ANCHORS = 900;
    private static final int BONE_ANCHOR_CANDIDATE_MULTIPLIER = 4;
    private static final float BONE_ANCHOR_VOLUME_STEP = 900.0f;
    private static final float BONE_ANCHOR_SURFACE_AREA_STEP = 420.0f;
    private static final int MIN_BONE_EMITTERS_PER_TICK = 54;
    private static final int MAX_BONE_EMITTERS_PER_TICK = 120;
    private static final float BONE_EMITTER_ACTIVE_FRACTION = 0.32f;
    private static final int BODY_ANCHOR_FILL_EMITTERS_PER_TICK = 22;
    private static final int BODY_VOLUME_EMITTERS_PER_TICK = 18;
    private static final boolean ENABLE_HITBOX_AURA_EMITTERS = true;
    private static final float BODY_ANCHOR_FILL_STRENGTH = 0.18f;
    private static final float BODY_VOLUME_FILL_STRENGTH = 0.32f;
    private static final int SMALL_MODEL_UPPER_FILL_EMITTERS_PER_TICK = 18;
    private static final float SMALL_MODEL_UPPER_FILL_STRENGTH = 0.16f;
    private static final float BODY_CUBE_ANCHOR_BUDGET_FRACTION = 0.58f;
    private static final float MODEL_ANCHOR_RECENTER_BLEND = 0.62f;
    private static final float MODEL_ANCHOR_RECENTER_MAX_BLOCKS = 0.85f;
    private static final float MIN_AURA_CUBE_AXIS = 0.20f;
    private static final float MIN_AURA_SOLID_VOLUME = 7.0f;
    private static final float MIN_AURA_FLAT_AREA = 7.0f;
    private static final float MIN_BODY_SHELL_VOLUME = 420.0f;
    private static final float SMALL_MODEL_COVERAGE_SIZE = 1.18f;
    private static final float MIN_CHAINABLE_CUBE_AXIS = 3.25f;
    private static final float BONE_CHAIN_SAMPLE_SPACING = 0.22f;
    private static final float BONE_CHAIN_MIN_LENGTH = 0.16f;
    private static final float BONE_CHAIN_MAX_LENGTH = 1.35f;
    private static final int BONE_CHAIN_MAX_SAMPLES = 10;
    private static final int BONE_CHAIN_MAX_RING_POINTS = 4;
    private static final int MIN_ADAPTIVE_BONE_ANCHORS = 72;
    private static final float MIN_AURA_QUALITY = 0.22f;
    private static final float MIN_MASK_RENDER_QUALITY = 0.30f;
    private static final float QUALITY_NEAR_DISTANCE = 9.0f;
    private static final float QUALITY_FAR_DISTANCE = 38.0f;
    private static final int MIN_ADAPTIVE_PUFFS = 8500;
    private static final int PUFF_LIMIT_PER_EXTRA_SOURCE_DROP = 1400;
    private static final boolean DEBUG_EMITTER_MARKERS = false;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final MultiBufferSource.BufferSource MASK_BUFFERS =
            MultiBufferSource.immediate(new ByteBufferBuilder(786432));
    private static final MultiBufferSource.BufferSource DEBUG_MARKER_BUFFERS =
            MultiBufferSource.immediate(new ByteBufferBuilder(262144));
    private static final MultiBufferSource.BufferSource CLASSIFICATION_BUFFERS =
            MultiBufferSource.immediate(new ByteBufferBuilder(786432));

    private static final List<Puff> ACTIVE = new ArrayList<>();
    private static final Map<Integer, SourceState> SOURCES = new ConcurrentHashMap<>();
    private static final Map<ModelPart.Cube, CubeAuraTemplate> CUBE_TEMPLATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static long lastTickGameTime = Long.MIN_VALUE;
    private static long lastRenderFrameToken = Long.MIN_VALUE;
    private static long lastMaskFrameToken = Long.MIN_VALUE;
    private static boolean irisCompositePending;
    private static RenderedAnchorCapture activeRenderedAnchorCapture;
    private static boolean maskCapturedThisFrame;
    private static boolean renderingModelMask;

    private ShadowPokemonAuraSystem() {}

    public static void observe(PokemonEntity entity,
                               double x, double y, double z,
                               float radius, float height,
                               float fade, float corruption,
                               float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || fade <= 0.001f || corruption <= 0.01f) {
            return;
        }

        long gameTime = mc.level.getGameTime();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        float quality = auraQualityFor(entity, cameraPos, x, y, z, gameTime);
        SourceState state = SOURCES.computeIfAbsent(entity.getId(), id -> new SourceState(x, y, z));
        state.lastSeenTick = gameTime;
        state.fade = fade;
        state.corruption = corruption;
        state.quality = quality;

        int emissionInterval = boneEmissionInterval(quality);
        if (state.lastEmitTick != Long.MIN_VALUE && gameTime - state.lastEmitTick < emissionInterval) {
            state.x = x;
            state.y = y;
            state.z = z;
            return;
        }

        double dx = x - state.x;
        double dy = y - state.y;
        double dz = z - state.z;
        Vec3 tickMotion = new Vec3(dx, dy, dz);
        Vec3 entityMotion = entity.getDeltaMovement();
        Vec3 motion = tickMotion.lengthSqr() > entityMotion.lengthSqr() ? tickMotion : entityMotion;
        double speed = motion.length();

        state.x = x;
        state.y = y;
        state.z = z;
        state.lastEmitTick = gameTime;

        if (gameTime - state.lastBoneTick <= 2L) {
            return;
        }

        float safeRadius = Mth.clamp(radius, 0.22f, 4.0f);
        float safeHeight = Mth.clamp(height, 0.35f, 6.0f);
        float intensity = Mth.clamp(fade * (0.45f + corruption * 0.75f), 0.0f, 1.25f);

        float animationPhase = (entity.tickCount + partialTicks) * 0.18f + state.seed;
        float breathing = 0.82f + 0.18f * (float) Math.sin(animationPhase * 1.7f);
        int baseCount = Math.max(5, Math.min(30, (int) (5 + safeRadius * 4.0f + safeHeight * 1.2f)));
        int motionBonus = Math.min(18, (int) (speed * 48.0));
        int count = Math.max(1, (int) ((baseCount + motionBonus) * intensity * breathing * adaptiveEmitterScale(quality)));

        Vec3 motionDir = speed > 0.0001 ? motion.normalize() : Vec3.ZERO;
        for (int i = 0; i < count; i++) {
            PuffType type = pickType(speed);
            spawnPuff(x, y, z, safeRadius, safeHeight, fade, corruption, motionDir, speed, animationPhase, type);
        }
    }

    public static void beginRenderedModelAnchorCapture(PokemonEntity entity, PoseStack renderStack, Bone rootPart) {
        activeRenderedAnchorCapture = null;
        if (entity == null || renderStack == null || rootPart == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (isIrisShadowRenderActive()) {
            return;
        }

        float strength = AuraEmitters.getFboAuraMaskStrength(entity);
        if (strength <= 0.001f) {
            return;
        }

        float partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(true);
        long gameTime = mc.level.getGameTime();
        double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        float quality = auraQualityFor(entity, cameraPos, x, y, z, gameTime);

        SourceState state = SOURCES.computeIfAbsent(entity.getId(), id -> new SourceState(x, y, z));
        state.lastSeenTick = gameTime;
        state.lastBoneTick = gameTime;
        state.fade = strength;
        state.corruption = 1.0f;
        state.quality = quality;
        state.x = x;
        state.y = y;
        state.z = z;

        if (debugShadowAuraEmittersEnabled()) {
            renderDebugModelClassification(entity, renderStack, rootPart);
        }

        int emissionInterval = boneEmissionInterval(quality);
        if (state.lastBoneEmitTick != Long.MIN_VALUE && gameTime - state.lastBoneEmitTick < emissionInterval) {
            return;
        }

        Vector3f rawModelOrigin = renderStack.last().pose().transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
        Vec3 sourceWorld = new Vec3(x, y, z);
        Vec3 poseToWorldOffset = sourceWorld.subtract(rawModelOrigin.x, rawModelOrigin.y, rawModelOrigin.z);
        int anchorBudget = adaptiveAnchorBudget(boneAnchorBudget(estimateModelGeometry(rootPart, 0)), quality);
        int candidateBudget = Math.min(MAX_BONE_ANCHORS * BONE_ANCHOR_CANDIDATE_MULTIPLIER,
                Math.max(anchorBudget, anchorBudget * BONE_ANCHOR_CANDIDATE_MULTIPLIER));
        int bodyAnchorBudget = Math.min(anchorBudget - 24, Math.max(42, Math.round(anchorBudget * BODY_CUBE_ANCHOR_BUDGET_FRACTION)));

        activeRenderedAnchorCapture = new RenderedAnchorCapture(
                entity,
                state,
                poseToWorldOffset,
                sourceWorld,
                strength,
                partialTicks,
                quality,
                gameTime,
                anchorBudget,
                candidateBudget,
                bodyAnchorBudget
        );
    }

    public static void captureRenderedModelPart(ModelPart part, PoseStack stack) {
        RenderedAnchorCapture capture = activeRenderedAnchorCapture;
        if (capture == null || part == null || stack == null || capture.anchorCandidates.size() >= capture.candidateBudget) {
            return;
        }

        int depth = capture.renderedPartStack.size() + 1;
        RenderedPartSample sample = renderedPartSample(part, stack, capture.poseToWorldOffset);
        RenderedPartNode parent = capture.renderedPartStack.isEmpty()
                ? null
                : capture.renderedPartStack.get(capture.renderedPartStack.size() - 1);

        if (sample.chainable && parent != null && parent.chainable && capture.anchorCandidates.size() < capture.candidateBudget) {
            collectRenderedBoneChainAnchors(
                    parent.geometryWorld,
                    sample.geometryWorld,
                    !sample.hasChainableDescendant,
                    capture.anchorCandidates,
                    depth,
                    capture.candidateBudget
            );
        }

        int anchorsBefore = capture.anchorCandidates.size();
        collectCubeAnchors(
                part,
                stack,
                capture.poseToWorldOffset,
                capture.anchorCandidates,
                depth,
                capture.candidateBudget,
                capture.bodyAnchorBudget,
                capture.bodyAnchorCount
        );

        if (sample.chainable
                && capture.anchorCandidates.size() < capture.candidateBudget
                && (capture.anchorCandidates.size() == anchorsBefore || depth <= 2)) {
            capture.anchorCandidates.add(new BoneAnchor(sample.geometryWorld, depth, 1.0f, AnchorRole.JOINT, AnchorSource.JOINT, Vec3.ZERO));
        }

        capture.renderedPartStack.add(new RenderedPartNode(part, sample.geometryWorld, sample.chainable));
    }

    public static void endRenderedModelPart(ModelPart part) {
        RenderedAnchorCapture capture = activeRenderedAnchorCapture;
        if (capture == null || part == null || capture.renderedPartStack.isEmpty()) {
            return;
        }

        int last = capture.renderedPartStack.size() - 1;
        if (capture.renderedPartStack.get(last).part == part) {
            capture.renderedPartStack.remove(last);
            return;
        }

        for (int i = last - 1; i >= 0; i--) {
            if (capture.renderedPartStack.get(i).part == part) {
                capture.renderedPartStack.subList(i, capture.renderedPartStack.size()).clear();
                return;
            }
        }
    }

    public static void endRenderedModelAnchorCapture(PokemonEntity entity) {
        RenderedAnchorCapture capture = activeRenderedAnchorCapture;
        activeRenderedAnchorCapture = null;
        if (capture == null || capture.entity != entity) {
            return;
        }

        List<BoneAnchor> anchors = downsampleBoneAnchors(capture.anchorCandidates, capture.anchorBudget);
        if (anchors.isEmpty()) {
            return;
        }

        Vec3 recenterOffset = modelAnchorRecenterOffset(capture.entity, anchors, capture.sourceWorld);
        if (recenterOffset.lengthSqr() > 0.000001) {
            anchors = shiftedBoneAnchors(anchors, recenterOffset);
        }

        spawnBoneAuraPuffs(capture.entity, capture.state, anchors, capture.strength, capture.partialTicks, capture.quality);
        spawnBodyAnchorFillPuffs(capture.entity, capture.state, anchors, capture.strength, capture.partialTicks, capture.quality);
        spawnSmallModelUpperAnchorFillPuffs(capture.entity, capture.state, anchors, capture.strength, capture.partialTicks, capture.quality);
        if (ENABLE_HITBOX_AURA_EMITTERS) {
            spawnBodyVolumeAuraPuffs(capture.entity, capture.state, capture.strength, capture.partialTicks, capture.quality);
        }
        updateDebugAnchors(capture.state, anchors);

        capture.state.lastBoneAnchors.clear();
        for (BoneAnchor anchor : anchors) {
            capture.state.lastBoneAnchors.add(anchor.pos);
        }
        capture.state.lastBoneEmitTick = capture.gameTime;
    }

    public static void onPokemonDespawn(int entityId) {
        SOURCES.remove(entityId);
    }

    public static void clear() {
        synchronized (ACTIVE) {
            ACTIVE.clear();
        }
        SOURCES.clear();
        lastTickGameTime = Long.MIN_VALUE;
        lastRenderFrameToken = Long.MIN_VALUE;
        lastMaskFrameToken = Long.MIN_VALUE;
        activeRenderedAnchorCapture = null;
        maskCapturedThisFrame = false;
        renderingModelMask = false;
    }

    public static void renderModelMask(PokemonEntity entity,
                                       ResourceLocation texture,
                                       RenderContext context,
                                       PoseStack stack,
                                       Bone rootPart) {
        if (renderingModelMask || entity == null || texture == null || context == null || rootPart == null) {
            return;
        }
        if (ModShaders.SHADOW_POKEMON_AURA_MASK == null) {
            return;
        }

        float strength = AuraEmitters.getFboAuraMaskStrength(entity);
        if (strength <= 0.001f) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        float partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        float quality = auraQualityFor(entity, cameraPos, x, y, z, mc.level.getGameTime());
        if (!debugShadowAuraEmittersEnabled() && quality < MIN_MASK_RENDER_QUALITY) {
            return;
        }

        long frameToken = currentFrameToken(mc, partialTicks);
        if (frameToken != lastMaskFrameToken) {
            lastMaskFrameToken = frameToken;
            maskCapturedThisFrame = false;
        }

        boolean clearForFirstMask = !maskCapturedThisFrame;
        if (!ShadowPokemonAuraFBO.beginDensityPass(clearForFirstMask, clearForFirstMask)) {
            return;
        }

        try {
            renderingModelMask = true;
            setupMaskUniforms(entity);
            RenderType maskType = AuraRenderTypes.shadowPokemonAuraMask(texture);
            VertexConsumer consumer = MASK_BUFFERS.getBuffer(maskType);
            rootPart.render(context, stack, consumer, FULLBRIGHT, OverlayTexture.NO_OVERLAY, packMaskColor(entity, strength));
            MASK_BUFFERS.endBatch(maskType);
            maskCapturedThisFrame = true;
        } finally {
            renderingModelMask = false;
            ShadowPokemonAuraFBO.endDensityPass();
        }

        if (debugShadowAuraEmittersEnabled()) {
            renderDebugModelClassification(entity, stack, rootPart);
        }
    }

    public static boolean isIrisShaderPackActive() {
        return AuraPulseRenderer.IRIS_HANDLER != null && AuraPulseRenderer.IRIS_HANDLER.isShaderPackInUse();
    }

    private static boolean isIrisShadowRenderActive() {
        return AuraPulseRenderer.IRIS_HANDLER != null && AuraPulseRenderer.IRIS_HANDLER.isShadowRenderActive();
    }

    public static void renderIris() {
        renderIrisDensityOnly();
    }

    public static void renderIrisDensityOnly() {
        if (!isIrisShaderPackActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        renderDensityPipelineInternal(camera, camera.getPartialTickTime(), false);
    }

    public static void compositeIris() {
        if (!irisCompositePending || !isIrisShaderPackActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            irisCompositePending = false;
            return;
        }

        RenderTarget main = mc.getMainRenderTarget();
        RenderTargetAccessor access = (RenderTargetAccessor) (Object) main;
        main.bindWrite(false);
        RenderSystem.viewport(0, 0, access.getWidth(), access.getHeight());

        ShadowPokemonAuraFBO.blur();
        ShadowPokemonAuraFBO.composite();

        irisCompositePending = false;
    }

    public static void renderDensityPipeline(Camera camera, float partialTicks, Matrix4f projectionMatrix) {
        if (projectionMatrix == null) {
            renderDensityPipelineInternal(camera, partialTicks, true);
            return;
        }

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        try {
            RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
            renderDensityPipelineInternal(camera, partialTicks, true);
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }

    public static void renderDensityPipeline(Camera camera, float partialTicks) {
        renderDensityPipelineInternal(camera, partialTicks, true);
    }

    private static void renderDensityPipelineInternal(Camera camera,
                                                      float partialTicks,
                                                      boolean compositeNow) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || camera == null) {
            clear();
            return;
        }

        long gameTime = mc.level.getGameTime();
        long frameToken = currentFrameToken(mc, partialTicks);
        if (frameToken == lastRenderFrameToken) {
            return;
        }
        lastRenderFrameToken = frameToken;

        tickToGameTime(gameTime);
        SOURCES.entrySet().removeIf(e -> gameTime - e.getValue().lastSeenTick > 30);

        boolean hasPuffs;
        synchronized (ACTIVE) {
            hasPuffs = !ACTIVE.isEmpty();
        }
        boolean hasModelMask = maskCapturedThisFrame && lastMaskFrameToken == frameToken;
        boolean debugMarkers = debugEmitterMarkersEnabled() && hasDebugAnchors();

        if (!hasPuffs && !hasModelMask && !debugMarkers) {
            return;
        }

        if (hasPuffs && ModShaders.SHADOW_POKEMON_AURA_DENSITY == null) {
            if (!hasModelMask) {
                return;
            }
            hasPuffs = false;
        }

        if (hasPuffs) {
            if (!ShadowPokemonAuraFBO.beginDensityPass(!hasModelMask, true)) {
                return;
            }

            try {
                renderDensitySplats(camera, partialTicks);
            } finally {
                ShadowPokemonAuraFBO.endDensityPass();
            }
        }

        if (hasPuffs || hasModelMask) {
            if (compositeNow) {
                ShadowPokemonAuraFBO.blur();
                ShadowPokemonAuraFBO.composite();
                irisCompositePending = false;
            } else {
                irisCompositePending = true;
            }
        }

        if (debugMarkers) {
            renderDebugAnchors(camera);
        }
    }

    private static boolean debugShadowAuraEmittersEnabled() {
        return ShadowedHeartsConfigs.getInstance().getClientConfig().debugShadowAuraEmitters();
    }

    private static boolean debugEmitterMarkersEnabled() {
        return DEBUG_EMITTER_MARKERS && debugShadowAuraEmittersEnabled();
    }

    private static boolean hasDebugAnchors() {
        for (SourceState state : SOURCES.values()) {
            if (!state.debugAnchors.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void updateDebugAnchors(SourceState state, List<BoneAnchor> anchors) {
        if (!debugEmitterMarkersEnabled()) {
            state.debugAnchors.clear();
            state.activeDebugAnchors.clear();
            return;
        }

        state.debugAnchors.clear();
        for (BoneAnchor anchor : anchors) {
            state.debugAnchors.add(new DebugAnchor(anchor.pos, anchor.role, anchor.source));
        }
    }

    private static void renderDebugAnchors(Camera camera) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Matrix4f savedModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        Quaternionf viewRot = new Quaternionf(camera.rotation()).conjugate();
        RenderSystem.getModelViewMatrix().set(new Matrix4f().rotation(viewRot));

        Vec3 camPos = camera.getPosition();
        RenderType renderType = AuraRenderTypes.shadowPokemonAuraDebugMarkers();
        VertexConsumer consumer = DEBUG_MARKER_BUFFERS.getBuffer(renderType);

        for (SourceState state : SOURCES.values()) {
            for (DebugAnchor anchor : state.debugAnchors) {
                drawDebugAnchorMarker(consumer, camPos, anchor, false);
            }

            for (DebugAnchor anchor : state.activeDebugAnchors) {
                drawDebugAnchorMarker(consumer, camPos, anchor, true);
            }
        }

        DEBUG_MARKER_BUFFERS.endBatch(renderType);

        RenderSystem.getModelViewMatrix().set(savedModelView);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
    }

    public static void renderDebugHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options.hideGui || !debugShadowAuraEmittersEnabled()) {
            return;
        }

        Vec3 focus = mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null
                ? mc.gameRenderer.getMainCamera().getPosition()
                : new Vec3(0.0, 0.0, 0.0);
        ClassificationStats classificationStats = debugClassificationStats(focus);
        if (classificationStats.total > 0) {
            renderClassificationDebugHud(graphics, mc, classificationStats);
            return;
        }

        if (!debugEmitterMarkersEnabled()) {
            return;
        }

        DebugAnchorStats stats = debugAnchorStats(focus);
        if (stats.total == 0) {
            return;
        }

        String title = "Shadow Aura Anchors";
        String totalLine = "Anchors: " + stats.total + "  Active: " + stats.active + "  Sets: " + stats.sources;
        String roleLine = "B " + stats.body + "  J " + stats.joint + "  A " + stats.appendage + "  S " + stats.surface + "  T " + stats.tip;
        String sourceLine = "Src C " + stats.cube + "  Ch " + stats.chain + "  Jn " + stats.jointSource;
        String activeRoleLine = "Act B " + stats.activeBody + "  J " + stats.activeJoint + "  A " + stats.activeAppendage
                + "  S " + stats.activeSurface + "  T " + stats.activeTip;
        int x = 8;
        int y = 8;
        int padding = 5;
        int width = Math.max(
                Math.max(mc.font.width(title), mc.font.width(totalLine)),
                Math.max(Math.max(mc.font.width(roleLine), mc.font.width(sourceLine)), mc.font.width(activeRoleLine))
        ) + padding * 2;
        int height = 54;

        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xAA120018);
        graphics.fill(x, y, x + width, y + height, 0xC0181024);
        graphics.drawString(mc.font, title, x + padding, y + 4, 0xFFFFD86A, true);
        graphics.drawString(mc.font, totalLine, x + padding, y + 14, 0xFFFFF7D0, true);
        graphics.drawString(mc.font, roleLine, x + padding, y + 24, 0xFFE8B6FF, true);
        graphics.drawString(mc.font, sourceLine, x + padding, y + 34, 0xFFD0F0FF, true);
        graphics.drawString(mc.font, activeRoleLine, x + padding, y + 44, 0xFFD6FFD8, true);
    }

    private static void renderClassificationDebugHud(GuiGraphics graphics, Minecraft mc, ClassificationStats stats) {
        String title = "Shadow Aura Cube Classes";
        String totalLine = "Cubes: " + stats.total + "  Sets: " + stats.sources + "  Empty parts: " + stats.empty;
        String roleLine = "Chain " + stats.chainBody + "  Body " + stats.body + "  App " + stats.appendage
                + "  Surf " + stats.surface;
        String ignoredLine = "Ignored " + stats.ignoredDetail + "  Colors: G body-chain, C body, R app, Y surf, M ignored";
        int x = 8;
        int y = 8;
        int padding = 5;
        int width = Math.max(
                Math.max(mc.font.width(title), mc.font.width(totalLine)),
                Math.max(mc.font.width(roleLine), mc.font.width(ignoredLine))
        ) + padding * 2;
        int height = 44;

        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xAA120018);
        graphics.fill(x, y, x + width, y + height, 0xC0181024);
        graphics.drawString(mc.font, title, x + padding, y + 4, 0xFFFFD86A, true);
        graphics.drawString(mc.font, totalLine, x + padding, y + 14, 0xFFFFF7D0, true);
        graphics.drawString(mc.font, roleLine, x + padding, y + 24, 0xFFE8B6FF, true);
        graphics.drawString(mc.font, ignoredLine, x + padding, y + 34, 0xFFD0F0FF, true);
    }

    private static ClassificationStats debugClassificationStats(Vec3 focus) {
        SourceState selected = null;
        int sources = 0;
        double nearestDist = Double.MAX_VALUE;
        for (SourceState state : SOURCES.values()) {
            if (state.lastClassificationStats.total == 0) {
                continue;
            }

            sources++;
            double dist = focus.distanceToSqr(state.x, state.y, state.z);
            if (dist < nearestDist) {
                nearestDist = dist;
                selected = state;
            }
        }

        if (selected == null) {
            return ClassificationStats.EMPTY;
        }

        ClassificationStats stats = selected.lastClassificationStats;
        return new ClassificationStats(stats.total, sources, stats.chainBody, stats.body, stats.appendage,
                stats.surface, stats.ignoredDetail, stats.empty);
    }

    private static DebugAnchorStats debugAnchorStats(Vec3 focus) {
        SourceState selected = null;
        int sources = 0;
        double nearestDist = Double.MAX_VALUE;
        for (SourceState state : SOURCES.values()) {
            if (state.debugAnchors.isEmpty()) {
                continue;
            }

            sources++;
            double dist = focus.distanceToSqr(state.x, state.y, state.z);
            if (dist < nearestDist) {
                nearestDist = dist;
                selected = state;
            }
        }

        if (selected == null) {
            return DebugAnchorStats.EMPTY;
        }

        int total = 0;
        int active = 0;
        int body = 0;
        int joint = 0;
        int appendage = 0;
        int surface = 0;
        int tip = 0;
        int activeBody = 0;
        int activeJoint = 0;
        int activeAppendage = 0;
        int activeSurface = 0;
        int activeTip = 0;
        int cube = 0;
        int chain = 0;
        int jointSource = 0;

        total += selected.debugAnchors.size();
        active += selected.activeDebugAnchors.size();
        for (DebugAnchor anchor : selected.debugAnchors) {
            switch (anchor.role) {
                case BODY -> body++;
                case JOINT -> joint++;
                case APPENDAGE -> appendage++;
                case SURFACE -> surface++;
                case TIP -> tip++;
            }
            switch (anchor.source) {
                case CUBE -> cube++;
                case CHAIN -> chain++;
                case JOINT -> jointSource++;
            }
        }

        for (DebugAnchor anchor : selected.activeDebugAnchors) {
            switch (anchor.role) {
                case BODY -> activeBody++;
                case JOINT -> activeJoint++;
                case APPENDAGE -> activeAppendage++;
                case SURFACE -> activeSurface++;
                case TIP -> activeTip++;
            }
        }

        return new DebugAnchorStats(total, active, sources, body, joint, appendage, surface, tip, cube, chain, jointSource,
                activeBody, activeJoint, activeAppendage, activeSurface, activeTip);
    }

    private static void drawDebugAnchorMarker(VertexConsumer consumer, Vec3 camPos, DebugAnchor anchor, boolean active) {
        double rx = anchor.pos.x - camPos.x;
        double ry = anchor.pos.y - camPos.y;
        double rz = anchor.pos.z - camPos.z;

        PoseStack stack = new PoseStack();
        stack.translate(rx, ry, rz);
        float radius = debugAnchorRadius(anchor.role) * (active ? 1.72f : 1.0f);
        stack.scale(radius, radius, radius);

        float[] color = debugAnchorColor(anchor.role, anchor.source, active);
        SphereBuffers.drawUnitSphereLod(consumer, stack.last().pose(), color[0], color[1], color[2], color[3], 0);
    }

    private static float debugAnchorRadius(AnchorRole role) {
        return switch (role) {
            case TIP -> 0.050f;
            case APPENDAGE -> 0.043f;
            case SURFACE -> 0.046f;
            case JOINT -> 0.038f;
            case BODY -> 0.034f;
        };
    }

    private static float[] debugAnchorColor(AnchorRole role, AnchorSource source, boolean active) {
        if (source == AnchorSource.CHAIN) {
            return active
                    ? new float[] {1.00f, 0.95f, 0.18f, 1.0f}
                    : new float[] {1.00f, 0.82f, 0.04f, 0.82f};
        }
        if (source == AnchorSource.JOINT) {
            return active
                    ? new float[] {1.00f, 0.25f, 1.00f, 1.0f}
                    : new float[] {0.90f, 0.18f, 1.00f, 0.74f};
        }

        if (active) {
            return switch (role) {
                case BODY -> new float[] {0.00f, 0.92f, 1.00f, 1.0f};
                case JOINT -> new float[] {0.70f, 0.45f, 1.00f, 1.0f};
                case APPENDAGE -> new float[] {1.00f, 0.08f, 0.02f, 1.0f};
                case SURFACE -> new float[] {1.00f, 0.72f, 0.02f, 1.0f};
                case TIP -> new float[] {0.35f, 1.00f, 0.08f, 1.0f};
            };
        }

        return switch (role) {
            case BODY -> new float[] {0.00f, 0.62f, 1.00f, 0.64f};
            case JOINT -> new float[] {0.56f, 0.34f, 1.00f, 0.70f};
            case APPENDAGE -> new float[] {1.00f, 0.04f, 0.02f, 0.72f};
            case SURFACE -> new float[] {1.00f, 0.56f, 0.00f, 0.78f};
            case TIP -> new float[] {0.28f, 1.00f, 0.06f, 0.86f};
        };
    }

    private static void renderDebugModelClassification(PokemonEntity entity,
                                                       PoseStack stack,
                                                       Bone rootPart) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || entity == null || stack == null || rootPart == null) {
            return;
        }

        RenderType type = AuraRenderTypes.shadowPokemonAuraClassificationBoxes();
        VertexConsumer consumer = CLASSIFICATION_BUFFERS.getBuffer(type);
        ClassificationCounter counter = new ClassificationCounter();
        PoseStack debugStack = copyTopPose(stack);

        renderClassifiedBone(rootPart, debugStack, consumer, counter, 0);
        CLASSIFICATION_BUFFERS.endBatch(type);

        float partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(true);
        double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        SourceState state = SOURCES.computeIfAbsent(entity.getId(), id -> new SourceState(x, y, z));
        state.x = x;
        state.y = y;
        state.z = z;
        state.lastClassificationStats = counter.toStats();
    }

    private static void renderClassifiedBone(Bone bone,
                                             PoseStack stack,
                                             VertexConsumer consumer,
                                             ClassificationCounter counter,
                                             int depth) {
        if (bone == null || depth > 24) {
            return;
        }

        stack.pushPose();
        bone.transform(stack);

        if (bone instanceof ModelPart part) {
            if (part.cubes.isEmpty()) {
                counter.add(PartClassification.EMPTY);
            } else {
                for (ModelPart.Cube cube : part.cubes) {
                    PartClassification classification = classifyModelCube(cube);
                    counter.add(classification);
                    int color = classificationColor(classification);
                    drawClassificationBox(consumer, stack.last().pose(), cube, color);
                }
            }
        }

        Map<String, Bone> children = bone.getChildren();
        if (children != null && !children.isEmpty()) {
            for (Bone child : children.values()) {
                renderClassifiedBone(child, stack, consumer, counter, depth + 1);
            }
        }

        stack.popPose();
    }

    private static PartClassification classifyModelCube(ModelPart.Cube cube) {
        return cubeTemplate(cube).classification;
    }

    private static int classificationColor(PartClassification classification) {
        return switch (classification) {
            case CHAIN_BODY -> 0x804CFF5E;
            case BODY -> 0x8030D6FF;
            case APPENDAGE -> 0x80FF583A;
            case SURFACE -> 0x80FFD63A;
            case IGNORED_DETAIL -> 0x78FF3FB8;
            case EMPTY -> 0x604D4D5C;
        };
    }

    private static void drawClassificationBox(VertexConsumer consumer, Matrix4f pose, ModelPart.Cube cube, int color) {
        float minX = cube.minX / 16.0f;
        float minY = cube.minY / 16.0f;
        float minZ = cube.minZ / 16.0f;
        float maxX = cube.maxX / 16.0f;
        float maxY = cube.maxY / 16.0f;
        float maxZ = cube.maxZ / 16.0f;

        float inflate = 0.026f;
        if (Math.abs(maxX - minX) < 0.003f) {
            minX -= inflate;
            maxX += inflate;
        } else {
            minX -= inflate;
            maxX += inflate;
        }
        if (Math.abs(maxY - minY) < 0.003f) {
            minY -= inflate;
            maxY += inflate;
        } else {
            minY -= inflate;
            maxY += inflate;
        }
        if (Math.abs(maxZ - minZ) < 0.003f) {
            minZ -= inflate;
            maxZ += inflate;
        } else {
            minZ -= inflate;
            maxZ += inflate;
        }

        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;

        addBoxQuad(consumer, pose, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, r, g, b, a);
        addBoxQuad(consumer, pose, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        addBoxQuad(consumer, pose, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, r, g, b, a);
        addBoxQuad(consumer, pose, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        addBoxQuad(consumer, pose, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        addBoxQuad(consumer, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, r, g, b, a);
    }

    private static void addBoxQuad(VertexConsumer consumer,
                                   Matrix4f pose,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2,
                                   float x3, float y3, float z3,
                                   float x4, float y4, float z4,
                                   int r, int g, int b, int a) {
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        consumer.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
    }

    private static long currentFrameToken(Minecraft mc, float partialTicks) {
        long frameToken = mc.getFrameTimeNs();
        if (frameToken == 0L && mc.level != null) {
            frameToken = (mc.level.getGameTime() << 20) ^ (long) (partialTicks * 1000.0f);
        }
        return frameToken;
    }

    private static int packMaskColor(PokemonEntity entity, float strength) {
        boolean hyperMode = entity.getAspects() != null && entity.getAspects().contains(SHAspects.HYPER_MODE);
        int alpha = Math.round(Mth.clamp(strength, 0.0f, 1.0f) * 255.0f);
        int hot = hyperMode ? 58 : 24;
        int core = hyperMode ? 232 : 206;
        return (alpha << 24) | (hot << 16) | (core << 8) | 255;
    }

    private static void setupMaskUniforms(PokemonEntity entity) {
        var shader = ModShaders.SHADOW_POKEMON_AURA_MASK;
        if (shader == null) {
            return;
        }

        float modelSize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        float expand = Mth.clamp(modelSize * 0.075f, 0.045f, 0.22f);
        float screenExpand = Mth.clamp(0.0045f + modelSize * 0.0008f, 0.0045f, 0.010f);

        Uniform uExpand = shader.getUniform("AuraExpand");
        if (uExpand != null) {
            uExpand.set(expand);
        }
        Uniform uScreenExpand = shader.getUniform("AuraScreenExpand");
        if (uScreenExpand != null) {
            uScreenExpand.set(screenExpand);
        }
    }

    private static PoseStack copyTopPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    private static int boneAnchorBudget(ModelGeometryStats geometry) {
        float volumeContribution = geometry.volume / BONE_ANCHOR_VOLUME_STEP;
        float surfaceContribution = geometry.surfaceArea / BONE_ANCHOR_SURFACE_AREA_STEP;
        int scaledBudget = MIN_BONE_ANCHORS + (int) Math.ceil(volumeContribution + surfaceContribution);
        return Mth.clamp(scaledBudget, MIN_BONE_ANCHORS, MAX_BONE_ANCHORS);
    }

    private static float auraQualityFor(PokemonEntity entity, Vec3 cameraPos, double x, double y, double z, long gameTime) {
        float modelSize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        double centerY = y + entity.getBbHeight() * 0.5;
        double distance = cameraPos.distanceTo(new Vec3(x, centerY, z));
        float near = Math.max(QUALITY_NEAR_DISTANCE, modelSize * 3.0f);
        float far = Math.max(QUALITY_FAR_DISTANCE, modelSize * 10.0f);
        float distanceFade = 1.0f - smoothstep(near, far, (float) distance);
        float distanceQuality = MIN_AURA_QUALITY + (1.0f - MIN_AURA_QUALITY) * distanceFade;
        float pressureQuality = sourcePressureQuality(activeAuraSourceCount(gameTime));
        return Mth.clamp(distanceQuality * pressureQuality, MIN_AURA_QUALITY, 1.0f);
    }

    private static int activeAuraSourceCount(long gameTime) {
        int count = 0;
        for (SourceState state : SOURCES.values()) {
            if (gameTime - state.lastSeenTick <= 20L) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private static float sourcePressureQuality(int activeSources) {
        return Mth.clamp(1.0f - Math.max(0, activeSources - 1) * 0.08f, 0.58f, 1.0f);
    }

    private static int boneEmissionInterval(float quality) {
        if (quality >= 0.78f) {
            return 1;
        }
        if (quality >= 0.52f) {
            return 2;
        }
        if (quality >= 0.34f) {
            return 3;
        }
        return 4;
    }

    private static int adaptiveAnchorBudget(int baseBudget, float quality) {
        float scale = 0.38f + quality * 0.62f;
        return Mth.clamp(Math.round(baseBudget * scale), MIN_ADAPTIVE_BONE_ANCHORS, MAX_BONE_ANCHORS);
    }

    private static float adaptiveEmitterScale(float quality) {
        return Mth.clamp(0.30f + quality * 0.70f, 0.30f, 1.0f);
    }

    private static float smallModelCoverageBoost(float modelSize) {
        return 1.0f - smoothstep(0.66f, SMALL_MODEL_COVERAGE_SIZE, modelSize);
    }

    private static ModelGeometryStats estimateModelGeometry(Bone bone, int depth) {
        if (bone == null || depth > 24) {
            return ModelGeometryStats.EMPTY;
        }

        float volume = 0.0f;
        float surfaceArea = 0.0f;
        if (bone instanceof ModelPart part && !part.cubes.isEmpty()) {
            for (ModelPart.Cube cube : part.cubes) {
                CubeAuraTemplate template = cubeTemplate(cube);
                if (!template.auraAnchor) {
                    continue;
                }

                volume += template.volume;
                surfaceArea += template.surfaceArea;
            }
        }

        Map<String, Bone> children = bone.getChildren();
        if (children != null && !children.isEmpty()) {
            for (Bone child : children.values()) {
                ModelGeometryStats childStats = estimateModelGeometry(child, depth + 1);
                volume += childStats.volume;
                surfaceArea += childStats.surfaceArea;
            }
        }

        return new ModelGeometryStats(volume, surfaceArea);
    }

    private static RenderedPartSample renderedPartSample(ModelPart part, PoseStack stack, Vec3 poseToWorldOffset) {
        boolean auraRenderable = false;
        boolean chainable = false;
        for (ModelPart.Cube cube : part.cubes) {
            CubeAuraTemplate template = cubeTemplate(cube);
            auraRenderable |= template.auraAnchor;
            chainable |= template.chainable;
        }

        Vec3 originWorld = poseOriginWorld(stack, poseToWorldOffset);
        Vec3 geometryWorld = auraRenderable
                ? partGeometryCenterWorld(part, stack, poseToWorldOffset, chainable)
                : originWorld;
        return new RenderedPartSample(geometryWorld, chainable, hasChainableDescendant(part, 0));
    }

    private static Vec3 poseOriginWorld(PoseStack stack, Vec3 poseToWorldOffset) {
        Vector3f origin = stack.last().pose().transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
        return new Vec3(origin.x + poseToWorldOffset.x, origin.y + poseToWorldOffset.y, origin.z + poseToWorldOffset.z);
    }

    private static boolean isChainablePart(ModelPart part) {
        for (ModelPart.Cube cube : part.cubes) {
            if (cubeTemplate(cube).chainable) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasChainableDescendant(ModelPart part, int depth) {
        if (part == null || depth > 24 || part.children.isEmpty()) {
            return false;
        }

        for (ModelPart child : part.children.values()) {
            if (isChainablePart(child) || hasChainableDescendant(child, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 partGeometryCenterWorld(ModelPart part, PoseStack stack, Vec3 poseToWorldOffset, boolean chainableOnly) {
        Matrix4f pose = stack.last().pose();
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        double totalWeight = 0.0;

        for (ModelPart.Cube cube : part.cubes) {
            CubeAuraTemplate template = cubeTemplate(cube);
            if (chainableOnly ? !template.chainable : !template.auraAnchor) {
                continue;
            }

            double weight = Math.max(template.volume, template.surfaceArea * 0.18);
            Vector3f localCenter = template.center;
            Vector3f center = pose.transformPosition(
                    localCenter.x / 16.0f,
                    localCenter.y / 16.0f,
                    localCenter.z / 16.0f,
                    new Vector3f()
            );
            x += (center.x + poseToWorldOffset.x) * weight;
            y += (center.y + poseToWorldOffset.y) * weight;
            z += (center.z + poseToWorldOffset.z) * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0) {
            Vector3f origin = pose.transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
            return new Vec3(origin.x + poseToWorldOffset.x, origin.y + poseToWorldOffset.y, origin.z + poseToWorldOffset.z);
        }

        return new Vec3(x / totalWeight, y / totalWeight, z / totalWeight);
    }

    private static void collectCubeAnchors(ModelPart part,
                                           PoseStack stack,
                                           Vec3 poseToWorldOffset,
                                           List<BoneAnchor> anchors,
                                           int depth,
                                           int anchorBudget,
                                           int bodyAnchorBudget,
                                           int[] bodyAnchorCount) {
        Matrix4f pose = stack.last().pose();
        for (ModelPart.Cube cube : part.cubes) {
            if (anchors.size() >= anchorBudget) {
                return;
            }
            CubeAuraTemplate template = cubeTemplate(cube);
            if (!template.auraAnchor) {
                continue;
            }

            AnchorRole role = template.role;
            if (role == AnchorRole.BODY && bodyAnchorCount[0] >= bodyAnchorBudget) {
                continue;
            }

            if (role == AnchorRole.SURFACE) {
                collectSurfaceAnchors(template, stack, poseToWorldOffset, anchors, depth, anchorBudget);
                continue;
            }

            if (template.shellCube) {
                collectCubeShellAnchors(template, pose, poseToWorldOffset, anchors, depth, anchorBudget, bodyAnchorBudget, bodyAnchorCount);
            }

            for (Vector3f local : template.samples) {
                if (anchors.size() >= anchorBudget) {
                    break;
                }
                if (role == AnchorRole.BODY && bodyAnchorCount[0] >= bodyAnchorBudget) {
                    break;
                }

                Vector3f p = pose.transformPosition(local.x / 16.0f, local.y / 16.0f, local.z / 16.0f, new Vector3f());
                anchors.add(new BoneAnchor(new Vec3(p.x + poseToWorldOffset.x, p.y + poseToWorldOffset.y, p.z + poseToWorldOffset.z), depth, template.sizeWeight, role, AnchorSource.CUBE, Vec3.ZERO));
                if (role == AnchorRole.BODY) {
                    bodyAnchorCount[0]++;
                }
            }
        }
    }

    private static void collectSurfaceAnchors(CubeAuraTemplate template,
                                              PoseStack stack,
                                              Vec3 poseToWorldOffset,
                                              List<BoneAnchor> anchors,
                                              int depth,
                                              int anchorBudget) {
        Matrix4f pose = stack.last().pose();
        SurfaceAxes axes = template.surfaceAxes;
        Vector3f normalA = transformedSurfaceNormal(stack, axes.normalAxis, 1.0f);
        Vector3f normalB = transformedSurfaceNormal(stack, axes.normalAxis, -1.0f);

        for (int i = 0; i < template.samples.length && anchors.size() < anchorBudget; i++) {
            Vector3f local = template.samples[i];
            Vector3f p = pose.transformPosition(local.x / 16.0f, local.y / 16.0f, local.z / 16.0f, new Vector3f());
            Vector3f n = (i & 1) == 0 ? normalA : normalB;
            anchors.add(new BoneAnchor(
                    new Vec3(p.x + poseToWorldOffset.x, p.y + poseToWorldOffset.y, p.z + poseToWorldOffset.z),
                    depth,
                    template.sizeWeight,
                    AnchorRole.SURFACE,
                    AnchorSource.CUBE,
                    new Vec3(n.x, n.y, n.z)
            ));
        }
    }

    private static void collectCubeShellAnchors(CubeAuraTemplate template,
                                                Matrix4f pose,
                                                Vec3 poseToWorldOffset,
                                                List<BoneAnchor> anchors,
                                                int depth,
                                                int anchorBudget,
                                                int bodyAnchorBudget,
                                                int[] bodyAnchorCount) {
        for (Vector3f local : template.shellPoints) {
            if (anchors.size() >= anchorBudget || bodyAnchorCount[0] >= bodyAnchorBudget) {
                break;
            }
            Vector3f p = pose.transformPosition(local.x / 16.0f, local.y / 16.0f, local.z / 16.0f, new Vector3f());
            anchors.add(new BoneAnchor(
                    new Vec3(p.x + poseToWorldOffset.x, p.y + poseToWorldOffset.y, p.z + poseToWorldOffset.z),
                    depth,
                    template.sizeWeight * 1.08f,
                    AnchorRole.BODY,
                    AnchorSource.CUBE,
                    Vec3.ZERO
            ));
            bodyAnchorCount[0]++;
        }
    }

    private static int cubeShellPointCount(ModelPart.Cube cube) {
        float volume = rawCubeAxis(cube.maxX, cube.minX) * rawCubeAxis(cube.maxY, cube.minY) * rawCubeAxis(cube.maxZ, cube.minZ);
        return volume >= 900.0f ? 14 : 8;
    }

    private static Vector3f cubeShellPoint(ModelPart.Cube cube, int index) {
        if (index < 8) {
            return new Vector3f(
                    (index & 1) == 0 ? cube.minX : cube.maxX,
                    (index & 2) == 0 ? cube.minY : cube.maxY,
                    (index & 4) == 0 ? cube.minZ : cube.maxZ
            );
        }

        float cx = (cube.minX + cube.maxX) * 0.5f;
        float cy = (cube.minY + cube.maxY) * 0.5f;
        float cz = (cube.minZ + cube.maxZ) * 0.5f;
        return switch (index - 8) {
            case 0 -> new Vector3f(cube.minX, cy, cz);
            case 1 -> new Vector3f(cube.maxX, cy, cz);
            case 2 -> new Vector3f(cx, cube.minY, cz);
            case 3 -> new Vector3f(cx, cube.maxY, cz);
            case 4 -> new Vector3f(cx, cy, cube.minZ);
            default -> new Vector3f(cx, cy, cube.maxZ);
        };
    }

    private static SurfaceAxes surfaceAxes(ModelPart.Cube cube) {
        float sx = rawCubeAxis(cube.maxX, cube.minX);
        float sy = rawCubeAxis(cube.maxY, cube.minY);
        float sz = rawCubeAxis(cube.maxZ, cube.minZ);
        if (sx <= sy && sx <= sz) {
            return new SurfaceAxes(0, 1, 2);
        }
        if (sy <= sx && sy <= sz) {
            return new SurfaceAxes(1, 0, 2);
        }
        return new SurfaceAxes(2, 0, 1);
    }

    private static Vector3f surfaceSample(ModelPart.Cube cube, SurfaceAxes axes, int index, int sampleCount) {
        float[] min = {cube.minX, cube.minY, cube.minZ};
        float[] max = {cube.maxX, cube.maxY, cube.maxZ};
        float[] pos = {
                (cube.minX + cube.maxX) * 0.5f,
                (cube.minY + cube.maxY) * 0.5f,
                (cube.minZ + cube.maxZ) * 0.5f
        };

        float u;
        float v;
        if (index < 4) {
            u = (index & 1) == 0 ? 0.0f : 1.0f;
            v = (index & 2) == 0 ? 0.0f : 1.0f;
        } else if (index < 8) {
            int edge = index - 4;
            u = switch (edge) {
                case 0 -> 0.5f;
                case 1 -> 0.5f;
                case 2 -> 0.0f;
                default -> 1.0f;
            };
            v = switch (edge) {
                case 0 -> 0.0f;
                case 1 -> 1.0f;
                case 2 -> 0.5f;
                default -> 0.5f;
            };
        } else {
            int sample = index - 7;
            u = halton(sample, 2);
            v = halton(sample, 3);
        }

        pos[axes.uAxis] = Mth.lerp(u, min[axes.uAxis], max[axes.uAxis]);
        pos[axes.vAxis] = Mth.lerp(v, min[axes.vAxis], max[axes.vAxis]);
        pos[axes.normalAxis] = (min[axes.normalAxis] + max[axes.normalAxis]) * 0.5f;
        return new Vector3f(pos[0], pos[1], pos[2]);
    }

    private static Vector3f transformedSurfaceNormal(PoseStack stack, int axis, float sign) {
        Vector3f normal = switch (axis) {
            case 0 -> new Vector3f(sign, 0.0f, 0.0f);
            case 1 -> new Vector3f(0.0f, sign, 0.0f);
            default -> new Vector3f(0.0f, 0.0f, sign);
        };
        stack.last().normal().transform(normal);
        if (normal.lengthSquared() < 0.0001f) {
            return new Vector3f(0.0f, sign, 0.0f);
        }
        return normal.normalize();
    }

    private static void collectRenderedBoneChainAnchors(Vec3 parentWorld,
                                                        Vec3 childWorld,
                                                        boolean tip,
                                                        List<BoneAnchor> anchors,
                                                        int depth,
                                                        int anchorBudget) {
        if (anchors.size() >= anchorBudget) {
            return;
        }

        Vec3 segment = childWorld.subtract(parentWorld);
        double length = segment.length();
        if (length < BONE_CHAIN_MIN_LENGTH || length > BONE_CHAIN_MAX_LENGTH) {
            return;
        }

        Vec3 dir = segment.scale(1.0 / length);
        AnchorRole role = tip ? AnchorRole.TIP : AnchorRole.APPENDAGE;
        int sampleCount = Mth.clamp((int) Math.ceil(length / BONE_CHAIN_SAMPLE_SPACING) + (tip ? 0 : 1), 1, BONE_CHAIN_MAX_SAMPLES);
        int ringPoints = boneChainRingPoints(length, tip);
        float sizeWeight = Mth.clamp(0.82f + (float) length * 0.24f, 0.88f, tip ? 1.12f : 1.28f);
        float tubeRadius = Mth.clamp((float) length * 0.13f, 0.055f, tip ? 0.14f : 0.28f);

        for (int i = 0; i < sampleCount && anchors.size() < anchorBudget; i++) {
            float t = sampleCount == 1 ? 0.5f : (i + 0.5f) / sampleCount;
            Vec3 center = parentWorld.lerp(childWorld, t);
            for (int ring = 0; ring < ringPoints && anchors.size() < anchorBudget; ring++) {
                Vec3 pos = center.add(radialTubeOffset(dir, i, ring, ringPoints, tubeRadius));
                anchors.add(new BoneAnchor(pos, depth, sizeWeight, role, AnchorSource.CHAIN, Vec3.ZERO));
            }
        }
    }

    private static CubeAuraTemplate cubeTemplate(ModelPart.Cube cube) {
        CubeAuraTemplate cached = CUBE_TEMPLATES.get(cube);
        if (cached != null) {
            return cached;
        }

        CubeAuraTemplate created = createCubeTemplate(cube);
        CUBE_TEMPLATES.put(cube, created);
        return created;
    }

    private static CubeAuraTemplate createCubeTemplate(ModelPart.Cube cube) {
        float rawSx = rawCubeAxis(cube.maxX, cube.minX);
        float rawSy = rawCubeAxis(cube.maxY, cube.minY);
        float rawSz = rawCubeAxis(cube.maxZ, cube.minZ);
        float sx = Math.max(0.001f, rawSx);
        float sy = Math.max(0.001f, rawSy);
        float sz = Math.max(0.001f, rawSz);
        float longest = Math.max(sx, Math.max(sy, sz));
        float volume = rawSx * rawSy * rawSz;
        float surfaceArea = 2.0f * (rawSx * rawSy + rawSx * rawSz + rawSy * rawSz);
        boolean auraAnchor = isAuraAnchorCubeRaw(rawSx, rawSy, rawSz);
        boolean chainable = isChainableCubeRaw(rawSx, rawSy, rawSz);
        Vector3f center = new Vector3f(
                (cube.minX + cube.maxX) * 0.5f,
                (cube.minY + cube.maxY) * 0.5f,
                (cube.minZ + cube.maxZ) * 0.5f
        );

        if (!auraAnchor) {
            PartClassification classification = chainable ? PartClassification.CHAIN_BODY : PartClassification.IGNORED_DETAIL;
            return new CubeAuraTemplate(false, chainable, AnchorRole.BODY, classification, volume, surfaceArea,
                    0.0f, false, center, null, CubeAuraTemplate.EMPTY_POINTS, CubeAuraTemplate.EMPTY_POINTS);
        }

        AnchorRole role = cubeAnchorRole(sx, sy, sz);
        PartClassification classification = chainable ? PartClassification.CHAIN_BODY : switch (role) {
            case BODY -> PartClassification.BODY;
            case APPENDAGE -> PartClassification.APPENDAGE;
            case SURFACE -> PartClassification.SURFACE;
            case JOINT, TIP -> PartClassification.EMPTY;
        };

        int maxSamples = role == AnchorRole.SURFACE ? 26 : 18;
        boolean shellCube = role == AnchorRole.BODY && volume >= MIN_BODY_SHELL_VOLUME;
        int sampleCount = Mth.clamp((int) Math.ceil(volume / 1850.0f), 1, maxSamples);
        sampleCount = Math.max(sampleCount, Mth.clamp((int) Math.ceil(longest / 12.0f), 1, 8));
        sampleCount = Math.max(sampleCount, Mth.clamp((int) Math.ceil(surfaceArea / 560.0f), 1, maxSamples));
        sampleCount = Math.max(sampleCount, compactCubeSampleFloor(role, volume, surfaceArea, longest));
        if (role == AnchorRole.SURFACE) {
            sampleCount = Math.max(sampleCount, Mth.clamp((int) Math.ceil(longest / 6.0f), 3, maxSamples));
        } else if (shellCube) {
            sampleCount = Math.min(sampleCount, 6);
        }

        SurfaceAxes surfaceAxes = role == AnchorRole.SURFACE ? surfaceAxes(cube) : null;
        Vector3f[] samples = new Vector3f[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = role == AnchorRole.SURFACE
                    ? surfaceSample(cube, surfaceAxes, i, sampleCount)
                    : cubeVolumeSample(cube, i, sampleCount);
        }

        Vector3f[] shellPoints = CubeAuraTemplate.EMPTY_POINTS;
        if (shellCube) {
            int pointCount = cubeShellPointCount(cube);
            shellPoints = new Vector3f[pointCount];
            for (int i = 0; i < pointCount; i++) {
                shellPoints[i] = cubeShellPoint(cube, i);
            }
        }

        float sizeWeight = Mth.clamp(0.78f + longest / 68.0f + (role == AnchorRole.SURFACE ? 0.10f : 0.0f), 0.78f, 1.22f);
        return new CubeAuraTemplate(true, chainable, role, classification, volume, surfaceArea, sizeWeight,
                shellCube, center, surfaceAxes, samples, shellPoints);
    }

    private static int compactCubeSampleFloor(AnchorRole role, float volume, float surfaceArea, float longest) {
        return switch (role) {
            case BODY -> volume < MIN_BODY_SHELL_VOLUME
                    ? Mth.clamp((int) Math.ceil(Math.max(surfaceArea / 52.0f, longest / 3.8f)), 2, 5)
                    : 1;
            case APPENDAGE -> longest < 8.0f ? 2 : 1;
            case SURFACE -> 3;
            case JOINT, TIP -> 1;
        };
    }

    private static boolean isAuraAnchorCubeRaw(float sx, float sy, float sz) {
        int solidAxes = 0;
        if (sx >= MIN_AURA_CUBE_AXIS) solidAxes++;
        if (sy >= MIN_AURA_CUBE_AXIS) solidAxes++;
        if (sz >= MIN_AURA_CUBE_AXIS) solidAxes++;

        if (solidAxes >= 3) {
            return sx * sy * sz >= MIN_AURA_SOLID_VOLUME;
        }
        if (solidAxes == 2) {
            float[] axes = sortedCubeAxes(sx, sy, sz);
            return axes[1] * axes[2] >= MIN_AURA_FLAT_AREA;
        }
        return false;
    }

    private static boolean isChainableCubeRaw(float sx, float sy, float sz) {
        return sx >= MIN_CHAINABLE_CUBE_AXIS
                && sy >= MIN_CHAINABLE_CUBE_AXIS
                && sz >= MIN_CHAINABLE_CUBE_AXIS;
    }

    private static float rawCubeAxis(float max, float min) {
        return Math.max(0.0f, Math.abs(max - min));
    }

    private static float[] sortedCubeAxes(float sx, float sy, float sz) {
        float a = Math.min(sx, Math.min(sy, sz));
        float c = Math.max(sx, Math.max(sy, sz));
        float b = sx + sy + sz - a - c;
        return new float[] {a, b, c};
    }

    private static int boneChainRingPoints(double length, boolean tip) {
        if (tip) {
            return length >= 0.34 ? 2 : 1;
        }
        if (length >= 0.46) {
            return BONE_CHAIN_MAX_RING_POINTS;
        }
        return length >= 0.28 ? 3 : 2;
    }

    private static Vec3 radialTubeOffset(Vec3 dir, int sampleIndex, int ringIndex, int ringPoints, float radius) {
        Vec3 basisA = Math.abs(dir.y) < 0.92 ? dir.cross(new Vec3(0.0, 1.0, 0.0)) : dir.cross(new Vec3(1.0, 0.0, 0.0));
        if (basisA.lengthSqr() < 0.0001) {
            basisA = new Vec3(1.0, 0.0, 0.0);
        } else {
            basisA = basisA.normalize();
        }
        Vec3 basisB = dir.cross(basisA);
        if (basisB.lengthSqr() < 0.0001) {
            basisB = new Vec3(0.0, 0.0, 1.0);
        } else {
            basisB = basisB.normalize();
        }

        float twist = halton(sampleIndex + 1, 2) * 0.37f;
        float angle = ((ringIndex / (float) ringPoints) + twist) * Mth.TWO_PI;
        float r = radius * (ringPoints == 1 ? 0.58f : 0.88f);
        return basisA.scale(Math.cos(angle) * r).add(basisB.scale(Math.sin(angle) * r));
    }

    private static List<BoneAnchor> downsampleBoneAnchors(List<BoneAnchor> candidates, int anchorBudget) {
        if (candidates.size() <= anchorBudget) {
            return candidates;
        }

        List<BoneAnchor> anchors = new ArrayList<>(anchorBudget);
        boolean[] selected = new boolean[candidates.size()];

        addEvenlyByRole(candidates, selected, anchors, anchorBudget, AnchorRole.JOINT,
                Math.max(16, Math.round(anchorBudget * 0.075f)));
        addEvenlyByRole(candidates, selected, anchors, anchorBudget, AnchorRole.TIP,
                Math.max(22, Math.round(anchorBudget * 0.10f)));
        addEvenlyByRole(candidates, selected, anchors, anchorBudget, AnchorRole.SURFACE,
                Math.max(38, Math.round(anchorBudget * 0.18f)));
        addEvenlyByRole(candidates, selected, anchors, anchorBudget, AnchorRole.APPENDAGE,
                Math.max(64, Math.round(anchorBudget * 0.28f)));
        addEvenlyByRole(candidates, selected, anchors, anchorBudget, AnchorRole.BODY,
                Math.max(112, Math.round(anchorBudget * 0.38f)));

        List<Integer> remaining = new ArrayList<>(candidates.size() - anchors.size());
        for (int i = 0; i < candidates.size(); i++) {
            if (!selected[i]) {
                remaining.add(i);
            }
        }
        addEvenlyFromIndices(candidates, remaining, selected, anchors, anchorBudget, anchorBudget - anchors.size());

        return anchors;
    }

    private static Vec3 modelAnchorRecenterOffset(PokemonEntity entity, List<BoneAnchor> anchors, Vec3 sourceWorld) {
        if (anchors.isEmpty()) {
            return Vec3.ZERO;
        }

        AnchorCenter bodyCenter = anchorCenter(anchors, true);
        AnchorCenter center = bodyCenter.count > Math.max(24, anchors.size() / 5)
                ? bodyCenter
                : anchorCenter(anchors, false);
        if (center.count == 0) {
            return Vec3.ZERO;
        }

        double dx = sourceWorld.x - center.x;
        double dz = sourceWorld.z - center.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.035) {
            return Vec3.ZERO;
        }

        float modelSize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        float smallModelBoost = smallModelCoverageBoost(modelSize);
        double maxCorrection = Mth.clamp(modelSize * Mth.lerp(smallModelBoost, 0.16f, 0.24f), 0.18f, MODEL_ANCHOR_RECENTER_MAX_BLOCKS);
        double correction = Math.min(distance, maxCorrection) * Mth.clamp(MODEL_ANCHOR_RECENTER_BLEND + smallModelBoost * 0.20f, 0.0f, 0.88f);
        double scale = correction / distance;
        return new Vec3(dx * scale, 0.0, dz * scale);
    }

    private static AnchorCenter anchorCenter(List<BoneAnchor> anchors, boolean bodyOnly) {
        double sumX = 0.0;
        double sumZ = 0.0;
        double weightSum = 0.0;
        int count = 0;
        for (BoneAnchor anchor : anchors) {
            if (bodyOnly && anchor.role != AnchorRole.BODY && anchor.role != AnchorRole.JOINT) {
                continue;
            }

            double weight = anchorCenterWeight(anchor);
            sumX += anchor.pos.x * weight;
            sumZ += anchor.pos.z * weight;
            weightSum += weight;
            count++;
        }

        if (count == 0 || weightSum <= 0.000001) {
            return AnchorCenter.EMPTY;
        }
        return new AnchorCenter(sumX / weightSum, sumZ / weightSum, count);
    }

    private static double anchorCenterWeight(BoneAnchor anchor) {
        double roleWeight = switch (anchor.role) {
            case BODY -> 1.0;
            case JOINT -> 0.82;
            case APPENDAGE -> 0.42;
            case SURFACE -> 0.28;
            case TIP -> 0.20;
        };
        double sourceWeight = anchor.source == AnchorSource.CHAIN ? 1.12 : 1.0;
        return Math.max(0.05, anchor.sizeWeight * roleWeight * sourceWeight);
    }

    private static List<BoneAnchor> shiftedBoneAnchors(List<BoneAnchor> anchors, Vec3 offset) {
        List<BoneAnchor> shifted = new ArrayList<>(anchors.size());
        for (BoneAnchor anchor : anchors) {
            shifted.add(new BoneAnchor(
                    anchor.pos.add(offset),
                    anchor.depth,
                    anchor.sizeWeight,
                    anchor.role,
                    anchor.source,
                    anchor.normal
            ));
        }
        return shifted;
    }

    private static void addEvenlyByRole(List<BoneAnchor> candidates,
                                        boolean[] selected,
                                        List<BoneAnchor> anchors,
                                        int anchorBudget,
                                        AnchorRole role,
                                        int requestedCount) {
        List<Integer> roleIndices = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).role == role) {
                roleIndices.add(i);
            }
        }
        addEvenlyFromIndices(candidates, roleIndices, selected, anchors, anchorBudget, requestedCount);
    }

    private static void addEvenlyFromIndices(List<BoneAnchor> candidates,
                                             List<Integer> indices,
                                             boolean[] selected,
                                             List<BoneAnchor> anchors,
                                             int anchorBudget,
                                             int requestedCount) {
        int count = Math.min(requestedCount, Math.min(indices.size(), anchorBudget - anchors.size()));
        if (count <= 0) {
            return;
        }

        for (int pick = 0; pick < count && anchors.size() < anchorBudget; pick++) {
            int indexPos = Math.min(indices.size() - 1, (int) Math.floor((pick + 0.5f) * indices.size() / count));
            int candidateIndex = indices.get(indexPos);
            if (selected[candidateIndex]) {
                continue;
            }

            selected[candidateIndex] = true;
            anchors.add(candidates.get(candidateIndex));
        }
    }

    private static AnchorRole cubeAnchorRole(float sx, float sy, float sz) {
        float longest = Math.max(sx, Math.max(sy, sz));
        float shortest = Math.max(0.001f, Math.min(sx, Math.min(sy, sz)));
        float middle = sx + sy + sz - longest - shortest;
        float longRatio = longest / shortest;
        float panelRatio = middle / shortest;
        if (longRatio >= 3.2f && panelRatio >= 2.15f) {
            return AnchorRole.SURFACE;
        }
        return longRatio >= 3.2f ? AnchorRole.APPENDAGE : AnchorRole.BODY;
    }

    private static Vector3f cubeVolumeSample(ModelPart.Cube cube, int index, int sampleCount) {
        float x = Mth.lerp(halton(index + 1, 2), cube.minX, cube.maxX);
        float y = Mth.lerp(halton(index + 1, 3), cube.minY, cube.maxY);
        float z = Mth.lerp(halton(index + 1, 5), cube.minZ, cube.maxZ);

        if (sampleCount > 3 && (index % 4) == 0) {
            switch ((index / 4) % 6) {
                case 0 -> x = cube.minX;
                case 1 -> x = cube.maxX;
                case 2 -> y = cube.minY;
                case 3 -> y = cube.maxY;
                case 4 -> z = cube.minZ;
                default -> z = cube.maxZ;
            }
        }

        return new Vector3f(x, y, z);
    }

    private static float halton(int index, int base) {
        float f = 1.0f;
        float r = 0.0f;
        int i = index;
        while (i > 0) {
            f /= base;
            r += f * (i % base);
            i /= base;
        }
        return r;
    }

    private static int boneEmitterTargetCount(int anchorCount, float quality) {
        float emitterScale = adaptiveEmitterScale(quality);
        int minEmitters = Math.max(12, Math.round(MIN_BONE_EMITTERS_PER_TICK * emitterScale));
        int maxEmitters = Math.max(minEmitters, Math.round(MAX_BONE_EMITTERS_PER_TICK * emitterScale));
        int scaled = Math.round(anchorCount * BONE_EMITTER_ACTIVE_FRACTION * emitterScale);
        return Math.min(anchorCount, Mth.clamp(scaled, minEmitters, maxEmitters));
    }

    private static void spawnBoneAuraPuffs(PokemonEntity entity,
                                           SourceState state,
                                           List<BoneAnchor> anchors,
                                           float strength,
                                           float partialTicks,
                                           float quality) {
        int anchorCount = anchors.size();
        int targetCount = boneEmitterTargetCount(anchorCount, quality);

        Vec3 center = new Vec3(
                Mth.lerp(partialTicks, entity.xOld, entity.getX()),
                Mth.lerp(partialTicks, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.54,
                Mth.lerp(partialTicks, entity.zOld, entity.getZ())
        );
        Vec3 entityMotion = entity.getDeltaMovement();
        double speed = entityMotion.length();
        float modelSize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        float animationPhase = (entity.tickCount + partialTicks) * 0.18f + state.seed;
        float pulse = 0.84f + 0.16f * (float) Math.sin(animationPhase * 1.9f);
        float intensity = Mth.clamp(strength * pulse * (0.74f + quality * 0.14f), 0.0f, 1.18f);

        state.activeDebugAnchors.clear();
        List<DebugAnchor> activeDebugAnchors = debugEmitterMarkersEnabled() ? state.activeDebugAnchors : null;
        boolean[] used = new boolean[anchorCount];
        int tipBudget = Math.min(countAnchorsByRole(anchors, AnchorRole.TIP), Math.max(5, Math.round(targetCount * 0.16f)));
        int surfaceBudget = Math.min(countAnchorsByRole(anchors, AnchorRole.SURFACE), Math.max(12, Math.round(targetCount * 0.24f)));
        int appendageBudget = Math.min(countAnchorsByRole(anchors, AnchorRole.APPENDAGE), Math.max(14, Math.round(targetCount * 0.32f)));
        if (tipBudget + surfaceBudget > targetCount) {
            surfaceBudget = Math.max(0, targetCount - tipBudget);
        }
        if (tipBudget + surfaceBudget + appendageBudget > targetCount) {
            appendageBudget = Math.max(0, targetCount - tipBudget - surfaceBudget);
        }
        int emitted = 0;

        emitted += emitScheduledAnchors(anchors, state.lastBoneAnchors, used, AnchorRole.TIP, state.tipEmitterCursor, tipBudget,
                center, entityMotion, speed, modelSize, intensity, quality, activeDebugAnchors);
        state.tipEmitterCursor = advanceCursor(state.tipEmitterCursor, anchorCount, tipBudget);

        emitted += emitScheduledAnchors(anchors, state.lastBoneAnchors, used, AnchorRole.SURFACE, state.surfaceEmitterCursor, surfaceBudget,
                center, entityMotion, speed, modelSize, intensity, quality, activeDebugAnchors);
        state.surfaceEmitterCursor = advanceCursor(state.surfaceEmitterCursor, anchorCount, surfaceBudget);

        emitted += emitScheduledAnchors(anchors, state.lastBoneAnchors, used, AnchorRole.APPENDAGE, state.appendageEmitterCursor, appendageBudget,
                center, entityMotion, speed, modelSize, intensity, quality, activeDebugAnchors);
        state.appendageEmitterCursor = advanceCursor(state.appendageEmitterCursor, anchorCount, appendageBudget);

        int remaining = Math.max(0, targetCount - emitted);
        emitted += emitScheduledAnchors(anchors, state.lastBoneAnchors, used, null, state.boneEmitterCursor, remaining,
                center, entityMotion, speed, modelSize, intensity, quality, activeDebugAnchors);
        state.boneEmitterCursor = advanceCursor(state.boneEmitterCursor, anchorCount, Math.max(emitted, 1));
    }

    private static int countAnchorsByRole(List<BoneAnchor> anchors, AnchorRole role) {
        int count = 0;
        for (BoneAnchor anchor : anchors) {
            if (anchor.role == role) {
                count++;
            }
        }
        return count;
    }

    private static int advanceCursor(int cursor, int anchorCount, int step) {
        if (anchorCount <= 0) {
            return 0;
        }
        return Math.floorMod(cursor + Math.max(1, step), anchorCount);
    }

    private static int emitScheduledAnchors(List<BoneAnchor> anchors,
                                            List<Vec3> previousAnchors,
                                            boolean[] used,
                                            AnchorRole role,
                                            int cursor,
                                            int budget,
                                            Vec3 center,
                                            Vec3 entityMotion,
                                            double speed,
                                            float modelSize,
                                            float intensity,
                                            float quality,
                                            List<DebugAnchor> activeDebugAnchors) {
        if (budget <= 0 || anchors.isEmpty()) {
            return 0;
        }

        int emitted = 0;
        int anchorCount = anchors.size();
        int start = Math.floorMod(cursor, anchorCount);
        for (int step = 0; step < anchorCount && emitted < budget; step++) {
            int i = (start + step) % anchorCount;
            if (used[i]) {
                continue;
            }
            BoneAnchor anchor = anchors.get(i);
            if (role != null && anchor.role != role) {
                continue;
            }

            used[i] = true;
            emitBoneAnchorPuffs(anchor, previousAnchors, i, center, entityMotion, speed, modelSize, intensity, quality);
            if (activeDebugAnchors != null) {
                activeDebugAnchors.add(new DebugAnchor(anchor.pos, anchor.role, anchor.source));
            }
            emitted++;
        }
        return emitted;
    }

    private static void emitBoneAnchorPuffs(BoneAnchor anchor,
                                            List<Vec3> previousAnchors,
                                            int anchorIndex,
                                            Vec3 center,
                                            Vec3 entityMotion,
                                            double speed,
                                            float modelSize,
                                            float intensity,
                                            float quality) {
        Vec3 previous = previousAnchors != null && anchorIndex < previousAnchors.size()
                ? previousAnchors.get(anchorIndex)
                : anchor.pos;
        Vec3 boneMotion = anchor.pos.subtract(previous);
        float depthWeight = (anchor.depth <= 2 ? 1.02f : anchor.depth <= 5 ? 0.88f : 0.74f) * anchor.sizeWeight * anchor.role.sizeWeight;
        float roleIntensity = intensity * anchor.role.spawnWeight;
        float sourceDepthWeight = anchor.source == AnchorSource.CHAIN ? depthWeight * 1.16f : depthWeight;
        float sourceIntensity = anchor.source == AnchorSource.CHAIN ? roleIntensity * 1.08f : roleIntensity;
        float detailQuality = Mth.clamp((quality - 0.25f) / 0.75f, 0.0f, 1.0f);

        if (RANDOM.nextFloat() < (0.50f + quality * 0.12f) * sourceIntensity) {
            spawnBonePuff(anchor.pos, center, boneMotion, entityMotion, modelSize, sourceIntensity, sourceDepthWeight, anchor.role, anchor.normal, pickBoneAuraPuffType(quality), quality);
        }
        if (RANDOM.nextFloat() < anchor.role.wispChance * sourceIntensity * detailQuality) {
            spawnBonePuff(anchor.pos, center, boneMotion, entityMotion, modelSize, sourceIntensity, sourceDepthWeight, anchor.role, anchor.normal, PuffType.WISP, quality);
        }
        if (RANDOM.nextFloat() < anchor.role.hazeChance * sourceIntensity * (0.72f + quality * 0.28f)) {
            spawnBonePuff(anchor.pos, center, boneMotion, entityMotion, modelSize, sourceIntensity, sourceDepthWeight, anchor.role, anchor.normal, PuffType.BROAD_HAZE, quality);
        }
        if (anchor.source == AnchorSource.CHAIN && RANDOM.nextFloat() < 0.30f * sourceIntensity * quality) {
            spawnBonePuff(anchor.pos, center, boneMotion, entityMotion, modelSize, sourceIntensity, sourceDepthWeight * 1.10f, anchor.role, anchor.normal, PuffType.BROAD_HAZE, quality);
        }

        float fleckChance = (speed > 0.04 || boneMotion.lengthSqr() > 0.0005) ? 0.055f : 0.018f;
        float sourceSparkWeight = anchor.source == AnchorSource.CHAIN ? 0.72f : 1.0f;
        if (RANDOM.nextFloat() < fleckChance * anchor.role.sparkWeight * sourceSparkWeight * sourceIntensity * detailQuality) {
            spawnBonePuff(anchor.pos, center, boneMotion, entityMotion, modelSize, sourceIntensity, sourceDepthWeight, anchor.role, anchor.normal, PuffType.HOT_FLECK, quality);
        }
    }

    private static void spawnBodyAnchorFillPuffs(PokemonEntity entity,
                                                 SourceState state,
                                                 List<BoneAnchor> anchors,
                                                 float strength,
                                                 float partialTicks,
                                                 float quality) {
        int eligible = countBodyFillAnchors(anchors);
        if (eligible <= 0) {
            return;
        }

        Vec3 center = new Vec3(
                Mth.lerp(partialTicks, entity.xOld, entity.getX()),
                Mth.lerp(partialTicks, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.48,
                Mth.lerp(partialTicks, entity.zOld, entity.getZ())
        );
        float modelSize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        float smallModelBoost = smallModelCoverageBoost(modelSize);
        float intensity = Mth.clamp(strength * BODY_ANCHOR_FILL_STRENGTH * (0.76f + quality * 0.24f) * (1.0f + smallModelBoost * 0.70f), 0.0f, 0.34f);
        int count = Math.min(
                eligible,
                Math.max(2 + Math.round(smallModelBoost * 3.0f),
                        Math.round(BODY_ANCHOR_FILL_EMITTERS_PER_TICK * (1.0f + smallModelBoost * 1.35f) * intensity * adaptiveEmitterScale(quality)))
        );
        if (count <= 0) {
            return;
        }

        Vec3 entityMotion = entity.getDeltaMovement();
        int emitted = 0;
        int anchorCount = anchors.size();
        int start = Math.floorMod(state.bodyAnchorFillCursor, Math.max(1, anchorCount));
        for (int step = 0; step < anchorCount && emitted < count; step++) {
            int index = (start + step) % anchorCount;
            BoneAnchor anchor = anchors.get(index);
            if (!bodyFillAnchorEligible(anchor, center, modelSize)) {
                continue;
            }

            Vec3 previous = index < state.lastBoneAnchors.size() ? state.lastBoneAnchors.get(index) : anchor.pos;
            Vec3 boneMotion = anchor.pos.subtract(previous);
            float depthWeight = Mth.clamp(0.86f * anchor.sizeWeight * anchor.role.sizeWeight, 0.58f, 1.04f);
            spawnBonePuff(anchor.pos, center, boneMotion, entityMotion, modelSize, intensity, depthWeight, AnchorRole.BODY, Vec3.ZERO, PuffType.BROAD_HAZE, quality);
            emitted++;
        }

        state.bodyAnchorFillCursor = advanceCursor(state.bodyAnchorFillCursor, anchorCount, Math.max(emitted, 1));
    }

    private static int countBodyFillAnchors(List<BoneAnchor> anchors) {
        int count = 0;
        for (BoneAnchor anchor : anchors) {
            if (bodyFillAnchorEligible(anchor, Vec3.ZERO, Float.NaN)) {
                count++;
            }
        }
        return count;
    }

    private static boolean bodyFillAnchorEligible(BoneAnchor anchor, Vec3 center, float modelSize) {
        if (anchor.role != AnchorRole.BODY && anchor.role != AnchorRole.JOINT) {
            return false;
        }
        if (anchor.source == AnchorSource.CHAIN) {
            return false;
        }
        if (Float.isNaN(modelSize)) {
            return true;
        }

        double relativeY = anchor.pos.y - center.y;
        float smallModelBoost = smallModelCoverageBoost(modelSize);
        float lower = Mth.lerp(smallModelBoost, 0.48f, 0.56f);
        float upper = Mth.lerp(smallModelBoost, 0.34f, 0.50f);
        return relativeY >= -modelSize * lower && relativeY <= modelSize * upper;
    }

    private static void spawnSmallModelUpperAnchorFillPuffs(PokemonEntity entity,
                                                            SourceState state,
                                                            List<BoneAnchor> anchors,
                                                            float strength,
                                                            float partialTicks,
                                                            float quality) {
        float modelSize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        float smallModelBoost = smallModelCoverageBoost(modelSize);
        if (smallModelBoost <= 0.001f) {
            return;
        }

        Vec3 center = new Vec3(
                Mth.lerp(partialTicks, entity.xOld, entity.getX()),
                Mth.lerp(partialTicks, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.50,
                Mth.lerp(partialTicks, entity.zOld, entity.getZ())
        );
        int eligible = countSmallModelUpperFillAnchors(anchors, center, modelSize);
        if (eligible <= 0) {
            return;
        }

        float intensity = Mth.clamp(strength * SMALL_MODEL_UPPER_FILL_STRENGTH * smallModelBoost * (0.80f + quality * 0.20f), 0.0f, 0.22f);
        int count = Math.min(eligible, Math.max(2, Math.round(SMALL_MODEL_UPPER_FILL_EMITTERS_PER_TICK * intensity * adaptiveEmitterScale(quality))));
        if (count <= 0) {
            return;
        }

        Vec3 entityMotion = entity.getDeltaMovement();
        int emitted = 0;
        int anchorCount = anchors.size();
        int start = Math.floorMod(state.upperBodyAnchorFillCursor, Math.max(1, anchorCount));
        for (int step = 0; step < anchorCount && emitted < count; step++) {
            int index = (start + step) % anchorCount;
            BoneAnchor anchor = anchors.get(index);
            if (!smallModelUpperFillAnchorEligible(anchor, center, modelSize)) {
                continue;
            }

            Vec3 previous = index < state.lastBoneAnchors.size() ? state.lastBoneAnchors.get(index) : anchor.pos;
            Vec3 boneMotion = anchor.pos.subtract(previous);
            float heightWeight = Mth.clamp((float) ((anchor.pos.y - center.y) / Math.max(modelSize, 0.001f)) * 0.22f + 0.86f, 0.78f, 1.04f);
            float depthWeight = Mth.clamp(heightWeight * anchor.sizeWeight * 0.88f, 0.58f, 1.04f);
            PuffType type = RANDOM.nextFloat() < 0.72f ? PuffType.BROAD_HAZE : PuffType.CORE_HAZE;
            spawnBonePuff(anchor.pos, center, boneMotion, entityMotion, modelSize, intensity, depthWeight, AnchorRole.BODY, Vec3.ZERO, type, quality);
            emitted++;
        }

        state.upperBodyAnchorFillCursor = advanceCursor(state.upperBodyAnchorFillCursor, anchorCount, Math.max(emitted, 1));
    }

    private static int countSmallModelUpperFillAnchors(List<BoneAnchor> anchors, Vec3 center, float modelSize) {
        int count = 0;
        for (BoneAnchor anchor : anchors) {
            if (smallModelUpperFillAnchorEligible(anchor, center, modelSize)) {
                count++;
            }
        }
        return count;
    }

    private static boolean smallModelUpperFillAnchorEligible(BoneAnchor anchor, Vec3 center, float modelSize) {
        if (anchor.source == AnchorSource.CHAIN) {
            return false;
        }
        if (anchor.role != AnchorRole.BODY && anchor.role != AnchorRole.JOINT && anchor.role != AnchorRole.SURFACE) {
            return false;
        }

        double relativeY = anchor.pos.y - center.y;
        if (relativeY < -modelSize * 0.08 || relativeY > modelSize * 0.68) {
            return false;
        }

        double dx = anchor.pos.x - center.x;
        double dz = anchor.pos.z - center.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        return horizontalDistance <= modelSize * 0.58;
    }

    private static void spawnBodyVolumeAuraPuffs(PokemonEntity entity,
                                                 SourceState state,
                                                 float strength,
                                                 float partialTicks,
                                                 float quality) {
        float width = Math.max(0.22f, entity.getBbWidth());
        float height = Math.max(0.35f, entity.getBbHeight());
        float modelSize = Math.max(width, height);
        float smallModelBoost = smallModelCoverageBoost(modelSize);
        Vec3 center = new Vec3(
                Mth.lerp(partialTicks, entity.xOld, entity.getX()),
                Mth.lerp(partialTicks, entity.yOld, entity.getY()) + height * Mth.lerp(smallModelBoost, 0.34f, 0.43f),
                Mth.lerp(partialTicks, entity.zOld, entity.getZ())
        );
        Vec3 previousCenter = state.hasLastBodyCenter
                ? new Vec3(state.lastBodyX, state.lastBodyY, state.lastBodyZ)
                : center;
        Vec3 bodyMotion = center.subtract(previousCenter);
        state.lastBodyX = center.x;
        state.lastBodyY = center.y;
        state.lastBodyZ = center.z;
        state.hasLastBodyCenter = true;

        float xRadius = Math.max(0.15f, width * Mth.lerp(smallModelBoost, 0.54f, 0.34f));
        float yRadius = Math.max(0.16f, height * Mth.lerp(smallModelBoost, 0.34f, 0.39f));
        float zRadius = xRadius;
        float intensity = Mth.clamp(strength * BODY_VOLUME_FILL_STRENGTH * (0.72f + quality * 0.28f) * (1.0f - smallModelBoost * 0.18f), 0.0f, 0.36f);
        int count = Math.max(1, Math.round(BODY_VOLUME_EMITTERS_PER_TICK * (1.0f + smallModelBoost * 0.42f) * intensity * adaptiveEmitterScale(quality)));

        for (int i = 0; i < count; i++) {
            int sample = state.bodySampleCursor++;
            Vec3 local = lowerEllipsoidBodySample(sample, xRadius, yRadius, zRadius, smallModelBoost);
            Vec3 anchor = center.add(local);
            float depthWeight = 0.76f + 0.12f * halton(sample + 1, 7);

            spawnBonePuff(anchor, center, bodyMotion, entity.getDeltaMovement(), modelSize, intensity, depthWeight, AnchorRole.BODY, Vec3.ZERO, PuffType.BROAD_HAZE, quality);
        }
    }

    private static Vec3 lowerEllipsoidBodySample(int index, float xRadius, float yRadius, float zRadius, float compactness) {
        float u = halton(index + 1, 2);
        float v = halton(index + 1, 3);
        float shell = Mth.lerp(compactness, 0.42f, 0.18f) + Mth.lerp(compactness, 0.58f, 0.62f) * halton(index + 1, 5);
        float y = (float) Math.pow(v, 1.85f) * 2.0f - 1.0f;
        float ring = (float) Math.sqrt(Math.max(0.0f, 1.0f - y * y));
        float angle = u * Mth.TWO_PI;
        return new Vec3(
                Math.cos(angle) * ring * shell * xRadius,
                y * shell * yRadius,
                Math.sin(angle) * ring * shell * zRadius
        );
    }

    private static PuffType pickBoneAuraPuffType(float quality) {
        float roll = RANDOM.nextFloat();
        float wispChance = Mth.lerp(quality, 0.10f, 0.26f);
        float coreChance = Mth.lerp(quality, 0.72f, 0.62f);
        if (roll < coreChance) {
            return PuffType.CORE_HAZE;
        }
        if (roll < coreChance + wispChance) {
            return PuffType.WISP;
        }
        return PuffType.BROAD_HAZE;
    }

    private static void spawnBonePuff(Vec3 anchorPos,
                                      Vec3 center,
                                      Vec3 boneMotion,
                                      Vec3 entityMotion,
                                      float modelSize,
                                      float intensity,
                                      float depthWeight,
                                      AnchorRole role,
                                      Vec3 anchorNormal,
                                      PuffType type,
                                      float quality) {
        Vec3 outward = anchorPos.subtract(center);
        if (outward.lengthSqr() > 0.0001) {
            outward = outward.normalize();
        } else {
            outward = randomUnitVector();
        }
        if (role == AnchorRole.SURFACE && anchorNormal != null && anchorNormal.lengthSqr() > 0.0001) {
            outward = anchorNormal.normalize();
        }

        Vec3 jitter = randomUnitVector();
        float radiusScale = switch (type) {
            case BROAD_HAZE -> 0.18f;
            case CORE_HAZE -> 0.15f;
            case WISP -> 0.20f;
            case HOT_FLECK -> 0.22f;
        };
        float auraRadius = Mth.clamp(modelSize * radiusScale, 0.10f, 0.42f) * depthWeight;
        float smallModelBoost = smallModelCoverageBoost(modelSize);
        float compactPlacement = 1.0f - smallModelBoost * (role == AnchorRole.SURFACE ? 0.18f : 0.38f);
        float outwardPush = role == AnchorRole.SURFACE
                ? auraRadius * (0.08f + RANDOM.nextFloat() * 0.20f)
                : auraRadius * (0.30f + RANDOM.nextFloat() * 0.64f);
        float jitterPush = role == AnchorRole.SURFACE
                ? auraRadius * (0.05f + RANDOM.nextFloat() * 0.14f)
                : auraRadius * (0.14f + RANDOM.nextFloat() * 0.30f);
        outwardPush *= compactPlacement;
        jitterPush *= 1.0f - smallModelBoost * 0.26f;
        float horizontalSpread = anchorRoleHorizontalSpreadScale(role);
        float verticalPlacement = anchorRoleVerticalPlacementScale(role);

        double px = anchorPos.x + (outward.x * outwardPush + jitter.x * jitterPush) * horizontalSpread;
        double py = anchorPos.y + (outward.y * outwardPush + jitter.y * jitterPush) * verticalPlacement
                + RANDOM.nextFloat() * auraRadius * 0.10f * verticalPlacement;
        double pz = anchorPos.z + (outward.z * outwardPush + jitter.z * jitterPush) * horizontalSpread;

        float baseSize;
        int lifetime;
        float density;
        switch (type) {
            case BROAD_HAZE -> {
                baseSize = Mth.clamp(modelSize * (0.52f + RANDOM.nextFloat() * 0.24f) * depthWeight, 0.42f, 1.16f);
                lifetime = 52 + RANDOM.nextInt(30);
                density = (0.20f + RANDOM.nextFloat() * 0.20f) * intensity;
            }
            case CORE_HAZE -> {
                baseSize = Mth.clamp(modelSize * (0.32f + RANDOM.nextFloat() * 0.18f) * depthWeight, 0.26f, 0.82f);
                lifetime = 42 + RANDOM.nextInt(26);
                density = (0.34f + RANDOM.nextFloat() * 0.28f) * intensity;
            }
            case WISP -> {
                baseSize = Mth.clamp(modelSize * (0.16f + RANDOM.nextFloat() * 0.13f) * depthWeight, 0.14f, 0.46f);
                lifetime = 34 + RANDOM.nextInt(22);
                density = (0.40f + RANDOM.nextFloat() * 0.32f) * intensity;
            }
            case HOT_FLECK -> {
                baseSize = Mth.clamp(modelSize * (0.052f + RANDOM.nextFloat() * 0.050f) * depthWeight, 0.045f, 0.18f);
                lifetime = 18 + RANDOM.nextInt(18);
                density = (0.34f + RANDOM.nextFloat() * 0.28f) * intensity;
            }
            default -> throw new IllegalStateException("Unhandled aura puff type");
        }

        float roleSizeScale = anchorRolePuffSizeScale(role);
        float roleDensityScale = anchorRolePuffDensityScale(role);
        float roleVerticalScale = anchorRoleVerticalDriftScale(role);
        float highAltitude = Mth.clamp((float) ((anchorPos.y - center.y) / Math.max(modelSize, 0.001f)), 0.0f, 1.6f);
        float highAltitudeDamping = 1.0f - smoothstep(0.34f, 1.16f, highAltitude) * 0.42f;
        float smallModelSizeBoost = 1.0f + smallModelBoost * (type == PuffType.BROAD_HAZE ? 0.18f : 0.10f);
        baseSize *= roleSizeScale * smallModelSizeBoost;
        lifetime = Math.max(8, Math.round((lifetime + anchorRoleLifetimeBonus(role, type))
                * (0.78f + highAltitudeDamping * 0.22f)
                * (1.0f + (1.0f - quality) * 0.18f)));
        density *= roleDensityScale * highAltitudeDamping * (0.84f + quality * 0.16f) * (1.0f + smallModelBoost * 0.18f);

        Vec3 animationDrift = boneMotion.scale(0.34).add(entityMotion.scale(0.055));
        double driftScale = type == PuffType.HOT_FLECK ? 0.018 : 0.030;
        double xd = outward.x * driftScale + animationDrift.x + (RANDOM.nextFloat() - 0.5) * 0.020;
        double yd = (0.005 + RANDOM.nextFloat() * 0.014 + outward.y * 0.007) * roleVerticalScale * highAltitudeDamping
                + animationDrift.y * 0.45;
        double zd = outward.z * driftScale + animationDrift.z + (RANDOM.nextFloat() - 0.5) * 0.020;

        addPuff(new Puff(px, py, pz, xd, yd, zd, baseSize, lifetime, density, type));
    }

    private static float anchorRolePuffSizeScale(AnchorRole role) {
        if (role == null) {
            return 1.0f;
        }
        return switch (role) {
            case TIP -> 1.16f;
            case APPENDAGE -> 1.10f;
            case SURFACE -> 0.72f;
            case JOINT -> 0.92f;
            case BODY -> 1.0f;
        };
    }

    private static float anchorRolePuffDensityScale(AnchorRole role) {
        if (role == null) {
            return 1.0f;
        }
        return switch (role) {
            case TIP, APPENDAGE -> 1.06f;
            case SURFACE -> 0.92f;
            case JOINT -> 0.92f;
            case BODY -> 0.82f;
        };
    }

    private static float anchorRoleHorizontalSpreadScale(AnchorRole role) {
        if (role == null) {
            return 1.0f;
        }
        return switch (role) {
            case TIP -> 1.22f;
            case APPENDAGE -> 1.16f;
            case SURFACE -> 0.58f;
            case JOINT -> 0.92f;
            case BODY -> 1.04f;
        };
    }

    private static float anchorRoleVerticalPlacementScale(AnchorRole role) {
        if (role == null) {
            return 1.0f;
        }
        return switch (role) {
            case TIP -> 0.58f;
            case APPENDAGE -> 0.62f;
            case SURFACE -> 0.30f;
            case JOINT -> 0.66f;
            case BODY -> 0.52f;
        };
    }

    private static float anchorRoleVerticalDriftScale(AnchorRole role) {
        if (role == null) {
            return 1.0f;
        }
        return switch (role) {
            case SURFACE -> 0.24f;
            case TIP -> 0.48f;
            case APPENDAGE -> 0.54f;
            case JOINT -> 0.62f;
            case BODY -> 0.38f;
        };
    }

    private static int anchorRoleLifetimeBonus(AnchorRole role, PuffType type) {
        if (role == null || type == PuffType.HOT_FLECK) {
            return 0;
        }
        return switch (role) {
            case TIP -> 10;
            case APPENDAGE -> 8;
            case SURFACE -> type == PuffType.BROAD_HAZE ? 2 : 0;
            case BODY, JOINT -> 0;
        };
    }

    private static Vec3 randomUnitVector() {
        double x = RANDOM.nextFloat() * 2.0 - 1.0;
        double y = RANDOM.nextFloat() * 2.0 - 1.0;
        double z = RANDOM.nextFloat() * 2.0 - 1.0;
        Vec3 v = new Vec3(x, y, z);
        if (v.lengthSqr() < 0.0001) {
            return new Vec3(0.0, 1.0, 0.0);
        }
        return v.normalize();
    }

    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    private static PuffType pickType(double speed) {
        float roll = RANDOM.nextFloat();
        float fleckChance = speed > 0.05 ? 0.16f : 0.09f;
        if (roll < fleckChance) return PuffType.HOT_FLECK;
        if (roll < 0.42f) return PuffType.WISP;
        return PuffType.CORE_HAZE;
    }

    private static void spawnPuff(double x, double y, double z,
                                  float radius, float height,
                                  float fade, float corruption,
                                  Vec3 motionDir, double speed,
                                  float animationPhase,
                                  PuffType type) {
        float yFrac = 0.06f + RANDOM.nextFloat() * 0.88f;
        float ellipsoidProfile = (float) Math.sin(yFrac * Math.PI);
        float shellRadius = radius * (0.42f + 0.62f * ellipsoidProfile);

        float angle = RANDOM.nextFloat() * Mth.TWO_PI
                + animationPhase * (type == PuffType.HOT_FLECK ? 0.85f : 0.35f)
                + yFrac * 2.4f;
        float pulse = (float) Math.sin(animationPhase * 2.1f + yFrac * 5.7f);
        float radialJitter = 0.78f + RANDOM.nextFloat() * 0.58f + pulse * 0.07f;

        double ox = Math.cos(angle) * shellRadius * radialJitter;
        double oz = Math.sin(angle) * shellRadius * radialJitter;

        double wake = speed > 0.0001 ? RANDOM.nextFloat() * radius * Math.min(1.35, speed * 3.5) : 0.0;
        double px = x + ox - motionDir.x * wake;
        double py = y + height * yFrac + (RANDOM.nextFloat() - 0.5) * height * 0.09;
        double pz = z + oz - motionDir.z * wake;

        float scale = Math.max(0.45f, Math.min(radius, height) * 0.75f);
        float baseSize;
        int lifetime;
        float density;
        switch (type) {
            case CORE_HAZE -> {
                baseSize = (0.28f + RANDOM.nextFloat() * 0.24f) * scale;
                lifetime = 34 + RANDOM.nextInt(22);
                density = (0.34f + RANDOM.nextFloat() * 0.34f) * fade * (0.55f + corruption * 0.65f);
            }
            case WISP -> {
                baseSize = (0.16f + RANDOM.nextFloat() * 0.18f) * scale;
                lifetime = 28 + RANDOM.nextInt(18);
                density = (0.42f + RANDOM.nextFloat() * 0.40f) * fade * (0.50f + corruption * 0.70f);
            }
            case HOT_FLECK -> {
                baseSize = (0.055f + RANDOM.nextFloat() * 0.060f) * scale;
                lifetime = 24 + RANDOM.nextInt(22);
                density = (0.55f + RANDOM.nextFloat() * 0.45f) * fade * (0.50f + corruption * 0.75f);
            }
            default -> throw new IllegalStateException("Unhandled aura puff type");
        }

        Vec3 outward = new Vec3(ox, 0.0, oz);
        if (outward.lengthSqr() > 0.0001) {
            outward = outward.normalize();
        }

        double driftScale = type == PuffType.HOT_FLECK ? 0.010 : 0.018;
        double xd = outward.x * driftScale - motionDir.x * speed * 0.035 + (RANDOM.nextFloat() - 0.5) * 0.018;
        double yd = 0.004 + RANDOM.nextFloat() * 0.014 + (type == PuffType.CORE_HAZE ? 0.004 : 0.0);
        double zd = outward.z * driftScale - motionDir.z * speed * 0.035 + (RANDOM.nextFloat() - 0.5) * 0.018;

        addPuff(new Puff(px, py, pz, xd, yd, zd, baseSize, lifetime, density, type));
    }

    private static void addPuff(Puff puff) {
        synchronized (ACTIVE) {
            int puffLimit = adaptivePuffLimit();
            if (ACTIVE.size() >= puffLimit) {
                int overflow = ACTIVE.size() - puffLimit + 1;
                ACTIVE.subList(0, Math.min(overflow, ACTIVE.size())).clear();
            }
            ACTIVE.add(puff);
        }
    }

    private static int adaptivePuffLimit() {
        int activeSources = Math.max(1, SOURCES.size());
        int scaledLimit = MAX_PUFFS - Math.max(0, activeSources - 1) * PUFF_LIMIT_PER_EXTRA_SOURCE_DROP;
        return Mth.clamp(scaledLimit, MIN_ADAPTIVE_PUFFS, MAX_PUFFS);
    }

    private static void tickToGameTime(long gameTime) {
        if (lastTickGameTime == Long.MIN_VALUE) {
            lastTickGameTime = gameTime;
            return;
        }

        long delta = gameTime - lastTickGameTime;
        if (delta <= 0) {
            return;
        }

        tickOnce();
        lastTickGameTime = gameTime;
    }

    private static void tickOnce() {
        synchronized (ACTIVE) {
            Iterator<Puff> it = ACTIVE.iterator();
            while (it.hasNext()) {
                Puff puff = it.next();
                puff.xo = puff.x;
                puff.yo = puff.y;
                puff.zo = puff.z;
                puff.xdo = puff.xd;
                puff.ydo = puff.yd;
                puff.zdo = puff.zd;

                puff.x += puff.xd;
                puff.y += puff.yd;
                puff.z += puff.zd;

        switch (puff.type) {
                    case BROAD_HAZE -> {
                        puff.xd *= 0.89;
                        puff.yd *= 0.982;
                        puff.zd *= 0.89;
                    }
                    case CORE_HAZE -> {
                        puff.xd *= 0.91;
                        puff.yd *= 0.985;
                        puff.zd *= 0.91;
                    }
                    case WISP -> {
                        puff.xd *= 0.93;
                        puff.yd *= 0.985;
                        puff.yd += 0.0030;
                        puff.zd *= 0.93;
                    }
                    case HOT_FLECK -> {
                        puff.xd *= 0.96;
                        puff.yd *= 0.990;
                        puff.yd += 0.0015;
                        puff.zd *= 0.96;
                    }
                }

                puff.age++;
                if (puff.age >= puff.lifetime) {
                    it.remove();
                }
            }
        }
    }

    private static void renderDensitySplats(Camera camera, float partialTicks) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.colorMask(true, true, true, true);

        Matrix4f savedModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        Quaternionf viewRot = new Quaternionf(camera.rotation()).conjugate();
        RenderSystem.getModelViewMatrix().set(new Matrix4f().rotation(viewRot));

        var shader = ModShaders.SHADOW_POKEMON_AURA_DENSITY;
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, DENSITY_TEXTURE);

        float gameTime = 0.0f;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            gameTime = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true);
        }
        com.mojang.blaze3d.shaders.Uniform uGameTime = shader.getUniform("GameTime");
        if (uGameTime != null) {
            uGameTime.set(gameTime / 1200.0f);
        }

        Vec3 camPos = camera.getPosition();
        com.mojang.blaze3d.shaders.Uniform uCameraPos = shader.getUniform("CameraPos");
        if (uCameraPos != null) {
            uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        }

        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        PoseStack stack = new PoseStack();
        Quaternionf camOrientation = camera.rotation();

        synchronized (ACTIVE) {
            for (Puff puff : ACTIVE) {
                float alpha = sampleAlpha(puff, partialTicks);
                if (alpha <= 0.001f) continue;

                float size = sampleSize(puff, partialTicks);
                if (size <= 0.001f) continue;

                double rx = Mth.lerp(partialTicks, puff.xo, puff.x) - camPos.x;
                double ry = Mth.lerp(partialTicks, puff.yo, puff.y) - camPos.y;
                double rz = Mth.lerp(partialTicks, puff.zo, puff.z) - camPos.z;
                float renderQuality = puffRenderQuality(Math.sqrt(rx * rx + ry * ry + rz * rz));
                if (shouldSkipPuffRender(puff, renderQuality)) {
                    continue;
                }
                alpha *= 0.78f + renderQuality * 0.22f;
                size *= 0.92f + renderQuality * 0.08f;

                stack.pushPose();
                stack.translate(rx, ry, rz);
                stack.mulPose(camOrientation);

                double ivx = Mth.lerp(partialTicks, puff.xdo, puff.xd);
                double ivy = Mth.lerp(partialTicks, puff.ydo, puff.yd);
                double ivz = Mth.lerp(partialTicks, puff.zdo, puff.zd);
                double speed = Math.sqrt(ivx * ivx + ivy * ivy + ivz * ivz);

                if (puff.type == PuffType.HOT_FLECK) {
                    stack.mulPose(com.mojang.math.Axis.ZP.rotation(puff.rotation));
                    stack.scale(size, size, 1.0f);
                } else {
                    float stretch = 1.0f + (float) (speed * 16.0);
                    stretch = Math.min(stretch, switch (puff.type) {
                        case BROAD_HAZE -> 1.25f;
                        case CORE_HAZE -> 1.35f;
                        case WISP -> 1.65f;
                        case HOT_FLECK -> 1.0f;
                    });

                    float velAngle = puff.rotation;
                    if (speed > 0.0005) {
                        Vec3 vel = new Vec3(ivx, ivy, ivz);
                        org.joml.Vector3f right = camOrientation.transform(new org.joml.Vector3f(1, 0, 0));
                        org.joml.Vector3f up = camOrientation.transform(new org.joml.Vector3f(0, 1, 0));
                        Vec3 camRight = new Vec3(right.x, right.y, right.z);
                        Vec3 camUp = new Vec3(up.x, up.y, up.z);
                        float dotR = (float) vel.dot(camRight);
                        float dotU = (float) vel.dot(camUp);
                        velAngle = (float) Math.atan2(dotU, dotR);
                    }
                    float speedBlend = (float) Math.min(speed / 0.005, 1.0);
                    float angle = puff.rotation + speedBlend * wrapAngle(velAngle - puff.rotation);
                    stack.mulPose(com.mojang.math.Axis.ZP.rotation(angle));
                    stack.scale(size * stretch, size / Math.max(stretch * 0.62f, 1.0f), 1.0f);
                }

                float broadWeight = switch (puff.type) {
                    case BROAD_HAZE -> 1.00f;
                    case CORE_HAZE -> 0.72f;
                    case WISP -> 0.10f;
                    case HOT_FLECK -> 0.00f;
                };
                float sparkWeight = switch (puff.type) {
                    case BROAD_HAZE -> 0.00f;
                    case CORE_HAZE -> 0.06f;
                    case WISP -> 0.18f;
                    case HOT_FLECK -> 1.00f;
                };
                float wispWeight = switch (puff.type) {
                    case BROAD_HAZE -> 0.05f;
                    case CORE_HAZE -> 0.25f;
                    case WISP -> 1.00f;
                    case HOT_FLECK -> 0.25f;
                };

                Matrix4f pose = stack.last().pose();
                buf.addVertex(pose, -1f, -1f, 0f).setUv(0f, 1f).setColor(broadWeight, sparkWeight, wispWeight, alpha).setLight(FULLBRIGHT);
                buf.addVertex(pose, 1f, -1f, 0f).setUv(1f, 1f).setColor(broadWeight, sparkWeight, wispWeight, alpha).setLight(FULLBRIGHT);
                buf.addVertex(pose, 1f, 1f, 0f).setUv(1f, 0f).setColor(broadWeight, sparkWeight, wispWeight, alpha).setLight(FULLBRIGHT);
                buf.addVertex(pose, -1f, 1f, 0f).setUv(0f, 0f).setColor(broadWeight, sparkWeight, wispWeight, alpha).setLight(FULLBRIGHT);

                stack.popPose();
            }
        }

        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.getModelViewMatrix().set(savedModelView);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static float puffRenderQuality(double distance) {
        return Mth.clamp(1.0f - smoothstep(18.0f, 48.0f, (float) distance) * 0.62f, 0.38f, 1.0f);
    }

    private static boolean shouldSkipPuffRender(Puff puff, float renderQuality) {
        if (renderQuality >= 0.92f) {
            return false;
        }

        return switch (puff.type) {
            case HOT_FLECK -> renderQuality < 0.88f && puff.lodRoll > renderQuality * 0.42f;
            case WISP -> renderQuality < 0.68f && puff.lodRoll > renderQuality * 1.05f;
            case CORE_HAZE -> renderQuality < 0.46f && puff.lodRoll > renderQuality + 0.25f;
            case BROAD_HAZE -> false;
        };
    }

    private static float sampleSize(Puff puff, float partialTicks) {
        float t = Mth.clamp((puff.age + partialTicks) / (float) puff.lifetime, 0.0f, 1.0f);
        return switch (puff.type) {
            case BROAD_HAZE -> {
                float grow = Math.min(t * 4.0f, 1.0f);
                float fade = 1.0f - smoothstep(0.36f, 1.0f, t);
                yield puff.baseSize * (0.22f + 0.78f * grow) * fade * (1.0f + t * 0.14f);
            }
            case CORE_HAZE -> {
                float grow = Math.min(t * 5.5f, 1.0f);
                float fade = 1.0f - smoothstep(0.45f, 1.0f, t);
                yield puff.baseSize * (0.18f + 0.82f * grow) * fade * (1.0f + t * 0.18f);
            }
            case WISP -> {
                float grow = Math.min(t * 8.0f, 1.0f);
                float fade = 1.0f - smoothstep(0.55f, 1.0f, t);
                yield puff.baseSize * (0.08f + 0.92f * grow) * fade;
            }
            case HOT_FLECK -> {
                float grow = Math.min(t * 12.0f, 1.0f);
                float fade = 1.0f - smoothstep(0.72f, 1.0f, t);
                yield puff.baseSize * grow * fade;
            }
        };
    }

    private static float sampleAlpha(Puff puff, float partialTicks) {
        float t = Mth.clamp((puff.age + partialTicks) / (float) puff.lifetime, 0.0f, 1.0f);
        return switch (puff.type) {
            case BROAD_HAZE -> {
                float fadeIn = Math.min(1.0f, t / 0.42f);
                float fadeOut = 1.0f - smoothstep(0.76f, 1.0f, t);
                yield Math.min(1.0f, fadeIn * fadeOut) * puff.baseDensity;
            }
            case CORE_HAZE -> {
                float fadeIn = Math.min(1.0f, t / 0.35f);
                float fadeOut = 1.0f - smoothstep(0.82f, 1.0f, t);
                yield Math.min(1.0f, fadeIn * fadeOut) * puff.baseDensity;
            }
            case WISP -> {
                float fadeIn = Math.min(1.0f, t / 0.18f);
                float fadeOut = 1.0f - smoothstep(0.74f, 1.0f, t);
                yield Math.min(1.0f, fadeIn * fadeOut) * puff.baseDensity;
            }
            case HOT_FLECK -> {
                float fadeIn = Math.min(1.0f, t / 0.10f);
                float fadeOut = 1.0f - smoothstep(0.55f, 1.0f, t);
                float twinkle = 0.76f + 0.24f * (float) Math.sin(puff.age * 0.85f + puff.rotation * 8.0f);
                yield Math.min(1.0f, fadeIn * fadeOut) * puff.baseDensity * twinkle;
            }
        };
    }

    private static float wrapAngle(float a) {
        while (a > (float) Math.PI) a -= (float) (Math.PI * 2.0);
        while (a < -(float) Math.PI) a += (float) (Math.PI * 2.0);
        return a;
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private enum PuffType {
        BROAD_HAZE,
        CORE_HAZE,
        WISP,
        HOT_FLECK
    }

    private enum AnchorRole {
        BODY(1.00f, 1.00f, 0.10f, 0.13f, 1.00f),
        JOINT(0.96f, 1.00f, 0.20f, 0.12f, 1.00f),
        APPENDAGE(0.94f, 1.10f, 0.34f, 0.12f, 0.82f),
        SURFACE(0.92f, 0.98f, 0.24f, 0.08f, 0.34f),
        TIP(0.86f, 1.16f, 0.42f, 0.09f, 0.72f);

        final float sizeWeight;
        final float spawnWeight;
        final float wispChance;
        final float hazeChance;
        final float sparkWeight;

        AnchorRole(float sizeWeight, float spawnWeight, float wispChance, float hazeChance, float sparkWeight) {
            this.sizeWeight = sizeWeight;
            this.spawnWeight = spawnWeight;
            this.wispChance = wispChance;
            this.hazeChance = hazeChance;
            this.sparkWeight = sparkWeight;
        }
    }

    private enum AnchorSource {
        CUBE,
        CHAIN,
        JOINT
    }

    private enum PartClassification {
        CHAIN_BODY,
        BODY,
        APPENDAGE,
        SURFACE,
        IGNORED_DETAIL,
        EMPTY
    }

    private static final class SourceState {
        double x;
        double y;
        double z;
        double lastBodyX;
        double lastBodyY;
        double lastBodyZ;
        long lastEmitTick = Long.MIN_VALUE;
        long lastBoneEmitTick = Long.MIN_VALUE;
        long lastBoneTick = Long.MIN_VALUE;
        long lastSeenTick = Long.MIN_VALUE;
        int bodySampleCursor;
        int bodyAnchorFillCursor;
        int upperBodyAnchorFillCursor;
        int boneEmitterCursor;
        int appendageEmitterCursor;
        int surfaceEmitterCursor;
        int tipEmitterCursor;
        float fade = 1.0f;
        float corruption = 1.0f;
        float quality = 1.0f;
        boolean hasLastBodyCenter;
        final float seed = RANDOM.nextFloat() * Mth.TWO_PI;
        final List<Vec3> lastBoneAnchors = new ArrayList<>();
        final List<DebugAnchor> debugAnchors = new ArrayList<>();
        final List<DebugAnchor> activeDebugAnchors = new ArrayList<>();
        ClassificationStats lastClassificationStats = ClassificationStats.EMPTY;

        SourceState(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private record BoneAnchor(Vec3 pos, int depth, float sizeWeight, AnchorRole role, AnchorSource source, Vec3 normal) {}

    private static final class RenderedAnchorCapture {
        final PokemonEntity entity;
        final SourceState state;
        final Vec3 poseToWorldOffset;
        final Vec3 sourceWorld;
        final float strength;
        final float partialTicks;
        final float quality;
        final long gameTime;
        final int anchorBudget;
        final int candidateBudget;
        final int bodyAnchorBudget;
        final int[] bodyAnchorCount = new int[1];
        final List<BoneAnchor> anchorCandidates;
        final List<RenderedPartNode> renderedPartStack = new ArrayList<>();

        RenderedAnchorCapture(PokemonEntity entity,
                              SourceState state,
                              Vec3 poseToWorldOffset,
                              Vec3 sourceWorld,
                              float strength,
                              float partialTicks,
                              float quality,
                              long gameTime,
                              int anchorBudget,
                              int candidateBudget,
                              int bodyAnchorBudget) {
            this.entity = entity;
            this.state = state;
            this.poseToWorldOffset = poseToWorldOffset;
            this.sourceWorld = sourceWorld;
            this.strength = strength;
            this.partialTicks = partialTicks;
            this.quality = quality;
            this.gameTime = gameTime;
            this.anchorBudget = anchorBudget;
            this.candidateBudget = candidateBudget;
            this.bodyAnchorBudget = bodyAnchorBudget;
            this.anchorCandidates = new ArrayList<>(candidateBudget);
        }
    }

    private record AnchorCenter(double x, double z, int count) {
        static final AnchorCenter EMPTY = new AnchorCenter(0.0, 0.0, 0);
    }

    private record DebugAnchor(Vec3 pos, AnchorRole role, AnchorSource source) {}

    private record RenderedPartNode(ModelPart part, Vec3 geometryWorld, boolean chainable) {}

    private record RenderedPartSample(Vec3 geometryWorld, boolean chainable, boolean hasChainableDescendant) {}

    private record SurfaceAxes(int normalAxis, int uAxis, int vAxis) {}

    private record CubeAuraTemplate(boolean auraAnchor,
                                    boolean chainable,
                                    AnchorRole role,
                                    PartClassification classification,
                                    float volume,
                                    float surfaceArea,
                                    float sizeWeight,
                                    boolean shellCube,
                                    Vector3f center,
                                    SurfaceAxes surfaceAxes,
                                    Vector3f[] samples,
                                    Vector3f[] shellPoints) {
        static final Vector3f[] EMPTY_POINTS = new Vector3f[0];
    }

    private record ModelGeometryStats(float volume, float surfaceArea) {
        static final ModelGeometryStats EMPTY = new ModelGeometryStats(0.0f, 0.0f);
    }

    private static final class ClassificationCounter {
        int chainBody;
        int body;
        int appendage;
        int surface;
        int ignoredDetail;
        int empty;

        void add(PartClassification classification) {
            switch (classification) {
                case CHAIN_BODY -> chainBody++;
                case BODY -> body++;
                case APPENDAGE -> appendage++;
                case SURFACE -> surface++;
                case IGNORED_DETAIL -> ignoredDetail++;
                case EMPTY -> empty++;
            }
        }

        ClassificationStats toStats() {
            int total = chainBody + body + appendage + surface + ignoredDetail + empty;
            return new ClassificationStats(total, 1, chainBody, body, appendage, surface, ignoredDetail, empty);
        }
    }

    private record ClassificationStats(int total,
                                       int sources,
                                       int chainBody,
                                       int body,
                                       int appendage,
                                       int surface,
                                       int ignoredDetail,
                                       int empty) {
        static final ClassificationStats EMPTY = new ClassificationStats(0, 0, 0, 0, 0, 0, 0, 0);
    }

    private record DebugAnchorStats(int total,
                                    int active,
                                    int sources,
                                    int body,
                                    int joint,
                                    int appendage,
                                    int surface,
                                    int tip,
                                    int cube,
                                    int chain,
                                    int jointSource,
                                    int activeBody,
                                    int activeJoint,
                                    int activeAppendage,
                                    int activeSurface,
                                    int activeTip) {
        static final DebugAnchorStats EMPTY = new DebugAnchorStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static final class Puff {
        double x, y, z;
        double xo, yo, zo;
        double xd, yd, zd;
        double xdo, ydo, zdo;

        final float baseSize;
        final int lifetime;
        final float baseDensity;
        final float rotation;
        final float lodRoll;
        final PuffType type;
        int age;

        Puff(double x, double y, double z,
             double xd, double yd, double zd,
             float baseSize, int lifetime,
             float baseDensity, PuffType type) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.xo = x;
            this.yo = y;
            this.zo = z;
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            this.xdo = xd;
            this.ydo = yd;
            this.zdo = zd;
            this.baseSize = baseSize;
            this.lifetime = lifetime;
            this.baseDensity = baseDensity;
            this.type = type;
            this.rotation = RANDOM.nextFloat() * Mth.TWO_PI;
            this.lodRoll = RANDOM.nextFloat();
        }
    }
}
