package com.jayemceekay.shadowedhearts.client.ball;

import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.aura.AuraPulseRenderer;
import com.jayemceekay.shadowedhearts.client.render.DarkBallFieldMaskBufferSource;
import com.jayemceekay.shadowedhearts.client.render.rendertypes.BallRenderTypes;
import com.jayemceekay.shadowedhearts.common.capture.DarkBallCaptureTimings;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dark Ball capture VFX: a cohesive black conversion mass that keeps the
 * target silhouette briefly, then collapses into a twisting Bezier siphon.
 */
public final class DarkBallCaptureVfx {

    public static final float TIMING_SCALE = DarkBallCaptureTimings.TIMING_SCALE;
    public static final float CONVERSION_START = 0.08f * TIMING_SCALE;
    public static final float CONVERSION_SOLID = 0.56f * TIMING_SCALE;
    public static final float FROZEN_MODEL_CROSSFADE_START = CONVERSION_START;
    public static final float FROZEN_MODEL_CROSSFADE_END =
            CONVERSION_SOLID * 0.62f;
    public static final float VFX_END = DarkBallCaptureTimings.VFX_DURATION_SECONDS;
    public static final float VISIBLE_EXPANSION_RAMP_SECONDS = 1.10f;
    public static final float VISIBLE_EXPANSION_FULL =
            FROZEN_MODEL_CROSSFADE_END + VISIBLE_EXPANSION_RAMP_SECONDS;
    public static final float TURBULENCE_RAMP_SECONDS = 1.50f;
    public static final float TURBULENCE_FULL = VISIBLE_EXPANSION_FULL;
    public static final float TURBULENCE_START =
            TURBULENCE_FULL - TURBULENCE_RAMP_SECONDS;
    public static final float MAX_TURBULENCE_HOLD_SECONDS = 2.50f;
    public static final float SIPHON_START =
            TURBULENCE_FULL + MAX_TURBULENCE_HOLD_SECONDS;
    public static final float SIPHON_END = VFX_END - 0.30f;
    public static final float SIPHON_DRAIN_LEAD_SECONDS = 0.75f;
    public static final float SIPHON_COLLAPSE_START =
            SIPHON_END - SIPHON_DRAIN_LEAD_SECONDS;
    public static final float BALL_ABSORB_END = VFX_END - 0.10f;
    private static final float SIPHON_TUBE_CLIP_START = 0.08f;
    private static final float SIPHON_TUBE_CLIP_SOLID = 0.16f;
    private static final float SDF_THROAT_ROOT_RADIUS_SCALE = 0.155f;
    private static final float SDF_THROAT_END_RADIUS_SCALE = 0.038f;
    private static final float SDF_THROAT_BLEND_RADIUS_SCALE = 0.085f;
    private static final int SDF_THROAT_SAMPLES = 18;
    private static final float SPLAT_SIM_STEP = 1.0f / 45.0f;
    private static final int SPLAT_SIM_MAX_STEPS = 2;

    private static final int BASE_SPLAT_COUNT = 1080;
    private static final int MIN_SPLAT_COUNT = 280;
    private static final int MAX_SPLAT_COUNT = 1500;
    private static final int MAX_SNAPSHOT_QUADS = 12000;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final int FULLBRIGHT = 0x00F000F0;
    private static final boolean ENABLE_BOUNDARY_VORTICITY = false;
    private static final boolean ENABLE_MODEL_SHAPED_SPLATS = true;
    private static final boolean ENABLE_VELOCITY_DEFORMED_SPLATS = true;
    private static final boolean ENABLE_WALL_AWARE_COVARIANCE = true;
    private static final boolean ENABLE_VOXEL_MASS_TRANSPORT = true;
    private static final boolean ENABLE_ADVECTED_DENSITY_FIELD = true;
    private static final boolean ENABLE_MASS_TRANSPORT_DENSITY_GATE = true;
    private static final boolean ENABLE_BODY_VOLUME_RAYMARCH = false;
    private static final boolean ENABLE_VISIBLE_FIELD_MASK = false;
    private static final ResourceLocation SOFT_GLOW_TEXTURE =
            ResourceLocation.parse("shadowedhearts:textures/vfx/soft_glow.png");
    private static final MultiBufferSource.BufferSource MASK_BUFFERS =
            MultiBufferSource.immediate(new ByteBufferBuilder(786432));

    private static final Map<Integer, DarkBallCaptureVfx> ACTIVE = new ConcurrentHashMap<>();
    private static long lastTickNanos = 0L;
    private static long lastMaskFrameToken = Long.MIN_VALUE;
    private static boolean maskCapturedThisFrame;
    private static boolean renderingModelMask;
    private static float compositeMaskClipStrength;
    private static float compositeMaskVisibleStrength;
    private static float compositeProxyDepthStrength;
    private static float compositeProxyDepthAvailable;
    private static boolean compositeExactMaskReady;
    private static float compositeDeformationBlend;
    private static float compositeSignedBodyAuthority;
    private static float compositeTurbulenceBlend;
    private static float compositeDeformationTime;
    private static float compositeSiphonRootU = 0.5f;
    private static float compositeSiphonRootV = 0.5f;
    private static float compositeBallU = 0.5f;
    private static float compositeBallV = 0.5f;
    private static float compositeSiphonProgress;
    private static final Vector2f compositeProjectedUp = new Vector2f(0f, 1f);
    private static final Vector4f compositeBodyUvBounds = new Vector4f(0f, 0f, 1f, 1f);
    private static int compositeMassTextureId;
    private static int compositeSiphonTextureId;
    private static float compositeBodyVolumeStrength;
    private static float compositeAnalyticSiphonStrength;
    private static final Matrix4f compositeInvProjection = new Matrix4f();
    private static final Matrix4f compositeProjection = new Matrix4f();
    private static final Matrix4f compositeCameraToWorld = new Matrix4f();
    private static final Vector3f compositeCameraPos = new Vector3f();
    private static final Vector3f compositeVolumeRoot = new Vector3f();
    private static final Vector3f compositeVolumeAxis = new Vector3f(1f, 0f, 0f);
    private static final Vector3f compositeVolumeSide = new Vector3f(0f, 1f, 0f);
    private static final Vector3f compositeVolumeUp = new Vector3f(0f, 0f, 1f);
    private static final Vector3f compositeVolumeSize = new Vector3f(1f, 1f, 1f);
    private static final Vector3f compositeSiphonP0 = new Vector3f();
    private static final Vector3f compositeSiphonP1 = new Vector3f();
    private static final Vector3f compositeSiphonP2 = new Vector3f();
    private static final Vector3f compositeSiphonP3 = new Vector3f();
    private static boolean reconstructionAuxiliaryTargetsCleared;
    private static ModelSnapshotCapture activeSnapshotCapture;
    private static TexturedSnapshotCapture activeTexturedSnapshotCapture;

    public static void start(EmptyPokeBallEntity ball, PokemonEntity pokemon) {
        ACTIVE.putIfAbsent(ball.getId(), new DarkBallCaptureVfx(ball, pokemon));
    }

    public static void startAtHit(EmptyPokeBallEntity ball, PokemonEntity pokemon) {
        DarkBallCaptureVfx vfx = ACTIVE.computeIfAbsent(ball.getId(), id -> new DarkBallCaptureVfx(ball, pokemon));
        vfx.markHitStage();
    }

    public static void stop(int ballId) {
        DarkBallCaptureVfx removed = ACTIVE.remove(ballId);
        if (removed != null) {
            removed.destroy();
        }
    }

    public static void clearAll() {
        for (DarkBallCaptureVfx vfx : ACTIVE.values()) {
            vfx.destroy();
        }
        ACTIVE.clear();
        lastTickNanos = 0L;
        activeSnapshotCapture = null;
        activeTexturedSnapshotCapture = null;
        maskCapturedThisFrame = false;
        renderingModelMask = false;
    }

    public static DarkBallCaptureVfx get(int ballId) {
        return ACTIVE.get(ballId);
    }

    /** Returns true only after the direct replacement has rendered successfully. */
    public static boolean isReplacementReady(int ballId) {
        DarkBallCaptureVfx vfx = ACTIVE.get(ballId);
        return ACTIVE.size() == 1 && vfx != null
                && vfx.snapshotApplied && vfx.directReplacementReady;
    }

    public static java.util.Collection<DarkBallCaptureVfx> getActiveInstances() {
        return ACTIVE.values();
    }

    /** Clears last frame's readiness before the direct FBO pipeline runs. */
    public static void beginDirectCompositeFrame() {
        for (DarkBallCaptureVfx vfx : ACTIVE.values()) {
            vfx.directReplacementReady = false;
        }
    }

    /** Publishes readiness only after the exact-core postprocess composites. */
    public static void finishDirectCompositeFrame(boolean compositeRendered) {
        if (ACTIVE.size() != 1) {
            return;
        }
        for (DarkBallCaptureVfx vfx : ACTIVE.values()) {
            vfx.directReplacementReady = compositeRendered;
        }
    }

    public static DarkBallCaptureVfx getByPokemonId(int pokemonId) {
        for (DarkBallCaptureVfx vfx : ACTIVE.values()) {
            if (vfx.pokemonId == pokemonId) {
                return vfx;
            }
        }
        return null;
    }

    public static boolean shouldHideOriginalModel(PokemonEntity entity) {
        DarkBallCaptureVfx vfx = entity == null ? null : getByPokemonId(entity.getId());
        return vfx != null && vfx.snapshotApplied;
    }

    public static boolean wantsSnapshotCapture(PokemonEntity entity) {
        DarkBallCaptureVfx vfx = entity == null ? null : getByPokemonId(entity.getId());
        return vfx != null && vfx.snapshotRequested && !vfx.snapshotApplied;
    }

    public static MultiBufferSource wrapSnapshotBuffer(PokemonEntity entity,
                                                       ResourceLocation texture,
                                                       MultiBufferSource delegate) {
        if (entity == null) {
            return delegate;
        }

        DarkBallCaptureVfx vfx = getByPokemonId(entity.getId());
        if (vfx == null || !vfx.snapshotRequested || vfx.snapshotApplied) {
            return delegate;
        }

        // Once the Dark Ball has requested its hit snapshot, the original
        // Pokemon must never reach the live color or depth buffers. If capture
        // inputs are temporarily unavailable, suppress this render and retry
        // next frame instead of leaking the model through the handoff.
        if (texture == null || delegate == null) {
            return DarkBallFieldMaskBufferSource.discarding();
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return DarkBallFieldMaskBufferSource.discarding();
        }

        vfx.snapshotQuads.clear();
        vfx.texturedSnapshotQuads.clear();
        vfx.capturedModelVertices.clear();
        vfx.capturedModelTriangles.clear();
        vfx.voxelVolume = null;
        vfx.cancelPendingVoxelBuild();
        vfx.destroyMassTransport();
        vfx.massTransportLogged = false;
        vfx.siphonDrainRootValid = false;
        vfx.resetSplatSimulation();
        vfx.snapshotBounds = null;
        vfx.initializedSplats = false;
        vfx.snapshotTexture = texture;
        vfx.texturedSnapshotCapturing = true;

        DarkBallFieldMaskBufferSource wrapper = new DarkBallFieldMaskBufferSource(
                vfx.texturedSnapshotQuads,
                vfx.capturedModelVertices,
                vfx.capturedModelTriangles,
                mc.gameRenderer.getMainCamera().getPosition()
        );
        activeTexturedSnapshotCapture = new TexturedSnapshotCapture(vfx, entity, wrapper);
        return wrapper;
    }

    public static void tickAll(float ignoredDt) {
        long now = System.nanoTime();
        float dt;
        if (lastTickNanos == 0L || ACTIVE.isEmpty()) {
            dt = 0f;
            lastTickNanos = now;
        } else {
            dt = Math.min((now - lastTickNanos) / 1_000_000_000f, 0.1f);
        }
        lastTickNanos = now;

        ACTIVE.entrySet().removeIf(entry -> {
            entry.getValue().tick(dt);
            boolean expired = entry.getValue().age > VFX_END + 0.35f * TIMING_SCALE;
            if (expired) {
                entry.getValue().destroy();
            }
            return expired;
        });
    }

