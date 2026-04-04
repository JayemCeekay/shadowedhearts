package com.jayemceekay.shadowedhearts.client.trail;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.aura.AuraPulseRenderer;
import com.jayemceekay.shadowedhearts.client.aura.IrisHandler;
import com.jayemceekay.shadowedhearts.client.render.DepthCapture;
import com.jayemceekay.shadowedhearts.mixin.RenderTargetAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a shadow aura trail using a fullscreen quad with sphere-traced SDF
 * raymarching. The vertex shader reconstructs camera-relative world-aligned
 * ray directions from NDC via {@code mat3(uInvView) * (uInvProj * clip).xyz}.
 * Node positions are uploaded in the same camera-relative world-aligned space,
 * so both the SDF evaluation and noise sampling are inherently rotation-stable
 * — no extra rotation is needed in the fragment shader.
 * Depth buffer comparison prevents the trail from rendering over solid geometry.
 */
public final class TrailRibbonRenderer {

    private static final int MAX_NODES = 48;
    private static final float NEAR_FADE_MIN = 1.0f;
    private static final float NEAR_FADE_MAX = 4.0f;

    // Adaptive resolution: dense near player, sparse far away
    private static final float NEAR_SPACING = 0.75f;   // blocks between nodes near the player
    private static final float FAR_SPACING  = 2.5f;    // blocks between nodes far from player
    private static final float SPACING_RAMP = 20.0f;   // distance (in nodes) over which spacing transitions

    private TrailRibbonRenderer() {}

