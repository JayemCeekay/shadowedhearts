package com.jayemceekay.shadowedhearts.client.aura.shadow;

import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import com.jayemceekay.shadowedhearts.network.aura.AuraLifecyclePacket;
import com.jayemceekay.shadowedhearts.network.aura.AuraStatePacket;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import com.jayemceekay.shadowedhearts.client.sound.LoopingSoundHandle;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuraEmitter system that attaches an aura instance to a Pokémon when it is sent out.
 * Rendering is driven by this system, not by the Pokémon's own renderer.
 */
public final class ShadowAuraEmitters {
    private ShadowAuraEmitters() {
    }

    private static final Map<Integer, AuraInstance> ACTIVE = new ConcurrentHashMap<>();
    private static ClientLevel clientLevel;

    /** World ownership is checked on ticks as well as packets so title screens release old loops. */
    public static void onClientTick(Minecraft minecraft) {
        syncWorld(minecraft);
        if (clientLevel == null) return;
        long now = clientLevel.getGameTime();
        ACTIVE.entrySet().removeIf(entry -> {
            if (!entry.getValue().isExpired(now)) return false;
            entry.getValue().stopSound();
            return true;
        });
        if (!ShadowedHeartsConfigs.getInstance().getClientConfig().enableShadowAura()) {
            ACTIVE.values().forEach(AuraInstance::stopSound);
        } else {
            // Keep audio available when the accessibility override hides only aura visuals.
            ACTIVE.values().forEach(AuraInstance::updateSound);
        }
    }

    private static void syncWorld(Minecraft minecraft) {
        if (minecraft.level != clientLevel) {
            ACTIVE.values().forEach(AuraInstance::stopSound);
            ACTIVE.clear();
            clientLevel = minecraft.level;
        }
    }