    public static void renderAllDensityPass(Camera camera, float partialTick) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Vec3 camPos = camera.getPosition();
        compositeMaskClipStrength = 0.0f;
        compositeMaskVisibleStrength = 0.0f;
        compositeProxyDepthStrength = 0.0f;
        compositeProxyDepthAvailable = 0.0f;
        compositeExactMaskReady = false;
        compositeDeformationBlend = 0.0f;
        compositeSignedBodyAuthority = 0.0f;
        compositeTurbulenceBlend = 0.0f;
        compositeDeformationTime = 0.0f;
        compositeSiphonRootU = 0.5f;
        compositeSiphonRootV = 0.5f;
        compositeBallU = 0.5f;
        compositeBallV = 0.5f;
        compositeSiphonProgress = 0.0f;
        compositeProjectedUp.set(0.0f, 1.0f);
        compositeBodyUvBounds.set(0.0f, 0.0f, 1.0f, 1.0f);
        compositeMassTextureId = 0;
        compositeSiphonTextureId = 0;
        compositeBodyVolumeStrength = 0.0f;
        compositeAnalyticSiphonStrength = 0.0f;
        compositeCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        compositeProjection.set(RenderSystem.getProjectionMatrix());
        compositeInvProjection.set(compositeProjection).invert();
        compositeCameraToWorld.identity().rotation(camera.rotation());
        reconstructionAuxiliaryTargetsCleared = false;
        boolean allowDirectVolume = ACTIVE.size() == 1;
        for (DarkBallCaptureVfx vfx : ACTIVE.values()) {
            if (!allowDirectVolume) {
                vfx.directReplacementReady = false;
            }
            vfx.renderDensityPass(camera, camPos, partialTick, allowDirectVolume);
        }
    }

    public static float getCompositeMaskClipStrength() {
        return compositeMaskClipStrength;
    }

    public static float getCompositeMaskVisibleStrength() {
        return ENABLE_VISIBLE_FIELD_MASK ? compositeMaskVisibleStrength : 0.0f;
    }

    public static float getCompositeProxyDepthStrength() {
        return compositeProxyDepthStrength;
    }

    public static float getCompositeProxyDepthAvailable() {
        return compositeProxyDepthAvailable;
    }

    public static boolean isCompositeExactMaskReady() {
        return compositeExactMaskReady;
    }

    public static float getCompositeDeformationBlend() {
        return compositeDeformationBlend;
    }

    public static float getCompositeSignedBodyAuthority() {
        return compositeSignedBodyAuthority;
    }

    public static float getCompositeTurbulenceBlend() {
        return compositeTurbulenceBlend;
    }

    public static float getCompositeDeformationTime() {
        return compositeDeformationTime;
    }

    public static float getCompositeSiphonRootU() {
        return compositeSiphonRootU;
    }

    public static float getCompositeSiphonRootV() {
        return compositeSiphonRootV;
    }

    public static float getCompositeBallU() {
        return compositeBallU;
    }

    public static float getCompositeBallV() {
        return compositeBallV;
    }

    public static float getCompositeSiphonProgress() {
        return compositeSiphonProgress;
    }

    public static Vector2f getCompositeProjectedUp() {
        return compositeProjectedUp;
    }

    public static Vector4f getCompositeBodyUvBounds() {
        return compositeBodyUvBounds;
    }

    public static int getCompositeMassTextureId() {
        return compositeMassTextureId;
    }

    public static int getCompositeSiphonTextureId() {
        return compositeSiphonTextureId;
    }

    public static float getCompositeBodyVolumeStrength() {
        return compositeBodyVolumeStrength;
    }

    public static float getCompositeAnalyticSiphonStrength() {
        return compositeAnalyticSiphonStrength;
    }

    public static Matrix4f getCompositeInvProjection() {
        return compositeInvProjection;
    }

    public static Matrix4f getCompositeProjection() {
        return compositeProjection;
    }

    public static Matrix4f getCompositeCameraToWorld() {
        return compositeCameraToWorld;
    }

    public static Vector3f getCompositeCameraPos() {
        return compositeCameraPos;
    }

    public static Vector3f getCompositeVolumeRoot() {
        return compositeVolumeRoot;
    }

    public static Vector3f getCompositeVolumeAxis() {
        return compositeVolumeAxis;
    }

    public static Vector3f getCompositeVolumeSide() {
        return compositeVolumeSide;
    }

    public static Vector3f getCompositeVolumeUp() {
        return compositeVolumeUp;
    }

    public static Vector3f getCompositeVolumeSize() {
        return compositeVolumeSize;
    }

    public static Vector3f getCompositeSiphonP0() {
        return compositeSiphonP0;
    }

    public static Vector3f getCompositeSiphonP1() {
        return compositeSiphonP1;
    }

    public static Vector3f getCompositeSiphonP2() {
        return compositeSiphonP2;
    }

    public static Vector3f getCompositeSiphonP3() {
        return compositeSiphonP3;
    }

    public static void beginModelSnapshotCapture(PokemonEntity entity, PoseStack renderStack, Bone rootPart) {
        activeSnapshotCapture = null;
        if (entity == null || renderStack == null || rootPart == null || renderingModelMask) {
            return;
        }
        if (AuraPulseRenderer.IRIS_HANDLER != null && AuraPulseRenderer.IRIS_HANDLER.isShadowRenderActive()) {
            return;
        }

        DarkBallCaptureVfx vfx = getByPokemonId(entity.getId());
        if (vfx == null || !vfx.snapshotRequested || vfx.snapshotApplied) {
            return;
        }
        if (vfx.texturedSnapshotCapturing) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        float partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 sourceWorld = new Vec3(
                Mth.lerp(partialTicks, entity.xOld, entity.getX()),
                Mth.lerp(partialTicks, entity.yOld, entity.getY()),
                Mth.lerp(partialTicks, entity.zOld, entity.getZ())
        );
        Vector3f rawModelOrigin = renderStack.last().pose().transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
        Vec3 poseToWorldOffset = sourceWorld.subtract(rawModelOrigin.x, rawModelOrigin.y, rawModelOrigin.z);

        vfx.snapshotQuads.clear();
        activeSnapshotCapture = new ModelSnapshotCapture(vfx, entity, poseToWorldOffset);
    }

    public static void captureRenderedModelPart(ModelPart part, PoseStack stack) {
        ModelSnapshotCapture capture = activeSnapshotCapture;
        if (capture == null || part == null || stack == null || capture.vfx.snapshotQuads.size() >= MAX_SNAPSHOT_QUADS) {
            return;
        }

        Matrix4f pose = stack.last().pose();
        for (ModelPart.Cube cube : part.cubes) {
            if (capture.vfx.snapshotQuads.size() >= MAX_SNAPSHOT_QUADS) {
                return;
            }
            captureCubeFaces(capture, pose, cube);
        }
    }

    public static void endModelSnapshotCapture(PokemonEntity entity) {
        ModelSnapshotCapture capture = activeSnapshotCapture;
        activeSnapshotCapture = null;
        if (capture == null || capture.entity != entity) {
            return;
        }
        capture.vfx.finishSnapshotCapture(capture);
    }

    public static void finishTexturedSnapshotCapture(PokemonEntity entity) {
        TexturedSnapshotCapture capture = activeTexturedSnapshotCapture;
        if (capture == null || capture.entity != entity) {
            return;
        }

        activeTexturedSnapshotCapture = null;
        capture.bufferSource.finishCapture();
        capture.vfx.finishTexturedSnapshotCapture();
    }

    public static void renderModelMask(PokemonEntity entity,
                                       ResourceLocation texture,
                                       RenderContext context,
                                       PoseStack stack,
                                       Bone rootPart) {
        if (renderingModelMask || entity == null || texture == null || context == null || rootPart == null) {
            return;
        }

        DarkBallCaptureVfx vfx = getByPokemonId(entity.getId());
        if (vfx == null || ModShaders.DARK_BALL_MASK == null) {
            return;
        }

        float strength = vfx.getMaskStrength();
        if (strength <= 0.002f) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        float partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(true);
        long frameToken = currentFrameToken(mc, partialTicks);
        if (frameToken != lastMaskFrameToken) {
            lastMaskFrameToken = frameToken;
            maskCapturedThisFrame = false;
        }

        boolean clearForFirstMask = !maskCapturedThisFrame;
        if (!DarkBallDensityFBO.beginDensityPass(clearForFirstMask, clearForFirstMask)) {
            return;
        }

        try {
            if (!DarkBallDensityFBO.beginMaskPass(clearForFirstMask, clearForFirstMask)) {
                return;
            }
            renderingModelMask = true;
            setupMaskUniforms(entity, vfx);
            RenderType maskType = BallRenderTypes.darkBallMask(texture);
            VertexConsumer consumer = MASK_BUFFERS.getBuffer(maskType);
            rootPart.render(context, stack, consumer, FULLBRIGHT, OverlayTexture.NO_OVERLAY, packMaskColor(strength));
            MASK_BUFFERS.endBatch(maskType);
            maskCapturedThisFrame = true;
        } finally {
            renderingModelMask = false;
            DarkBallDensityFBO.endDensityPass();
        }
    }

    public static boolean hasModelMaskThisFrame(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        return maskCapturedThisFrame && lastMaskFrameToken == currentFrameToken(mc, partialTicks);
    }

    private final int ballId;
    private final int pokemonId;
    private final Random rng = new Random();
    private final List<VolumeSplat> splats = new ArrayList<>();
    private final List<SplatState> splatStates = new ArrayList<>();
    private final List<SnapshotQuad> snapshotQuads = new ArrayList<>();
    private final List<DarkBallFieldMaskBufferSource.CapturedQuad> texturedSnapshotQuads = new ArrayList<>();
    private final List<Vec3> capturedModelVertices = new ArrayList<>();
    private final List<DarkBallFieldMaskBufferSource.CapturedTriangle> capturedModelTriangles = new ArrayList<>();

    public float age = 0f;
    private boolean initializedSplats = false;
    private boolean hitStageSeen = false;
    private boolean snapshotRequested = false;
    private boolean snapshotApplied = false;
    private boolean directReplacementReady = false;
    private boolean texturedSnapshotCapturing = false;
    private boolean splatSourceLogged = false;
    private boolean voxelBuildLogged = false;
    private boolean siphonOutletLogged = false;
    private boolean splatSimulationLogged = false;
    private float splatSimAge = 0f;
    private float splatSimAccumulator = 0f;
    private float splatRenderAlpha = 1f;
    private float massTransportAge = 0f;
    private ResourceLocation snapshotTexture;
    private Vec3 snapshotCenter = Vec3.ZERO;
    private float snapshotExtent = 1.0f;
    private AABB snapshotBounds;
    private DarkBallVolumeBuildResult voxelVolume;
    private DarkBallVoxelMassTransport massTransport;
    private boolean massTransportLogged = false;
    private DarkBallAnalyticalVolume analyticalVolume;
    private DarkBallVfxQuality analyticalQuality;
    private float analyticalVolumeAge;
    private boolean analyticalVolumeLogged;
    private DarkBallAdvectedDensityField advectedDensityField;
    private DarkBallAnalyticalVolume advectedDensityShape;
    private DarkBallVfxQuality advectedDensityQuality;
    private float advectedDensityAge;
    private boolean advectedDensityLogged;
    private CompletableFuture<VoxelBuildOutput> voxelBuildFuture;
    private int voxelBuildGeneration;
    private final Vector3f siphonDrainRootLocal = new Vector3f(0f, 0f, 0f);
    private boolean siphonDrainRootValid = false;

    private DarkBallCaptureVfx(EmptyPokeBallEntity ball, PokemonEntity pokemon) {
        this.ballId = ball.getId();
        this.pokemonId = pokemon.getId();
    }

    private void markHitStage() {
        if (!hitStageSeen) {
            hitStageSeen = true;
            age = 0f;
        }
        if (!snapshotApplied) {
            snapshotRequested = true;
        }
    }

    public float getDesiredPokemonScale() {
        if (age < SIPHON_START) {
            return 1f;
        }
        float t = remapClamped(age, SIPHON_START, SIPHON_END);
        return 1f - smoothstep(0f, 1f, t);
    }

    public float getDissolveProgress() {
        return remapClamped(age, CONVERSION_START, CONVERSION_SOLID);
    }

    public float getAge() {
        return age;
    }

    private float getMaskStrength() {
        float conversion = 0.42f + smoothstep(0f, CONVERSION_SOLID, age) * 0.58f;
        float siphon = remapClamped(age, SIPHON_START, SIPHON_END);
        float release = 1f - smoothstep(0.16f, 0.82f, siphon);
        return conversion * release;
    }

    private float getSnapshotMaskStrength() {
        float seed = getMaskStrength();
        float handedOff = smoothstep(CONVERSION_SOLID * 0.62f,
                SIPHON_START + 0.10f * TIMING_SCALE, age);
        return seed * Mth.lerp(handedOff, 1.0f, 0.16f);
    }

    private float getFieldMaskClipStrength() {
        if (usesSdfContainment()) {
            return 0.0f;
        }
        return age < VFX_END ? 1.0f : 0.0f;
    }

    private float getReconstructionMaskStrength() {
        // This texture is infrastructure, not a visible formation layer. Once
        // captured it is the mandatory coverage authority for every displayed
        // body frame.
        return snapshotApplied && age < VFX_END ? 1.0f : 0.0f;
    }

    private float getDeformationBlend() {
        // Preserve the texture-exact capture while it forms, then hand visual
        // authority to the deformed volume before extraction begins. Keeping
        // the snapshot after this point would pin the original outline in
        // place and turn real SDF displacement into a detached halo.
        return silhouetteHandoffBlendAt(age);
    }

    static float silhouetteHandoffBlendAt(float captureAge) {
        return smoothstep(FROZEN_MODEL_CROSSFADE_START,
                FROZEN_MODEL_CROSSFADE_END, captureAge);
    }

    static float turbulenceBlendAt(float captureAge) {
        // Square a smoothstep rather than advancing linearly. The silhouette
        // stays restrained through the texture-exact handoff, escalates hard
        // near the end of the ramp, then arrives with zero slope for a stable
        // full-strength hold before depletion begins.
        float eased = smoothstep(TURBULENCE_START, TURBULENCE_FULL,
                captureAge);
        return eased * eased;
    }

    static float signedBodyAuthorityAt(float captureAge) {
        // Color first: the frozen model must finish fading onto the opaque,
        // texture-exact black mask before signed SDF displacement can own the
        // contour. Give the visible expansion its own art-directed ramp rather
        // than compressing it into the short remainder of the turbulence ramp.
        // The exact mask still releases completely so inward cuts are not
        // permanently filled back in.
        return smoothstep(FROZEN_MODEL_CROSSFADE_END,
                VISIBLE_EXPANSION_FULL, captureAge);
    }

    static float finalCollapseAt(float captureAge) {
        return remapClamped(captureAge, SIPHON_COLLAPSE_START,
                BALL_ABSORB_END);
    }

    static float siphonProgressAt(float captureAge) {
        return remapClamped(captureAge, SIPHON_START, SIPHON_END);
    }

    static float bodyReleaseFrontAt(float captureAge) {
        float siphon = siphonProgressAt(captureAge);
        if (siphon <= 0.001f) {
            return -1.0f;
        }
        return Mth.clamp(siphon * 1.16f - 0.045f
                + finalCollapseAt(captureAge) * 0.16f, 0.0f, 1.30f);
    }

    static float siphonTrailingGateAt(float captureAge, float curveT) {
        float trailingProgress = smoothstep(0.015f, 0.94f,
                finalCollapseAt(captureAge));
        float trailingFront = Mth.lerp(trailingProgress, -0.06f, 1.08f);
        return smoothstep(trailingFront - 0.045f,
                trailingFront + 0.045f, curveT);
    }

    private boolean usesSdfContainment() {
        return voxelVolume != null
                && siphonDrainRootValid
                && voxelVolume.signedDistanceField() != null
                && voxelVolume.sdfGradientX() != null
                && voxelVolume.sdfGradientY() != null
                && voxelVolume.sdfGradientZ() != null;
    }

    private void tick(float dt) {
        installVoxelBuildIfReady();
        age += dt;
    }

    private void destroy() {
        cancelPendingVoxelBuild();
        destroyMassTransport();
    }

    private void cancelPendingVoxelBuild() {
        voxelBuildGeneration++;
        CompletableFuture<VoxelBuildOutput> pending = voxelBuildFuture;
        voxelBuildFuture = null;
        if (pending != null) {
            pending.cancel(false);
        }
    }

    private void destroyMassTransport() {
        if (advectedDensityField != null) {
            advectedDensityField.destroy();
            advectedDensityField = null;
        }
        advectedDensityShape = null;
        advectedDensityQuality = null;
        advectedDensityLogged = false;
        if (analyticalVolume != null) {
            analyticalVolume.destroy();
            analyticalVolume = null;
        }
        analyticalQuality = null;
        analyticalVolumeLogged = false;
        if (massTransport != null) {
            massTransport.destroy();
            massTransport = null;
        }
    }

    private void renderDensityPass(Camera camera, Vec3 camPos, float partialTick,
                                   boolean allowDirectVolume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity ballEnt = mc.level.getEntity(ballId);
        Entity pokemonEnt = mc.level.getEntity(pokemonId);
        if (!(ballEnt instanceof EmptyPokeBallEntity ball)) {
            return;
        }
        if (!(pokemonEnt instanceof PokemonEntity pokemon)) {
            return;
        }

        boolean renderReconstructionMask = hasTexturedSnapshotMask();
        Vec3 ballPos = new Vec3(
                Mth.lerp(partialTick, ball.xOld, ball.getX()),
                Mth.lerp(partialTick, ball.yOld, ball.getY()),
                Mth.lerp(partialTick, ball.zOld, ball.getZ())
        ).add(0, 0.08, 0);

        AABB bounds = snapshotBounds != null ? snapshotBounds : pokemon.getBoundingBox();
        Vec3 pokemonCenter = snapshotBounds != null
                ? snapshotCenter
                : bounds.getCenter().add(0, pokemon.getBbHeight() * 0.04, 0);
        if ((allowDirectVolume && voxelVolume != null) || renderReconstructionMask) {
            // The direct advected renderer returns immediately on success, so
            // all screen-space material inputs must be captured before it is
            // attempted. This is intentionally independent of the auxiliary
            // reconstruction mask's lifetime; the exact volume may outlive it.
            // The legacy fallback consumes the same values later.
            updateCompositeReconstructionInputs(camera, camPos, ballPos,
                    pokemonCenter, bounds);
        }
        boolean reconstructionAuxiliaryRendered = false;
        if (allowDirectVolume && renderReconstructionMask) {
            reconstructionAuxiliaryRendered = renderReconstructionAuxiliaryTargets(camPos);
            compositeExactMaskReady |= reconstructionAuxiliaryRendered;
            restoreDensityPassDrawState();
        }
        if (reconstructionAuxiliaryRendered) {
            // Reconstruction replays the captured Pokemon into the proxy-front
            // target, including its depth attachment. Both the mask-only edge
            // handoff and the eventual volume raymarch need real scene
            // occlusion there, so restore that attachment without clearing the
            // proxy's encoded front-depth color.
            DarkBallDensityFBO.refreshSceneDepthForDirectVolume();
            restoreDensityPassDrawState();
        }
        if (allowDirectVolume && ENABLE_ADVECTED_DENSITY_FIELD && voxelVolume != null) {
            ensureAnalyticalVolume(voxelVolume, ballPos);
            ensureAdvectedDensityField(voxelVolume, ballPos);
            if (advectedDensityField != null
                    && advanceAdvectedDensityField(ballPos)
                    && DarkBallAdvectedDensityRenderer.render(
                    advectedDensityField, analyticalVolume, voxelVolume,
                    ballPos, camera, age)) {
                return;
            }
        }
        // The analytical fullscreen and particle/splat renderers were legacy
        // attempts at this effect. The Dark Ball now has one authoritative
        // material path; if it is not ready, the vanilla model remains visible
        // instead of silently dropping into a differently shaped fallback.
    }

    private static void restoreDensityPassDrawState() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
    }

    private boolean hasSnapshotMask() {
        return snapshotApplied && !snapshotQuads.isEmpty() && getSnapshotMaskStrength() > 0.002f;
    }

    private boolean shouldWaitForSnapshotBeforeSplats() {
        return snapshotRequested && !snapshotApplied
                && age < CONVERSION_SOLID + 0.20f * TIMING_SCALE;
    }

    private boolean hasTexturedSnapshotMask() {
        return snapshotApplied
                && snapshotTexture != null
                && !texturedSnapshotQuads.isEmpty()
                && ModShaders.DARK_BALL_MASK != null
                && getReconstructionMaskStrength() > 0.002f;
    }

    private boolean renderReconstructionAuxiliaryTargets(Vec3 camPos) {
        if (snapshotTexture == null || texturedSnapshotQuads.isEmpty()) {
            return false;
        }

        boolean clearAuxiliaryTargets = !reconstructionAuxiliaryTargetsCleared;
        boolean exactMaskRendered = false;
        if (DarkBallDensityFBO.beginMaskPass(clearAuxiliaryTargets, true)) {
            setupSnapshotMaskUniforms();
            exactMaskRendered = renderTexturedSnapshotMask(camPos);
        }

        float proxyStrength = getProxyDepthStrength();
        if (proxyStrength > 0.002f && ModShaders.DARK_BALL_PROXY_DEPTH != null) {
            boolean frontRendered = renderTexturedSnapshotProxyDepth(camPos, false, proxyStrength, clearAuxiliaryTargets);
            boolean backRendered = renderTexturedSnapshotProxyDepth(camPos, true, proxyStrength, clearAuxiliaryTargets);
            if (frontRendered && backRendered) {
                compositeProxyDepthAvailable = 1.0f;
            }
        }

        reconstructionAuxiliaryTargetsCleared = true;
        DarkBallDensityFBO.resumeDensityPass();
        return exactMaskRendered;
    }

    private boolean renderTexturedSnapshotMask(Vec3 camPos) {
        float strength = getReconstructionMaskStrength();
        if (strength <= 0.002f || snapshotTexture == null || texturedSnapshotQuads.isEmpty()) {
            return false;
        }

        setupSnapshotMaskUniforms();
        RenderType maskType = BallRenderTypes.darkBallMask(snapshotTexture);
        VertexConsumer consumer = MASK_BUFFERS.getBuffer(maskType);
        Matrix4f identity = new Matrix4f();
        int color = packMaskColor(strength);

        for (DarkBallFieldMaskBufferSource.CapturedQuad quad : texturedSnapshotQuads) {
            addTexturedSnapshotVertex(consumer, identity, quad.p1(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p2(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p3(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p4(), camPos, color);
        }

        MASK_BUFFERS.endBatch(maskType);
        return true;
    }

    private boolean renderTexturedSnapshotProxyDepth(Vec3 camPos, boolean backFaces, float strength, boolean clearTarget) {
        if (!DarkBallDensityFBO.beginProxyDepthPass(backFaces, clearTarget, true)) {
            return false;
        }

        ShaderInstance shader = ModShaders.DARK_BALL_PROXY_DEPTH;
        if (shader == null) {
            return false;
        }

        Uniform uProxyAlpha = shader.getUniform("u_proxyAlpha");
        if (uProxyAlpha != null) {
            uProxyAlpha.set(Mth.clamp(strength, 0.0f, 1.0f));
        }

        RenderSystem.enableCull();
        GL11.glCullFace(backFaces ? GL11.GL_FRONT : GL11.GL_BACK);

        RenderType proxyType = BallRenderTypes.darkBallProxyDepth(snapshotTexture);
        VertexConsumer consumer = MASK_BUFFERS.getBuffer(proxyType);
        Matrix4f identity = new Matrix4f();
        int color = packMaskColor(1.0f);

        for (DarkBallFieldMaskBufferSource.CapturedQuad quad : texturedSnapshotQuads) {
            addTexturedSnapshotVertex(consumer, identity, quad.p1(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p2(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p3(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p4(), camPos, color);
        }

        MASK_BUFFERS.endBatch(proxyType);
        GL11.glCullFace(GL11.GL_BACK);
        return true;
    }

    private static void addTexturedSnapshotVertex(VertexConsumer consumer,
                                                  Matrix4f pose,
                                                  DarkBallFieldMaskBufferSource.CapturedVertex vertex,
                                                  Vec3 camPos,
                                                  int color) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * (vertex.alpha() / 255.0f));
        consumer.addVertex(pose,
                        (float) (vertex.worldPos().x - camPos.x),
                        (float) (vertex.worldPos().y - camPos.y),
                        (float) (vertex.worldPos().z - camPos.z))
                .setColor(vertex.red(), vertex.green(), vertex.blue(), alpha)
                .setUv(vertex.u(), vertex.v())
                .setUv1(vertex.overlayU(), vertex.overlayV())
                .setUv2(vertex.lightU(), vertex.lightV())
                .setNormal(vertex.normalX(), vertex.normalY(), vertex.normalZ());
    }

    private void setupSnapshotMaskUniforms() {
        ShaderInstance shader = ModShaders.DARK_BALL_MASK;
        if (shader == null) {
            return;
        }
        RenderSystem.setupShaderLights(shader);

        float expand = 0.0f;
        float screenExpand = 0.0f;

        Uniform uExpand = shader.getUniform("MaskExpand");
        if (uExpand != null) {
            uExpand.set(expand);
        }
        Uniform uScreenExpand = shader.getUniform("MaskScreenExpand");
        if (uScreenExpand != null) {
            uScreenExpand.set(screenExpand);
        }
    }

    private void renderSnapshotMask(Vec3 camPos, Camera camera, BufferBuilder buffer) {
        float strength = getSnapshotMaskStrength();
        if (strength <= 0.002f || snapshotQuads.isEmpty()) {
            return;
        }

        Vec3 viewDir = snapshotCenter.subtract(camPos);
        if (viewDir.lengthSqr() < 1e-7) {
            viewDir = new Vec3(0, 0, 1);
        } else {
            viewDir = viewDir.normalize();
        }

        float siphon = remapClamped(age, SIPHON_START, SIPHON_END);
        float surfaceDensity = Mth.clamp(strength * Mth.lerp(siphon, 0.28f, 0.14f), 0.0f, 0.34f);
        float extent = Math.max(0.18f, snapshotExtent);
        Matrix4f identity = new Matrix4f();

        for (SnapshotQuad quad : snapshotQuads) {
            float depth01 = normalizedViewDepth(quad.center, snapshotCenter, viewDir, extent);
            emitSnapshotQuad(buffer, identity, quad, camPos, surfaceDensity, depth01);
        }
    }

    private static void emitSnapshotQuad(BufferBuilder buffer,
                                         Matrix4f pose,
                                         SnapshotQuad quad,
                                         Vec3 camPos,
                                         float density,
                                         float depth01) {
        float d = Mth.clamp(density, 0f, 1f);
        float z = Mth.clamp(depth01, 0f, 1f);
        addSnapshotVertex(buffer, pose, quad.p1, camPos, 0f, 1f, d, z);
        addSnapshotVertex(buffer, pose, quad.p2, camPos, 1f, 1f, d, z);
        addSnapshotVertex(buffer, pose, quad.p3, camPos, 1f, 0f, d, z);
        addSnapshotVertex(buffer, pose, quad.p4, camPos, 0f, 0f, d, z);
    }

    private static void addSnapshotVertex(BufferBuilder buffer,
                                          Matrix4f pose,
                                          Vec3 world,
                                          Vec3 camPos,
                                          float u,
                                          float v,
                                          float density,
                                          float depth01) {
        buffer.addVertex(pose,
                        (float) (world.x - camPos.x),
                        (float) (world.y - camPos.y),
                        (float) (world.z - camPos.z))
                .setUv(u, v)
                .setColor(density, depth01, 1.0f, 1.0f)
                .setLight(FULLBRIGHT);
    }

    private static void setDensitySnapshotMode(ShaderInstance shader, float mode) {
        Uniform uniform = shader.getUniform("SnapshotMode");
        if (uniform != null) {
            uniform.set(mode);
        }
    }

    private static void setDensityProxyDepthMode(ShaderInstance shader, float mode) {
        Uniform uniform = shader.getUniform("ProxyDepthMode");
        if (uniform != null) {
            uniform.set(mode);
        }
    }

    private float getProxyDepthStrength() {
        float absorbFade = 1.0f - smoothstep(
                SIPHON_END + 0.10f * TIMING_SCALE, BALL_ABSORB_END, age);
        // Depth is also reconstruction infrastructure. Making it available on
        // the first exact-mask frame preserves the captured surface for later
        // highlights without exposing the proxy as visible geometry.
        return Mth.clamp(absorbFade, 0f, 1f);
    }

    private void updateCompositeReconstructionInputs(Camera camera, Vec3 camPos,
                                                      Vec3 ballPos, Vec3 pokemonCenter,
                                                      AABB bodyBounds) {
        if (voxelVolume != null) {
            // These two components are also needed when the legacy body-volume
            // raymarch is disabled: Y supplies the depth-continuity and
            // pseudo-surface scale used by both direct composite passes.
            compositeVolumeSize.x = Math.max(voxelVolume.captureLength(), 0.0001f);
            compositeVolumeSize.y = Math.max(voxelVolume.radius(), 0.0001f);
        } else {
            // The texture-exact handoff can render before the asynchronous
            // volume build completes. Give its proxy-depth lighting a stable
            // model-scale fallback without making that volume visible.
            compositeVolumeSize.x = Math.max(snapshotExtent * 2.0f, 0.05f);
            compositeVolumeSize.y = Math.max(snapshotExtent, 0.05f);
        }
        compositeDeformationBlend = Math.max(compositeDeformationBlend,
                getDeformationBlend());
        compositeSignedBodyAuthority = Math.max(
                compositeSignedBodyAuthority, signedBodyAuthorityAt(age));
        compositeTurbulenceBlend = Math.max(compositeTurbulenceBlend,
                turbulenceBlendAt(age));
        compositeDeformationTime = Math.max(compositeDeformationTime,
                DarkBallAdvectedDensityRenderer.deformationTimeAt(age));
        Vector2f centerUv = projectWorldToScreenUv(pokemonCenter, camera, camPos);
        Vector2f worldUpUv = projectWorldToScreenUv(
                pokemonCenter.add(0.0, 1.0, 0.0), camera, camPos);
        if (centerUv != null && worldUpUv != null) {
            float upX = worldUpUv.x - centerUv.x;
            float upY = worldUpUv.y - centerUv.y;
            float upLengthSquared = upX * upX + upY * upY;
            if (upLengthSquared > 1.0e-8f) {
                float inverseLength = Mth.invSqrt(upLengthSquared);
                compositeProjectedUp.set(upX * inverseLength, upY * inverseLength);
            }
        }

        if (bodyBounds != null) {
            Vector3d bodyCenter = new Vector3d(
                    (bodyBounds.minX + bodyBounds.maxX) * 0.5,
                    (bodyBounds.minY + bodyBounds.maxY) * 0.5,
                    (bodyBounds.minZ + bodyBounds.maxZ) * 0.5);
            float halfX =
                    (float) ((bodyBounds.maxX - bodyBounds.minX) * 0.5);
            float halfY =
                    (float) ((bodyBounds.maxY - bodyBounds.minY) * 0.5);
            float halfZ =
                    (float) ((bodyBounds.maxZ - bodyBounds.minZ) * 0.5);
            DarkBallProjectedEffectBounds.Projection bodyProjection =
                    DarkBallProjectedEffectBounds.begin(
                            new Matrix4f(RenderSystem.getProjectionMatrix()),
                            camera.rotation(),
                            new Vector3d(camPos.x, camPos.y, camPos.z));
            bodyProjection.includeOrientedBox(
                    bodyCenter,
                    new Vector3d(1.0, 0.0, 0.0),
                    new Vector3d(0.0, 1.0, 0.0),
                    new Vector3d(0.0, 0.0, 1.0),
                    -halfX, -halfY, -halfZ,
                    halfX, halfY, halfZ);
            DarkBallProjectedEffectBounds.UvBounds bodyUvBounds =
                    bodyProjection.finish(1, 1, 0);
            compositeBodyUvBounds.set(
                    bodyUvBounds.minU(), bodyUvBounds.minV(),
                    bodyUvBounds.maxU(), bodyUvBounds.maxV());
        }

        Vector3f rootLocal = voxelVolume != null && siphonDrainRootValid
                ? new Vector3f(siphonDrainRootLocal)
                : null;
        Vec3 rootWorld = rootLocal != null ? volumeLocalToWorld(voxelVolume, rootLocal) : pokemonCenter;

        compositeSiphonProgress = Math.max(compositeSiphonProgress,
                remapClamped(age, SIPHON_START, SIPHON_END));
        Vector2f rootUv = projectWorldToScreenUv(rootWorld, camera, camPos);
        Vector2f ballUv = projectWorldToScreenUv(ballPos, camera, camPos);
        if (rootUv == null || ballUv == null) {
            return;
        }

        compositeSiphonRootU = Mth.clamp(rootUv.x, 0.0f, 1.0f);
        compositeSiphonRootV = Mth.clamp(rootUv.y, 0.0f, 1.0f);
        compositeBallU = Mth.clamp(ballUv.x, 0.0f, 1.0f);
        compositeBallV = Mth.clamp(ballUv.y, 0.0f, 1.0f);
    }

    private void updateCompositeBodyVolumeInputs(Vec3 ballPos) {
        if (!ENABLE_BODY_VOLUME_RAYMARCH || massTransport == null || voxelVolume == null) {
            return;
        }

        int textureId = massTransport.uploadMassTexture();
        int siphonTextureId = massTransport.uploadSiphonTexture();
        if (textureId == 0) {
            return;
        }

        float formation = smoothstep(CONVERSION_START, CONVERSION_SOLID, age);
        float fade = 1.0f - smoothstep(BALL_ABSORB_END, VFX_END, age);
        float strength = Mth.clamp(formation * fade * 0.72f, 0f, 1f);

        compositeMassTextureId = textureId;
        compositeSiphonTextureId = siphonTextureId;
        compositeBodyVolumeStrength = Math.max(compositeBodyVolumeStrength, strength);
        compositeAnalyticSiphonStrength = Math.max(compositeAnalyticSiphonStrength,
                Mth.clamp(smoothstep(0.02f, 0.20f, remapClamped(age, SIPHON_START, SIPHON_END))
                        * (1.0f - smoothstep(BALL_ABSORB_END, VFX_END, age)), 0f, 1f));
        compositeVolumeRoot.set((float) voxelVolume.root().x, (float) voxelVolume.root().y, (float) voxelVolume.root().z);
        compositeVolumeAxis.set((float) voxelVolume.axis().x, (float) voxelVolume.axis().y, (float) voxelVolume.axis().z);
        compositeVolumeSide.set((float) voxelVolume.side().x, (float) voxelVolume.side().y, (float) voxelVolume.side().z);
        compositeVolumeUp.set((float) voxelVolume.up().x, (float) voxelVolume.up().y, (float) voxelVolume.up().z);
        compositeVolumeSize.set(
                Math.max(voxelVolume.captureLength(), 0.0001f),
                Math.max(voxelVolume.radius(), 0.0001f),
                Math.max(massTransport.initialTotalMass(), 0.0001f)
        );

        Vector3f root = massTransport.outletLocal();
        Vector3f intake = siphonIntakeLocal(voxelVolume, ballPos);
        Vector3f c0 = DarkBallCaptureMath.siphonCurvePoint(root, intake, voxelVolume.bodyRadius(), 0f);
        Vector3f c1 = DarkBallCaptureMath.siphonCurveControlALocal(root, intake, voxelVolume.bodyRadius());
        Vector3f c2 = DarkBallCaptureMath.siphonCurveControlBLocal(root, intake, voxelVolume.bodyRadius());
        Vector3f c3 = DarkBallCaptureMath.siphonCurvePoint(root, intake, voxelVolume.bodyRadius(), 1f);
        setWorldVector(compositeSiphonP0, volumeLocalToWorld(voxelVolume, c0));
        setWorldVector(compositeSiphonP1, volumeLocalToWorld(voxelVolume, c1));
        setWorldVector(compositeSiphonP2, volumeLocalToWorld(voxelVolume, c2));
        setWorldVector(compositeSiphonP3, volumeLocalToWorld(voxelVolume, c3));
    }

    private static void setWorldVector(Vector3f target, Vec3 source) {
        target.set((float) source.x, (float) source.y, (float) source.z);
    }

    private static Vector2f projectWorldToScreenUv(Vec3 world, Camera camera, Vec3 camPos) {
        Vector3f view = new Vector3f(
                (float) (world.x - camPos.x),
                (float) (world.y - camPos.y),
                (float) (world.z - camPos.z)
        );
        view.rotate(new Quaternionf(camera.rotation()).conjugate());

        Vector4f clip = new Vector4f(view.x, view.y, view.z, 1.0f);
        RenderSystem.getProjectionMatrix().transform(clip);
        if (clip.w <= 1e-5f) {
            return null;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        return new Vector2f(ndcX * 0.5f + 0.5f, ndcY * 0.5f + 0.5f);
    }

    private void finishSnapshotCapture(ModelSnapshotCapture capture) {
        if (snapshotQuads.isEmpty() || !capture.bounds.hasBounds) {
            return;
        }

        snapshotCenter = capture.bounds.center();
        snapshotExtent = capture.bounds.extent();
        snapshotBounds = capture.bounds.toAabb();
        snapshotRequested = false;
        snapshotApplied = true;
        maskCapturedThisFrame = false;
    }

    private void finishTexturedSnapshotCapture() {
        texturedSnapshotCapturing = false;
        if (texturedSnapshotQuads.isEmpty()) {
            snapshotTexture = null;
            return;
        }

        SnapshotBounds bounds = new SnapshotBounds();
        for (DarkBallFieldMaskBufferSource.CapturedQuad quad : texturedSnapshotQuads) {
            bounds.include(quad.p1().worldPos());
            bounds.include(quad.p2().worldPos());
            bounds.include(quad.p3().worldPos());
            bounds.include(quad.p4().worldPos());
        }
        if (!bounds.hasBounds) {
            snapshotTexture = null;
            return;
        }

        snapshotCenter = bounds.center();
        snapshotExtent = bounds.extent();
        snapshotBounds = bounds.toAabb();
        buildVoxelVolumeFromSnapshot(bounds);
        snapshotRequested = false;
        snapshotApplied = true;
        maskCapturedThisFrame = false;
    }

    private void buildVoxelVolumeFromSnapshot(SnapshotBounds bounds) {
        voxelVolume = null;
        cancelPendingVoxelBuild();
        destroyMassTransport();
        massTransportLogged = false;
        siphonDrainRootValid = false;
        initializedSplats = false;
        splats.clear();
        resetSplatSimulation();
        splatSourceLogged = false;
        siphonOutletLogged = false;
        if (capturedModelVertices.size() < 12 && capturedModelTriangles.size() < 6) {
            logVoxelBuild("insufficient captured mesh", capturedModelVertices.size(), capturedModelTriangles.size(), 0);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Entity ballEnt = mc.level.getEntity(ballId);
        if (!(ballEnt instanceof EmptyPokeBallEntity ball)) {
            logVoxelBuild("missing ball entity", capturedModelVertices.size(), capturedModelTriangles.size(), 0);
            return;
        }

        Vec3 ballPos = new Vec3(ball.getX(), ball.getY(), ball.getZ()).add(0, 0.08, 0);
        Vec3 capturedCenter = snapshotCenter;
        AABB capturedBounds = bounds.toAabb();
        List<Vec3> capturedVertices = List.copyOf(capturedModelVertices);
        List<DarkBallFieldMaskBufferSource.CapturedTriangle> capturedTriangles =
                List.copyOf(capturedModelTriangles);
        NativeImage textureImage = loadSnapshotTextureImage();
        DarkBallVfxQuality quality = DarkBallVfxQuality.parse(
                com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                        .getInstance().getClientConfig().darkBallVfxQuality()
        );
        int generation = voxelBuildGeneration;

        // Triangle rasterization, interior fill, SDF construction, gradients,
        // component labeling, and release-order propagation are all CPU-only.
        // Running them here used to stall the render thread exactly when the
        // ball hit. Keep GL texture allocation deferred to the normal renderer.
        voxelBuildFuture = CompletableFuture.supplyAsync(() -> {
            long started = System.nanoTime();
            try (NativeImage image = textureImage) {
                DarkBallVolumeBuildResult volume = DarkBallVolumeVoxelizer.build(
                        ballPos,
                        capturedCenter,
                        capturedBounds,
                        capturedVertices,
                        capturedTriangles,
                        image
                );
                Vector3f ballLocal = volume == null
                        ? new Vector3f()
                        : volumeWorldToLocal(volume, ballPos);
                DarkBallAnalyticalVolume analytical = volume == null
                        ? null
                        : DarkBallAnalyticalVolume.build(volume, ballLocal, quality);
                long buildMillis = (System.nanoTime() - started) / 1_000_000L;
                return new VoxelBuildOutput(
                        generation,
                        volume,
                        analytical,
                        quality,
                        capturedVertices.size(),
                        capturedTriangles.size(),
                        buildMillis
                );
            }
        }, Util.backgroundExecutor());
    }

    private void installVoxelBuildIfReady() {
        CompletableFuture<VoxelBuildOutput> pending = voxelBuildFuture;
        if (pending == null || !pending.isDone()) {
            return;
        }
        voxelBuildFuture = null;

        VoxelBuildOutput result;
        try {
            result = pending.join();
        } catch (CompletionException failure) {
            Shadowedhearts.LOGGER.error(
                    "[ShadowedHearts] Dark Ball asynchronous voxel build failed for pokemon {}",
                    pokemonId,
                    failure.getCause() == null ? failure : failure.getCause()
            );
            return;
        }
        if (result == null || result.generation() != voxelBuildGeneration) {
            return;
        }

        voxelVolume = result.volume();
        analyticalVolume = result.analyticalVolume();
        analyticalQuality = result.quality();
        analyticalVolumeAge = age;
        logVoxelBuild("built asynchronously in " + result.buildMillis() + " ms",
                result.vertexCount(), result.triangleCount(),
                countVoxelCandidates(voxelVolume));
        logVoxelSdfStats(voxelVolume);

        if (analyticalVolume != null) {
            siphonDrainRootLocal.set(analyticalVolume.outletLocal());
            siphonDrainRootValid = true;
            analyticalVolumeLogged = true;
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball captured shape atlas for pokemon {} active ({})",
                    pokemonId,
                    analyticalVolume.summary()
            );
        }
    }

    private NativeImage loadSnapshotTextureImage() {
        if (snapshotTexture == null) {
            return null;
        }
        try {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(snapshotTexture);
            if (resource.isEmpty()) {
                return null;
            }
            try (var stream = resource.get().open()) {
                return NativeImage.read(stream);
            }
        } catch (Exception failure) {
            Shadowedhearts.LOGGER.warn(
                    "[ShadowedHearts] Could not read Dark Ball snapshot texture {}; "
                            + "falling back to geometry-only voxelization",
                    snapshotTexture,
                    failure
            );
            return null;
        }
    }

    private static void captureCubeFaces(ModelSnapshotCapture capture, Matrix4f pose, ModelPart.Cube cube) {
        float minX = cube.minX / 16.0f;
        float minY = cube.minY / 16.0f;
        float minZ = cube.minZ / 16.0f;
        float maxX = cube.maxX / 16.0f;
        float maxY = cube.maxY / 16.0f;
        float maxZ = cube.maxZ / 16.0f;

        float inflate = 0.006f;
        minX -= inflate;
        minY -= inflate;
        minZ -= inflate;
        maxX += inflate;
        maxY += inflate;
        maxZ += inflate;

        addSnapshotQuad(capture, pose, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ);
        addSnapshotQuad(capture, pose, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ);
        addSnapshotQuad(capture, pose, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ);
        addSnapshotQuad(capture, pose, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
        addSnapshotQuad(capture, pose, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        addSnapshotQuad(capture, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ);
    }

    private static void addSnapshotQuad(ModelSnapshotCapture capture,
                                        Matrix4f pose,
                                        float x1, float y1, float z1,
                                        float x2, float y2, float z2,
                                        float x3, float y3, float z3,
                                        float x4, float y4, float z4) {
        if (capture.vfx.snapshotQuads.size() >= MAX_SNAPSHOT_QUADS) {
            return;
        }

        Vec3 p1 = transformSnapshotPoint(pose, capture.poseToWorldOffset, x1, y1, z1);
        Vec3 p2 = transformSnapshotPoint(pose, capture.poseToWorldOffset, x2, y2, z2);
        Vec3 p3 = transformSnapshotPoint(pose, capture.poseToWorldOffset, x3, y3, z3);
        Vec3 p4 = transformSnapshotPoint(pose, capture.poseToWorldOffset, x4, y4, z4);
        Vec3 center = new Vec3(
                (p1.x + p2.x + p3.x + p4.x) * 0.25,
                (p1.y + p2.y + p3.y + p4.y) * 0.25,
                (p1.z + p2.z + p3.z + p4.z) * 0.25
        );

        capture.include(p1);
        capture.include(p2);
        capture.include(p3);
        capture.include(p4);
        capture.vfx.snapshotQuads.add(new SnapshotQuad(p1, p2, p3, p4, center));
    }

    private static Vec3 transformSnapshotPoint(Matrix4f pose, Vec3 offset, float x, float y, float z) {
        Vector3f transformed = pose.transformPosition(x, y, z, new Vector3f());
        return new Vec3(transformed.x + offset.x, transformed.y + offset.y, transformed.z + offset.z);
    }

    private void renderConversionVolume(AABB bounds, Vec3 center, Vec3 ballPos, Vec3 camPos,
                                        Quaternionf camOrientation, PoseStack stack, BufferBuilder buffer) {
        float formation = smoothstep(CONVERSION_START, CONVERSION_SOLID, age);
        if (formation <= 0.001f) {
            return;
        }

        Basis basis = makeBasis(center, ballPos);
        float globalSiphon = remapClamped(age, SIPHON_START, SIPHON_END);
        float globalAbsorb = remapClamped(age, SIPHON_END, BALL_ABSORB_END);
        float releaseProgress = smoothstep(0.02f, 0.94f, globalSiphon);
        float finalFlush = smoothstep(0.84f, 1.0f, globalSiphon);
        float effectiveRelease = Math.max(releaseProgress, finalFlush);
        if (voxelVolume != null && siphonDrainRootValid && !siphonOutletLogged) {
            logSiphonOutletStats(voxelVolume, ballPos);
        }

        float halfX = (float) Math.max(0.22, bounds.getXsize() * 0.50);
        float halfY = (float) Math.max(0.28, bounds.getYsize() * 0.50);
        float halfZ = (float) Math.max(0.22, bounds.getZsize() * 0.50);
        float bodyScale = Math.max(0.32f, Math.max(halfX, Math.max(halfY, halfZ)));
        float viewExtent = Math.max(0.001f, bodyScale * 1.65f);
        Vec3 viewDir = center.subtract(camPos);
        if (viewDir.lengthSqr() < 1e-7) {
            viewDir = new Vec3(0, 0, 1);
        } else {
            viewDir = viewDir.normalize();
        }

        for (int splatIndex = 0; splatIndex < splats.size(); splatIndex++) {
            VolumeSplat splat = splats.get(splatIndex);
            float releaseOrder = voxelVolume != null && siphonDrainRootValid ? splat.release : splat.depthLayer;
            float depthReleaseDelay = (1f - Mth.clamp(splat.depthLayer, 0f, 1f)) * 0.045f;
            float releaseJitter = (splat.seed - 0.5f) * 0.025f;
            float layeredRelease = Mth.clamp(releaseOrder + depthReleaseDelay + releaseJitter, 0f, 1f);
            float releaseWave = smoothstep(layeredRelease - 0.035f, layeredRelease + 0.055f, effectiveRelease);
            releaseWave = Math.max(releaseWave, finalFlush);
            float travel = Mth.clamp((effectiveRelease - layeredRelease + finalFlush * 0.22f) / 0.24f, 0f, 1f);
            travel = Math.max(travel, finalFlush);
            float siphonEase = smoothstep(0f, 1f, travel);
            float entranceBlend = Mth.clamp(releaseWave * smoothstep(0.02f, 0.36f, travel), 0f, 1f);

            Vec3 bodyPos = bodyPosition(center, halfX, halfY, halfZ, bodyScale, splat);
            Vec3 curlVelocity = boundaryVorticity(center, bodyPos, basis, bodyScale, splat, globalSiphon);
            bodyPos = bodyPos.add(curlVelocity);
            Vector3f restLocalPos = voxelVolume != null ? splatVolumeLocalPosition(splat, voxelVolume) : null;
            Vector3f splatLocalPos = restLocalPos;

            float pathTravel = smoothstep(0.28f, 0.98f, travel);
            float entranceT = Mth.clamp(0.020f + releaseOrder * 0.010f, 0f, 0.065f);
            Vec3 entrancePos = siphonTubePosition(center, ballPos, basis, bodyScale, splat, entranceT, globalSiphon, finalFlush);
            float tubeT = Mth.clamp(0.035f + pathTravel * 0.945f + releaseOrder * 0.010f, 0f, 1f);
            Vec3 tubePos = siphonTubePosition(center, ballPos, basis, bodyScale, splat, tubeT, globalSiphon, finalFlush);
            Vec3 siphonTarget = entrancePos.lerp(tubePos, pathTravel);
            Vec3 finalPos = bodyPos.lerp(siphonTarget, entranceBlend);
            Vec3 flowDirection = siphonTarget.subtract(bodyPos);
            if (flowDirection.lengthSqr() > 1e-7) {
                flowDirection = flowDirection.normalize();
            }
            float flowMagnitude = bodyScale * releaseWave * smoothstep(0.04f, 0.82f, travel)
                    * Mth.lerp(siphonEase, 0.34f, 1.12f);
            Vec3 stretchVelocity = curlVelocity.scale(1f - entranceBlend)
                    .add(flowDirection.scale(flowMagnitude));
            SplatState state = simulatedSplatState(splatIndex);
            if (state != null && voxelVolume != null) {
                Vector3f simPos = new Vector3f(state.previousPosition).lerp(state.position, splatRenderAlpha);
                splatLocalPos = simPos;
                finalPos = volumeLocalToWorld(voxelVolume, simPos);
                stretchVelocity = volumeLocalDirectionToWorld(voxelVolume, state.smoothedVelocity);
            }

            float vanish = splatVisibilityBeforeSink(splatLocalPos, ballPos, travel);
            float endFade = 1f - smoothstep(0.72f, 1f, globalAbsorb);
            float density = splat.density * formation * vanish * endFade;
            density *= Mth.lerp(globalSiphon, 1.36f, 1.12f);
            if (ENABLE_MASS_TRANSPORT_DENSITY_GATE && massTransport != null && restLocalPos != null) {
                float massPresence = Mth.clamp(massTransport.sampleRelativeMass(restLocalPos), 0f, 1.18f);
                float transportInfluence = smoothstep(0.035f, 0.96f, globalSiphon)
                        * Mth.lerp(smoothstep(0.16f, 0.72f, travel), 1.0f, 0.42f);
                density *= Mth.lerp(transportInfluence, 1.0f, massPresence);
            }
            if (density < 0.006f) {
                continue;
            }

            float baseSize = (voxelVolume != null ? splat.radius : bodyScale * splat.radius)
                    * Mth.lerp(splat.boundaryWeight, 1.08f, 0.98f);
            float siphonShrink = Mth.lerp(splat.boundaryWeight, 0.58f, 0.76f);
            float sizeTransition = smoothstep(0.12f, 0.72f, travel);
            float size = baseSize * Mth.lerp(sizeTransition, 1.0f, siphonShrink);
            size = Math.max(Mth.lerp(splat.boundaryWeight, 0.028f, 0.012f), size);

            float depth01 = normalizedViewDepth(finalPos, center, viewDir, viewExtent);
            float movingGuide = smoothstep(0.08f, 0.28f, travel) * vanish * endFade;
            float selfGuide = Mth.clamp(movingGuide * Mth.lerp(siphonEase, 0.34f, 0.86f), 0f, 0.86f);
            if (usesSdfContainment() && splatLocalPos != null) {
                float localThickness = sampleLocalThickness(voxelVolume, splatLocalPos);
                selfGuide = Mth.clamp(localThickness / Math.max(viewExtent * 2.0f, 0.001f) * 0.5f, 0.008f, 0.22f);
            }
            Vec3 shapeAxis = splatShapeAxisWorld(splat);
            float shapeStretch = splat.shapeStretch;
            SplatCovariance covariance = wallAwareCovariance(splat, splatLocalPos, ballPos, viewDir, shapeAxis, stretchVelocity, travel);
            if (covariance.active()) {
                shapeAxis = covariance.axisWorld();
                shapeStretch = Math.max(shapeStretch, covariance.stretch());
                size *= covariance.sizeScale();
            }
            SdfClipContext sdfClip = splatSdfClipContext(ballPos, size);
            emitMaterialSplat(finalPos, stretchVelocity, camPos, camOrientation, stack, buffer,
                    density, size, depth01, splat.boundaryWeight, selfGuide, bodyScale,
                    travel, siphonEase, shapeAxis, shapeStretch, sdfClip);
        }
    }

    private SplatState simulatedSplatState(int index) {
        return index >= 0 && index < splatStates.size() ? splatStates.get(index) : null;
    }

    private float splatVisibilityBeforeSink(Vector3f splatLocalPos, Vec3 ballPos, float travel) {
        if (!usesSdfContainment() || splatLocalPos == null) {
            return 1f - smoothstep(0.94f, 1f, travel);
        }

        Vector3f intake = siphonIntakeLocal(voxelVolume, ballPos);
        float distanceToSink = splatLocalPos.distance(intake);
        float sinkInner = Math.max(voxelVolume.bodyRadius() * 0.020f, voxelVolume.sdfVoxelYz() * 0.70f);
        float sinkOuter = Math.max(voxelVolume.bodyRadius() * 0.085f, voxelVolume.sdfVoxelYz() * 2.30f);
        float sinkVisibility = smoothstep(sinkInner, sinkOuter, distanceToSink);
        float finalTravelFade = 1f - smoothstep(0.985f, 1f, travel);
        return Mth.clamp(Math.max(sinkVisibility, finalTravelFade * 0.36f), 0f, 1f);
    }

    private SdfClipContext splatSdfClipContext(Vec3 ballPos, float splatSize) {
        if (!usesSdfContainment()) {
            return null;
        }

        Vector3f intake = siphonIntakeLocal(voxelVolume, ballPos);
        float allowedOutside = Math.max(voxelVolume.sdfVoxelYz() * 0.18f, voxelVolume.bodyRadius() * 0.003f);
        float softBand = Math.max(voxelVolume.sdfVoxelYz() * 1.15f, splatSize * 0.16f);
        return new SdfClipContext(intake, allowedOutside, softBand);
    }

    private Vec3 splatShapeAxisWorld(VolumeSplat splat) {
        if (!ENABLE_MODEL_SHAPED_SPLATS || splat.shapeStretch <= 1.01f) {
            return Vec3.ZERO;
        }
        Vec3 axis = voxelVolume != null
                ? voxelVolume.axis().scale(splat.shapeAxisX)
                .add(voxelVolume.side().scale(splat.shapeAxisY))
                .add(voxelVolume.up().scale(splat.shapeAxisZ))
                : new Vec3(splat.shapeAxisX, splat.shapeAxisY, splat.shapeAxisZ);
        return axis.lengthSqr() < 1e-7 ? Vec3.ZERO : axis.normalize();
    }

    private SplatCovariance wallAwareCovariance(VolumeSplat splat, Vector3f localPos, Vec3 ballPos, Vec3 viewDir,
                                                Vec3 preferredAxisWorld, Vec3 flowVelocity, float travel) {
        if (!ENABLE_WALL_AWARE_COVARIANCE || !usesSdfContainment() || localPos == null) {
            return SplatCovariance.inactive();
        }

        Vector3f intake = siphonIntakeLocal(voxelVolume, ballPos);
        SdfSample pokemonSample = sampleVolumeSdf(voxelVolume, localPos);
        SdfSample combinedSample = sampleCombinedContainerSdf(voxelVolume, localPos, siphonDrainRootLocal, intake);
        SdfSample sample = travel < 0.16f ? pokemonSample : combinedSample;
        if (!Float.isFinite(sample.distance) || sample.normal.lengthSqr() < 1e-7) {
            return SplatCovariance.inactive();
        }

        float wallBandWidth = Math.max(voxelVolume.sdfVoxelYz() * 2.75f, voxelVolume.bodyRadius() * 0.070f);
        float wallBand = 1.0f - smoothstep(voxelVolume.sdfVoxelYz() * 0.22f, wallBandWidth, Math.abs(sample.distance));
        wallBand = Mth.clamp(Math.max(wallBand, splat.boundaryWeight * 0.55f), 0f, 1f);
        if (wallBand <= 0.015f) {
            return SplatCovariance.inactive();
        }

        Vec3 normal = splatSurfaceNormalWorld(splat);
        if (normal.lengthSqr() < 1e-7) {
            normal = sample.normal.normalize();
        }
        Vec3 view = viewDir.lengthSqr() > 1e-7 ? viewDir.normalize() : voxelVolume.axis();
        Vec3 silhouetteTangent = normal.cross(view);
        float projectedNormal = Mth.clamp((float) silhouetteTangent.length(), 0f, 1f);
        if (silhouetteTangent.lengthSqr() > 1e-7) {
            silhouetteTangent = silhouetteTangent.normalize();
        }

        Vec3 storedTangent = splatSurfaceTangentWorld(splat, normal);
        Vec3 storedBitangent = splatSurfaceBitangentWorld(splat, normal, storedTangent);
        if (silhouetteTangent.lengthSqr() > 1e-7
                && storedTangent.lengthSqr() > 1e-7
                && storedBitangent.lengthSqr() > 1e-7) {
            Vec3 framedSilhouette = storedTangent.scale(silhouetteTangent.dot(storedTangent))
                    .add(storedBitangent.scale(silhouetteTangent.dot(storedBitangent)));
            if (framedSilhouette.lengthSqr() > 1e-7) {
                silhouetteTangent = framedSilhouette.normalize();
            }
        }
        Vec3 tangent = silhouetteTangent.lengthSqr() > 1e-7
                ? storedTangent.scale(0.54f).add(silhouetteTangent.scale(0.46f))
                : storedTangent.lengthSqr() > 1e-7
                ? storedTangent
                : preferredAxisWorld.lengthSqr() > 1e-7
                ? preferredAxisWorld.subtract(normal.scale(preferredAxisWorld.dot(normal)))
                : Vec3.ZERO;
        if (tangent.lengthSqr() < 1e-7) {
            tangent = fallbackWallTangent(normal);
        } else {
            tangent = tangent.normalize();
        }

        Vec3 flowTangent = flowVelocity.lengthSqr() > 1e-7
                ? flowVelocity.subtract(normal.scale(flowVelocity.dot(normal)))
                : Vec3.ZERO;
        if (flowTangent.lengthSqr() > 1e-7) {
            float flowBlend = smoothstep(0.22f, 0.82f, travel);
            tangent = tangent.scale(1.0f - flowBlend).add(flowTangent.normalize().scale(flowBlend));
            tangent = tangent.lengthSqr() < 1e-7 ? fallbackWallTangent(normal) : tangent.normalize();
        }

        float localThickness = sampleLocalThickness(voxelVolume, localPos);
        float thickness01 = Mth.clamp(localThickness / Math.max(voxelVolume.bodyRadius() * 0.72f, voxelVolume.sdfVoxelYz()), 0f, 1f);
        float thin01 = 1.0f - thickness01;
        float silhouetteWeight = smoothstep(0.18f, 0.74f, projectedNormal);
        float shapeWeight = Mth.clamp(wallBand * Mth.lerp(travel, silhouetteWeight, 1.0f), 0f, 1f);
        float travelRelax = smoothstep(0.18f, 0.82f, travel) * 0.22f;
        float surfaceStretch = Mth.lerp(shapeWeight, 1.0f, 1.78f + thin01 * 0.24f - travelRelax);
        float sizeScale = Mth.lerp(shapeWeight, 1.0f, Mth.lerp(thin01, 0.98f, 0.91f));
        return new SplatCovariance(tangent, surfaceStretch, sizeScale, true);
    }

    private Vec3 fallbackWallTangent(Vec3 normal) {
        Vec3 tangent = normal.cross(voxelVolume.axis());
        if (tangent.lengthSqr() < 1e-7) {
            tangent = normal.cross(voxelVolume.side());
        }
        if (tangent.lengthSqr() < 1e-7) {
            tangent = normal.cross(voxelVolume.up());
        }
        return tangent.lengthSqr() < 1e-7 ? voxelVolume.side() : tangent.normalize();
    }

    private Vec3 splatSurfaceNormalWorld(VolumeSplat splat) {
        Vec3 normal = voxelVolume != null
                ? volumeLocalDirectionToWorld(voxelVolume, new Vector3f(
                splat.surfaceNormalX,
                splat.surfaceNormalY,
                splat.surfaceNormalZ
        ))
                : new Vec3(splat.surfaceNormalX, splat.surfaceNormalY, splat.surfaceNormalZ);
        return normal.lengthSqr() < 1e-7 ? Vec3.ZERO : normal.normalize();
    }

    private Vec3 splatSurfaceTangentWorld(VolumeSplat splat, Vec3 normalWorld) {
        Vec3 tangent = voxelVolume != null
                ? volumeLocalDirectionToWorld(voxelVolume, new Vector3f(
                splat.surfaceTangentX,
                splat.surfaceTangentY,
                splat.surfaceTangentZ
        ))
                : new Vec3(splat.surfaceTangentX, splat.surfaceTangentY, splat.surfaceTangentZ);
        if (normalWorld.lengthSqr() > 1e-7 && tangent.lengthSqr() > 1e-7) {
            tangent = tangent.subtract(normalWorld.scale(tangent.dot(normalWorld)));
        }
        if (tangent.lengthSqr() < 1e-7) {
            tangent = voxelVolume != null ? fallbackWallTangent(normalWorld) : new Vec3(0, 1, 0);
        }
        return tangent.lengthSqr() < 1e-7 ? Vec3.ZERO : tangent.normalize();
    }

    private Vec3 splatSurfaceBitangentWorld(VolumeSplat splat, Vec3 normalWorld, Vec3 tangentWorld) {
        Vec3 bitangent = voxelVolume != null
                ? volumeLocalDirectionToWorld(voxelVolume, new Vector3f(
                splat.surfaceBitangentX,
                splat.surfaceBitangentY,
                splat.surfaceBitangentZ
        ))
                : new Vec3(splat.surfaceBitangentX, splat.surfaceBitangentY, splat.surfaceBitangentZ);
        if (normalWorld.lengthSqr() > 1e-7 && bitangent.lengthSqr() > 1e-7) {
            bitangent = bitangent.subtract(normalWorld.scale(bitangent.dot(normalWorld)));
        }
        if (tangentWorld.lengthSqr() > 1e-7 && bitangent.lengthSqr() > 1e-7) {
            bitangent = bitangent.subtract(tangentWorld.scale(bitangent.dot(tangentWorld)));
        }
        if (bitangent.lengthSqr() < 1e-7 && normalWorld.lengthSqr() > 1e-7 && tangentWorld.lengthSqr() > 1e-7) {
            bitangent = normalWorld.cross(tangentWorld);
        }
        return bitangent.lengthSqr() < 1e-7 ? Vec3.ZERO : bitangent.normalize();
    }

    private Vec3 bodyPosition(Vec3 center, float halfX, float halfY, float halfZ,
                              float bodyScale, VolumeSplat splat) {
        Vec3 pos;
        if (voxelVolume != null) {
            pos = voxelVolume.root()
                    .add(voxelVolume.axis().scale(splat.localX * voxelVolume.captureLength()))
                    .add(voxelVolume.side().scale(splat.localY * voxelVolume.radius()))
                    .add(voxelVolume.up().scale(splat.localZ * voxelVolume.radius()));
        } else {
            pos = center.add(
                    splat.localX * halfX,
                    splat.localY * halfY,
                    splat.localZ * halfZ
            );
        }

        float pulse = (float) Math.sin(age * (2.2f + splat.seed * 1.4f) + splat.seed * TAU);
        Vec3 radial = voxelVolume != null
                ? voxelVolume.side().scale(splat.localY).add(voxelVolume.up().scale(splat.localZ))
                : new Vec3(splat.localX, splat.localY, splat.localZ);
        if (radial.lengthSqr() > 1e-7) {
            pos = pos.add(radial.normalize().scale(pulse * bodyScale * 0.018f * splat.boundaryWeight));
        }
        return pos;
    }

    private Vec3 boundaryVorticity(Vec3 center, Vec3 bodyPos, Basis basis,
                                   float bodyScale, VolumeSplat splat, float globalSiphon) {
        if (!ENABLE_BOUNDARY_VORTICITY) {
            return Vec3.ZERO;
        }

        float boundary = splat.boundaryWeight;
        if (boundary <= 0.001f) {
            return Vec3.ZERO;
        }

        Vec3 normal = bodyPos.subtract(center);
        if (normal.lengthSqr() < 1e-7) {
            normal = basis.up;
        } else {
            normal = normal.normalize();
        }

        Vec3 tangentA = normal.cross(basis.forward);
        if (tangentA.lengthSqr() < 1e-7) {
            tangentA = normal.cross(basis.right);
        }
        tangentA = tangentA.lengthSqr() < 1e-7 ? basis.right : tangentA.normalize();
        Vec3 tangentB = normal.cross(tangentA).normalize();

        float phaseA = age * (4.0f + splat.volatility * 2.2f) + splat.seed * TAU;
        float phaseB = age * (2.5f + splat.depthLayer) + splat.localY * 4.7f;
        float strength = bodyScale * boundary * (0.045f + splat.volatility * 0.055f);
        strength *= Mth.lerp(globalSiphon, 1f, 0.52f);

        Vec3 tangentialFlow = tangentA.scale(Math.sin(phaseA) * strength)
                .add(tangentB.scale(Math.cos(phaseB + splat.localX * 2.6f) * strength * 0.72f));
        Vec3 normalPerturb = normal.scale(Math.sin(phaseA * 0.73f + phaseB) * strength * 0.22f);

        return tangentialFlow.add(normalPerturb);
    }

    private void renderSiphonTube(AABB bounds, Vec3 center, Vec3 ballPos, Vec3 camPos,
                                  Quaternionf camOrientation,
                                  PoseStack stack, BufferBuilder buffer) {
        float siphon = remapClamped(age, SIPHON_START, SIPHON_END);
        float tubeClip = smoothstep(SIPHON_TUBE_CLIP_START, SIPHON_TUBE_CLIP_SOLID, siphon);
        if (tubeClip <= 0.001f) {
            return;
        }

        Basis basis = makeBasis(center, ballPos);
        float bodyScale = Math.max(0.32f, (float) Math.max(bounds.getXsize(),
                Math.max(bounds.getYsize(), bounds.getZsize())) * 0.5f);
        float stencilEnd = Mth.clamp(tubeClip * 1.28f, 0f, 1f);
        Vec3 viewDir = center.subtract(camPos);
        if (viewDir.lengthSqr() < 1e-7) {
            viewDir = new Vec3(0, 0, 1);
        } else {
            viewDir = viewDir.normalize();
        }

        int axialSamples = 40;
        int ringSamples = 6;
        for (int i = 0; i <= axialSamples; i++) {
            float t = i / (float) axialSamples;
            if (t > stencilEnd) {
                continue;
            }

            Vec3 curve = bezierPoint(center, ballPos, basis, bodyScale, t);
            Vec3 tangent = bezierTangent(center, ballPos, basis, bodyScale, t);
            Basis frame = makeFrame(tangent, siphonPreferredUp(basis));
            float pathScale = siphonPathScale(bodyScale);
            float radius = pathScale * Math.max(0.030f, 0.155f * (float) Math.pow(1f - t, 1.70f));
            radius *= Mth.lerp(siphon, 1.0f, 0.58f);
            float stencilStrength = tubeClip * 0.28f;
            float centerSize = Math.max(0.030f, pathScale * 0.062f * (1.04f - t * 0.36f));
            float centerDepth01 = normalizedViewDepth(curve, center, viewDir, pathScale * 1.8f);
            emitEncodedSplat(curve, camPos, camOrientation, stack, buffer, 0.0f,
                    centerSize, centerDepth01, 1f, stencilStrength);

            for (int r = 0; r < ringSamples; r++) {
                float angle = r * TAU / ringSamples + ((i & 1) == 0 ? 0.0f : TAU / (ringSamples * 2.0f));
                Vec3 pos = curve.add(frame.right.scale(Math.cos(angle) * radius))
                        .add(frame.up.scale(Math.sin(angle) * radius));
                float size = Math.max(0.022f, pathScale * 0.056f * (1.08f - t * 0.42f));
                float depth01 = normalizedViewDepth(pos, center, viewDir, pathScale * 1.8f);
                emitEncodedSplat(pos, camPos, camOrientation, stack, buffer, 0.0f, size, depth01, 1f, stencilStrength);
            }
        }
    }

    private void renderAbsorptionCore(Vec3 ballPos, Vec3 camPos, Quaternionf camOrientation,
                                      PoseStack stack, BufferBuilder buffer) {
        float absorb = remapClamped(age,
                SIPHON_END - 0.16f * TIMING_SCALE, BALL_ABSORB_END);
        if (absorb <= 0.001f) {
            return;
        }

        float flash = (float) Math.sin(absorb * Math.PI);
        float stencilStrength = flash;
        float size = Mth.lerp(absorb, 0.34f, 0.075f);
        emitEncodedSplat(ballPos, camPos, camOrientation, stack, buffer, 0.0f, size, 0.5f, 1f, stencilStrength);
        emitEncodedSplat(ballPos, camPos, camOrientation, stack, buffer, 0.0f, size * 1.65f, 0.5f, 1f, stencilStrength * 0.55f);
    }

    private Vec3 siphonTubePosition(Vec3 center, Vec3 ballPos, Basis basis,
                                    float bodyScale, VolumeSplat splat,
                                    float t, float globalSiphon, float finalFlush) {
        Vec3 curve = bezierPoint(center, ballPos, basis, bodyScale, t);
        Vec3 tangent = bezierTangent(center, ballPos, basis, bodyScale, t);
        Basis frame = makeFrame(tangent, siphonPreferredUp(basis));
        float pathScale = siphonPathScale(bodyScale);
        float radial = Mth.clamp((float) Math.sqrt(splat.localY * splat.localY + splat.localZ * splat.localZ), 0.12f, 1.0f);
        float radius = pathScale * Mth.lerp(t, 0.125f, 0.032f);
        radius *= Mth.lerp(radial, 0.42f, 1.0f);
        radius *= Mth.lerp(finalFlush, 1.0f, 0.42f);
        radius *= Mth.lerp(globalSiphon, 1.0f, 0.58f);

        float twist = t * TAU * (0.42f + splat.volatility * 0.34f) + finalFlush * TAU * 0.20f;
        float angle = splat.angle + twist;
        return curve.add(frame.right.scale(Math.cos(angle) * radius))
                .add(frame.up.scale(Math.sin(angle) * radius));
    }

    private Vec3 bezierPoint(Vec3 center, Vec3 ballPos, Basis basis, float bodyScale, float t) {
        if (voxelVolume != null && siphonDrainRootValid) {
            Vector3f root = new Vector3f(siphonDrainRootLocal);
            Vector3f intake = volumeWorldToLocal(voxelVolume, ballPos);
            intake.x = Math.max(intake.x, voxelVolume.bodyRadius() * 0.25f);
            Vector3f local = DarkBallCaptureMath.siphonCurvePoint(root, intake, voxelVolume.bodyRadius(), t);
            return volumeLocalToWorld(voxelVolume, local);
        }

        double dist = Math.max(0.001, ballPos.distanceTo(center));
        Vec3 p0 = center.add(basis.forward.scale(bodyScale * 0.12)).add(0, bodyScale * 0.08, 0);
        Vec3 p1 = center.add(basis.forward.scale(dist * 0.18))
                .add(basis.right.scale(bodyScale * 0.62))
                .add(basis.up.scale(bodyScale * 0.50));
        Vec3 p2 = ballPos.subtract(basis.forward.scale(dist * 0.22))
                .add(basis.right.scale(-bodyScale * 0.28))
                .add(basis.up.scale(bodyScale * 0.24));
        Vec3 p3 = ballPos;
        return cubicBezier(p0, p1, p2, p3, t);
    }

    private Vec3 bezierTangent(Vec3 center, Vec3 ballPos, Basis basis, float bodyScale, float t) {
        if (voxelVolume != null && siphonDrainRootValid) {
            float t0 = Mth.clamp(t - 0.0125f, 0f, 1f);
            float t1 = Mth.clamp(t + 0.0125f, 0f, 1f);
            Vec3 tangent = bezierPoint(center, ballPos, basis, bodyScale, t1)
                    .subtract(bezierPoint(center, ballPos, basis, bodyScale, t0));
            return tangent.lengthSqr() < 1e-7 ? voxelVolume.axis() : tangent.normalize();
        }

        double dist = Math.max(0.001, ballPos.distanceTo(center));
        Vec3 p0 = center.add(basis.forward.scale(bodyScale * 0.12)).add(0, bodyScale * 0.08, 0);
        Vec3 p1 = center.add(basis.forward.scale(dist * 0.18))
                .add(basis.right.scale(bodyScale * 0.62))
                .add(basis.up.scale(bodyScale * 0.50));
        Vec3 p2 = ballPos.subtract(basis.forward.scale(dist * 0.22))
                .add(basis.right.scale(-bodyScale * 0.28))
                .add(basis.up.scale(bodyScale * 0.24));
        Vec3 p3 = ballPos;
        return cubicBezierTangent(p0, p1, p2, p3, t);
    }

    private Vec3 siphonPreferredUp(Basis fallbackBasis) {
        return voxelVolume != null && siphonDrainRootValid ? voxelVolume.up() : fallbackBasis.up;
    }

    private float siphonPathScale(float fallbackBodyScale) {
        return voxelVolume != null && siphonDrainRootValid
                ? Math.max(0.32f, voxelVolume.bodyRadius())
                : fallbackBodyScale;
    }

    private static Vec3 volumeLocalToWorld(DarkBallVolumeBuildResult volume, Vector3f local) {
        return volume.root()
                .add(volume.axis().scale(local.x))
                .add(volume.side().scale(local.y))
                .add(volume.up().scale(local.z));
    }

    static Vector3f volumeWorldToLocal(DarkBallVolumeBuildResult volume, Vec3 world) {
        Vec3 rel = world.subtract(volume.root());
        return new Vector3f(
                (float) rel.dot(volume.axis()),
                (float) rel.dot(volume.side()),
                (float) rel.dot(volume.up())
        );
    }

    private static Vector3f volumeWorldDirectionToLocal(DarkBallVolumeBuildResult volume, Vec3 direction) {
        return new Vector3f(
                (float) direction.dot(volume.axis()),
                (float) direction.dot(volume.side()),
                (float) direction.dot(volume.up())
        );
    }

    private void initSplats(AABB bounds, float particleMultiplier) {
        if (initializedSplats) {
            return;
        }
        initializedSplats = true;

        int count = Mth.clamp(Math.round(BASE_SPLAT_COUNT * particleMultiplier),
                MIN_SPLAT_COUNT, MAX_SPLAT_COUNT);
        if (initSplatsFromVoxelVolume(count)) {
            return;
        }
        logSplatSource("fallback ellipsoid", count, 0);

        float yBias = (float) Mth.clamp(bounds.getYsize() / Math.max(0.001, bounds.getXsize() + bounds.getZsize()),
                0.65, 1.55);

        for (int i = 0; i < count; i++) {
            float x;
            float y;
            float z;
            float r2;
            int tries = 0;
            do {
                x = rng.nextFloat() * 2f - 1f;
                y = (rng.nextFloat() * 2f - 1f) / yBias;
                z = rng.nextFloat() * 2f - 1f;
                r2 = x * x + y * y + z * z;
                tries++;
            } while (r2 > 1f && tries < 24);

            if (r2 > 1f) {
                float invLen = 1.0f / (float) Math.sqrt(r2);
                x *= invLen * 0.96f;
                y *= invLen * 0.96f;
                z *= invLen * 0.96f;
                r2 = x * x + y * y + z * z;
            }

            float r = Mth.sqrt(r2);
            float boundary = smoothstep(0.54f, 0.98f, r);
            float density = Mth.lerp(boundary, 0.64f, 0.46f) * (0.88f + rng.nextFloat() * 0.28f);
            float radius = Mth.lerp(boundary, 0.142f, 0.104f) * (0.92f + rng.nextFloat() * 0.24f);
            float depthLayer = Mth.clamp((z + 1f) * 0.5f + (rng.nextFloat() - 0.5f) * 0.08f, 0f, 1f);
            SplatFrame frame = ellipsoidSplatFrame(x, y, z);
            splats.add(new VolumeSplat(
                    x,
                    y,
                    z,
                    radius,
                    density,
                    depthLayer,
                    boundary,
                    rng.nextFloat(),
                    rng.nextFloat() * TAU,
                    rng.nextFloat(),
                    0f,
                    1f,
                    0f,
                    frame.normal.x,
                    frame.normal.y,
                    frame.normal.z,
                    frame.tangent.x,
                    frame.tangent.y,
                    frame.tangent.z,
                    frame.bitangent.x,
                    frame.bitangent.y,
                    frame.bitangent.z,
                    Mth.lerp(boundary, 1.02f, 1.16f),
                    Mth.clamp((1f - x) * 0.5f, 0f, 1f)
            ));
        }
        resetSplatSimulation();
    }

    private boolean initSplatsFromVoxelVolume(int count) {
        DarkBallVolumeBuildResult volume = voxelVolume;
        if (volume == null || volume.coreField() == null || volume.envelopeField() == null) {
            logSplatSource("fallback ellipsoid; no voxel volume", count, 0);
            return false;
        }
        if (volume.coreField().length != volume.envelopeField().length) {
            logSplatSource("fallback ellipsoid; invalid voxel fields", count, 0);
            return false;
        }

        boolean hasSurfaceDepth = volume.surfaceDepthField() != null
                && volume.surfaceDepthField().length == volume.coreField().length;
        List<Integer> candidates = new ArrayList<>();
        List<Integer> surfaceCandidates = new ArrayList<>();
        List<Integer> coreCandidates = new ArrayList<>();
        for (int i = 0; i < volume.coreField().length; i++) {
            boolean coreMaterial = volume.coreField()[i] > 0.018f;
            if (coreMaterial) {
                candidates.add(i);
                float surfaceDepth01 = hasSurfaceDepth
                        ? Mth.clamp(volume.surfaceDepthField()[i], 0f, 1f)
                        : Mth.clamp(volume.coreField()[i], 0f, 1f);
                if (surfaceDepth01 < 0.42f) {
                    surfaceCandidates.add(i);
                } else {
                    coreCandidates.add(i);
                }
            }
        }
        if (candidates.size() < 24) {
            for (int i = 0; i < volume.coreField().length; i++) {
                if (volume.envelopeField()[i] > 0.052f) {
                    candidates.add(i);
                }
            }
        }
        if (candidates.size() < 24) {
            logSplatSource("fallback ellipsoid; empty voxel field", count, candidates.size());
            return false;
        }
        int sliceSize = DarkBallVolumeGrid.SLICE_SIZE;
        float voxelStep = Math.max(
                volume.captureLength() / Math.max(1, DarkBallVolumeGrid.X_SLICES),
                volume.radius() * 2.0f / Math.max(1, sliceSize)
        );
        boolean hasTriangleSurface = capturedModelTriangles.size() >= 6;
        int adaptiveCount = adaptiveVoxelSplatCount(count, candidates.size(), volume);
        float coverageScale = Mth.clamp((float) Math.sqrt(900.0f / Math.max(1.0f, adaptiveCount)), 0.86f, 1.18f);
        float baseRadius = Math.max(0.055f, voxelStep * 5.75f * coverageScale);
        float surfaceBias = hasTriangleSurface
                ? Mth.lerp(smoothstep(16000f, 52000f, candidates.size()), 0.76f, 0.84f)
                : 0.0f;
        int surfaceTarget = hasTriangleSurface ? Math.round(adaptiveCount * surfaceBias) : 0;
        int voxelTarget = Math.max(0, adaptiveCount - surfaceTarget);
        logSplatSource("voxelized captured mesh", adaptiveCount, candidates.size());
        logVoxelSplatPlan(adaptiveCount, surfaceTarget, voxelTarget, baseRadius, voxelStep, surfaceBias);
        if (surfaceTarget > 0) {
            addTriangleSurfaceSplats(volume, surfaceTarget, baseRadius);
        }
        float sequenceOffset = rng.nextFloat();

        for (int i = 0; i < voxelTarget; i++) {
            float sequence = (sequenceOffset + i * 0.61803398875f) % 1.0f;
            List<Integer> source = candidates;
            if (hasTriangleSurface && !coreCandidates.isEmpty()) {
                source = coreCandidates;
            } else if (!surfaceCandidates.isEmpty() && !coreCandidates.isEmpty()) {
                source = (i % 10) < 7 ? surfaceCandidates : coreCandidates;
            } else if (!surfaceCandidates.isEmpty()) {
                source = surfaceCandidates;
            } else if (!coreCandidates.isEmpty()) {
                source = coreCandidates;
            }
            int sourceCount = source.size();
            int index = source.get(Mth.clamp((int) (sequence * sourceCount), 0, sourceCount - 1));
            int x = index / (sliceSize * sliceSize);
            int rem = index - x * sliceSize * sliceSize;
            int z = rem / sliceSize;
            int y = rem - z * sliceSize;

            float core = volume.coreField()[index];
            float envelope = volume.envelopeField()[index];
            float material = Math.max(core, envelope * 0.72f);
            float boundary = Mth.clamp((envelope - core * 0.58f) * 1.55f, 0f, 1f);
            boundary = Math.max(boundary, smoothstep(0.08f, 0.62f, envelope) * (1.0f - smoothstep(0.50f, 0.95f, core)));
            float surfaceDepth01 = hasSurfaceDepth
                    ? Mth.clamp(volume.surfaceDepthField()[index], 0f, 1f)
                    : Mth.clamp(core, 0f, 1f);
            float localThickness = localThicknessAtVoxel(volume, index);
            float thickness01 = Mth.clamp(localThickness / Math.max(volume.bodyRadius() * 0.72f, volume.sdfVoxelYz()), 0f, 1f);
            float thin01 = 1.0f - thickness01;
            if (hasSurfaceDepth) {
                float surfaceBand = 1.0f - smoothstep(0.08f, 0.30f, surfaceDepth01);
                boundary = Math.max(boundary, surfaceBand);
            }
            float jitterAmount = hasSurfaceDepth
                    ? Mth.lerp(boundary, 0.94f, 0.30f)
                    : 1.0f;

            float localX = ((x + 0.5f + (rng.nextFloat() - 0.5f) * jitterAmount) / DarkBallVolumeGrid.X_SLICES);
            float localY = (((y + 0.5f + (rng.nextFloat() - 0.5f) * jitterAmount) / sliceSize) * 2.0f - 1.0f);
            float localZ = (((z + 0.5f + (rng.nextFloat() - 0.5f) * jitterAmount) / sliceSize) * 2.0f - 1.0f);
            float coreDepth = (float) Math.pow(surfaceDepth01, 0.68f);
            float density = hasSurfaceDepth
                    ? Mth.lerp(coreDepth, 0.78f, 1.18f) * Mth.lerp(material, 0.92f, 1.10f)
                    : Mth.clamp(0.70f + material * 0.50f, 0.58f, 1.22f);
            density *= 0.96f + rng.nextFloat() * 0.14f;
            density *= hasSurfaceDepth ? Mth.lerp(boundary, 1.04f, 0.94f) : Mth.lerp(boundary, 1.0f, 1.18f);

            float radiusScale;
            if (hasSurfaceDepth) {
                radiusScale = Mth.lerp(coreDepth, 0.42f, 1.54f);
                float thinFeatureFloor = Mth.lerp(smoothstep(0.10f, 0.46f, material), 0.58f, 0.78f);
                radiusScale = Math.max(radiusScale, thinFeatureFloor);
            } else {
                float edgeTightness = boundary * boundary;
                radiusScale = Mth.lerp(edgeTightness, 1.18f, 0.38f);
            }
            float radius = baseRadius * radiusScale * (0.94f + rng.nextFloat() * 0.14f);
            radius *= Mth.lerp(thin01 * boundary, 1.0f, 0.78f);
            float depthLayer = Mth.clamp(localX + (rng.nextFloat() - 0.5f) * 0.065f, 0f, 1f);
            float shapeStretch = hasSurfaceDepth
                    ? Mth.lerp(boundary, 1.04f, Mth.lerp(thin01, 1.22f, 1.48f))
                    : Mth.lerp(boundary, 1.02f, 1.14f);
            SplatFrame frame = voxelSplatFrame(volume, new Vector3f(
                    localX * volume.captureLength(),
                    localY * volume.radius(),
                    localZ * volume.radius()
            ), null);

            splats.add(new VolumeSplat(
                    localX,
                    localY,
                    localZ,
                    radius,
                    density,
                    depthLayer,
                    boundary,
                    rng.nextFloat(),
                    rng.nextFloat() * TAU,
                    rng.nextFloat(),
                    0f,
                    1f,
                    0f,
                    frame.normal.x,
                    frame.normal.y,
                    frame.normal.z,
                    frame.tangent.x,
                    frame.tangent.y,
                    frame.tangent.z,
                    frame.bitangent.x,
                    frame.bitangent.y,
                    frame.bitangent.z,
                    shapeStretch,
                    0f
            ));
        }
        assignVoxelSiphonTopology(volume);
        initializeSplatSimulation(volume);
        return true;
    }

    private void addTriangleSurfaceSplats(DarkBallVolumeBuildResult volume, int count, float baseRadius) {
        if (volume == null || capturedModelTriangles.isEmpty() || count <= 0) {
            return;
        }

        float[] cumulativeArea = new float[capturedModelTriangles.size()];
        float totalArea = 0f;
        for (int i = 0; i < capturedModelTriangles.size(); i++) {
            totalArea += triangleArea(capturedModelTriangles.get(i));
            cumulativeArea[i] = totalArea;
        }
        if (totalArea <= 0.0001f) {
            return;
        }

        int added = 0;
        int attempts = 0;
        while (added < count && attempts < count * 3) {
            attempts++;
            DarkBallFieldMaskBufferSource.CapturedTriangle triangle = weightedTriangle(cumulativeArea, totalArea);
            Vec3 world = randomPointOnTriangle(triangle);
            Vector3f local = volumeWorldToLocal(volume, world);
            float localX = local.x / Math.max(volume.captureLength(), 0.001f);
            float localY = local.y / Math.max(volume.radius(), 0.001f);
            float localZ = local.z / Math.max(volume.radius(), 0.001f);
            if (localX < -0.035f || localX > 1.035f
                    || localY < -1.08f || localY > 1.08f
                    || localZ < -1.08f || localZ > 1.08f) {
                continue;
            }

            float density = (0.58f + rng.nextFloat() * 0.28f);
            float radius = baseRadius * (0.48f + rng.nextFloat() * 0.20f);
            float depthLayer = Mth.clamp(localX + (rng.nextFloat() - 0.5f) * 0.025f, 0f, 1f);
            SplatFrame frame = voxelSplatFrame(volume, local, triangleTangent(triangle));
            splats.add(new VolumeSplat(
                    Mth.clamp(localX, 0f, 1f),
                    Mth.clamp(localY, -1f, 1f),
                    Mth.clamp(localZ, -1f, 1f),
                    radius,
                    density,
                    depthLayer,
                    1.0f,
                    0.82f + rng.nextFloat() * 0.18f,
                    rng.nextFloat() * TAU,
                    rng.nextFloat(),
                    frame.tangent.x,
                    frame.tangent.y,
                    frame.tangent.z,
                    frame.normal.x,
                    frame.normal.y,
                    frame.normal.z,
                    frame.tangent.x,
                    frame.tangent.y,
                    frame.tangent.z,
                    frame.bitangent.x,
                    frame.bitangent.y,
                    frame.bitangent.z,
                    1.38f + rng.nextFloat() * 0.30f,
                    0f
            ));
            added++;
        }
    }

    private DarkBallFieldMaskBufferSource.CapturedTriangle weightedTriangle(float[] cumulativeArea, float totalArea) {
        float sample = rng.nextFloat() * totalArea;
        int lo = 0;
        int hi = cumulativeArea.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumulativeArea[mid] < sample) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return capturedModelTriangles.get(lo);
    }

    private static int adaptiveVoxelSplatCount(int requestedCount, int candidateCount, DarkBallVolumeBuildResult volume) {
        float candidateScale = (float) Math.pow(
                Math.max(1.0f, candidateCount / 16000.0f),
                0.42f
        );
        float linearExtent = Math.max(volume.captureLength(), volume.radius() * 2.0f);
        float extentScale = Mth.lerp(smoothstep(1.4f, 3.4f, linearExtent), 1.0f, 1.16f);
        return Mth.clamp(Math.round(requestedCount * candidateScale * extentScale), MIN_SPLAT_COUNT, MAX_SPLAT_COUNT);
    }

    private void logVoxelSplatPlan(int splatCount, int surfaceTarget, int voxelTarget,
                                   float baseRadius, float voxelStep, float surfaceBias) {
        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball voxel splat plan for pokemon {} (splats={}, surface={}, volume={}, surfaceBias={}, baseRadius={}, voxelStep={})",
                pokemonId,
                splatCount,
                surfaceTarget,
                voxelTarget,
                String.format(java.util.Locale.ROOT, "%.2f", surfaceBias),
                String.format(java.util.Locale.ROOT, "%.4f", baseRadius),
                String.format(java.util.Locale.ROOT, "%.4f", voxelStep)
        );
    }

    private Vec3 randomPointOnTriangle(DarkBallFieldMaskBufferSource.CapturedTriangle triangle) {
        float u = rng.nextFloat();
        float v = rng.nextFloat();
        if (u + v > 1f) {
            u = 1f - u;
            v = 1f - v;
        }

        Vec3 a = triangle.a();
        Vec3 ab = triangle.b().subtract(a);
        Vec3 ac = triangle.c().subtract(a);
        return a.add(ab.scale(u)).add(ac.scale(v));
    }

    private static float triangleArea(DarkBallFieldMaskBufferSource.CapturedTriangle triangle) {
        Vec3 ab = triangle.b().subtract(triangle.a());
        Vec3 ac = triangle.c().subtract(triangle.a());
        return (float) ab.cross(ac).length() * 0.5f;
    }

    private static Vec3 triangleTangent(DarkBallFieldMaskBufferSource.CapturedTriangle triangle) {
        Vec3 ab = triangle.b().subtract(triangle.a());
        Vec3 bc = triangle.c().subtract(triangle.b());
        Vec3 ca = triangle.a().subtract(triangle.c());
        Vec3 tangent = ab;
        if (bc.lengthSqr() > tangent.lengthSqr()) {
            tangent = bc;
        }
        if (ca.lengthSqr() > tangent.lengthSqr()) {
            tangent = ca;
        }
        return tangent.lengthSqr() < 1e-7 ? new Vec3(0, 1, 0) : tangent.normalize();
    }

    private SplatFrame voxelSplatFrame(DarkBallVolumeBuildResult volume, Vector3f localPosition,
                                       Vec3 preferredWorldTangent) {
        Vector3f normal = null;
        if (volume != null) {
            SdfSample sample = sampleVolumeSdf(volume, localPosition);
            if (sample.normal.lengthSqr() > 1e-7) {
                normal = volumeWorldDirectionToLocal(volume, sample.normal);
            }
        }
        if (normal == null || normal.lengthSquared() < 1e-7f) {
            normal = new Vector3f(
                    localPosition.x - (volume == null ? 0.5f : volume.captureLength() * 0.5f),
                    localPosition.y,
                    localPosition.z
            );
        }

        Vector3f tangent = null;
        if (volume != null && preferredWorldTangent != null && preferredWorldTangent.lengthSqr() > 1e-7) {
            tangent = volumeWorldDirectionToLocal(volume, preferredWorldTangent);
        }
        return localSurfaceFrame(normal, tangent);
    }

    private static SplatFrame ellipsoidSplatFrame(float x, float y, float z) {
        Vector3f normal = new Vector3f(x, y, z);
        return localSurfaceFrame(normal, null);
    }

    private static SplatFrame localSurfaceFrame(Vector3f normalSource, Vector3f tangentSource) {
        Vector3f normal = new Vector3f(normalSource);
        normalizeOrDefault(normal, 1f, 0f, 0f);

        Vector3f tangent = tangentSource == null ? new Vector3f() : new Vector3f(tangentSource);
        tangent.fma(-tangent.dot(normal), normal);
        if (tangent.lengthSquared() < 1e-7f) {
            tangent = stableLocalSurfaceTangent(normal);
        }
        normalizeOrDefault(tangent, 0f, 1f, 0f);

        Vector3f bitangent = new Vector3f(normal).cross(tangent);
        if (bitangent.lengthSquared() < 1e-7f) {
            bitangent = stableLocalSurfaceTangent(tangent);
        }
        normalizeOrDefault(bitangent, 0f, 0f, 1f);

        tangent = new Vector3f(bitangent).cross(normal);
        normalizeOrDefault(tangent, 0f, 1f, 0f);
        return new SplatFrame(normal, tangent, bitangent);
    }

    private static Vector3f stableLocalSurfaceTangent(Vector3f normal) {
        Vector3f reference = Math.abs(normal.y) < 0.82f
                ? new Vector3f(0f, 1f, 0f)
                : new Vector3f(0f, 0f, 1f);
        Vector3f tangent = reference.cross(normal, new Vector3f());
        normalizeOrDefault(tangent, 0f, 1f, 0f);
        return tangent;
    }

    private static void normalizeOrDefault(Vector3f axis, float fallbackX, float fallbackY, float fallbackZ) {
        float lenSq = axis.lengthSquared();
        if (lenSq < 1e-7f) {
            axis.set(fallbackX, fallbackY, fallbackZ);
            return;
        }
        axis.mul(1.0f / Mth.sqrt(lenSq));
    }

    private void assignVoxelSiphonTopology(DarkBallVolumeBuildResult volume) {
        if (volume == null || splats.isEmpty()) {
            siphonDrainRootValid = false;
            return;
        }

        float safeCaptureLength = Math.max(volume.captureLength(), volume.bodyRadius() * 1.4f);
        Vector3f rootLocal = null;
        float bestScore = Float.MAX_VALUE;
        for (VolumeSplat splat : splats) {
            Vector3f local = splatVolumeLocalPosition(splat, volume);
            float axialGap = Math.max(0f, safeCaptureLength - local.x);
            float lateral = (float) Math.sqrt(local.y * local.y + local.z * local.z);
            float score = axialGap + lateral * 0.42f;
            if (score < bestScore) {
                bestScore = score;
                rootLocal = local;
            }
        }

        if (rootLocal == null) {
            siphonDrainRootLocal.set(0f, 0f, 0f);
            siphonDrainRootValid = false;
            return;
        }

        siphonDrainRootLocal.set(rootLocal);
        siphonDrainRootValid = true;

        float maxDistance = 0.001f;
        for (VolumeSplat splat : splats) {
            maxDistance = Math.max(maxDistance,
                    progressiveReleaseDistance(splatVolumeLocalPosition(splat, volume), siphonDrainRootLocal));
        }

        for (int i = 0; i < splats.size(); i++) {
            VolumeSplat splat = splats.get(i);
            float distance = progressiveReleaseDistance(splatVolumeLocalPosition(splat, volume), siphonDrainRootLocal);
            float jitter = (float) Math.sin(splat.seed * 17.31f + distance * 2.71f) * 0.018f;
            float release = Mth.clamp(distance / maxDistance + jitter, 0f, 1f);
            splats.set(i, new VolumeSplat(
                    splat.localX,
                    splat.localY,
                    splat.localZ,
                    splat.radius,
                    splat.density,
                    splat.depthLayer,
                    splat.boundaryWeight,
                    splat.volatility,
                    splat.angle,
                    splat.seed,
                    splat.shapeAxisX,
                    splat.shapeAxisY,
                    splat.shapeAxisZ,
                    splat.surfaceNormalX,
                    splat.surfaceNormalY,
                    splat.surfaceNormalZ,
                    splat.surfaceTangentX,
                    splat.surfaceTangentY,
                    splat.surfaceTangentZ,
                    splat.surfaceBitangentX,
                    splat.surfaceBitangentY,
                    splat.surfaceBitangentZ,
                    splat.shapeStretch,
                    release
            ));
        }
    }

    private void initializeSplatSimulation(DarkBallVolumeBuildResult volume) {
        splatStates.clear();
        if (volume == null || splats.isEmpty() || !siphonDrainRootValid) {
            resetSplatSimulation();
            return;
        }

        for (VolumeSplat splat : splats) {
            Vector3f rest = splatVolumeLocalPosition(splat, volume);
            splatStates.add(new SplatState(rest));
        }
        splatSimAge = age;
        splatSimAccumulator = 0f;
        splatRenderAlpha = 1f;
        massTransportAge = age;
        splatSimulationLogged = false;
    }

    private void resetSplatSimulation() {
        splatStates.clear();
        splatSimAge = age;
        splatSimAccumulator = 0f;
        splatRenderAlpha = 1f;
        massTransportAge = age;
        splatSimulationLogged = false;
    }

    private void advanceSplatSimulation(Vec3 ballPos) {
        DarkBallVolumeBuildResult volume = voxelVolume;
        if (volume == null || !siphonDrainRootValid || splats.isEmpty()) {
            return;
        }
        if (splatStates.size() != splats.size()) {
            initializeSplatSimulation(volume);
        }
        if (splatStates.size() != splats.size()) {
            return;
        }
        ensureMassTransport(volume, ballPos);
        if (!splatSimulationLogged) {
            splatSimulationLogged = true;
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball splat simulation for pokemon {} active (states={}, stepHz={}, maxSteps={})",
                    pokemonId,
                    splatStates.size(),
                    Math.round(1.0f / SPLAT_SIM_STEP),
                    SPLAT_SIM_MAX_STEPS
            );
        }

        float delta = Mth.clamp(age - splatSimAge, 0f, 0.10f);
        splatSimAge = age;
        if (delta <= 0f) {
            splatRenderAlpha = 1f;
            return;
        }

        splatSimAccumulator += delta;
        int steps = 0;
        while (splatSimAccumulator >= SPLAT_SIM_STEP && steps < SPLAT_SIM_MAX_STEPS) {
            simulateSplatStep(volume, ballPos, SPLAT_SIM_STEP);
            splatSimAccumulator -= SPLAT_SIM_STEP;
            steps++;
        }
        if (splatSimAccumulator >= SPLAT_SIM_STEP) {
            splatSimAccumulator = SPLAT_SIM_STEP * 0.95f;
        }
        splatRenderAlpha = Mth.clamp(splatSimAccumulator / SPLAT_SIM_STEP, 0f, 1f);
    }

    private void ensureMassTransport(DarkBallVolumeBuildResult volume, Vec3 ballPos) {
        if (!ENABLE_VOXEL_MASS_TRANSPORT || massTransport != null || volume == null) {
            return;
        }
        Vector3f ballLocal = volumeWorldToLocal(volume, ballPos);
        massTransport = DarkBallVoxelMassTransport.build(volume, ballLocal);
        if (massTransport != null && !massTransportLogged) {
            siphonDrainRootLocal.set(massTransport.outletLocal());
            siphonDrainRootValid = true;
            massTransportAge = age;
            massTransportLogged = true;
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball voxel mass transport for pokemon {} active ({})",
                    pokemonId,
                    massTransport.summary()
            );
        }
    }

    private void ensureAnalyticalVolume(DarkBallVolumeBuildResult volume, Vec3 ballPos) {
        // Despite the historical name, this object now owns the captured
        // shape/cohesive atlases consumed by the direct advected renderer.
        if (volume == null) {
            return;
        }

        DarkBallVfxQuality requestedQuality = DarkBallVfxQuality.parse(
                com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                        .getInstance().getClientConfig().darkBallVfxQuality()
        );
        if (analyticalVolume != null && analyticalQuality == requestedQuality) {
            return;
        }
        if (analyticalVolume != null) {
            analyticalVolume.destroy();
        }

        Vector3f ballLocal = volumeWorldToLocal(volume, ballPos);
        analyticalVolume = DarkBallAnalyticalVolume.build(volume, ballLocal, requestedQuality);
        analyticalQuality = requestedQuality;
        analyticalVolumeAge = age;
        if (analyticalVolume != null && !analyticalVolumeLogged) {
            siphonDrainRootLocal.set(analyticalVolume.outletLocal());
            siphonDrainRootValid = true;
            analyticalVolumeLogged = true;
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball captured shape atlas for pokemon {} active ({})",
                    pokemonId,
                    analyticalVolume.summary()
            );
        }
    }

    private void ensureAdvectedDensityField(DarkBallVolumeBuildResult volume, Vec3 ballPos) {
        if (!ENABLE_ADVECTED_DENSITY_FIELD || volume == null || analyticalVolume == null) {
            return;
        }

        DarkBallVfxQuality requestedQuality = DarkBallVfxQuality.parse(
                com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                        .getInstance().getClientConfig().darkBallVfxQuality()
        );
        if (advectedDensityField != null
                && advectedDensityQuality == requestedQuality
                && advectedDensityShape == analyticalVolume) {
            return;
        }
        if (advectedDensityField != null) {
            advectedDensityField.destroy();
        }

        Vector3f ballLocal = volumeWorldToLocal(volume, ballPos);
        advectedDensityField = DarkBallAdvectedDensityField.build(
                volume, analyticalVolume, ballLocal, requestedQuality);
        advectedDensityShape = analyticalVolume;
        advectedDensityQuality = requestedQuality;
        advectedDensityAge = Math.min(age, SIPHON_START);
        if (advectedDensityField != null && advectedDensityField.available()
                && !advectedDensityLogged) {
            siphonDrainRootLocal.set(advectedDensityField.outletLocal());
            siphonDrainRootValid = true;
            advectedDensityLogged = true;
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball advected density for pokemon {} active ({})",
                    pokemonId,
                    advectedDensityField.summary()
            );
        }
    }

    private boolean advanceAdvectedDensityField(Vec3 ballPos) {
        if (advectedDensityField == null || voxelVolume == null) {
            return false;
        }
        float delta = Mth.clamp(age - advectedDensityAge, 0.0f, 0.12f);
        advectedDensityAge = age;
        if (!advectedDensityField.updateBallLocal(
                volumeWorldToLocal(voxelVolume, ballPos))) {
            return false;
        }
        float siphon = siphonProgressAt(age);
        float collapse = finalCollapseAt(age);
        float destabilization = turbulenceBlendAt(age)
                * (1.0f - collapse * 0.72f);
        return advectedDensityField.advance(siphon, collapse,
                destabilization, age, delta);
    }

    private void advanceAnalyticalVolume(Vec3 ballPos) {
        if (analyticalVolume == null || voxelVolume == null) {
            return;
        }
        float delta = Mth.clamp(age - analyticalVolumeAge, 0.0f, 0.12f);
        analyticalVolumeAge = age;
        analyticalVolume.updateBallLocal(volumeWorldToLocal(voxelVolume, ballPos));
        float siphon = siphonProgressAt(age);
        float collapse = finalCollapseAt(age);
        float destabilization = turbulenceBlendAt(age);
        analyticalVolume.advance(siphon + collapse * 0.16f, destabilization, delta);
    }

    private void advanceMassTransport() {
        if (massTransport == null) {
            return;
        }
        float delta = Mth.clamp(age - massTransportAge, 0f, 0.12f);
        massTransportAge = age;
        if (delta <= 0f) {
            return;
        }
        float globalSiphon = remapClamped(age, SIPHON_START, SIPHON_END);
        float finalFlush = smoothstep(0.84f, 1.0f, globalSiphon);
        massTransport.advance(globalSiphon, finalFlush, delta);
    }

    private void simulateSplatStep(DarkBallVolumeBuildResult volume, Vec3 ballPos, float step) {
        float globalSiphon = remapClamped(age, SIPHON_START, SIPHON_END);
        float releaseProgress = smoothstep(0.02f, 0.94f, globalSiphon);
        float finalFlush = smoothstep(0.84f, 1.0f, globalSiphon);
        float effectiveRelease = Math.max(releaseProgress, finalFlush);
        Vector3f intake = siphonIntakeLocal(volume, ballPos);

        for (int i = 0; i < splats.size(); i++) {
            VolumeSplat splat = splats.get(i);
            SplatState state = splatStates.get(i);
            state.previousPosition.set(state.position);

            SiphonPhase phase = computeSiphonPhase(splat, effectiveRelease, finalFlush);
            Vector3f rest = splatVolumeLocalPosition(splat, volume);
            Vector3f target = simulationTargetLocal(volume, splat, phase, intake);

            Vector3f force = new Vector3f(rest).sub(state.position)
                    .mul((1f - phase.releaseWave) * Mth.lerp(globalSiphon, 16.0f, 8.0f));

            if (phase.releaseWave > 0.001f) {
                float stiffness = Mth.lerp(phase.travel, 13.0f, 31.0f)
                        * phase.releaseWave
                        * Mth.lerp(splat.volatility, 0.92f, 1.12f);
                force.add(new Vector3f(target).sub(state.position).mul(stiffness));

                Vector3f toTarget = new Vector3f(target).sub(state.position);
                if (toTarget.lengthSquared() > 1e-7f) {
                    toTarget.normalize();
                    float currentForwardSpeed = state.velocity.dot(toTarget);
                    float desiredForwardSpeed = Math.max(volume.captureLength(), volume.bodyRadius())
                            * Mth.lerp(phase.travel, 0.28f, 2.65f)
                            * (1f + finalFlush * 0.70f);
                    float correction = (desiredForwardSpeed - currentForwardSpeed)
                            * phase.releaseWave
                            * Mth.lerp(finalFlush, 1.4f, 3.8f);
                    force.add(toTarget.mul(correction));
                }
            }

            state.velocity.fma(step, force);
            float drag = (float) Math.pow(Mth.lerp(phase.releaseWave, 0.875f, 0.918f), step * 60.0f);
            state.velocity.mul(drag);

            float maxSpeed = Math.max(1.4f, Math.max(volume.captureLength(), volume.bodyRadius()) * Mth.lerp(finalFlush, 2.2f, 4.4f));
            clampLength(state.velocity, maxSpeed);
            integrateContainedSplatMotion(volume, state, intake, step);
            state.smoothedVelocity.lerp(state.velocity, 1.0f - (float) Math.exp(-step * 8.5f));
            state.siphonProgress = Math.max(state.siphonProgress, phase.travel);
        }
    }

    private static SiphonPhase computeSiphonPhase(VolumeSplat splat, float effectiveRelease, float finalFlush) {
        float releaseOrder = splat.release;
        float depthReleaseDelay = (1f - Mth.clamp(splat.depthLayer, 0f, 1f)) * 0.045f;
        float releaseJitter = (splat.seed - 0.5f) * 0.025f;
        float layeredRelease = Mth.clamp(releaseOrder + depthReleaseDelay + releaseJitter, 0f, 1f);
        float releaseWave = smoothstep(layeredRelease - 0.035f, layeredRelease + 0.055f, effectiveRelease);
        releaseWave = Math.max(releaseWave, finalFlush);
        float travel = Mth.clamp((effectiveRelease - layeredRelease + finalFlush * 0.22f) / 0.24f, 0f, 1f);
        travel = Math.max(travel, finalFlush);
        float siphonEase = smoothstep(0f, 1f, travel);
        float entranceBlend = Mth.clamp(releaseWave * smoothstep(0.02f, 0.36f, travel), 0f, 1f);
        float pathTravel = smoothstep(0.28f, 0.98f, travel);
        return new SiphonPhase(releaseWave, travel, siphonEase, entranceBlend, pathTravel, releaseOrder, finalFlush);
    }

    private Vector3f simulationTargetLocal(DarkBallVolumeBuildResult volume, VolumeSplat splat,
                                           SiphonPhase phase, Vector3f intake) {
        Vector3f rest = splatVolumeLocalPosition(splat, volume);
        float entranceT = Mth.clamp(0.020f + phase.releaseOrder * 0.010f, 0f, 0.065f);
        Vector3f entrance = localSiphonTubePosition(volume, splat, intake, entranceT, phase.finalFlush);
        float tubeT = Mth.clamp(0.035f + phase.pathTravel * 0.945f + phase.releaseOrder * 0.010f, 0f, 1f);
        Vector3f tube = localSiphonTubePosition(volume, splat, intake, tubeT, phase.finalFlush);
        Vector3f siphonTarget = new Vector3f(entrance).lerp(tube, phase.pathTravel);
        return rest.lerp(siphonTarget, phase.entranceBlend, new Vector3f());
    }

    private Vector3f localSiphonTubePosition(DarkBallVolumeBuildResult volume, VolumeSplat splat,
                                             Vector3f intake, float t, float finalFlush) {
        Vector3f root = new Vector3f(siphonDrainRootLocal);
        Vector3f curve = DarkBallCaptureMath.siphonCurvePoint(root, intake, volume.bodyRadius(), t);
        Vector3f tangent = localSiphonCurveTangent(root, intake, volume.bodyRadius(), t);
        LocalFrame frame = makeLocalFrame(tangent);
        float radial = Mth.clamp((float) Math.sqrt(splat.localY * splat.localY + splat.localZ * splat.localZ), 0.12f, 1.0f);
        float radius = volume.bodyRadius() * Mth.lerp(t, 0.090f, 0.022f);
        radius *= Mth.lerp(radial, 0.34f, 0.92f);
        radius *= Mth.lerp(finalFlush, 1.0f, 0.42f);
        float twist = t * TAU * (0.32f + splat.volatility * 0.24f) + finalFlush * TAU * 0.16f;
        float angle = splat.angle + twist;
        return curve.add(new Vector3f(frame.right).mul((float) Math.cos(angle) * radius))
                .add(new Vector3f(frame.up).mul((float) Math.sin(angle) * radius));
    }

    private static Vector3f localSiphonCurveTangent(Vector3f root, Vector3f intake, float bodyRadius, float t) {
        float t0 = Mth.clamp(t - 0.0125f, 0f, 1f);
        float t1 = Mth.clamp(t + 0.0125f, 0f, 1f);
        Vector3f tangent = DarkBallCaptureMath.siphonCurvePoint(root, intake, bodyRadius, t1)
                .sub(DarkBallCaptureMath.siphonCurvePoint(root, intake, bodyRadius, t0));
        normalizeOrDefault(tangent, 1f, 0f, 0f);
        return tangent;
    }

    private static LocalFrame makeLocalFrame(Vector3f tangent) {
        Vector3f forward = new Vector3f(tangent);
        normalizeOrDefault(forward, 1f, 0f, 0f);
        Vector3f preferredUp = new Vector3f(0f, 0f, 1f);
        Vector3f right = new Vector3f(forward).cross(preferredUp);
        if (right.lengthSquared() < 1e-7f) {
            right = new Vector3f(forward).cross(new Vector3f(0f, 1f, 0f));
        }
        normalizeOrDefault(right, 0f, 1f, 0f);
        Vector3f up = new Vector3f(right).cross(forward);
        normalizeOrDefault(up, 0f, 0f, 1f);
        return new LocalFrame(forward, right, up);
    }

    private void enforceCombinedSdfContainment(DarkBallVolumeBuildResult volume, SplatState state, Vector3f intake) {
        SdfSample sample = sampleCombinedContainerSdf(volume, state.position, siphonDrainRootLocal, intake);
        if (!Float.isFinite(sample.distance) || sample.normal.lengthSqr() < 1e-7) {
            return;
        }

        Vector3f normalLocal = volumeWorldDirectionToLocal(volume, sample.normal);
        normalizeOrDefault(normalLocal, 1f, 0f, 0f);
        float allowedOuter = Math.max(volume.sdfVoxelYz() * 0.38f, volume.bodyRadius() * 0.010f);
        float nearWall = Math.max(volume.sdfVoxelYz() * 1.45f, volume.bodyRadius() * 0.035f);
        float outwardSpeed = state.velocity.dot(normalLocal);

        if (sample.distance > allowedOuter) {
            state.position.fma(-(sample.distance - allowedOuter), normalLocal);
            if (outwardSpeed > 0f) {
                state.velocity.fma(-outwardSpeed * 1.05f, normalLocal);
            }
            state.velocity.mul(0.84f);
        } else if (sample.distance > -nearWall && outwardSpeed > 0f) {
            state.velocity.fma(-outwardSpeed * 0.72f, normalLocal);
            state.velocity.mul(0.94f);
        }
    }

    private void integrateContainedSplatMotion(DarkBallVolumeBuildResult volume, SplatState state,
                                               Vector3f intake, float step) {
        float speed = state.velocity.length();
        float maxSegment = Math.max(volume.sdfVoxelYz() * 0.65f, volume.bodyRadius() * 0.018f);
        int substeps = Mth.clamp((int) Math.ceil(speed * step / Math.max(maxSegment, 0.0001f)), 1, 5);
        float substep = step / substeps;
        for (int i = 0; i < substeps; i++) {
            state.position.fma(substep, state.velocity);
            enforceCombinedSdfContainment(volume, state, intake);
        }
    }

    private static void clampLength(Vector3f vector, float maxLength) {
        float lenSq = vector.lengthSquared();
        float maxSq = maxLength * maxLength;
        if (lenSq > maxSq && lenSq > 1e-7f) {
            vector.mul(maxLength / Mth.sqrt(lenSq));
        }
    }

    private static Vector3f splatVolumeLocalPosition(VolumeSplat splat, DarkBallVolumeBuildResult volume) {
        return new Vector3f(
                splat.localX * volume.captureLength(),
                splat.localY * volume.radius(),
                splat.localZ * volume.radius()
        );
    }

    private static float progressiveReleaseDistance(Vector3f point, Vector3f root) {
        float dx = (point.x - root.x) * 0.82f;
        float dy = point.y - root.y;
        float dz = point.z - root.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static SdfSample sampleVolumeSdf(DarkBallVolumeBuildResult volume, Vector3f local) {
        if (volume == null || volume.signedDistanceField() == null
                || volume.sdfGradientX() == null || volume.sdfGradientY() == null || volume.sdfGradientZ() == null) {
            return new SdfSample(Float.POSITIVE_INFINITY, Vec3.ZERO);
        }

        float gridX = local.x / Math.max(volume.captureLength(), 0.001f) * (DarkBallVolumeGrid.X_SLICES - 1);
        float gridY = (local.y / Math.max(volume.radius(), 0.001f) * 0.5f + 0.5f) * (DarkBallVolumeGrid.SLICE_SIZE - 1);
        float gridZ = (local.z / Math.max(volume.radius(), 0.001f) * 0.5f + 0.5f) * (DarkBallVolumeGrid.SLICE_SIZE - 1);

        float distance = sampleTrilinear(volume.signedDistanceField(), gridX, gridY, gridZ);
        float gx = sampleTrilinear(volume.sdfGradientX(), gridX, gridY, gridZ);
        float gy = sampleTrilinear(volume.sdfGradientY(), gridX, gridY, gridZ);
        float gz = sampleTrilinear(volume.sdfGradientZ(), gridX, gridY, gridZ);
        Vec3 normal = volume.axis().scale(gx).add(volume.side().scale(gy)).add(volume.up().scale(gz));
        if (normal.lengthSqr() > 1e-7) {
            normal = normal.normalize();
        }
        return new SdfSample(distance, normal);
    }

    private static float sampleLocalThickness(DarkBallVolumeBuildResult volume, Vector3f local) {
        if (volume == null || volume.localThicknessField() == null) {
            return 0f;
        }

        float gridX = local.x / Math.max(volume.captureLength(), 0.001f) * (DarkBallVolumeGrid.X_SLICES - 1);
        float gridY = (local.y / Math.max(volume.radius(), 0.001f) * 0.5f + 0.5f) * (DarkBallVolumeGrid.SLICE_SIZE - 1);
        float gridZ = (local.z / Math.max(volume.radius(), 0.001f) * 0.5f + 0.5f) * (DarkBallVolumeGrid.SLICE_SIZE - 1);
        return sampleTrilinear(volume.localThicknessField(), gridX, gridY, gridZ);
    }

    private static SdfSample sampleCombinedContainerSdf(DarkBallVolumeBuildResult volume, Vector3f local,
                                                       Vector3f drainRootLocal, Vector3f intakeLocal) {
        SdfSample pokemon = sampleVolumeSdf(volume, local);
        if (volume == null || drainRootLocal == null || intakeLocal == null) {
            return pokemon;
        }

        SdfSample throat = sampleSiphonThroatSdf(volume, local, drainRootLocal, intakeLocal);
        float blendRadius = Math.max(volume.bodyRadius() * SDF_THROAT_BLEND_RADIUS_SCALE, volume.sdfVoxelYz() * 1.35f);
        float h = Mth.clamp(0.5f + 0.5f * (throat.distance - pokemon.distance) / Math.max(blendRadius, 0.0001f), 0f, 1f);
        float distance = Mth.lerp(h, throat.distance, pokemon.distance) - blendRadius * h * (1f - h);

        Vec3 normal = pokemon.distance <= throat.distance ? pokemon.normal : throat.normal;
        if (Math.abs(pokemon.distance - throat.distance) < blendRadius && pokemon.normal.lengthSqr() > 1e-7 && throat.normal.lengthSqr() > 1e-7) {
            normal = pokemon.normal.scale(h).add(throat.normal.scale(1f - h));
            if (normal.lengthSqr() > 1e-7) {
                normal = normal.normalize();
            }
        }
        return new SdfSample(distance, normal);
    }

    private static SdfSample sampleSiphonThroatSdf(DarkBallVolumeBuildResult volume, Vector3f local,
                                                  Vector3f drainRootLocal, Vector3f intakeLocal) {
        float bestDistance = Float.POSITIVE_INFINITY;
        Vec3 bestNormal = Vec3.ZERO;
        Vector3f previous = DarkBallCaptureMath.siphonCurvePoint(drainRootLocal, intakeLocal, volume.bodyRadius(), 0f);
        float previousT = 0f;

        for (int i = 1; i <= SDF_THROAT_SAMPLES; i++) {
            float t = i / (float) SDF_THROAT_SAMPLES;
            Vector3f current = DarkBallCaptureMath.siphonCurvePoint(drainRootLocal, intakeLocal, volume.bodyRadius(), t);
            ClosestSegmentPoint closest = closestPointOnSegment(local, previous, current);
            float segmentT = Mth.lerp(closest.t, previousT, t);
            float radius = siphonThroatRadius(volume, segmentT);
            float distance = closest.distance - radius;
            if (distance < bestDistance) {
                bestDistance = distance;
                Vec3 normal = volumeLocalDirectionToWorld(volume, closest.normalLocal);
                bestNormal = normal.lengthSqr() < 1e-7 ? volume.axis() : normal.normalize();
            }
            previous = current;
            previousT = t;
        }

        return new SdfSample(bestDistance, bestNormal);
    }

    private static float siphonThroatRadius(DarkBallVolumeBuildResult volume, float t) {
        float taper = smoothstep(0f, 1f, Mth.clamp(t, 0f, 1f));
        float rootRadius = Math.max(volume.sdfVoxelYz() * 2.35f, volume.bodyRadius() * SDF_THROAT_ROOT_RADIUS_SCALE);
        float endRadius = Math.max(volume.sdfVoxelYz() * 1.15f, volume.bodyRadius() * SDF_THROAT_END_RADIUS_SCALE);
        return Mth.lerp(taper, rootRadius, endRadius);
    }

    private static ClosestSegmentPoint closestPointOnSegment(Vector3f point, Vector3f a, Vector3f b) {
        Vector3f ab = new Vector3f(b).sub(a);
        float lenSq = ab.lengthSquared();
        if (lenSq < 1e-7f) {
            Vector3f delta = new Vector3f(point).sub(a);
            float distance = delta.length();
            normalizeOrDefault(delta, 1f, 0f, 0f);
            return new ClosestSegmentPoint(0f, distance, delta);
        }

        float t = Mth.clamp(new Vector3f(point).sub(a).dot(ab) / lenSq, 0f, 1f);
        Vector3f closest = new Vector3f(a).fma(t, ab);
        Vector3f delta = new Vector3f(point).sub(closest);
        float distance = delta.length();
        normalizeOrDefault(delta, 1f, 0f, 0f);
        return new ClosestSegmentPoint(t, distance, delta);
    }

    private static Vec3 volumeLocalDirectionToWorld(DarkBallVolumeBuildResult volume, Vector3f localDirection) {
        return volume.axis().scale(localDirection.x)
                .add(volume.side().scale(localDirection.y))
                .add(volume.up().scale(localDirection.z));
    }

    private static Vector3f siphonIntakeLocal(DarkBallVolumeBuildResult volume, Vec3 ballPos) {
        Vector3f intake = volumeWorldToLocal(volume, ballPos);
        intake.x = Math.max(intake.x, volume.bodyRadius() * 0.25f);
        return intake;
    }

    private static float sampleTrilinear(float[] field, float gridX, float gridY, float gridZ) {
        int xCount = DarkBallVolumeGrid.X_SLICES;
        int sliceSize = DarkBallVolumeGrid.SLICE_SIZE;
        float x = Mth.clamp(gridX, 0f, xCount - 1f);
        float y = Mth.clamp(gridY, 0f, sliceSize - 1f);
        float z = Mth.clamp(gridZ, 0f, sliceSize - 1f);
        int x0 = Mth.clamp((int) Math.floor(x), 0, xCount - 1);
        int y0 = Mth.clamp((int) Math.floor(y), 0, sliceSize - 1);
        int z0 = Mth.clamp((int) Math.floor(z), 0, sliceSize - 1);
        int x1 = Mth.clamp(x0 + 1, 0, xCount - 1);
        int y1 = Mth.clamp(y0 + 1, 0, sliceSize - 1);
        int z1 = Mth.clamp(z0 + 1, 0, sliceSize - 1);
        float tx = x - x0;
        float ty = y - y0;
        float tz = z - z0;

        float c000 = field[DarkBallVolumeVoxelizer.voxelIndex(x0, y0, z0)];
        float c100 = field[DarkBallVolumeVoxelizer.voxelIndex(x1, y0, z0)];
        float c010 = field[DarkBallVolumeVoxelizer.voxelIndex(x0, y1, z0)];
        float c110 = field[DarkBallVolumeVoxelizer.voxelIndex(x1, y1, z0)];
        float c001 = field[DarkBallVolumeVoxelizer.voxelIndex(x0, y0, z1)];
        float c101 = field[DarkBallVolumeVoxelizer.voxelIndex(x1, y0, z1)];
        float c011 = field[DarkBallVolumeVoxelizer.voxelIndex(x0, y1, z1)];
        float c111 = field[DarkBallVolumeVoxelizer.voxelIndex(x1, y1, z1)];

        float c00 = Mth.lerp(tx, c000, c100);
        float c10 = Mth.lerp(tx, c010, c110);
        float c01 = Mth.lerp(tx, c001, c101);
        float c11 = Mth.lerp(tx, c011, c111);
        float c0 = Mth.lerp(ty, c00, c10);
        float c1 = Mth.lerp(ty, c01, c11);
        return Mth.lerp(tz, c0, c1);
    }

    private static int countVoxelCandidates(DarkBallVolumeBuildResult volume) {
        if (volume == null || volume.coreField() == null || volume.envelopeField() == null
                || volume.coreField().length != volume.envelopeField().length) {
            return 0;
        }

        int candidates = 0;
        for (int i = 0; i < volume.coreField().length; i++) {
            if (volume.coreField()[i] > 0.018f || volume.envelopeField()[i] > 0.052f) {
                candidates++;
            }
        }
        return candidates;
    }

    private static float localThicknessAtVoxel(DarkBallVolumeBuildResult volume, int index) {
        if (volume == null || volume.localThicknessField() == null
                || index < 0 || index >= volume.localThicknessField().length) {
            return 0f;
        }
        return volume.localThicknessField()[index];
    }

    private void logVoxelSdfStats(DarkBallVolumeBuildResult volume) {
        if (volume == null || volume.signedDistanceField() == null || volume.signedDistanceField().length == 0) {
            return;
        }

        int inside = 0;
        int outside = 0;
        float deepestInside = 0f;
        float farthestOutside = 0f;
        for (float d : volume.signedDistanceField()) {
            if (d < 0f) {
                inside++;
                deepestInside = Math.min(deepestInside, d);
            } else {
                outside++;
                farthestOutside = Math.max(farthestOutside, d);
            }
        }

        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball SDF for pokemon {} (insideVoxels={}, outsideVoxels={}, deepestInside={}, farthestOutside={}, voxelX={}, voxelYZ={})",
                pokemonId,
                inside,
                outside,
                String.format(java.util.Locale.ROOT, "%.4f", deepestInside),
                String.format(java.util.Locale.ROOT, "%.4f", farthestOutside),
                String.format(java.util.Locale.ROOT, "%.4f", volume.sdfVoxelX()),
                String.format(java.util.Locale.ROOT, "%.4f", volume.sdfVoxelYz())
        );
    }

    private void logSiphonOutletStats(DarkBallVolumeBuildResult volume, Vec3 ballPos) {
        if (siphonOutletLogged || volume == null || !siphonDrainRootValid) {
            return;
        }
        siphonOutletLogged = true;

        Vector3f intake = siphonIntakeLocal(volume, ballPos);
        Vector3f root = new Vector3f(siphonDrainRootLocal);
        Vector3f mid = DarkBallCaptureMath.siphonCurvePoint(root, intake, volume.bodyRadius(), 0.34f);
        SdfSample rootPokemon = sampleVolumeSdf(volume, root);
        SdfSample rootCombined = sampleCombinedContainerSdf(volume, root, root, intake);
        SdfSample midPokemon = sampleVolumeSdf(volume, mid);
        SdfSample midCombined = sampleCombinedContainerSdf(volume, mid, root, intake);
        float blendRadius = Math.max(volume.bodyRadius() * SDF_THROAT_BLEND_RADIUS_SCALE, volume.sdfVoxelYz() * 1.35f);

        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball outlet SDF for pokemon {} (root=({}, {}, {}), intake=({}, {}, {}), rootD={}->{}, midD={}->{}, rootRadius={}, endRadius={}, blend={})",
                pokemonId,
                String.format(java.util.Locale.ROOT, "%.3f", root.x),
                String.format(java.util.Locale.ROOT, "%.3f", root.y),
                String.format(java.util.Locale.ROOT, "%.3f", root.z),
                String.format(java.util.Locale.ROOT, "%.3f", intake.x),
                String.format(java.util.Locale.ROOT, "%.3f", intake.y),
                String.format(java.util.Locale.ROOT, "%.3f", intake.z),
                String.format(java.util.Locale.ROOT, "%.4f", rootPokemon.distance),
                String.format(java.util.Locale.ROOT, "%.4f", rootCombined.distance),
                String.format(java.util.Locale.ROOT, "%.4f", midPokemon.distance),
                String.format(java.util.Locale.ROOT, "%.4f", midCombined.distance),
                String.format(java.util.Locale.ROOT, "%.4f", siphonThroatRadius(volume, 0f)),
                String.format(java.util.Locale.ROOT, "%.4f", siphonThroatRadius(volume, 1f)),
                String.format(java.util.Locale.ROOT, "%.4f", blendRadius)
        );
    }

    private void logVoxelBuild(String result, int vertexCount, int triangleCount, int candidateCount) {
        if (voxelBuildLogged) {
            return;
        }
        voxelBuildLogged = true;
        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball voxel source for pokemon {} {} (vertices={}, triangles={}, candidates={})",
                pokemonId,
                result,
                vertexCount,
                triangleCount,
                candidateCount
        );
    }

    private void logSplatSource(String source, int splatCount, int candidateCount) {
        if (splatSourceLogged) {
            return;
        }
        splatSourceLogged = true;
        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball splats for pokemon {} use {} (splats={}, voxelCandidates={})",
                pokemonId,
                source,
                splatCount,
                candidateCount
        );
    }

    private static int packMaskColor(float strength) {
        int alpha = Math.round(Mth.clamp(strength, 0.0f, 1.0f) * 255.0f);
        return (alpha << 24) | 0x00FFFFFF;
    }

    private static void setupMaskUniforms(PokemonEntity entity, DarkBallCaptureVfx vfx) {
        ShaderInstance shader = ModShaders.DARK_BALL_MASK;
        if (shader == null) {
            return;
        }
        RenderSystem.setupShaderLights(shader);

        float modelSize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        float siphon = remapClamped(vfx.age, SIPHON_START, SIPHON_END);
        float expand = Mth.clamp(modelSize * Mth.lerp(siphon, 0.018f, 0.085f), 0.018f, 0.18f);
        float screenExpand = Mth.clamp(0.0028f + modelSize * 0.00045f, 0.0028f, 0.0075f);
        screenExpand *= 1.0f - smoothstep(0.55f, 0.92f, siphon) * 0.65f;

        Uniform uExpand = shader.getUniform("MaskExpand");
        if (uExpand != null) {
            uExpand.set(expand);
        }
        Uniform uScreenExpand = shader.getUniform("MaskScreenExpand");
        if (uScreenExpand != null) {
            uScreenExpand.set(screenExpand);
        }
    }

    private static long currentFrameToken(Minecraft mc, float partialTicks) {
        long frameToken = mc.getFrameTimeNs();
        if (frameToken == 0L && mc.level != null) {
            frameToken = (mc.level.getGameTime() << 20) ^ (long) (partialTicks * 1000.0f);
        }
        return frameToken;
    }

    private void emitMaterialSplat(Vec3 worldPos, Vec3 flowVelocity, Vec3 camPos, Quaternionf camOrientation,
                                   PoseStack stack, BufferBuilder buffer,
                                   float density, float size, float depth01, float boundary, float clipStencil,
                                   float bodyScale, float localSiphon, float siphonEase,
                                   Vec3 shapeAxisWorld, float shapeStretch, SdfClipContext sdfClip) {
        boolean flowCanDeform = ENABLE_VELOCITY_DEFORMED_SPLATS
                && localSiphon > 0.055f
                && flowVelocity.lengthSqr() >= 1e-8;
        if (ENABLE_MODEL_SHAPED_SPLATS && shapeStretch > 1.01f && !flowCanDeform) {
            if (emitModelShapedSplat(worldPos, shapeAxisWorld, camPos, camOrientation, stack, buffer,
                    density, size, depth01, boundary, clipStencil, shapeStretch, sdfClip)) {
                return;
            }
        }

        if (!ENABLE_VELOCITY_DEFORMED_SPLATS) {
            emitEncodedSplat(worldPos, camPos, camOrientation, stack, buffer,
                    density, size, depth01, boundary, clipStencil, sdfClip);
            return;
        }

        if (flowVelocity.lengthSqr() < 1e-8) {
            emitEncodedSplat(worldPos, camPos, camOrientation, stack, buffer,
                    density, size, depth01, boundary, clipStencil, sdfClip);
            return;
        }

        Vector3f rightV = new Vector3f(1f, 0f, 0f).rotate(camOrientation);
        Vector3f upV = new Vector3f(0f, 1f, 0f).rotate(camOrientation);
        Vec3 cameraRight = new Vec3(rightV.x, rightV.y, rightV.z);
        Vec3 cameraUp = new Vec3(upV.x, upV.y, upV.z);
        float right = (float) flowVelocity.dot(cameraRight);
        float up = (float) flowVelocity.dot(cameraUp);
        float projectedSpeed = (float) Math.sqrt(right * right + up * up);
        if (projectedSpeed < 1e-5f) {
            emitEncodedSplat(worldPos, camPos, camOrientation, stack, buffer,
                    density, size, depth01, boundary, clipStencil, sdfClip);
            return;
        }

        float siphonStretch = smoothstep(0.04f, 0.88f, localSiphon) * Mth.lerp(boundary, 0.42f, 1.0f);
        float curlStretch = boundary * smoothstep(0.05f, 0.22f, localSiphon) * (1f - siphonEase) * 0.34f;
        float stretchWeight = Math.max(siphonStretch, curlStretch);
        float speed = Mth.clamp(projectedSpeed / Math.max(bodyScale * 0.72f, 0.001f), 0f, 1.25f);
        float stretch = Mth.clamp(1f + speed * stretchWeight * 2.35f, 1f, 3.45f);
        if (stretch < 1.025f) {
            if (ENABLE_MODEL_SHAPED_SPLATS && shapeStretch > 1.01f
                    && emitModelShapedSplat(worldPos, shapeAxisWorld, camPos, camOrientation, stack, buffer,
                    density, size, depth01, boundary, clipStencil, shapeStretch, sdfClip)) {
                return;
            }
            emitEncodedSplat(worldPos, camPos, camOrientation, stack, buffer,
                    density, size, depth01, boundary, clipStencil, sdfClip);
            return;
        }

        float angle = (float) Math.atan2(up, right);
        float majorScale = Mth.sqrt(stretch);
        float minorScale = 1.0f / Math.max(majorScale, 0.001f);
        float tailBias = Mth.clamp((stretch - 1f) * siphonEase * 0.105f, 0f, 0.20f);
        emitEncodedSplat(worldPos, camPos, camOrientation, stack, buffer,
                density, size, depth01, boundary, clipStencil,
                angle, majorScale, minorScale, tailBias, sdfClip);
    }

    private boolean emitModelShapedSplat(Vec3 worldPos, Vec3 shapeAxisWorld,
                                         Vec3 camPos, Quaternionf camOrientation,
                                         PoseStack stack, BufferBuilder buffer,
                                         float density, float size, float depth01,
                                         float boundary, float clipStencil, float shapeStretch,
                                         SdfClipContext sdfClip) {
        if (shapeAxisWorld.lengthSqr() < 1e-7) {
            return false;
        }

        Vector3f rightV = new Vector3f(1f, 0f, 0f).rotate(camOrientation);
        Vector3f upV = new Vector3f(0f, 1f, 0f).rotate(camOrientation);
        Vec3 cameraRight = new Vec3(rightV.x, rightV.y, rightV.z);
        Vec3 cameraUp = new Vec3(upV.x, upV.y, upV.z);
        float right = (float) shapeAxisWorld.dot(cameraRight);
        float up = (float) shapeAxisWorld.dot(cameraUp);
        float projected = (float) Math.sqrt(right * right + up * up);
        if (projected < 0.12f) {
            return false;
        }

        float cameraFade = smoothstep(0.12f, 0.52f, projected);
        float stretch = Mth.lerp(cameraFade, 1.0f, Mth.clamp(shapeStretch, 1.0f, 2.65f));
        if (stretch < 1.025f) {
            return false;
        }

        float angle = (float) Math.atan2(up, right);
        float majorScale = Mth.sqrt(stretch);
        float minorScale = 1.0f / Math.max(majorScale, 0.001f);
        emitEncodedSplat(worldPos, camPos, camOrientation, stack, buffer,
                density, size, depth01, boundary, clipStencil,
                angle, majorScale, minorScale, 0f, sdfClip);
        return true;
    }

    private static void emitEncodedSplat(Vec3 worldPos, Vec3 camPos, Quaternionf camOrientation,
                                         PoseStack stack, BufferBuilder buffer,
                                         float density, float size, float depth01, float boundary, float clipStencil) {
        emitEncodedSplat(worldPos, camPos, camOrientation, stack, buffer,
                density, size, depth01, boundary, clipStencil, 0f, 1f, 1f, 0f);
    }

    private void emitEncodedSplat(Vec3 worldPos, Vec3 camPos, Quaternionf camOrientation,
                                  PoseStack stack, BufferBuilder buffer,
                                  float density, float size, float depth01, float boundary, float clipStencil,
                                  SdfClipContext sdfClip) {
        emitEncodedSplat(worldPos, camPos, camOrientation, stack, buffer,
                density, size, depth01, boundary, clipStencil, 0f, 1f, 1f, 0f, sdfClip);
    }

    private static void emitEncodedSplat(Vec3 worldPos, Vec3 camPos, Quaternionf camOrientation,
                                         PoseStack stack, BufferBuilder buffer,
                                         float density, float size, float depth01, float boundary, float clipStencil,
                                         float angle, float majorScale, float minorScale, float tailBias) {
        emitEncodedSplatUnclipped(worldPos, camPos, camOrientation, stack, buffer,
                density, size, depth01, boundary, clipStencil, angle, majorScale, minorScale, tailBias);
    }

    private void emitEncodedSplat(Vec3 worldPos, Vec3 camPos, Quaternionf camOrientation,
                                  PoseStack stack, BufferBuilder buffer,
                                  float density, float size, float depth01, float boundary, float clipStencil,
                                  float angle, float majorScale, float minorScale, float tailBias,
                                  SdfClipContext sdfClip) {
        if (sdfClip != null) {
            emitSdfClippedEncodedSplat(worldPos, camPos, camOrientation, buffer,
                    density, size, depth01, boundary, clipStencil, angle, majorScale, minorScale, tailBias, sdfClip);
            return;
        }
        emitEncodedSplatUnclipped(worldPos, camPos, camOrientation, stack, buffer,
                density, size, depth01, boundary, clipStencil, angle, majorScale, minorScale, tailBias);
    }

    private static void emitEncodedSplatUnclipped(Vec3 worldPos, Vec3 camPos, Quaternionf camOrientation,
                                                  PoseStack stack, BufferBuilder buffer,
                                                  float density, float size, float depth01, float boundary, float clipStencil,
                                                  float angle, float majorScale, float minorScale, float tailBias) {
        if ((density < 0.004f && clipStencil < 0.004f) || size <= 0.001f) {
            return;
        }

        float d = Mth.clamp(density, 0f, 1f);
        float z = Mth.clamp(depth01, 0f, 1f);
        float edge = Mth.clamp(boundary, 0f, 1f);
        float clip = Mth.clamp(clipStencil, 0f, 1f);

        stack.pushPose();
        stack.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        stack.mulPose(camOrientation);
        if (Math.abs(angle) > 1e-5f) {
            stack.mulPose(com.mojang.math.Axis.ZP.rotation(angle));
        }
        if (tailBias > 0.0001f) {
            stack.translate(-size * tailBias, 0f, 0f);
        }
        stack.scale(size * Math.max(0.001f, majorScale), size * Math.max(0.001f, minorScale), 1f);
        Matrix4f pose = stack.last().pose();
        buffer.addVertex(pose, -1f, -1f, 0f).setUv(0f, 1f).setColor(d, z, edge, clip).setLight(0xF000F0);
        buffer.addVertex(pose,  1f, -1f, 0f).setUv(1f, 1f).setColor(d, z, edge, clip).setLight(0xF000F0);
        buffer.addVertex(pose,  1f,  1f, 0f).setUv(1f, 0f).setColor(d, z, edge, clip).setLight(0xF000F0);
        buffer.addVertex(pose, -1f,  1f, 0f).setUv(0f, 0f).setColor(d, z, edge, clip).setLight(0xF000F0);
        stack.popPose();
    }

    private void emitSdfClippedEncodedSplat(Vec3 worldPos, Vec3 camPos, Quaternionf camOrientation,
                                            BufferBuilder buffer,
                                            float density, float size, float depth01,
                                            float boundary, float clipStencil,
                                            float angle, float majorScale, float minorScale,
                                            float tailBias, SdfClipContext sdfClip) {
        if ((density < 0.004f && clipStencil < 0.004f) || size <= 0.001f) {
            return;
        }

        float d = Mth.clamp(density, 0f, 1f);
        float z = Mth.clamp(depth01, 0f, 1f);
        float edge = Mth.clamp(boundary, 0f, 1f);
        float clip = Mth.clamp(clipStencil, 0f, 1f);

        Vector3f rightV = new Vector3f(1f, 0f, 0f).rotate(camOrientation);
        Vector3f upV = new Vector3f(0f, 1f, 0f).rotate(camOrientation);
        Vec3 cameraRight = new Vec3(rightV.x, rightV.y, rightV.z);
        Vec3 cameraUp = new Vec3(upV.x, upV.y, upV.z);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        Vec3 axisX = cameraRight.scale(cos).add(cameraUp.scale(sin));
        Vec3 axisY = cameraRight.scale(-sin).add(cameraUp.scale(cos));
        Vec3 center = tailBias > 0.0001f ? worldPos.subtract(axisX.scale(size * tailBias)) : worldPos;

        int subdivisions = 4;
        float[][] gates = new float[subdivisions + 1][subdivisions + 1];
        Vec3[][] points = new Vec3[subdivisions + 1][subdivisions + 1];
        boolean anyVisible = false;
        for (int y = 0; y <= subdivisions; y++) {
            float localY = -1f + y * (2f / subdivisions);
            for (int x = 0; x <= subdivisions; x++) {
                float localX = -1f + x * (2f / subdivisions);
                Vec3 point = center
                        .add(axisX.scale(localX * size * Math.max(0.001f, majorScale)))
                        .add(axisY.scale(localY * size * Math.max(0.001f, minorScale)));
                float gate = sdfContributionGate(point, sdfClip);
                gates[x][y] = gate;
                points[x][y] = point;
                anyVisible |= gate > 0.003f;
            }
        }
        if (!anyVisible) {
            return;
        }

        Matrix4f identity = new Matrix4f();
        for (int y = 0; y < subdivisions; y++) {
            for (int x = 0; x < subdivisions; x++) {
                float g00 = gates[x][y];
                float g10 = gates[x + 1][y];
                float g11 = gates[x + 1][y + 1];
                float g01 = gates[x][y + 1];
                if (Math.max(Math.max(g00, g10), Math.max(g11, g01)) <= 0.003f) {
                    continue;
                }

                float x0 = x / (float) subdivisions;
                float x1 = (x + 1) / (float) subdivisions;
                float y0 = y / (float) subdivisions;
                float y1 = (y + 1) / (float) subdivisions;
                addSdfClippedSplatVertex(buffer, identity, points[x][y], camPos, x0, 1f - y0, d * g00, z, edge * g00, clip);
                addSdfClippedSplatVertex(buffer, identity, points[x + 1][y], camPos, x1, 1f - y0, d * g10, z, edge * g10, clip);
                addSdfClippedSplatVertex(buffer, identity, points[x + 1][y + 1], camPos, x1, 1f - y1, d * g11, z, edge * g11, clip);
                addSdfClippedSplatVertex(buffer, identity, points[x][y + 1], camPos, x0, 1f - y1, d * g01, z, edge * g01, clip);
            }
        }
    }

    private void addSdfClippedSplatVertex(BufferBuilder buffer, Matrix4f pose,
                                          Vec3 point, Vec3 camPos, float u, float v,
                                          float density, float depth01, float boundary, float clipStencil) {
        buffer.addVertex(pose,
                        (float) (point.x - camPos.x),
                        (float) (point.y - camPos.y),
                        (float) (point.z - camPos.z))
                .setUv(u, v)
                .setColor(Mth.clamp(density, 0f, 1f),
                        Mth.clamp(depth01, 0f, 1f),
                        Mth.clamp(boundary, 0f, 1f),
                        Mth.clamp(clipStencil, 0f, 1f))
                .setLight(0xF000F0);
    }

    private float sdfContributionGate(Vec3 worldPoint, SdfClipContext sdfClip) {
        if (voxelVolume == null || sdfClip == null) {
            return 1f;
        }

        Vector3f local = volumeWorldToLocal(voxelVolume, worldPoint);
        SdfSample sample = sampleCombinedContainerSdf(voxelVolume, local, siphonDrainRootLocal, sdfClip.intakeLocal);
        if (!Float.isFinite(sample.distance)) {
            return 0f;
        }

        float outside = sample.distance - sdfClip.allowedOutside;
        return 1f - smoothstep(0f, Math.max(sdfClip.softBand, 0.0001f), outside);
    }

    private static float normalizedViewDepth(Vec3 pos, Vec3 center, Vec3 viewDir, float extent) {
        float projected = (float) pos.subtract(center).dot(viewDir);
        return Mth.clamp(projected / (extent * 2f) + 0.5f, 0f, 1f);
    }

    private static Basis makeBasis(Vec3 center, Vec3 ballPos) {
        Vec3 forward = ballPos.subtract(center);
        if (forward.lengthSqr() < 1e-7) {
            forward = new Vec3(0, 1, 0);
        } else {
            forward = forward.normalize();
        }
        Vec3 ref = Math.abs(forward.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = forward.cross(ref);
        if (right.lengthSqr() < 1e-7) {
            right = new Vec3(1, 0, 0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward);
        if (up.lengthSqr() < 1e-7) {
            up = new Vec3(0, 1, 0);
        } else {
            up = up.normalize();
        }
        return new Basis(forward, right, up);
    }

    private static Basis makeFrame(Vec3 tangent, Vec3 preferredUp) {
        Vec3 forward = tangent.lengthSqr() < 1e-7 ? new Vec3(0, 1, 0) : tangent.normalize();
        Vec3 right = forward.cross(preferredUp);
        if (right.lengthSqr() < 1e-7) {
            right = forward.cross(new Vec3(1, 0, 0));
        }
        if (right.lengthSqr() < 1e-7) {
            right = new Vec3(1, 0, 0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward);
        if (up.lengthSqr() < 1e-7) {
            up = new Vec3(0, 1, 0);
        } else {
            up = up.normalize();
        }
        return new Basis(forward, right, up);
    }

    private static Vec3 cubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        float u = 1f - t;
        return p0.scale(u * u * u)
                .add(p1.scale(3f * u * u * t))
                .add(p2.scale(3f * u * t * t))
                .add(p3.scale(t * t * t));
    }

    private static Vec3 cubicBezierTangent(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        float u = 1f - t;
        Vec3 tangent = p1.subtract(p0).scale(3f * u * u)
                .add(p2.subtract(p1).scale(6f * u * t))
                .add(p3.subtract(p2).scale(3f * t * t));
        return tangent.lengthSqr() < 1e-7 ? new Vec3(0, 1, 0) : tangent.normalize();
    }

    private static float configParticleMultiplier() {
        return com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                .getInstance().getClientConfig().snagParticleMultiplier();
    }

    private static float remapClamped(float x, float a, float b) {
        return Mth.clamp((x - a) / (b - a), 0f, 1f);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static final class ModelSnapshotCapture {
        private final DarkBallCaptureVfx vfx;
        private final PokemonEntity entity;
        private final Vec3 poseToWorldOffset;
        private final SnapshotBounds bounds = new SnapshotBounds();

        private ModelSnapshotCapture(DarkBallCaptureVfx vfx, PokemonEntity entity, Vec3 poseToWorldOffset) {
            this.vfx = vfx;
            this.entity = entity;
            this.poseToWorldOffset = poseToWorldOffset;
        }

        private void include(Vec3 point) {
            bounds.include(point);
        }
    }

    private static final class TexturedSnapshotCapture {
        private final DarkBallCaptureVfx vfx;
        private final PokemonEntity entity;
        private final DarkBallFieldMaskBufferSource bufferSource;

        private TexturedSnapshotCapture(DarkBallCaptureVfx vfx,
                                        PokemonEntity entity,
                                        DarkBallFieldMaskBufferSource bufferSource) {
            this.vfx = vfx;
            this.entity = entity;
            this.bufferSource = bufferSource;
        }
    }

    private static final class SnapshotBounds {
        private boolean hasBounds = false;
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        private void include(Vec3 point) {
            hasBounds = true;
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }

        private Vec3 center() {
            return new Vec3((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
        }

        private float extent() {
            double sx = maxX - minX;
            double sy = maxY - minY;
            double sz = maxZ - minZ;
            return Math.max(0.18f, (float) (Math.max(sx, Math.max(sy, sz)) * 0.62));
        }

        private AABB toAabb() {
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private record SnapshotQuad(Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, Vec3 center) {
    }

    private record VoxelBuildOutput(
            int generation,
            DarkBallVolumeBuildResult volume,
            DarkBallAnalyticalVolume analyticalVolume,
            DarkBallVfxQuality quality,
            int vertexCount,
            int triangleCount,
            long buildMillis
    ) {
    }

    private static final class SplatState {
        private final Vector3f previousPosition;
        private final Vector3f position;
        private final Vector3f velocity = new Vector3f();
        private final Vector3f smoothedVelocity = new Vector3f();
        private float density = 1f;
        private float siphonProgress = 0f;

        private SplatState(Vector3f restPosition) {
            this.previousPosition = new Vector3f(restPosition);
            this.position = new Vector3f(restPosition);
        }
    }

    private record SiphonPhase(
            float releaseWave,
            float travel,
            float siphonEase,
            float entranceBlend,
            float pathTravel,
            float releaseOrder,
            float finalFlush
    ) {
    }

    private record LocalFrame(Vector3f forward, Vector3f right, Vector3f up) {
    }

    private record SplatFrame(Vector3f normal, Vector3f tangent, Vector3f bitangent) {
    }

    private record SplatCovariance(Vec3 axisWorld, float stretch, float sizeScale, boolean active) {
        private static SplatCovariance inactive() {
            return new SplatCovariance(Vec3.ZERO, 1f, 1f, false);
        }
    }

    private record VolumeSplat(
            float localX,
            float localY,
            float localZ,
            float radius,
            float density,
            float depthLayer,
            float boundaryWeight,
            float volatility,
            float angle,
            float seed,
            float shapeAxisX,
            float shapeAxisY,
            float shapeAxisZ,
            float surfaceNormalX,
            float surfaceNormalY,
            float surfaceNormalZ,
            float surfaceTangentX,
            float surfaceTangentY,
            float surfaceTangentZ,
            float surfaceBitangentX,
            float surfaceBitangentY,
            float surfaceBitangentZ,
            float shapeStretch,
            float release
    ) {
    }

    private record SdfSample(float distance, Vec3 normal) {
    }

    private record SdfClipContext(Vector3f intakeLocal, float allowedOutside, float softBand) {
    }

    private record ClosestSegmentPoint(float t, float distance, Vector3f normalLocal) {
    }

    private record Basis(Vec3 forward, Vec3 right, Vec3 up) {
    }
}