    public static void render(
            List<Vec3> smoothedPath,
            Vec3 playerPos,
            float maxDist,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            float hudAlpha
    ) {
        if (smoothedPath.size() < 2) return;

        ShaderInstance shader = ModShaders.SHADOW_AURA_TRAIL;
        if (shader == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Use the actual camera position so that camera-relative node offsets
        // are consistent with the view matrix in both first- and third-person.
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        // --- Select window of nodes with adaptive resolution ---
        // Keep high density near the player, increase spacing further away.
        // This allows the trail to cover 100+ blocks while staying smooth near the camera.
        int closestIdx = findClosest(smoothedPath, playerPos);

        // No lead-in behind the player — eliminates backward trail growth
        int leadIn = 0;
        List<Vec3> adaptiveNodes = new ArrayList<>();
        // Behind the player: add lead-in nodes at full density
        for (int i = closestIdx - leadIn; i < closestIdx; i++) {
            adaptiveNodes.add(smoothedPath.get(i));
            if (adaptiveNodes.size() >= MAX_NODES) break;
        }
        // Forward from player: adaptive spacing
        float accumDist = 0.0f;
        float nextThreshold = 0.0f;
        Vec3 lastAdded = (closestIdx < smoothedPath.size()) ? smoothedPath.get(closestIdx) : null;
        for (int i = closestIdx; i < smoothedPath.size() && adaptiveNodes.size() < MAX_NODES; i++) {
            Vec3 node = smoothedPath.get(i);
            if (lastAdded != null && i > closestIdx) {
                accumDist += (float) node.distanceTo(lastAdded);
            }
            lastAdded = node;
            // Compute adaptive spacing: dense near player, sparse far away
            float nodesFromPlayer = adaptiveNodes.size() - leadIn;
            float spacingT = Math.min(nodesFromPlayer / SPACING_RAMP, 1.0f);
            float spacing = NEAR_SPACING + (FAR_SPACING - NEAR_SPACING) * spacingT * spacingT;
            if (i == closestIdx || accumDist >= nextThreshold) {
                adaptiveNodes.add(node);
                nextThreshold = accumDist + spacing;
            }
        }
        int nodeCount = adaptiveNodes.size();
        if (nodeCount < 2) return;

        // --- Prepare control points for splining ---
        Vec3[] controls = new Vec3[nodeCount];
        Vec3 anchoredPlayerPos = playerPos.add(0, 0.25, 0);
        int playerRelIdx = leadIn; // player position within adaptive nodes

        for (int i = 0; i < nodeCount; i++) {
            controls[i] = (i == playerRelIdx) ? anchoredPlayerPos : adaptiveNodes.get(i);
        }

        // --- Apply Verlet-style distance constraints ---
        // This pulls the neighboring nodes toward the player to create a smooth curve
        // even when the player is far from the underlying path.
        // Use adaptive target distance matching the node spacing rather than a tight constant.
        int iterations = 2;
        for (int iter = 0; iter < iterations; iter++) {
            // Forward pass from player anchor to the end of the window
            for (int i = playerRelIdx + 1; i < nodeCount; i++) {
                float nodesFromPlayer = Math.abs(i - playerRelIdx);
                float spacingT = Math.min(nodesFromPlayer / SPACING_RAMP, 1.0f);
                float localTarget = NEAR_SPACING + (FAR_SPACING - NEAR_SPACING) * spacingT * spacingT;
                controls[i] = constrainDistance(controls[i - 1], controls[i], localTarget);
            }
            // Backward pass from player anchor to the start of the window
            for (int i = playerRelIdx - 1; i >= 0; i--) {
                float nodesFromPlayer = Math.abs(i - playerRelIdx);
                float spacingT = Math.min(nodesFromPlayer / SPACING_RAMP, 1.0f);
                float localTarget = NEAR_SPACING + (FAR_SPACING - NEAR_SPACING) * spacingT * spacingT;
                controls[i] = constrainDistance(controls[i + 1], controls[i], localTarget);
            }
        }

        // --- Generate splined path ---
        // We over-sample the segments connected to the player anchor to ensure
        // the trail curves smoothly toward them instead of forming sharp angles.
        List<Vec3> splinedNodes = new ArrayList<>();
        for (int i = 0; i < nodeCount - 1; i++) {
            Vec3 p1 = controls[i];
            Vec3 p2 = controls[i + 1];

            if (i == playerRelIdx || i == playerRelIdx - 1) {
                // This segment touches the player anchor; use Catmull-Rom to over-sample.
                Vec3 p0 = (i > 0) ? controls[i - 1] : p1.add(p1.subtract(p2));
                Vec3 p3 = (i < nodeCount - 2) ? controls[i + 2] : p2.add(p2.subtract(p1));

                splinedNodes.add(catmullRom(p0, p1, p2, p3, 0.00f));
                splinedNodes.add(catmullRom(p0, p1, p2, p3, 0.33f));
                splinedNodes.add(catmullRom(p0, p1, p2, p3, 0.66f));
            } else {
                splinedNodes.add(p1);
            }
        }
        splinedNodes.add(controls[nodeCount - 1]);

        // Ensure we don't exceed MAX_NODES
        if (splinedNodes.size() > MAX_NODES) {
            splinedNodes = splinedNodes.subList(0, MAX_NODES);
        }

        // --- Compute camera-relative node positions ---
        int finalNodeCount = splinedNodes.size();
        float[][] camRelNodes = new float[finalNodeCount][3];

        for (int i = 0; i < finalNodeCount; i++) {
            Vec3 p = splinedNodes.get(i);
            camRelNodes[i][0] = (float) (p.x - camPos.x);
            camRelNodes[i][1] = (float) (p.y - camPos.y);
            camRelNodes[i][2] = (float) (p.z - camPos.z);
        }

        // --- Upload per-frame uniforms ---
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        float time = (mc.level.getGameTime() + partialTicks) * 0.05f;

        // Matrices: uView and uInvView for the fragment shader (depth comparison
        // and vertex shader ray reconstruction respectively), uInvProj for VS.
        Matrix4f invView = new Matrix4f(view).invert();
        Matrix4f invProj = new Matrix4f(proj).invert();

        if (shader.getUniform("uView") != null) shader.getUniform("uView").set(view);
        if (shader.getUniform("uInvView") != null) shader.getUniform("uInvView").set(invView);
        if (shader.getUniform("uInvProj") != null) shader.getUniform("uInvProj").set(invProj);
        if (shader.getUniform("uProj") != null) shader.getUniform("uProj").set(proj);

        if (shader.getUniform("uTime") != null) shader.getUniform("uTime").set(time);
        if (shader.getUniform("uTrailMaxDist") != null) shader.getUniform("uTrailMaxDist").set(maxDist);
        if (shader.getUniform("uNearFadeMin") != null) shader.getUniform("uNearFadeMin").set(NEAR_FADE_MIN);
        if (shader.getUniform("uNearFadeMax") != null) shader.getUniform("uNearFadeMax").set(NEAR_FADE_MAX);
        if (shader.getUniform("uAuraFade") != null) shader.getUniform("uAuraFade").set(hudAlpha);

        // Upload actual camera world position for noise anchoring in the fragment
        // shader.  Noise is sampled at (p + uCameraPosWS) so the domain is fixed
        // in absolute world space regardless of camera movement or rotation.
        if (shader.getUniform("uCameraPosWS") != null) {
            shader.getUniform("uCameraPosWS").set(
                    (float) camPos.x, (float) camPos.y, (float) camPos.z);
        }

        // Screen size for depth UV computation
        RenderTarget main = mc.getMainRenderTarget();
        int mw = ((RenderTargetAccessor)(Object)main).getWidth();
        int mh = ((RenderTargetAccessor)(Object)main).getHeight();
        if (shader.getUniform("uScreenSize") != null) {
            shader.getUniform("uScreenSize").set((float) mw, (float) mh);
        }

        // Player position in camera-relative world-aligned space for exclusion sphere
        Vec3 playerCamRel = playerPos.subtract(camPos);
        if (shader.getUniform("uPlayerPosCS") != null) {
            shader.getUniform("uPlayerPosCS").set(
                    (float) playerCamRel.x, (float) playerCamRel.y, (float) playerCamRel.z);
        }

        // --- Upload node positions in camera-relative world-aligned space ---
        // The vertex shader reconstructs world-aligned ray directions via
        // mat3(uInvView) * uInvProj, so p and uNodes are both in the same
        // camera-relative world-aligned coordinate frame.
        float[] nodeData = new float[MAX_NODES * 3];
        for (int i = 0; i < finalNodeCount; i++) {
            nodeData[i * 3]     = camRelNodes[i][0];
            nodeData[i * 3 + 1] = camRelNodes[i][1];
            nodeData[i * 3 + 2] = camRelNodes[i][2];
        }

        Uniform uNodes = shader.getUniform("uNodes");
        if (uNodes != null) {
            uNodes.set(nodeData);
        }
        if (shader.getUniform("uNumNodes") != null) {
            shader.getUniform("uNumNodes").set((float) finalNodeCount);
        }

        // --- Upload hunt state uniforms ---
        if (shader.getUniform("uTension") != null) {
            shader.getUniform("uTension").set(TrailClientState.INSTANCE.getTension());
        }
        if (shader.getUniform("uTrailQuality") != null) {
            shader.getUniform("uTrailQuality").set(TrailClientState.INSTANCE.getTrailQuality());
        }

        // --- Bind depth texture ---
        DepthCapture.captureIfNeeded();
        int depthTexture = DepthCapture.textureId();

        IrisHandler iris = AuraPulseRenderer.IRIS_HANDLER;
        if (iris != null && iris.isShaderPackInUse()) {
            IrisHandler.IrisRenderingSnapshot snapshot = iris.getIrisRenderingSnapshot();
            if (snapshot != null) {
                depthTexture = snapshot.depthTexture;
            }
        }

        shader.setSampler("uDepth", depthTexture);
        RenderSystem.depthMask(false);

        // --- Draw fullscreen quad ---
        // Emit a quad covering NDC (-1,-1) to (1,1). The vertex shader passes
        // these through directly and reconstructs view-space ray directions.
        RenderSystem.setShader(() -> shader);
        shader.apply();

        var tess = Tesselator.getInstance();
        var buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buf.addVertex(-1.0f, -1.0f, 0.0f);
        buf.addVertex( 1.0f, -1.0f, 0.0f);
        buf.addVertex( 1.0f,  1.0f, 0.0f);
        buf.addVertex(-1.0f,  1.0f, 0.0f);
        BufferUploader.drawWithShader(buf.buildOrThrow());

        shader.clear();
        RenderSystem.depthMask(true);
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;

        double x = 0.5 * (
                (2 * p1.x) +
                (-p0.x + p2.x) * t +
                (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
                (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3
        );
        double y = 0.5 * (
                (2 * p1.y) +
                (-p0.y + p2.y) * t +
                (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
                (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3
        );
        double z = 0.5 * (
                (2 * p1.z) +
                (-p0.z + p2.z) * t +
                (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 +
                (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3
        );
        return new Vec3(x, y, z);
    }

    private static Vec3 prevPlayerPos = null;

    private static int findClosest(List<Vec3> path, Vec3 pos) {
        int bestIdx = 0;
        double bestScore = Double.MAX_VALUE;

        // Direction-aware: strongly bias toward nodes ahead of the player.
        // Use proportional penalty so it scales with distance from path.
        Vec3 playerForward = null;
        if (prevPlayerPos != null) {
            Vec3 delta = pos.subtract(prevPlayerPos);
            double len = delta.length();
            if (len > 0.01) {
                playerForward = delta.scale(1.0 / len);
            }
        }
        prevPlayerPos = pos;

        for (int i = 0; i < path.size(); i++) {
            double d2 = path.get(i).distanceToSqr(pos);
            double score = d2;
            if (playerForward != null) {
                Vec3 toNode = path.get(i).subtract(pos);
                double dot = toNode.dot(playerForward);
                if (dot < 0) {
                    // Proportional penalty: behind-nodes get distance multiplied
                    // so even far-off-path scenarios still prefer forward nodes
                    double behindDist = -dot;
                    score += behindDist * behindDist * 4.0;
                }
            }
            // Also prefer nodes further along the path (higher index = closer to goal)
            // Small tiebreaker that favors forward progress
            score -= i * 0.01;
            if (score < bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private static Vec3 constrainDistance(Vec3 anchor, Vec3 point, float maxDist) {
        Vec3 diff = point.subtract(anchor);
        double dist = diff.length();
        if (dist <= (double) maxDist || dist < 1e-6) {
            return point;
        }
        // Soft constraint: blend toward the target rather than hard-clamping
        Vec3 target = anchor.add(diff.scale((double) maxDist / dist));
        float stiffness = 0.15f;
        return point.add(target.subtract(point).scale(stiffness));
    }
}
