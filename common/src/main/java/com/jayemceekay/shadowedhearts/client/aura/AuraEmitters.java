package com.jayemceekay.shadowedhearts.client.aura;

import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.util.math.QuaternionUtilsKt;
import com.jayemceekay.shadowedhearts.SHAspects;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.AuraRenderTypes;
import com.jayemceekay.shadowedhearts.client.render.geom.CylinderBuffers;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.AuraLifecyclePacket;
import com.jayemceekay.shadowedhearts.network.AuraStatePacket;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuraEmitter system that attaches an aura instance to a Pokémon when it is sent out.
 * Rendering is driven by this system, not by the Pokémon's own renderer.
 */
public final class AuraEmitters {
    public static MultiBufferSource.BufferSource buffersSummary = MultiBufferSource.immediate(new ByteBufferBuilder(786432));
    public static MultiBufferSource.BufferSource buffersPC = MultiBufferSource.immediate(new ByteBufferBuilder(786432));
    public static MultiBufferSource.BufferSource buffersPurification = MultiBufferSource.immediate(new ByteBufferBuilder(786432));
    public static MultiBufferSource.BufferSource buffersOverworld = MultiBufferSource.immediate(new ByteBufferBuilder(786432));

    private AuraEmitters() {
    }

    private static final Map<Integer, AuraInstance> ACTIVE = new ConcurrentHashMap<>();


    /**
     * Called by a networking handler when a state update arrives.
     */
    public static void receiveState(AuraStatePacket pkt) {
        if (!ShadowedHeartsConfigs.getInstance().getClientConfig().enableShadowAura()) return;
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        AuraInstance inst = ACTIVE.getOrDefault(pkt.getEntityId(), null);
        if (inst == null) {
            return;
        }
        // ID reuse guard: ensure the entity UUID matches the instance's UUID; otherwise, ignore this state
        Entity cur = mc.level.getEntity(pkt.getEntityId());
        UUID curUuid = (cur != null) ? cur.getUUID() : null;
        if (inst.entityUuid != null && curUuid != null && !inst.entityUuid.equals(curUuid)) {
            return;
        }
        // Enforce ordering by server tick; ignore stale or duplicate packets
        if (pkt.getServerTick() <= inst.lastServerTick) {
            return;
        }
        inst.lastServerTick = pkt.getServerTick();
        // Update server-authoritative transform and bounding box
        // Teleport/jump snap: if movement between last and new is too large, snap to new to avoid long lerps
        double ddx = pkt.getX() - inst.x;
        double ddy = pkt.getY() - inst.y;
        double ddz = pkt.getZ() - inst.z;
        double dist2 = ddx * ddx + ddy * ddy + ddz * ddz;
        if (dist2 > TELEPORT_SNAP_DIST2) {
            inst.lastX = pkt.getX();
            inst.lastY = pkt.getY();
            inst.lastZ = pkt.getZ();
        } else {
            inst.lastX = inst.x;
            inst.lastY = inst.y;
            inst.lastZ = inst.z;
        }
        inst.x = pkt.getX();
        inst.y = pkt.getY();
        inst.z = pkt.getZ();
        inst.lastDeltaX = pkt.getDx();
        inst.lastDeltaY = pkt.getDy();
        inst.lastDeltaZ = pkt.getDz();
        // Smooth bbox and corruption by tracking previous values for interpolation
        inst.prevBbW = inst.lastBbW;
        inst.prevBbH = inst.lastBbH;
        inst.prevBbSize = inst.lastBbSize;
        inst.prevCorruption = inst.lastCorruption;
        inst.lastBbW = pkt.getBbw();
        inst.lastBbH = pkt.getBbh();
        inst.lastBbSize = pkt.getBbs();
        inst.lastCorruption = pkt.getCorruption();
    }

    /**
     * Called by networking handler when a lifecycle update arrives.
     */
    public static void receiveLifecycle(AuraLifecyclePacket pkt) {
        if (!ShadowedHeartsConfigs.getInstance().getClientConfig().enableShadowAura()) return;
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        long now = mc.level.getGameTime();
        switch (pkt.getAction()) {
            case START -> {
                // Replace any existing instance for this entity ID (handles rapid entity ID reuse on recall/swap)
                Entity ent = mc.level.getEntity(pkt.getEntityId());
                UUID newUuid = (ent != null) ? ent.getUUID() : null;
                if (newUuid != null) {
                    for (Map.Entry<Integer, AuraInstance> e : ACTIVE.entrySet()) {
                        AuraInstance ai = e.getValue();
                        if (ai != null && newUuid.equals(ai.entityUuid) && e.getKey() != pkt.getEntityId()) {
                            ACTIVE.remove(e.getKey());
                        }
                    }
                }
                ACTIVE.put(pkt.getEntityId(), new AuraInstance(pkt.getEntityId(), ent, now, FADE_IN, (pkt.getSustainOverride() > 0) ? pkt.getSustainOverride() : SUSTAIN, FADE_OUT, pkt.getX(), pkt.getY(), pkt.getZ(), pkt.getDx(), pkt.getDy(), pkt.getDz(), pkt.getBbw(), pkt.getBbh() * pkt.getHeightMultiplier(), pkt.getBbs(), pkt.getCorruption()));
            }
            case FADE_OUT -> {
                ACTIVE.computeIfPresent(pkt.getEntityId(), (id, inst) -> {
                    inst.beginImmediateFadeOut(now, Math.max(1, pkt.getOutTicks()));
                    return inst;
                });
            }
            default -> {
            }
        }
    }

    // Timings (ticks): ~0.25s fade-in, ~5s sustain, ~0.5s fade-out
    private static final int FADE_IN = 10;
    private static final int SUSTAIN = 60; // can be tuned; original recent value ~90-100
    private static final int FADE_OUT = 10;
    // If a position update jumps farther than this squared distance, snap to avoid long lerp streaks
    private static final double TELEPORT_SNAP_DIST2 = 36.0; // 6 blocks squared

