package com.jayemceekay.shadowedhearts.client.moonwound;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderPulseRenderer;
import com.jayemceekay.shadowedhearts.client.ClientEffects;
import com.jayemceekay.shadowedhearts.config.ClientEffectPolicy;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.jayemceekay.shadowedhearts.client.render.IrisScaledWorldTarget;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Phase;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundPortalGeometry;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A vertical quad turns only around world Y; the shader traces a hollow vortex and drifting miasma. */
public final class MoonwoundPortalRenderer {
    // A distinct format identity avoids Iris substituting its terrain/entity attributes.
    public static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR).build();
    public static ShaderInstance SHADER;
    public static final VertexFormat PIXEL_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION).build();
    public static ShaderInstance PIXEL_RESOLVE;
    public static ShaderInstance PIXEL_PRESENT;
    private static final MoonwoundPixelationTarget PIXEL_TARGET = new MoonwoundPixelationTarget();
    private static boolean pixelationFailed;
    private static ShaderInstance failedShader;
    private record Draw(MoonwoundSnapshot portal, MoonwoundSurfaceFlow.Surface surface,
                        MoonwoundSurfaceFlow.Wisp wisp, Vec3 center, double distanceSquared, float openingAge) { }

    private MoonwoundPortalRenderer() { }

    public static void resetShader() {
        SHADER = null;
        failedShader = null;
        PIXEL_RESOLVE = null;
        PIXEL_PRESENT = null;
        clearPixelationResources();
    }

    public static void clearPixelationResources() {
        pixelationFailed = false;
        PIXEL_TARGET.destroy();
    }

    public static boolean hasActivePortals() {
        return ClientEffects.level(ClientEffectPolicy.Family.MOONWOUND) != ClientEffectPolicy.Level.OFF
                && SHADER != null && SHADER != failedShader && !MoonwoundClient.portals().isEmpty();
    }

    /** Caller owns a world-color target and installs the matching projection/model-view matrices. */
    public static void render(Camera camera, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        var iris = AuraReaderPulseRenderer.IRIS_HANDLER;
        if (camera == null || minecraft.level == null || !hasActivePortals()
                || (iris != null && iris.isShadowRenderActive())) return;
        ShaderInstance shader = SHADER;
        Vec3 cameraPos = camera.getPosition();
        var portals = MoonwoundClient.portals().stream()
                .filter(state -> portalPosition(state).distanceToSqr(cameraPos) < 128 * 128)
                .sorted(Comparator.comparingDouble((MoonwoundSnapshot state) ->
                        portalPosition(state).distanceToSqr(cameraPos)).reversed()).toList();
        if (portals.isEmpty()) return;
        double seconds = ((double) minecraft.level.getGameTime() + partialTicks) / 20.0;
        var draws = new ArrayList<Draw>();
        for (var portal : portals) {
            if (portal.portalSize() < 0.001f) continue;
            var surface = ClientEffects.moonwoundMiasma() ? MoonwoundSurfaceMiasma.surface(portal) : null;
            float openingAge = ClientEffects.moonwoundMiasma()
                    ? MoonwoundClient.portalOpeningAge(portal, partialTicks) : MoonwoundPortalOpening.SETTLED_SECONDS;
            Vec3 center = portalPosition(portal);
            draws.add(new Draw(portal, surface, null, center, center.distanceToSqr(cameraPos), openingAge));
            if (surface == null) continue;
            for (var wisp : surface.sample(seconds, openingAge)) {
                Vec3 position = surfacePosition(portal, wisp.center());
                draws.add(new Draw(portal, surface, wisp, position, position.distanceToSqr(cameraPos), openingAge));
            }
        }
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f clipFromRelative = new Matrix4f(projection).mul(RenderSystem.getModelViewMatrix());
        MoonwoundFrustum frustum = new MoonwoundFrustum(clipFromRelative);
        // Include the falling stream and every surface wisp in visibility decisions;
        // the portal center alone can be off-screen while its miasma is still visible.
        draws.removeIf(draw -> !isVisible(draw, camera, frustum));
        if (draws.isEmpty()) return;
        draws.sort(Comparator.comparingDouble(Draw::distanceSquared).reversed());
        try (var state = DensityFboPipeline.preserveRenderState()) {
            int strength = ShadowedHeartsConfigs.getInstance().getClientConfig().moonwoundPortalStyle().pixelSize();
            if (strength <= 1 || pixelationFailed || PIXEL_RESOLVE == null || PIXEL_PRESENT == null) {
                drawGeometry(camera, partialTicks, draws, false);
                return;
            }
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            // Each portal carries its own texel scale. A close portal must not impose
            // large screen blocks on a distant one; keep each group's wisps with it.
            for (var portal : portals) {
                List<Draw> group = draws.stream().filter(draw -> draw.portal == portal).toList();
                if (group.isEmpty()) continue;
                Vec3 eye = cameraPos.subtract(portalPosition(portal));
                Vector3f relative = new Vector3f((float) -eye.x, (float) -eye.y, (float) -eye.z);
                double nominalWidth = MoonwoundPixelationScale.nominalWidth(projection, eye.length(),
                        portal.portalSize(), viewport[2], viewport[3]);
                var center = MoonwoundPixelationScale.projectedCenter(clipFromRelative, relative, viewport[2], viewport[3]);
                var grid = center == null ? null : MoonwoundPixelationScale.grid(strength, nominalWidth,
                        center.x, center.y, viewport[2], viewport[3]);
                if (grid == null || !renderPixelated(camera, partialTicks, group, grid))
                    drawGeometry(camera, partialTicks, group, false);
            }
        } catch (RuntimeException exception) {
            failedShader = shader;
            Shadowedhearts.LOGGER.warn("[Moonwound] Portal rendering disabled until resource reload", exception);
        }
    }

    private static boolean isVisible(Draw draw, Camera camera, MoonwoundFrustum frustum) {
        Vec3 relative = draw.center.subtract(camera.getPosition());
        if (draw.wisp != null) {
            double extent = Math.sqrt(2) * draw.wisp.radius();
            return frustum.isVisible(relative.x - extent, relative.y - extent, relative.z - extent,
                    relative.x + extent, relative.y + extent, relative.z + extent);
        }
        Vec3 eye = relative.scale(-1);
        double horizontal = Math.hypot(eye.x, eye.z);
        double rightX = horizontal > 1.0e-5 ? eye.z / horizontal : Math.cos(Math.toRadians(camera.getYRot()));
        double rightZ = horizontal > 1.0e-5 ? -eye.x / horizontal : Math.sin(Math.toRadians(camera.getYRot()));
        double halfWidth = MoonwoundPortalGeometry.halfWidth(draw.portal.portalSize());
        double halfHeight = MoonwoundPortalGeometry.halfHeight(draw.portal.portalSize());
        double extentX = Math.abs(rightX) * halfWidth * MoonwoundPortalGeometry.QUAD_HALF_WIDTH;
        double extentZ = Math.abs(rightZ) * halfWidth * MoonwoundPortalGeometry.QUAD_HALF_WIDTH;
        return frustum.isVisible(relative.x - extentX, relative.y + portalBottom(draw) * halfHeight, relative.z - extentZ,
                relative.x + extentX, relative.y + MoonwoundPortalGeometry.QUAD_TOP * halfHeight, relative.z + extentZ);
    }

    private static float portalBottom(Draw draw) {
        float size = draw.portal.portalSize();
        double floor = draw.surface == null ? MoonwoundPortalGeometry.fogFloor(size)
                : (surfacePosition(draw.portal, draw.surface.landing()).y - draw.center.y)
                / MoonwoundPortalGeometry.halfHeight(size);
        return (float) Math.min(MoonwoundPortalGeometry.quadBottom(size), floor - 0.35);
    }

    private static void drawGeometry(Camera camera, float partialTicks, List<Draw> draws, boolean captureDepth) {
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = SHADER;
        Vec3 cameraPos = camera.getPosition();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        var time = shader.getUniform("Time");
        if (time != null) time.set(ClientEffectPolicy.moonwoundTime(
                ClientEffects.level(ClientEffectPolicy.Family.MOONWOUND),
                ((minecraft.level.getGameTime() % 24000) + partialTicks) / 20.0F));
        RenderSystem.enableDepthTest();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        // Pixelation owns a scene-depth copy. Record nearest visible geometry in that
        // scratch copy for the final full-resolution terrain test; never write world depth.
        RenderSystem.depthMask(captureDepth);
        GL11.glDepthMask(captureDepth);
        RenderSystem.disableCull();
        GL11.glDisable(GL11.GL_CULL_FACE);
        RenderSystem.enableBlend();
        GL11.glEnable(GL11.GL_BLEND);
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        RenderSystem.colorMask(true, true, true, true);
        GL11.glColorMask(true, true, true, true);
        RenderSystem.disableScissor();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        var portalCamera = shader.getUniform("PortalCamera");
        var portalBounds = shader.getUniform("PortalBounds");
        var fogFloor = shader.getUniform("FogFloor");
        var fogImpact = shader.getUniform("FogImpact");
        var surfaceCoverage = shader.getUniform("SurfaceCoverage");
        var surfacePass = shader.getUniform("SurfacePass");
        var openingAge = shader.getUniform("OpeningAge");
        try (ByteBufferBuilder memory = new ByteBufferBuilder(Math.max(4, draws.size() * 4) * FORMAT.getVertexSize())) {
            BufferBuilder wisps = null;
            for (Draw draw : draws) {
                if (draw.wisp != null) {
                    if (wisps == null) {
                        if (surfacePass != null) surfacePass.set(1);
                        wisps = new BufferBuilder(memory, VertexFormat.Mode.QUADS, FORMAT);
                    }
                    emitWisp(wisps, draw, cameraPos);
                    continue;
                }
                if (wisps != null) {
                    var mesh = wisps.build();
                    if (mesh != null) BufferUploader.drawWithShader(mesh);
                    wisps = null;
                }
                if (surfacePass != null) surfacePass.set(0);
                if (openingAge != null) openingAge.set(draw.openingAge);
                MoonwoundSnapshot portal = draw.portal;
                double halfWidth = MoonwoundPortalGeometry.halfWidth(portal.portalSize());
                double halfHeight = MoonwoundPortalGeometry.halfHeight(portal.portalSize());
                Vec3 eye = cameraPos.subtract(draw.center);
                float floor = (float) MoonwoundPortalGeometry.fogFloor(portal.portalSize());
                float impact = 0;
                if (draw.surface != null) {
                    Vec3 landing = surfacePosition(portal, draw.surface.landing()).subtract(draw.center);
                    floor = (float) (landing.y / halfHeight);
                    double horizontal = Math.hypot(eye.x, eye.z);
                    double rightX = horizontal > 1.0e-5 ? eye.z / horizontal : Math.cos(Math.toRadians(camera.getYRot()));
                    double rightZ = horizontal > 1.0e-5 ? -eye.x / horizontal : Math.sin(Math.toRadians(camera.getYRot()));
                    impact = (float) ((landing.x * rightX + landing.z * rightZ) / halfWidth);
                }
                float bottom = portalBottom(draw);
                if (portalCamera != null) {
                    // Yaw billboarding puts the eye on local X=0, facing the positive-Z side.
                    portalCamera.set(0f, (float) (eye.y / halfHeight),
                            (float) (Math.hypot(eye.x, eye.z) / halfWidth));
                }
                if (portalBounds != null) portalBounds.set(MoonwoundPortalGeometry.QUAD_HALF_WIDTH,
                        MoonwoundPortalGeometry.QUAD_TOP, bottom);
                if (fogFloor != null) fogFloor.set(floor);
                if (fogImpact != null) fogImpact.set(impact);
                if (surfaceCoverage != null) surfaceCoverage.set(draw.surface == null ? 0f : 1f);
                BufferBuilder vertices = new BufferBuilder(memory, VertexFormat.Mode.QUADS, FORMAT);
                emit(vertices, portal, camera, bottom);
                var mesh = vertices.build();
                // A separate draw keeps each portal's ray origin paired with its own geometry.
                if (mesh != null) BufferUploader.drawWithShader(mesh);
            }
            if (wisps != null) {
                var mesh = wisps.build();
                if (mesh != null) BufferUploader.drawWithShader(mesh);
            }
        }
    }

    private static boolean renderPixelated(Camera camera, float partialTicks, List<Draw> draws,
                                           MoonwoundPixelationScale.Grid grid) {
        if (pixelationFailed || PIXEL_RESOLVE == null || PIXEL_PRESENT == null) return false;
        int[] samplers = new int[4];
        for (int unit = 0; unit < samplers.length; unit++)
            samplers[unit] = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, unit);
        boolean separable = MoonwoundPixelationTarget.requiresSeparableResolve(grid.cellSize());
        try (var state = DensityFboPipeline.preserveRenderState();
             var target = PIXEL_TARGET.open(grid.width(), grid.height(), separable)) {
            if (target == null) {
                pixelationFailed = true;
                Shadowedhearts.LOGGER.warn("[Moonwound] Pixelation target unavailable; using smooth rendering until reload");
                return false;
            }
            RenderSystem.viewport(0, 0, target.width(), target.height());
            drawGeometry(camera, partialTicks, draws, true);
            RenderSystem.disableBlend();
            GL11.glDisable(GL11.GL_BLEND);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            GL11.glDepthFunc(GL11.GL_ALWAYS);
            for (int unit = 0; unit < samplers.length; unit++) GL33.glBindSampler(unit, 0);
            bindPixelShader(PIXEL_RESOLVE, target.sourceColor(), target.sourceDepth(), grid.cellSize());
            PIXEL_RESOLVE.safeGetUniform("SourceSize").set((float) target.width(), (float) target.height());
            PIXEL_RESOLVE.safeGetUniform("GridOrigin").set(grid.originX(), grid.originY());
            if (separable) {
                target.bindHorizontal();
                RenderSystem.viewport(0, 0, target.gridWidth(), target.height());
                PIXEL_RESOLVE.safeGetUniform("ResolvePass").set(1);
                drawFullscreen();
                bindPixelShader(PIXEL_RESOLVE, target.horizontalColor(), target.horizontalDepth(), grid.cellSize());
            }
            target.bindResolve();
            RenderSystem.viewport(0, 0, target.gridWidth(), target.gridHeight());
            PIXEL_RESOLVE.safeGetUniform("ResolvePass").set(separable ? 2 : 0);
            drawFullscreen();

            target.bindOutput();
            RenderSystem.viewport(target.viewportX(), target.viewportY(), target.width(), target.height());
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            GL11.glDepthMask(false);
            RenderSystem.enableBlend();
            GL11.glEnable(GL11.GL_BLEND);
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            bindPixelShader(PIXEL_PRESENT, target.pixelColor(), target.pixelDepth(), grid.cellSize());
            RenderSystem.setShaderTexture(2, target.sourceColor());
            RenderSystem.setShaderTexture(3, target.sourceDepth());
            PIXEL_PRESENT.setSampler("Sampler2", target.sourceColor());
            PIXEL_PRESENT.setSampler("Sampler3", target.sourceDepth());
            PIXEL_PRESENT.safeGetUniform("GridOrigin").set(target.viewportX() + grid.originX(), target.viewportY() + grid.originY());
            PIXEL_PRESENT.safeGetUniform("GridSize").set((float) grid.width(), (float) grid.height());
            PIXEL_PRESENT.safeGetUniform("ViewportOrigin").set((float) target.viewportX(), (float) target.viewportY());
            PIXEL_PRESENT.safeGetUniform("PixelBlend").set(grid.blend());
            drawFullscreen();
            return true;
        } catch (RuntimeException failure) {
            pixelationFailed = true;
            Shadowedhearts.LOGGER.warn("[Moonwound] Pixelation failed; using smooth rendering until reload", failure);
            return false;
        } finally {
            for (int unit = 0; unit < samplers.length; unit++) GL33.glBindSampler(unit, samplers[unit]);
        }
    }

    private static void bindPixelShader(ShaderInstance shader, int color, int depth, float pixelSize) {
        RenderSystem.setShaderTexture(0, color);
        RenderSystem.setShaderTexture(1, depth);
        shader.setSampler("Sampler0", color);
        shader.setSampler("Sampler1", depth);
        shader.safeGetUniform("PixelSize").set(pixelSize);
        RenderSystem.setShader(() -> shader);
    }

    private static void drawFullscreen() {
        try (ByteBufferBuilder memory = new ByteBufferBuilder(4 * PIXEL_FORMAT.getVertexSize())) {
            BufferBuilder vertices = new BufferBuilder(memory, VertexFormat.Mode.QUADS, PIXEL_FORMAT);
            vertices.addVertex(-1f, -1f, 0f);
            vertices.addVertex(1f, -1f, 0f);
            vertices.addVertex(1f, 1f, 0f);
            vertices.addVertex(-1f, 1f, 0f);
            var mesh = vertices.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        }
    }

    /** Called inside IrisWorldColorScope while Iris' own core-shader override is disabled. */
    public static void renderIris() {
        var iris = AuraReaderPulseRenderer.IRIS_HANDLER;
        Minecraft minecraft = Minecraft.getInstance();
        if (!hasActivePortals() || iris == null || !iris.isShaderPackInUse() || iris.isShadowRenderActive()) return;
        var snapshot = iris.getIrisRenderingSnapshot();
        if (snapshot == null || snapshot.depthTexture <= 0 || snapshot.renderWidth <= 0 || snapshot.renderHeight <= 0
                || !snapshot.projectionMatrix.isFinite() || !snapshot.modelViewMatrix.isFinite()) return;
        try (var state = DensityFboPipeline.preserveRenderState();
             var target = IrisScaledWorldTarget.open(snapshot.renderWidth, snapshot.renderHeight, snapshot.depthTexture)) {
            if (target == null) return;
            RenderSystem.viewport(0, 0, snapshot.renderWidth, snapshot.renderHeight);
            GL11.glViewport(0, 0, snapshot.renderWidth, snapshot.renderHeight);
            RenderSystem.setProjectionMatrix(snapshot.projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.getModelViewMatrix().set(snapshot.modelViewMatrix);
            Camera camera = minecraft.gameRenderer.getMainCamera();
            render(camera, camera.getPartialTickTime());
            target.present();
        }
    }

    private static Vec3 portalPosition(MoonwoundSnapshot snapshot) {
        return new Vec3(snapshot.center().getX() + 0.5, snapshot.portalY(), snapshot.center().getZ() + 0.5);
    }

    private static Vec3 surfacePosition(MoonwoundSnapshot portal, MoonwoundSurfaceFlow.Point point) {
        return new Vec3(portal.center().getX() + point.x(), portal.center().getY() + point.y(),
                portal.center().getZ() + point.z());
    }

    private static void emitWisp(BufferBuilder vertices, Draw draw, Vec3 cameraPos) {
        Vec3 normal = cameraPos.subtract(draw.center).normalize();
        Vec3 right = new Vec3(normal.z, 0, -normal.x);
        right = right.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = normal.cross(right);
        up = up.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : up.normalize();
        Vec3 relative = draw.center.subtract(cameraPos);
        double radius = draw.wisp.radius();
        int red = draw.portal.phase() == Phase.OVERLOAD ? 255 : 178;
        int phase = (int) Math.round(draw.wisp.phase() / (Math.PI * 2) * 255) & 255;
        int alpha = (int) Math.round(Math.clamp(draw.wisp.alpha(), 0, 1) * 255);
        wispVertex(vertices, relative, right, up, -radius, -radius, 0, 1, red, phase, alpha);
        wispVertex(vertices, relative, right, up, radius, -radius, 1, 1, red, phase, alpha);
        wispVertex(vertices, relative, right, up, radius, radius, 1, 0, red, phase, alpha);
        wispVertex(vertices, relative, right, up, -radius, radius, 0, 0, red, phase, alpha);
    }

    private static void wispVertex(BufferBuilder vertices, Vec3 center, Vec3 right, Vec3 up,
                                   double x, double y, float u, float v, int red, int phase, int alpha) {
        Vec3 position = center.add(right.scale(x)).add(up.scale(y));
        vertices.addVertex((float) position.x, (float) position.y, (float) position.z);
        vertices.setUv(u, v);
        vertices.setColor(red, phase, 255, alpha);
    }

    private static void emit(BufferBuilder vertices, MoonwoundSnapshot portal, Camera camera, float bottom) {
        Vec3 center = portalPosition(portal);
        Vec3 relative = center.subtract(camera.getPosition());
        double dx = camera.getPosition().x - center.x;
        double dz = camera.getPosition().z - center.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        // Looking vertically down/up still yields a finite, stable vertical plane.
        double rightX = length > 1.0e-5 ? dz / length : Math.cos(Math.toRadians(camera.getYRot()));
        double rightZ = length > 1.0e-5 ? -dx / length : Math.sin(Math.toRadians(camera.getYRot()));
        double halfWidth = MoonwoundPortalGeometry.halfWidth(portal.portalSize());
        double halfHeight = MoonwoundPortalGeometry.halfHeight(portal.portalSize());
        float left = -MoonwoundPortalGeometry.QUAD_HALF_WIDTH;
        float right = MoonwoundPortalGeometry.QUAD_HALF_WIDTH;
        float top = MoonwoundPortalGeometry.QUAD_TOP;
        boolean overload = portal.phase() == Phase.OVERLOAD;
        int red = overload ? 255 : 178;
        int green = overload ? 112 : 124;
        // Extend UVs with the padded vertices: the original opening retains its world size.
        vertex(vertices, relative, rightX, rightZ, left * halfWidth, bottom * halfHeight, (left + 1) * 0.5f, (1 - bottom) * 0.5f, red, green);
        vertex(vertices, relative, rightX, rightZ, right * halfWidth, bottom * halfHeight, (right + 1) * 0.5f, (1 - bottom) * 0.5f, red, green);
        vertex(vertices, relative, rightX, rightZ, right * halfWidth, top * halfHeight, (right + 1) * 0.5f, (1 - top) * 0.5f, red, green);
        vertex(vertices, relative, rightX, rightZ, left * halfWidth, top * halfHeight, (left + 1) * 0.5f, (1 - top) * 0.5f, red, green);
    }

    private static void vertex(BufferBuilder vertices, Vec3 center, double rightX, double rightZ,
                               double x, double y, float u, float v, int red, int green) {
        vertices.addVertex((float) (center.x + x * rightX), (float) (center.y + y), (float) (center.z + x * rightZ));
        vertices.setUv(u, v);
        vertices.setColor(red, green, 255, 255);
    }
}