    /**
     * Called by a networking handler when a state update arrives.
     */
    public static void receiveState(AuraStatePacket pkt) {
        if (!ShadowedHeartsConfigs.getInstance().getClientConfig().enableShadowAura()) return;
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        syncWorld(mc);
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
        syncWorld(mc);
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
                            if (ACTIVE.remove(e.getKey(), ai)) ai.stopSound();
                        }
                    }
                }
                AuraInstance previous = ACTIVE.put(pkt.getEntityId(), new AuraInstance(pkt.getEntityId(), ent, now, FADE_IN, (pkt.getSustainOverride() > 0) ? pkt.getSustainOverride() : SUSTAIN, FADE_OUT, pkt.getX(), pkt.getY(), pkt.getZ(), pkt.getDx(), pkt.getDy(), pkt.getDz(), pkt.getBbw(), pkt.getBbh() * pkt.getHeightMultiplier(), pkt.getBbs(), pkt.getCorruption()));
                if (previous != null) previous.stopSound();
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
    private static final int SUSTAIN = 3600; // can be tuned; original recent value ~90-100
    private static final int FADE_OUT = 10;
    // If a position update jumps farther than this squared distance, snap to avoid long lerp streaks
    private static final double TELEPORT_SNAP_DIST2 = 36.0; // 6 blocks squared

    public static void init() {
        // Server is authoritative for aura lifecycle now. Client no longer subscribes to Cobblemon send/recall events.
    }

    public static void onPokemonDespawn(int entityId) {
        // Start a quick fade-out if we still have an instance; if missing, nothing to do
        var mc = Minecraft.getInstance();
        ShadowPokemonAuraSystem.onPokemonDespawn(entityId);
        long now = (mc != null && mc.level != null) ? mc.level.getGameTime() : 0L;
        ACTIVE.computeIfPresent(entityId, (id, inst) -> {
            inst.stopSound();
            inst.beginImmediateFadeOut(now, 10);
            return inst;
        });
    }

    public static float getFboAuraMaskStrength(PokemonEntity entity) {
        if (entity == null || !com.jayemceekay.shadowedhearts.client.ClientEffects.auraVisible()) {
            return 0.0f;
        }

        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return 0.0f;
        }

        AuraInstance inst = ACTIVE.get(entity.getId());
        if (inst == null) {
            return 0.0f;
        }

        UUID entityUuid = entity.getUUID();
        if (inst.entityUuid != null && !inst.entityUuid.equals(entityUuid)) {
            return 0.0f;
        }
        if (inst.entityUuid == null) {
            inst.entityUuid = entityUuid;
            inst.entityRef = new WeakReference<>(entity);
        }

        long now = mc.level.getGameTime();
        if (inst.isExpired(now)) {
            return 0.0f;
        }

        boolean auraReaderRequired = ShadowedHeartsConfigs.getInstance().getShadowConfig().auraReaderRequiredForAura();
        if (auraReaderRequired && !SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(mc.player)) {
            if (!entity.getPokemon().cosmeticItem().is(ModItems.SHADOW_SHARD.get())) {
                return 0.0f;
            }
        }

        float fade = inst.fadeFactor(now);
        float partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(true);
        float corruption = Mth.lerp(partialTicks, inst.prevCorruption, inst.lastCorruption);
        if (fade <= 0.001f || corruption <= 0.01f) {
            return 0.0f;
        }

        return Mth.clamp(fade * (0.55f + corruption * 0.65f), 0.0f, 1.0f);
    }

    /** Compatibility entry point; GUI preview layout is owned by {@link ShadowPokemonAuraGuiRenderer}. */
    public static void renderInSummaryGUI(GuiGraphics context,
                                          MultiBufferSource bufferSource,
                                          float corruption,
                                          float partialTicks,
                                          RenderablePokemon pokemon,
                                          ModelWidget widget,
                                          ShadowPokemonAuraSystem.PreviewInstance previewAura) {
        ShadowPokemonAuraGuiRenderer.renderInSummaryGUI(
                context, bufferSource, corruption, partialTicks, pokemon, widget, previewAura);
    }

    /** Compatibility entry point; GUI preview layout is owned by {@link ShadowPokemonAuraGuiRenderer}. */
    public static void renderInPcGUI(GuiGraphics context,
                                     MultiBufferSource bufferSource,
                                     float corruption,
                                     float partialTicks,
                                     RenderablePokemon pokemon,
                                     ModelWidget widget,
                                     ShadowPokemonAuraSystem.PreviewInstance previewAura) {
        ShadowPokemonAuraGuiRenderer.renderInPcGUI(
                context, bufferSource, corruption, partialTicks, pokemon, widget, previewAura);
    }

    /** Compatibility entry point; GUI preview layout is owned by {@link ShadowPokemonAuraGuiRenderer}. */
    public static void renderInPurificationGUI(GuiGraphics context,
                                               PoseStack matrices,
                                               MultiBufferSource bufferSource,
                                               float corruption,
                                               float partialTicks,
                                               RenderablePokemon pokemon,
                                               com.cobblemon.mod.common.client.render.models.blockbench.PosableState state,
                                               float x, float y, float width, float height,
                                               ShadowPokemonAuraSystem.PreviewInstance previewAura) {
        ShadowPokemonAuraGuiRenderer.renderInPurificationGUI(
                context, matrices, bufferSource, corruption, partialTicks, pokemon, state,
                x, y, width, height, previewAura);
    }

    public static void onRender(Camera camera, float partialTicks) {
        if (!com.jayemceekay.shadowedhearts.client.ClientEffects.auraVisible()) return;
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        //xd aura not implemented yet
        boolean useXd = false;
        var activeShader = useXd ? ModShaders.SHADOW_AURA_XD_CYLINDER : ModShaders.SHADOW_AURA_FOG_CYLINDER;
        var activeUniforms = useXd ? ModShaders.SHADOW_AURA_XD_CYLINDER_UNIFORMS : ModShaders.SHADOW_AURA_FOG_CYLINDER_UNIFORMS;

        // Cache per-frame matrices for the retained shader uniform setup
        Matrix4f view = RenderSystem.getModelViewMatrix();
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        Matrix4f invView = new Matrix4f(view).invert();
        Matrix4f invProj = new Matrix4f(proj).invert();
        long now = mc.level.getGameTime();

        boolean auraReaderRequired = ShadowedHeartsConfigs.getInstance().getShadowConfig().auraReaderRequiredForAura();
        boolean hasAuraReader = auraReaderRequired && SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(mc.player);

        // Hoist per-frame uniforms (view/proj/inverses, time) out of the per-instance loop
        var shFrame = activeShader;
        var uuFrame = activeUniforms;
        float timeValFrame = (System.currentTimeMillis() % 100000) / 1000.0f * 2.0f;
        if (shFrame != null) {
            RenderSystem.setShader(() -> shFrame);
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

            if (auraReaderRequired && !hasAuraReader) {
                if (ent instanceof PokemonEntity pe && pe.getPokemon().cosmeticItem().is(ModItems.SHADOW_SHARD.get())) {
                    // Bypass Aura Reader requirement for Shadow Shard cosmetic item
                } else {
                    continue;
                }
            }

            if (ent instanceof PokemonEntity pokemonEntity) {
                float entityHeight = Math.max(0.1f, Mth.lerp(partialTicks, inst.prevBbH, inst.lastBbH));
                float radius = Math.max(0.25f, Mth.lerp(partialTicks, (float) inst.prevBbSize, (float) inst.lastBbSize));

                ShadowPokemonAuraSystem.observe(
                        pokemonEntity,
                        ix,
                        iy,
                        iz,
                        radius,
                        entityHeight,
                        fade,
                        corruption,
                        partialTicks
                );
                continue;
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
        private final LoopingSoundHandle<ShadowAuraSoundInstance> sound = new LoopingSoundHandle<>();

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
        private long lastDetectedTick = -1L;


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
            if (mc == null || mc.level == null || mc.isPaused()) return;

            boolean auraReaderRequired = ShadowedHeartsConfigs.getInstance().getShadowConfig().auraReaderRequiredForAura();
            boolean hasAuraReader = auraReaderRequired && SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(mc.player);

            if (auraReaderRequired && !hasAuraReader) {
                stopSound();
                return;
            }

            boolean audible = mc.options.getSoundSourceVolume(SoundSource.MASTER) > 0
                    && mc.options.getSoundSourceVolume(SoundSource.NEUTRAL) > 0
                    && ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().shadowAuraLoopVolume() > 0;
            sound.update(mc.level.getGameTime(), audible, () -> new ShadowAuraSoundInstance(this),
                    instance -> !instance.isStopped() && mc.getSoundManager().isActive(instance),
                    mc.getSoundManager()::play, AuraInstance::disposeSound);
        }

        public void stopSound() {
            sound.stop(AuraInstance::disposeSound);
        }

        private static void disposeSound(ShadowAuraSoundInstance sound) {
            sound.stopSound();
            Minecraft.getInstance().getSoundManager().stop(sound);
        }

        void beginImmediateFadeOut(long now, int outTicks) {
            this.startTick = now - (long) this.fadeInTicks - (long) this.sustainTicks;
            this.fadeOutTicks = Math.max(1, outTicks);
        }

        public boolean isExpired(long now) {
            long total = (long) fadeInTicks + (long) sustainTicks + (long) fadeOutTicks;
            boolean originalExpired = now - startTick >= total;
            if (originalExpired) {
                if (lastDetectedTick != -1L && now - lastDetectedTick < fadeOutTicks) return false;
                return true;
            }
            return false;
        }

        public float fadeFactor(long now) {
            float normalFade = 0f;
            long age = Math.max(0, now - startTick);
            long fi = this.fadeInTicks;
            long sus = this.sustainTicks;
            long fo = this.fadeOutTicks;
            if (age < fi) normalFade = (float) age / (float) fi;
            else {
                age -= fi;
                if (age < sus) normalFade = 1f;
                else {
                    age -= sus;
                    if (age < fo) normalFade = 1f - (float) age / (float) fo;
                }
            }

            float pulseFade = 0f;
            if (lastDetectedTick != -1L) {
                long pulseAge = now - lastDetectedTick;
                if (pulseAge < fo) {
                    pulseFade = 1.0f - (float) pulseAge / (float) fo;
                }
            }

            return Math.max(normalFade, pulseFade);
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

    private static void setMat4(ShaderInstance sh, String name, Matrix4f m) {
        final Uniform u = sh.getUniform(name);
        if (u != null) u.set(m);
    }
}