    public static void init() {
        // Server is authoritative for aura lifecycle now. Client no longer subscribes to Cobblemon send/recall events.
    }

    public static void onPokemonDespawn(int entityId) {
        // Start a quick fade-out if we still have an instance; if missing, nothing to do
        var mc = Minecraft.getInstance();
        long now = (mc != null && mc.level != null) ? mc.level.getGameTime() : 0L;
        ACTIVE.computeIfPresent(entityId, (id, inst) -> {
            inst.stopSound();
            inst.beginImmediateFadeOut(now, 10);
            return inst;
        });
    }

    /**
     * Lightweight GUI render path for preview models (Summary screen).
     * Renders a cylinder aura using the current GUI matrices without relying on world depth.
     * This does not use entity/world velocity; visuals are static/ambient for UI.
     */
    public static void renderInSummaryGUI(GuiGraphics context,
                                          MultiBufferSource bufferSource,
                                          float corruption,
                                          float partialTicks,
                                          RenderablePokemon pokemon,
                                          ModelWidget widget) {
        PoseStack matrices = context.pose();
        matrices.pushPose();

        matrices.scale(20f, 20f, -20f);
        var pokemonModel = VaryingModelRepository.INSTANCE.getPoser(pokemon.getSpecies().resourceIdentifier, widget.getState());
        matrices.translate(
                pokemonModel.getProfileTranslation().x,
                pokemonModel.getProfileTranslation().y + 1.5 * pokemonModel.getProfileScale(),
                pokemonModel.getProfileTranslation().z - 4.0
        );
        matrices.scale(pokemonModel.getProfileScale(), pokemonModel.getProfileScale(), 1/pokemonModel.getProfileScale());
        Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(13F, 325F, 0F));
        matrices.mulPose(rotation);
        matrices.mulPose(new Quaternionf().rotateLocalZ(Mth.PI));

        float radius = (float) pokemon.getForm().getHitbox().makeBoundingBox(new Vec3(0, 0, 0)).getSize();
        float halfHeight = (float) pokemon.getForm().getHitbox().makeBoundingBox(new Vec3(0.0, 0.0, 0.0)).getYsize();

        boolean useXd = false;
        var shader = useXd ? ModShaders.SHADOW_AURA_XD_CYLINDER : ModShaders.SHADOW_AURA_FOG_CYLINDER;
        if (shader == null) return;

        // Pull the active GUI matrices and build model-related matrices from the current pose.
        Matrix4f view = RenderSystem.getModelViewMatrix();
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        Matrix4f model = new Matrix4f(matrices.last().pose());
        model.translate(0f, radius / 2f, 0f);
        Matrix4f invModel = new Matrix4f(model).invert();
        Matrix4f mvp = new Matrix4f(proj).mul(view).mul(model);

        float timeVal = (Minecraft.getInstance() != null)
                ? (((Minecraft.getInstance().level != null)
                ? (Minecraft.getInstance().level.getGameTime() + partialTicks)
                : (Minecraft.getInstance().gui.getGuiTicks() + partialTicks)) * 0.05f)
                : 0f;

        var uu = useXd ? ModShaders.SHADOW_AURA_XD_CYLINDER_UNIFORMS : ModShaders.SHADOW_AURA_FOG_CYLINDER_UNIFORMS;
        if (uu != null) {
            if (uu.uView() != null) uu.uView().set(view);
            if (uu.uProj() != null) uu.uProj().set(proj);
            if (uu.uModel() != null) uu.uModel().set(model);
            if (uu.uInvModel() != null) uu.uInvModel().set(invModel);
            if (uu.uMVP() != null) uu.uMVP().set(mvp);

            // do not ask why its 100f, 100f, 0f cameraPosWS....it kinda works and i'm too lazy to fix it
            //if (uu.uCameraPosWS() != null) uu.uCameraPosWS().set(0f,0f, 0f);
            if (uu.uCameraPosWS() != null)
                uu.uCameraPosWS().set(widget.getX() + widget.getWidth() / 2.0f, Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2.0f, 100f);
            if (uu.uEntityPosWS() != null)
                uu.uEntityPosWS().set((float) (0f), (float) (0f), (float) (0f));
            if (uu.uEntityVelWS() != null) uu.uEntityVelWS().set(0f, 0f, 0f);
            if (uu.uVelLagWS() != null) uu.uVelLagWS().set(0f, 0f, 0f);
            if (uu.uSpeed() != null) uu.uSpeed().set(0f);

            if (uu.uProxyRadius() != null)
                uu.uProxyRadius().set(radius);
            if (uu.uProxyHalfHeight() != null)
                uu.uProxyHalfHeight().set(halfHeight * 0.5f);
            if (uu.uAuraFade() != null)
                uu.uAuraFade().set(0.7f * corruption);
            if (uu.uDensity() != null)
                uu.uDensity().set(radius * (useXd ? 2.5f : 1.0f));
            if (uu.uMaxThickness() != null)
                uu.uMaxThickness().set(radius * (useXd ? 0.25f : 0.65f));
            if (uu.uLimbSoft() != null) uu.uLimbSoft().set(0.22f);
            if (uu.uMinPathNorm() != null) uu.uMinPathNorm().set(.15f);
            if (uu.uCorePow() != null) uu.uCorePow().set(useXd ? 2.0f : 4f);
            if (uu.uGlowGamma() != null)
                uu.uGlowGamma().set(useXd ? 1.2f : 0.5f);
            if (uu.uRimPower() != null)
                uu.uRimPower().set(useXd ? 3.5f : 0.05f);
            if (uu.uRimStrength() != null)
                uu.uRimStrength().set(useXd ? 2.5f : 5.5f);
            if (uu.uPixelsPerRadius() != null)
                uu.uPixelsPerRadius().set(24.0f);
            if (uu.uPosterizeSteps() != null)
                uu.uPosterizeSteps().set(useXd ? 0.0f : 3.0f);
            if (uu.uPatchSharpness() != null)
                uu.uPatchSharpness().set(useXd ? 0.08f : 0.25f);
            if (uu.uPatchGamma() != null) uu.uPatchGamma().set(0.65f);
            if (uu.uPatchThreshTop() != null)
                uu.uPatchThreshTop().set(useXd ? 0.65f : 0.80f);
            if (uu.uPatchThreshBase() != null)
                uu.uPatchThreshBase().set(useXd ? 0.35f : 0.30f);
            if (uu.uPatchScaleRel() != null)
                uu.uPatchScaleRel().set(useXd ? 5.5f : 6.5f);
            if (uu.uScrollSpeedRel() != null)
                uu.uScrollSpeedRel().set(useXd ? 1.8f : -1.0f);
            if (uu.uWarpAmp() != null)
                uu.uWarpAmp().set(useXd ? 0.35f : 0.075f);
            if (uu.uNoiseScaleRel() != null)
                uu.uNoiseScaleRel().set(useXd ? 8.0f : 3.0f);

            // If this Pokémon is in Hyper Mode, shift the aura highlight color to magenta.
            boolean isHyper = pokemon != null && pokemon.getAspects() != null && pokemon.getAspects().contains(SHAspects.HYPER_MODE);
            if (isHyper) {
                if (uu.uColorB() != null) uu.uColorB().set(1.30f, 0.30f, 0.85f);
            } else {
                if (uu.uColorB() != null) uu.uColorB().set(0.85f, 0.30f, 1.30f);
            }

            // Noise parameters are not exposed in the cached uniform set for the cylinder variant
            if (uu.uTime() != null) uu.uTime().set(timeVal);
        } else {
            // Fallback path without uniform cache
            setMat4(shader, "uView", view);
            setMat4(shader, "uProj", proj);
            setMat4(shader, "uModel", model);
            setMat4(shader, "uInvModel", invModel);
            shader.safeGetUniform("uMVP").set(mvp);
            setVec3(shader, "uCameraPosWS", 0f, 0f, 0f);
            setVec3(shader, "uEntityPosWS", 0f, 0f, 0f);
            setVec3(shader, "uEntityVelWS", 0f, 0f, 0f);
            setVec3(shader, "uVelLagWS", 0f, 0f, 0f);
            set1f(shader, "uSpeed", 0f);
            set1f(shader, "uProxyRadius", radius);
            set1f(shader, "uProxyHalfHeight", halfHeight);
            set1f(shader, "uAuraFade", Math.max(0f, Math.min(1f, corruption)) * 0.8f);
            set1f(shader, "uDensity", radius);
            set1f(shader, "uMaxThickness", radius * 0.65f);
            set1f(shader, "uThicknessFeather", 0f);
            set1f(shader, "uEdgeKill", 0f);
            set1f(shader, "uLimbSoft", 0.22f);
            set1f(shader, "uLimbHardness", 2.25f);
            set1f(shader, "uMinPathNorm", 0.15f);
            set1f(shader, "uHeightFadePow", 1.25f);
            set1f(shader, "uHeightFadeMin", -0.25f);
            set1f(shader, "uPixelsPerRadius", 20f);
            set1f(shader, "uPosterizeSteps", 3f);
            set1f(shader, "uPatchSharpness", 0.6f);
            // Noise parameters not available in this path
            set1f(shader, "uTime", timeVal);

            // Hyper Mode highlight color override
            boolean isHyper = pokemon != null && pokemon.getAspects() != null && pokemon.getAspects().contains(SHAspects.HYPER_MODE);
            if (isHyper) {
                setVec3(shader, "uColorB", 0.85f, 0.30f, 1.30f);
            }
        }

        VertexConsumer vc = buffersSummary.getBuffer(useXd ? AuraRenderTypes.shadow_xd() : AuraRenderTypes.shadow_fog());
        // Build a scaled model matrix for the unit cylinder and render with low LOD for UI
        Matrix4f mat = new Matrix4f();
        mat.scale(radius, halfHeight * 1.5f, radius);
        //mat.rotate(new Quaternionf().rotateX(Mth.PI));
        CylinderBuffers.drawCylinderWithDomesLod(vc, mat, 1f, 0f, 0f, 0f, 0f, 0);
        //drawAxes(matrices, buffers, Math.max(0.05f, radius), 220);
        buffersSummary.endLastBatch();
        matrices.popPose();
    }

    public static void renderInPcGUI(GuiGraphics context,
                                     MultiBufferSource bufferSource,
                                     float corruption,
                                     float partialTicks,
                                     RenderablePokemon pokemon,
                                     ModelWidget widget) {
        PoseStack matrices = context.pose();
        matrices.pushPose();
        matrices.scale(20f, 20f, -20f);
        var pokemonModel = VaryingModelRepository.INSTANCE.getPoser(pokemon.getSpecies().resourceIdentifier, widget.getState());
        matrices.translate(
                pokemonModel.getProfileTranslation().x,
                pokemonModel.getProfileTranslation().y + 1.5 * pokemonModel.getProfileScale(),
                pokemonModel.getProfileTranslation().z - 20.0
        );
        matrices.scale(pokemonModel.getProfileScale(), pokemonModel.getProfileScale(), 1/pokemonModel.getProfileScale());
        Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(13F, 325F, 0F));
        matrices.mulPose(rotation);
        matrices.mulPose(new Quaternionf().rotateLocalZ(Mth.PI));
        float radius = (float) pokemon.getForm().getHitbox().makeBoundingBox(new Vec3(0.0, 0.0, 0.0)).getSize();
        float halfHeight = (float) pokemon.getForm().getHitbox().makeBoundingBox(new Vec3(0.0, 0.0, 0.0)).getYsize();
        boolean useXd = false;
        //ClientConfig.get().useXdAura;
        var shader = useXd ? ModShaders.SHADOW_AURA_XD_CYLINDER : ModShaders.SHADOW_AURA_FOG_CYLINDER;
        if (shader == null) return;

        // Pull the active GUI matrices and build model-related matrices from the current pose.
        Matrix4f view = RenderSystem.getModelViewMatrix();
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        Matrix4f model = new Matrix4f(matrices.last().pose());
        model.translate(0f, radius / 2f, 0f);
        Matrix4f invModel = new Matrix4f(model).invert();
        Matrix4f mvp = new Matrix4f(proj).mul(view).mul(model);

        float timeVal = (Minecraft.getInstance() != null)
                ? (((Minecraft.getInstance().level != null)
                ? (Minecraft.getInstance().level.getGameTime() + partialTicks)
                : (Minecraft.getInstance().gui.getGuiTicks() + partialTicks)) * 0.05f)
                : 0f;

        var uu = useXd ? ModShaders.SHADOW_AURA_XD_CYLINDER_UNIFORMS : ModShaders.SHADOW_AURA_FOG_CYLINDER_UNIFORMS;
        if (uu != null) {
            if (uu.uView() != null) uu.uView().set(view);
            if (uu.uProj() != null) uu.uProj().set(proj);
            if (uu.uModel() != null) uu.uModel().set(model);
            if (uu.uInvModel() != null) uu.uInvModel().set(invModel);
            if (uu.uMVP() != null) uu.uMVP().set(mvp);

            // do not ask why its 100f, 100f, 0f cameraPosWS....it kinda works and i'm too lazy to fix it
            if (uu.uCameraPosWS() != null)
                uu.uCameraPosWS().set(widget.getX() + widget.getWidth() / 2.0f, widget.getY() + widget.getHeight() / 2.0f, 0f);
            if (uu.uEntityPosWS() != null)
                uu.uEntityPosWS().set((float) (0f), (float) (0f), (float) (0f));
            if (uu.uEntityVelWS() != null) uu.uEntityVelWS().set(0f, 0f, 0f);
            if (uu.uVelLagWS() != null) uu.uVelLagWS().set(0f, 0f, 0f);
            if (uu.uSpeed() != null) uu.uSpeed().set(0f);

            if (uu.uExpand() != null)
                uu.uExpand().set(1f);
            if (uu.uProxyRadius() != null)
                uu.uProxyRadius().set(radius);
            if (uu.uProxyHalfHeight() != null)
                uu.uProxyHalfHeight().set(halfHeight * 0.5f);
            if (uu.uAuraFade() != null)
                uu.uAuraFade().set(0.7f * corruption);
            if (uu.uDensity() != null)
                uu.uDensity().set(radius * (useXd ? 2.5f : 1.0f));
            if (uu.uMaxThickness() != null)
                uu.uMaxThickness().set(radius * (useXd ? 0.25f : 0.65f));
            if (uu.uLimbSoft() != null) uu.uLimbSoft().set(0.22f);
            if (uu.uLimbHardness() != null)
                uu.uLimbHardness().set(2.25f);
            if (uu.uMinPathNorm() != null) uu.uMinPathNorm().set(.15f);
            if (uu.uCorePow() != null) uu.uCorePow().set(useXd ? 2.0f : 4f);
            if (uu.uGlowGamma() != null)
                uu.uGlowGamma().set(useXd ? 1.2f : 0.5f);
            if (uu.uRimPower() != null)
                uu.uRimPower().set(useXd ? 3.5f : 0.05f);
            if (uu.uRimStrength() != null)
                uu.uRimStrength().set(useXd ? 2.5f : 5.5f);
            if (uu.uPixelsPerRadius() != null)
                uu.uPixelsPerRadius().set(24.0f);
            if (uu.uPosterizeSteps() != null)
                uu.uPosterizeSteps().set(useXd ? 0.0f : 3.0f);
            if (uu.uPatchSharpness() != null)
                uu.uPatchSharpness().set(useXd ? 0.08f : 0.25f);
            if (uu.uPatchGamma() != null) uu.uPatchGamma().set(0.65f);
            if (uu.uPatchThreshTop() != null)
                uu.uPatchThreshTop().set(useXd ? 0.65f : 0.80f);
            if (uu.uPatchThreshBase() != null)
                uu.uPatchThreshBase().set(useXd ? 0.35f : 0.30f);
            if (uu.uPatchScaleRel() != null)
                uu.uPatchScaleRel().set(useXd ? 5.5f : 6.5f);
            if (uu.uScrollSpeedRel() != null)
                uu.uScrollSpeedRel().set(useXd ? 1.8f : -1.0f);
            if (uu.uWarpAmp() != null)
                uu.uWarpAmp().set(useXd ? 0.35f : 0.075f);
            if (uu.uNoiseScaleRel() != null)
                uu.uNoiseScaleRel().set(useXd ? 8.0f : 3.0f);

            // If this Pokémon is in Hyper Mode, shift the aura highlight color to magenta.
            boolean isHyper = pokemon != null && pokemon.getAspects() != null && pokemon.getAspects().contains(SHAspects.HYPER_MODE);
            if (isHyper) {
                if (uu.uColorB() != null) uu.uColorB().set(1.30f, 0.30f, 0.85f);
            } else {
                if (uu.uColorB() != null) uu.uColorB().set(0.85f, 0.30f, 1.30f);
            }

            // Noise parameters are not exposed in the cached uniform set for the cylinder variant
            if (uu.uTime() != null) uu.uTime().set(timeVal);
        } else {
            // Fallback path without uniform cache
            setMat4(shader, "uView", view);
            setMat4(shader, "uProj", proj);
            setMat4(shader, "uModel", model);
            setMat4(shader, "uInvModel", invModel);
            shader.safeGetUniform("uMVP").set(mvp);
            setVec3(shader, "uCameraPosWS", 0f, 0f, 0f);
            setVec3(shader, "uEntityPosWS", 0f, 0f, 0f);
            setVec3(shader, "uEntityVelWS", 0f, 0f, 0f);
            setVec3(shader, "uVelLagWS", 0f, 0f, 0f);
            set1f(shader, "uSpeed", 0f);
            set1f(shader, "uProxyRadius", radius);
            set1f(shader, "uProxyHalfHeight", halfHeight);
            set1f(shader, "uAuraFade", Math.max(0f, Math.min(1f, corruption)) * 0.8f);
            set1f(shader, "uDensity", radius);
            set1f(shader, "uMaxThickness", radius * 0.65f);
            set1f(shader, "uThicknessFeather", 0f);
            set1f(shader, "uEdgeKill", 0f);
            set1f(shader, "uLimbSoft", 0.22f);
            set1f(shader, "uLimbHardness", 2.25f);
            set1f(shader, "uMinPathNorm", 0.15f);
            set1f(shader, "uHeightFadePow", 1.25f);
            set1f(shader, "uHeightFadeMin", -0.25f);
            set1f(shader, "uPixelsPerRadius", 20f);
            set1f(shader, "uPosterizeSteps", 3f);
            set1f(shader, "uPatchSharpness", 0.6f);
            // Noise parameters not available in this path
            set1f(shader, "uTime", timeVal);

            // Hyper Mode highlight color override
            boolean isHyper = pokemon != null && pokemon.getAspects() != null && pokemon.getAspects().contains(SHAspects.HYPER_MODE);
            if (isHyper) {
                setVec3(shader, "uColorB", 0.85f, 0.30f, 1.30f);
            }
        }

        VertexConsumer vc = buffersPC.getBuffer(useXd ? AuraRenderTypes.shadow_xd() : AuraRenderTypes.shadow_fog());
        // Build a scaled model matrix for the unit cylinder and render with low LOD for UI
        Matrix4f mat = new Matrix4f();
        mat.scale(radius, halfHeight * 1.5f, radius);
        CylinderBuffers.drawCylinderWithDomesLod(vc, mat, 1f, 0f, 0f, 0f, 0f, 0);
        //drawAxes(matrices, buffers, Math.max(0.05f, radius), 220);
        buffersPC.endLastBatch();
        matrices.popPose();
    }

    public static void renderInPurificationGUI(GuiGraphics context,
                                               PoseStack matrices,
                                               MultiBufferSource bufferSource,
                                               float corruption,
                                               float partialTicks,
                                               RenderablePokemon pokemon,
                                               com.cobblemon.mod.common.client.render.models.blockbench.PosableState state,
                                               float x, float y, float width, float height) {
        matrices.pushPose();
        matrices.scale(20f, 20f, -20f);
        var pokemonModel = VaryingModelRepository.INSTANCE.getPoser(pokemon.getSpecies().resourceIdentifier, state);
        matrices.translate(
                pokemonModel.getProfileTranslation().x,
                pokemonModel.getProfileTranslation().y + 1.5 * pokemonModel.getProfileScale(),
                pokemonModel.getProfileTranslation().z - 20.0
        );
        matrices.scale(pokemonModel.getProfileScale(), pokemonModel.getProfileScale(), 1/pokemonModel.getProfileScale());
        Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(13F, 325F, 0F));
        matrices.mulPose(rotation);
        matrices.mulPose(new Quaternionf().rotateLocalZ(Mth.PI));
        float radius = (float) pokemon.getForm().getHitbox().makeBoundingBox(new Vec3(0, 0, 0)).getSize();
        float halfHeight = (float) pokemon.getForm().getHitbox().makeBoundingBox(new Vec3(0.0, 0.0, 0.0)).getYsize();

        boolean useXd = false;
        //ClientConfig.get().useXdAura;
        var shader = useXd ? ModShaders.SHADOW_AURA_XD_CYLINDER : ModShaders.SHADOW_AURA_FOG_CYLINDER;
        if (shader == null) return;

        // Pull the active GUI matrices and build model-related matrices from the current pose.
        Matrix4f view = RenderSystem.getModelViewMatrix();
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        Matrix4f model = new Matrix4f(matrices.last().pose());
        model.translate(0f, radius / 2f, 0f);
        Matrix4f invModel = new Matrix4f(model).invert();
        Matrix4f mvp = new Matrix4f(proj).mul(view).mul(model);

        float timeVal = (Minecraft.getInstance() != null)
                ? (((Minecraft.getInstance().level != null)
                ? (Minecraft.getInstance().level.getGameTime() + partialTicks)
                : (Minecraft.getInstance().gui.getGuiTicks() + partialTicks)) * 0.05f)
                : 0f;

        var uu = useXd ? ModShaders.SHADOW_AURA_XD_CYLINDER_UNIFORMS : ModShaders.SHADOW_AURA_FOG_CYLINDER_UNIFORMS;
        if (uu != null) {
            if (uu.uView() != null) uu.uView().set(view);
            if (uu.uProj() != null) uu.uProj().set(proj);
            if (uu.uModel() != null) uu.uModel().set(model);
            if (uu.uInvModel() != null) uu.uInvModel().set(invModel);
            if (uu.uMVP() != null) uu.uMVP().set(mvp);
            if (uu.uCameraPosWS() != null)
                uu.uCameraPosWS().set(x + (width / 2.0f), y + (height / 2.0f), 0f);
            if (uu.uEntityPosWS() != null)
                uu.uEntityPosWS().set((float) (0f), (float) (0f), (float) (0f));
            if (uu.uEntityVelWS() != null) uu.uEntityVelWS().set(0f, 0f, 0f);
            if (uu.uVelLagWS() != null) uu.uVelLagWS().set(0f, 0f, 0f);
            if (uu.uSpeed() != null) uu.uSpeed().set(0f);

            if (uu.uExpand() != null)
                uu.uExpand().set(1f);
            if (uu.uProxyRadius() != null)
                uu.uProxyRadius().set(radius);
            if (uu.uProxyHalfHeight() != null)
                uu.uProxyHalfHeight().set(halfHeight * 0.5f);
            if (uu.uAuraFade() != null)
                uu.uAuraFade().set(0.7f * corruption);
            if (uu.uDensity() != null)
                uu.uDensity().set(radius * (useXd ? 2.5f : 1.0f));
            if (uu.uMaxThickness() != null)
                uu.uMaxThickness().set(radius * (useXd ? 0.25f : 0.65f));
            if (uu.uLimbSoft() != null) uu.uLimbSoft().set(0.22f);
            if (uu.uLimbHardness() != null)
                uu.uLimbHardness().set(2.25f);
            if (uu.uMinPathNorm() != null) uu.uMinPathNorm().set(.15f);
            if (uu.uCorePow() != null) uu.uCorePow().set(useXd ? 2.0f : 4f);
            if (uu.uGlowGamma() != null)
                uu.uGlowGamma().set(useXd ? 1.2f : 0.5f);
            if (uu.uRimPower() != null)
                uu.uRimPower().set(useXd ? 3.5f : 0.05f);
            if (uu.uRimStrength() != null)
                uu.uRimStrength().set(useXd ? 2.5f : 5.5f);
            if (uu.uPixelsPerRadius() != null)
                uu.uPixelsPerRadius().set(24.0f);
            if (uu.uPosterizeSteps() != null)
                uu.uPosterizeSteps().set(useXd ? 0.0f : 3.0f);
            if (uu.uPatchSharpness() != null)
                uu.uPatchSharpness().set(useXd ? 0.08f : 0.25f);
            if (uu.uPatchGamma() != null) uu.uPatchGamma().set(0.65f);
            if (uu.uPatchThreshTop() != null)
                uu.uPatchThreshTop().set(useXd ? 0.65f : 0.80f);
            if (uu.uPatchThreshBase() != null)
                uu.uPatchThreshBase().set(useXd ? 0.35f : 0.30f);
            if (uu.uPatchScaleRel() != null)
                uu.uPatchScaleRel().set(useXd ? 5.5f : 6.5f);
            if (uu.uScrollSpeedRel() != null)
                uu.uScrollSpeedRel().set(useXd ? 1.8f : -1.0f);
            if (uu.uWarpAmp() != null)
                uu.uWarpAmp().set(useXd ? 0.35f : 0.075f);
            if (uu.uNoiseScaleRel() != null)
                uu.uNoiseScaleRel().set(useXd ? 8.0f : 3.0f);

            // If this Pokémon is in Hyper Mode, shift the aura highlight color to magenta.
            boolean isHyper = pokemon != null && pokemon.getAspects() != null && pokemon.getAspects().contains(SHAspects.HYPER_MODE);
            if (isHyper) {
                if (uu.uColorB() != null) uu.uColorB().set(1.30f, 0.30f, 0.85f);
            } else {
                if (uu.uColorB() != null) uu.uColorB().set(0.85f, 0.30f, 1.30f);
            }

            // Noise parameters are not exposed in the cached uniform set for the cylinder variant
            if (uu.uTime() != null) uu.uTime().set(timeVal);
        } else {
            // Fallback path without uniform cache
            setMat4(shader, "uView", view);
            setMat4(shader, "uProj", proj);
            setMat4(shader, "uModel", model);
            setMat4(shader, "uInvModel", invModel);
            shader.safeGetUniform("uMVP").set(mvp);
            setVec3(shader, "uCameraPosWS", 0f, 0f, 0f);
            setVec3(shader, "uEntityPosWS", 0f, 0f, 0f);
            setVec3(shader, "uEntityVelWS", 0f, 0f, 0f);
            setVec3(shader, "uVelLagWS", 0f, 0f, 0f);
            set1f(shader, "uSpeed", 0f);
            set1f(shader, "uProxyRadius", radius);
            set1f(shader, "uProxyHalfHeight", halfHeight);
            set1f(shader, "uAuraFade", Math.max(0f, Math.min(1f, corruption)) * 0.8f);
            set1f(shader, "uDensity", radius);
            set1f(shader, "uMaxThickness", radius * 0.65f);
            set1f(shader, "uThicknessFeather", 0f);
            set1f(shader, "uEdgeKill", 0f);
            set1f(shader, "uLimbSoft", 0.22f);
            set1f(shader, "uLimbHardness", 2.25f);
            set1f(shader, "uMinPathNorm", 0.15f);
            set1f(shader, "uHeightFadePow", 1.25f);
            set1f(shader, "uHeightFadeMin", -0.25f);
            set1f(shader, "uPixelsPerRadius", 20f);
            set1f(shader, "uPosterizeSteps", 3f);
            set1f(shader, "uPatchSharpness", 0.6f);
            // Noise parameters not available in this path
            set1f(shader, "uTime", timeVal);

            // Hyper Mode highlight color override
            boolean isHyper = pokemon != null && pokemon.getAspects() != null && pokemon.getAspects().contains(SHAspects.HYPER_MODE);
            if (isHyper) {
                setVec3(shader, "uColorB", 0.85f, 0.30f, 1.30f);
            }
        }

        VertexConsumer vc = buffersPurification.getBuffer(useXd ? AuraRenderTypes.shadow_xd() : AuraRenderTypes.shadow_fog());
        // Build a scaled model matrix for the unit cylinder and render with low LOD for UI
        Matrix4f mat = new Matrix4f();
        mat = mat.scale(radius, halfHeight, radius);
        CylinderBuffers.drawCylinderWithDomesLod(vc, mat, 1f, 0f, 0f, 0f, 0f, 0);
        buffersPurification.endLastBatch();
        matrices.popPose();
    }

    public static void onRender(Camera camera, float partialTicks) {
        if (!ShadowedHeartsConfigs.getInstance().getClientConfig().enableShadowAura()) return;
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        //xd aura not implemented yet
        boolean useXd = false;
        var activeShader = useXd ? ModShaders.SHADOW_AURA_XD_CYLINDER : ModShaders.SHADOW_AURA_FOG_CYLINDER;
        var activeUniforms = useXd ? ModShaders.SHADOW_AURA_XD_CYLINDER_UNIFORMS : ModShaders.SHADOW_AURA_FOG_CYLINDER_UNIFORMS;

        // Keep depth available for the aura shader's soft intersection

        // Cache per-frame matrices and projection parameters
        Matrix4f view = RenderSystem.getModelViewMatrix();
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        Matrix4f invView = new Matrix4f(view).invert();
        Matrix4f invProj = new Matrix4f(proj).invert();
        int screenHeightPx = mc.getWindow().getHeight();
        // Derive tan(fovY/2) from projection matrix: proj.m11 = cot(fovY/2)
        float tanHalfFovY = 1.0f / proj.m11();

        var camPos = camera.getPosition();
        long now = mc.level.getGameTime();

        // Hoist per-frame uniforms (view/proj/inverses, time) out of the per-instance loop
        var shFrame = activeShader;
        var uuFrame = activeUniforms;
        float timeValFrame = (Minecraft.getInstance().level.getGameTime() + partialTicks) * 0.05f;
        if (shFrame != null) {
            if (uuFrame != null) {
                if (uuFrame.uView() != null) uuFrame.uView().set(view);
                if (uuFrame.uProj() != null) uuFrame.uProj().set(proj);
                if (uuFrame.uInvView() != null) uuFrame.uInvView().set(invView);
                if (uuFrame.uInvProj() != null) uuFrame.uInvProj().set(invProj);
                if (uuFrame.uTime() != null) uuFrame.uTime().set(timeValFrame);
            } else {
                setMat4(shFrame, "uView", view);
                setMat4(shFrame, "uProj", proj);
                shFrame.safeGetUniform("uInvView").set(invView);
                shFrame.safeGetUniform("uInvProj").set(invProj);
                set1f(shFrame, "uTime", timeValFrame);
            }
        }

        for (Map.Entry<Integer, AuraInstance> en : ACTIVE.entrySet()) {
            AuraInstance inst = en.getValue();
            if (inst == null) {
                ACTIVE.remove(en.getKey());
                continue;
            }
            if (inst.isExpired(now)) {
                inst.stopSound();
                ACTIVE.remove(en.getKey());
                continue;
            }

            inst.updateSound();

            // Interpolate position from client-side entity when available; fallback to last server state
            double ix, iy, iz;
            Entity ent = (inst.entityRef != null) ? inst.entityRef.get() : null;
            boolean useEnt = false;
            if (ent != null && ent.isAlive() && ent.getId() == inst.entityId) {
                if (inst.entityUuid == null || inst.entityUuid.equals(ent.getUUID())) {
                    useEnt = true;

                }
            }
            if (useEnt) {
                ix = Mth.lerp(partialTicks, ent.xOld, ent.getX());
                iy = Mth.lerp(partialTicks, ent.yOld, ent.getY());
                iz = Mth.lerp(partialTicks, ent.zOld, ent.getZ());
            } else {
                // If the entity wasn't available at START, try to find it now
                ent = mc.level.getEntity(inst.entityId);
                if (ent != null && ent.isAlive() && (inst.entityUuid == null || inst.entityUuid.equals(ent.getUUID()))) {
                    inst.entityRef = new WeakReference<>(ent);
                    inst.entityUuid = ent.getUUID();
                }
                ix = Mth.lerp(partialTicks, inst.lastX, inst.x);
                iy = Mth.lerp(partialTicks, inst.lastY, inst.y);
                iz = Mth.lerp(partialTicks, inst.lastZ, inst.z);
            }
            float fade = inst.fadeFactor(now);
            // Interpolate corruption
            float corruption = Mth.lerp(partialTicks, inst.prevCorruption, inst.lastCorruption);

            // Debug handling: if not debugging, completely skip when invisible; otherwise, proceed so the trail can render
            boolean hasVisibility = fade > 0.001f && corruption > 0.01f;
            if (!hasVisibility) continue;

            // Build matrices
            double x = ix - camPos.x;
            double y = iy - camPos.y;
            double camY = iy - camera.getEntity().getPosition(partialTicks).y;
            double z = iz - camPos.z;

            if (ent instanceof PokemonEntity pokemonEntity) {
                float entityHeight = Mth.lerp(partialTicks, inst.prevBbH, inst.lastBbH);
                float radius = Math.max(0.25f, Mth.lerp(partialTicks, (float) inst.prevBbSize, (float) inst.lastBbSize));
                // Screen-space radius for LOD selection
                double cy = y;
                double distCenter = Math.sqrt(x * x + cy * cy + z * z);
                float pxRadiusShell = distCenter > 0.0001 ? (radius * screenHeightPx) / (2f * (float) distCenter * tanHalfFovY) : 9999f;
                int lodShell = (pxRadiusShell > 150f) ? 3 : (pxRadiusShell > 60f) ? 2 : (pxRadiusShell > 20f) ? 1 : 0;

                Matrix4f model = new Matrix4f().translate((float) (x), (float) ((y + radius / 2f)), (float) (z));
                Matrix4f invModel = new Matrix4f(model).invert();
                Matrix4f mvp = new Matrix4f(proj).mul(view).mul(model);
                var sh = activeShader;
                var uu = activeUniforms;
                if (sh != null) {
                    if (uu != null) {
                        if (uu.uModel() != null) uu.uModel().set(model);
                        if (uu.uInvModel() != null)
                            uu.uInvModel().set(invModel);
                        if (uu.uMVP() != null) uu.uMVP().set(mvp);
                        if (uu.uCameraPosWS() != null)
                            uu.uCameraPosWS().set((float) 0f, (float) 0f, (float) 0f);
                        if (uu.uEntityPosWS() != null)
                            uu.uEntityPosWS().set((float) ix, (float) iy, (float) iz);
                        if (uu.uScrollSpeedRel() != null)
                            uu.uScrollSpeedRel().set(0.8f);

                        // If this entity is a Pokémon in Hyper Mode, shift the aura highlight color to magenta.
                        boolean isHyper = false;
                        if (useEnt && ent instanceof PokemonEntity pe) {
                            var aspects = pe.getAspects();
                            isHyper = aspects != null && aspects.contains(SHAspects.HYPER_MODE);
                        }
                        if (isHyper && uu.uColorB() != null) {
                            uu.uColorB().set(1.30f, 0.30f, 0.85f);
                        } else {
                            if (uu.uColorB() != null)
                                uu.uColorB().set(0.85f, 0.30f, 1.30f);
                        }
                    } else {
                        // Fallback if caching not initialized yet
                        setMat4(sh, "uModel", model);
                        setMat4(sh, "uInvModel", invModel);
                        sh.safeGetUniform("uMVP").set(mvp);
                        setVec3(sh, "uCameraPosWS", 0f, 0f, 0f);
                        setVec3(sh, "uEntityPosWS", (float) x, (float) y, (float) z);

                        // Hyper Mode highlight color override
                        boolean isHyper = false;
                        if (useEnt && ent instanceof PokemonEntity pe) {
                            var aspects = pe.getAspects();
                            isHyper = aspects != null && aspects.contains(SHAspects.HYPER_MODE);
                        }
                        if (isHyper) {
                            setVec3(sh, "uColorB", 1.0f, 0.30f, 1.30f);
                        }
                    }

                    if (uu != null) {
                        if (uu.uProxyRadius() != null)
                            uu.uProxyRadius().set(radius);
                        if (uu.uProxyHalfHeight() != null)
                            uu.uProxyHalfHeight().set(entityHeight * 0.5f);
                        if (uu.uAuraFade() != null)
                            uu.uAuraFade().set(0.8f * fade);
                        if (uu.uDensity() != null)
                            uu.uDensity().set(radius * (useXd ? 1.0f : 1.0f));
                        if (uu.uMaxThickness() != null)
                            uu.uMaxThickness().set(radius * (useXd ? 0.65f : 0.65f));
                    }
                }
                VertexConsumer vcShell = buffersOverworld.getBuffer(useXd ? AuraRenderTypes.shadow_xd() : AuraRenderTypes.shadow_fog());
                Matrix4f mat = new Matrix4f();
                mat = mat.scale(radius, entityHeight, radius);
                //CylinderBuffers.drawCylinderFlatCaps(vcShell, mat, 0, 0, 0, 0, lodShell);
                CylinderBuffers.drawCylinderWithDomesLod(vcShell, mat, 1f, 0, 0, 0, 0, lodShell);
                /*com.jayemceekay.shadowedhearts.client.render.geom.SphereBuffers.drawUnitSphereLod(vcShell, mat, 0, 0, 0, 0, lodShell);*/
                // Flush the buffer for this render type to ensure per-instance uniforms apply to this aura only
                buffersOverworld.endLastBatch();
            }
        }
    }

    public static final class AuraInstance {
        private long startTick;
        private int fadeInTicks;
        private int sustainTicks;
        private int fadeOutTicks;

        // Entity reference for client-side interpolation
        private int entityId;
        private WeakReference<Entity> entityRef;
        private UUID entityUuid;
        private ShadowAuraSoundInstance soundInstance;

        // Cached transform/state updated from server (used as fallback/smoothing for size & corruption)
        public double x, y, z;
        double lastX, lastY, lastZ;
        double lastDeltaX, lastDeltaY, lastDeltaZ;
        float lastBbH, lastBbW;
        float prevBbH, prevBbW;
        double lastBbSize;
        double prevBbSize;
        float lastCorruption = 1.0f;
        float prevCorruption = 1.0f;
        long lastServerTick = -1L;


        AuraInstance(int entityId, Entity ent, long startTick, int fadeInTicks, int sustainTicks, int fadeOutTicks, double x, double y, double z, double dx, double dy, double dz, float bbw, float bbh, double bbs, float lastCorruption) {
            this.entityId = entityId;
            this.entityRef = new WeakReference<>(ent);
            this.entityUuid = (ent != null) ? ent.getUUID() : null;
            this.startTick = startTick;
            this.fadeInTicks = Math.max(1, fadeInTicks);
            this.sustainTicks = Math.max(0, sustainTicks);
            this.fadeOutTicks = Math.max(1, fadeOutTicks);
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastDeltaX = dx;
            this.lastDeltaY = dy;
            this.lastDeltaZ = dz;
            this.lastBbH = bbh;
            this.prevBbH = bbh;
            this.lastBbW = bbw;
            this.prevBbW = bbw;
            this.lastBbSize = bbs;
            this.prevBbSize = bbs;
            this.lastCorruption = lastCorruption;
            this.prevCorruption = lastCorruption;
        }

        public Entity getEntity() {
            return (entityRef != null) ? entityRef.get() : null;
        }

        public void updateSound() {
            var mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return;
            if (soundInstance == null || soundInstance.isStopped()) {
                soundInstance = new ShadowAuraSoundInstance(this);
                mc.getSoundManager().play(soundInstance);
            }
        }

        public void stopSound() {
            if (soundInstance != null) {
                soundInstance.stopSound();
                soundInstance = null;
            }
        }

        void beginImmediateFadeOut(long now, int outTicks) {
            this.startTick = now - (long) this.fadeInTicks - (long) this.sustainTicks;
            this.fadeOutTicks = Math.max(1, outTicks);
        }

        public boolean isExpired(long now) {
            long total = (long) fadeInTicks + (long) sustainTicks + (long) fadeOutTicks;
            return now - startTick >= total;
        }

        public float fadeFactor(long now) {
            long age = Math.max(0, now - startTick);
            long fi = this.fadeInTicks;
            long sus = this.sustainTicks;
            long fo = this.fadeOutTicks;
            if (age < fi) return (float) age / (float) fi;
            age -= fi;
            if (age < sus) return 1f;
            age -= sus;
            if (age < fo) return 1f - (float) age / (float) fo;
            return 0f;
        }

        float getCorruption() {
            return lastCorruption;
        }
    }

    // --- small uniform helpers ---
    private static void set1f(ShaderInstance sh, String name, float v) {
        final Uniform u = sh.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void setVec3(ShaderInstance sh, String name, float x, float y, float z) {
        final Uniform u = sh.getUniform(name);
        if (u != null) u.set(x, y, z);
    }

    private static void setMat4(ShaderInstance sh, String name, Matrix4f m) {
        final Uniform u = sh.getUniform(name);
        if (u != null) u.set(m);
    }
}
