package com.jayemceekay.shadowedhearts.client.aura;

import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.rendertypes.AuraRenderTypes;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    private static final int MAX_PUFFS = 8500;
    private static final int MIN_BONE_ANCHORS = 160;
    private static final int MAX_BONE_ANCHORS = 900;
    private static final float BONE_ANCHOR_VOLUME_STEP = 900.0f;
    private static final int MAX_BONE_EMITTERS_PER_TICK = 54;
    private static final int BODY_VOLUME_EMITTERS_PER_TICK = 18;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final MultiBufferSource.BufferSource MASK_BUFFERS =
            MultiBufferSource.immediate(new ByteBufferBuilder(786432));

    private static final List<Puff> ACTIVE = new ArrayList<>();
    private static final Map<Integer, SourceState> SOURCES = new ConcurrentHashMap<>();

    private static long lastTickGameTime = Long.MIN_VALUE;
    private static long lastRenderFrameToken = Long.MIN_VALUE;
    private static long lastMaskFrameToken = Long.MIN_VALUE;
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
        SourceState state = SOURCES.computeIfAbsent(entity.getId(), id -> new SourceState(x, y, z));
        state.lastSeenTick = gameTime;
        state.fade = fade;
        state.corruption = corruption;

        if (state.lastEmitTick == gameTime) {
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
        int count = Math.max(2, (int) ((baseCount + motionBonus) * intensity * breathing));

        Vec3 motionDir = speed > 0.0001 ? motion.normalize() : Vec3.ZERO;
        for (int i = 0; i < count; i++) {
            PuffType type = pickType(speed);
            spawnPuff(x, y, z, safeRadius, safeHeight, fade, corruption, motionDir, speed, animationPhase, type);
        }
    }

    public static void observeModelBones(PokemonEntity entity, PoseStack renderStack, Bone rootPart) {
        if (entity == null || renderStack == null || rootPart == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
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

        SourceState state = SOURCES.computeIfAbsent(entity.getId(), id -> new SourceState(x, y, z));
        state.lastSeenTick = gameTime;
        state.lastBoneTick = gameTime;
        state.fade = strength;
        state.corruption = 1.0f;
        state.x = x;
        state.y = y;
        state.z = z;

        if (state.lastBoneEmitTick == gameTime) {
            return;
        }

        PoseStack boneStack = copyTopPose(renderStack);
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        int anchorBudget = boneAnchorBudget(estimateModelCubeVolume(rootPart, 0));
        List<BoneAnchor> anchors = new ArrayList<>(anchorBudget);
        collectBoneAnchors(rootPart, boneStack, cameraPos, anchors, 0, anchorBudget);
        if (anchors.isEmpty()) {
            return;
        }

        spawnBoneAuraPuffs(entity, state, anchors, strength, partialTicks);
        spawnBodyVolumeAuraPuffs(entity, state, strength, partialTicks);

        state.lastBoneAnchors.clear();
        for (BoneAnchor anchor : anchors) {
            state.lastBoneAnchors.add(anchor.pos);
        }
        state.lastBoneEmitTick = gameTime;
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
    }

    public static void renderDensityPipeline(Camera camera, float partialTicks) {
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

        if (!hasPuffs && !hasModelMask) {
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

        ShadowPokemonAuraFBO.blur();
        ShadowPokemonAuraFBO.composite();
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

    private static int boneAnchorBudget(float modelVolume) {
        int scaledBudget = MIN_BONE_ANCHORS + (int) Math.ceil(modelVolume / BONE_ANCHOR_VOLUME_STEP);
        return Mth.clamp(scaledBudget, MIN_BONE_ANCHORS, MAX_BONE_ANCHORS);
    }

    private static float estimateModelCubeVolume(Bone bone, int depth) {
        if (bone == null || depth > 24) {
            return 0.0f;
        }

        float volume = 0.0f;
        if (bone instanceof ModelPart part && !part.cubes.isEmpty()) {
            for (ModelPart.Cube cube : part.cubes) {
                float sx = Math.max(0.0f, Math.abs(cube.maxX - cube.minX));
                float sy = Math.max(0.0f, Math.abs(cube.maxY - cube.minY));
                float sz = Math.max(0.0f, Math.abs(cube.maxZ - cube.minZ));
                volume += sx * sy * sz;
            }
        }

        Map<String, Bone> children = bone.getChildren();
        if (children != null && !children.isEmpty()) {
            for (Bone child : children.values()) {
                volume += estimateModelCubeVolume(child, depth + 1);
            }
        }

        return volume;
    }

    private static void collectBoneAnchors(Bone bone,
                                           PoseStack stack,
                                           Vec3 cameraPos,
                                           List<BoneAnchor> anchors,
                                           int depth,
                                           int anchorBudget) {
        if (bone == null || anchors.size() >= anchorBudget || depth > 24) {
            return;
        }

        stack.pushPose();
        bone.transform(stack);

        int anchorsBefore = anchors.size();
        if (bone instanceof ModelPart part && !part.cubes.isEmpty()) {
            collectCubeAnchors(part, stack, cameraPos, anchors, depth, anchorBudget);
        }

        if (anchors.size() < anchorBudget && depth > 0 && (anchors.size() == anchorsBefore || depth <= 2)) {
            Vector3f p = stack.last().pose().transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
            anchors.add(new BoneAnchor(new Vec3(p.x + cameraPos.x, p.y + cameraPos.y, p.z + cameraPos.z), depth, 1.0f));
        }

        Map<String, Bone> children = bone.getChildren();
        if (children != null && !children.isEmpty()) {
            for (Bone child : children.values()) {
                if (anchors.size() >= anchorBudget) {
                    break;
                }
                collectBoneAnchors(child, stack, cameraPos, anchors, depth + 1, anchorBudget);
            }
        }

        stack.popPose();
    }

    private static void collectCubeAnchors(ModelPart part,
                                           PoseStack stack,
                                           Vec3 cameraPos,
                                           List<BoneAnchor> anchors,
                                           int depth,
                                           int anchorBudget) {
        Matrix4f pose = stack.last().pose();
        for (ModelPart.Cube cube : part.cubes) {
            if (anchors.size() >= anchorBudget) {
                return;
            }

            float sx = Math.max(0.001f, Math.abs(cube.maxX - cube.minX));
            float sy = Math.max(0.001f, Math.abs(cube.maxY - cube.minY));
            float sz = Math.max(0.001f, Math.abs(cube.maxZ - cube.minZ));
            float longest = Math.max(sx, Math.max(sy, sz));
            float volume = sx * sy * sz;

            int sampleCount = Mth.clamp((int) Math.ceil(volume / 1850.0f), 1, 18);
            sampleCount = Math.max(sampleCount, Mth.clamp((int) Math.ceil(longest / 12.0f), 1, 8));
            float sizeWeight = Mth.clamp(0.78f + longest / 68.0f, 0.78f, 1.16f);

            for (int i = 0; i < sampleCount && anchors.size() < anchorBudget; i++) {
                Vector3f local = cubeVolumeSample(cube, i, sampleCount);
                Vector3f p = pose.transformPosition(local.x / 16.0f, local.y / 16.0f, local.z / 16.0f, new Vector3f());
                anchors.add(new BoneAnchor(new Vec3(p.x + cameraPos.x, p.y + cameraPos.y, p.z + cameraPos.z), depth, sizeWeight));
            }
        }
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

    private static void spawnBoneAuraPuffs(PokemonEntity entity,
                                           SourceState state,
                                           List<BoneAnchor> anchors,
                                           float strength,
                                           float partialTicks) {
        int anchorCount = anchors.size();
        int targetCount = Math.min(anchorCount, MAX_BONE_EMITTERS_PER_TICK);
        int stride = Math.max(1, (int) Math.ceil(anchorCount / (double) targetCount));
        int offset = stride > 1 ? RANDOM.nextInt(stride) : 0;

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
        float intensity = Mth.clamp(strength * pulse * 0.88f, 0.0f, 1.18f);

        for (int i = offset; i < anchorCount; i += stride) {
            BoneAnchor anchor = anchors.get(i);
            Vec3 previous = i < state.lastBoneAnchors.size() ? state.lastBoneAnchors.get(i) : anchor.pos;
            Vec3 boneMotion = anchor.pos.subtract(previous);
            float depthWeight = (anchor.depth <= 2 ? 1.02f : anchor.depth <= 5 ? 0.88f : 0.74f) * anchor.sizeWeight;

            if (RANDOM.nextFloat() < 0.62f * intensity) {
                spawnBonePuff(anchor.pos, center, boneMotion, entityMotion, modelSize, intensity, depthWeight, pickBoneAuraPuffType());
            }
            if (RANDOM.nextFloat() < 0.18f * intensity) {
                spawnBonePuff(anchor.pos, center, boneMotion, entityMotion, modelSize, intensity, depthWeight, PuffType.WISP);
            }
            if (RANDOM.nextFloat() < 0.12f * intensity) {
                spawnBonePuff(anchor.pos, center, boneMotion, entityMotion, modelSize, intensity, depthWeight, PuffType.BROAD_HAZE);
            }

            float fleckChance = (speed > 0.04 || boneMotion.lengthSqr() > 0.0005) ? 0.055f : 0.018f;
            if (RANDOM.nextFloat() < fleckChance * intensity) {
                spawnBonePuff(anchor.pos, center, boneMotion, entityMotion, modelSize, intensity, depthWeight, PuffType.HOT_FLECK);
            }
        }
    }

    private static void spawnBodyVolumeAuraPuffs(PokemonEntity entity,
                                                 SourceState state,
                                                 float strength,
                                                 float partialTicks) {
        Vec3 center = new Vec3(
                Mth.lerp(partialTicks, entity.xOld, entity.getX()),
                Mth.lerp(partialTicks, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.34,
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

        float width = Math.max(0.22f, entity.getBbWidth());
        float height = Math.max(0.35f, entity.getBbHeight());
        float xRadius = Math.max(0.15f, width * 0.54f);
        float yRadius = Math.max(0.16f, height * 0.34f);
        float zRadius = xRadius;
        float modelSize = Math.max(width, height);
        float intensity = Mth.clamp(strength * 0.78f, 0.0f, 0.95f);
        int count = Math.max(4, Math.round(BODY_VOLUME_EMITTERS_PER_TICK * intensity));

        for (int i = 0; i < count; i++) {
            int sample = state.bodySampleCursor++;
            Vec3 local = lowerEllipsoidBodySample(sample, xRadius, yRadius, zRadius);
            Vec3 anchor = center.add(local);
            float depthWeight = 0.76f + 0.12f * halton(sample + 1, 7);

            spawnBonePuff(anchor, center, bodyMotion, entity.getDeltaMovement(), modelSize, intensity, depthWeight, PuffType.BROAD_HAZE);
            if (RANDOM.nextFloat() < 0.20f * intensity) {
                spawnBonePuff(anchor, center, bodyMotion, entity.getDeltaMovement(), modelSize, intensity, depthWeight, PuffType.CORE_HAZE);
            }
        }
    }

    private static Vec3 lowerEllipsoidBodySample(int index, float xRadius, float yRadius, float zRadius) {
        float u = halton(index + 1, 2);
        float v = halton(index + 1, 3);
        float shell = 0.42f + 0.58f * halton(index + 1, 5);
        float y = (float) Math.pow(v, 1.85f) * 2.0f - 1.0f;
        float ring = (float) Math.sqrt(Math.max(0.0f, 1.0f - y * y));
        float angle = u * Mth.TWO_PI;
        return new Vec3(
                Math.cos(angle) * ring * shell * xRadius,
                y * shell * yRadius,
                Math.sin(angle) * ring * shell * zRadius
        );
    }

    private static PuffType pickBoneAuraPuffType() {
        float roll = RANDOM.nextFloat();
        if (roll < 0.62f) {
            return PuffType.CORE_HAZE;
        }
        if (roll < 0.88f) {
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
                                      PuffType type) {
        Vec3 outward = anchorPos.subtract(center);
        if (outward.lengthSqr() > 0.0001) {
            outward = outward.normalize();
        } else {
            outward = randomUnitVector();
        }

        Vec3 jitter = randomUnitVector();
        float radiusScale = switch (type) {
            case BROAD_HAZE -> 0.18f;
            case CORE_HAZE -> 0.15f;
            case WISP -> 0.20f;
            case HOT_FLECK -> 0.22f;
        };
        float auraRadius = Mth.clamp(modelSize * radiusScale, 0.10f, 0.42f) * depthWeight;
        float outwardPush = auraRadius * (0.30f + RANDOM.nextFloat() * 0.64f);
        float jitterPush = auraRadius * (0.14f + RANDOM.nextFloat() * 0.30f);

        double px = anchorPos.x + outward.x * outwardPush + jitter.x * jitterPush;
        double py = anchorPos.y + outward.y * outwardPush + jitter.y * jitterPush + RANDOM.nextFloat() * auraRadius * 0.20f;
        double pz = anchorPos.z + outward.z * outwardPush + jitter.z * jitterPush;

        float baseSize;
        int lifetime;
        float density;
        switch (type) {
            case BROAD_HAZE -> {
                baseSize = Mth.clamp(modelSize * (0.52f + RANDOM.nextFloat() * 0.24f) * depthWeight, 0.42f, 1.16f);
                lifetime = 42 + RANDOM.nextInt(24);
                density = (0.20f + RANDOM.nextFloat() * 0.20f) * intensity;
            }
            case CORE_HAZE -> {
                baseSize = Mth.clamp(modelSize * (0.32f + RANDOM.nextFloat() * 0.18f) * depthWeight, 0.26f, 0.82f);
                lifetime = 34 + RANDOM.nextInt(22);
                density = (0.34f + RANDOM.nextFloat() * 0.28f) * intensity;
            }
            case WISP -> {
                baseSize = Mth.clamp(modelSize * (0.16f + RANDOM.nextFloat() * 0.13f) * depthWeight, 0.14f, 0.46f);
                lifetime = 28 + RANDOM.nextInt(20);
                density = (0.40f + RANDOM.nextFloat() * 0.32f) * intensity;
            }
            case HOT_FLECK -> {
                baseSize = Mth.clamp(modelSize * (0.052f + RANDOM.nextFloat() * 0.050f) * depthWeight, 0.045f, 0.18f);
                lifetime = 18 + RANDOM.nextInt(18);
                density = (0.34f + RANDOM.nextFloat() * 0.28f) * intensity;
            }
            default -> throw new IllegalStateException("Unhandled aura puff type");
        }

        Vec3 animationDrift = boneMotion.scale(0.34).add(entityMotion.scale(0.055));
        double driftScale = type == PuffType.HOT_FLECK ? 0.018 : 0.026;
        double xd = outward.x * driftScale + animationDrift.x + (RANDOM.nextFloat() - 0.5) * 0.020;
        double yd = 0.010 + RANDOM.nextFloat() * 0.024 + outward.y * 0.014 + animationDrift.y * 0.65;
        double zd = outward.z * driftScale + animationDrift.z + (RANDOM.nextFloat() - 0.5) * 0.020;

        addPuff(new Puff(px, py, pz, xd, yd, zd, baseSize, lifetime, density, type));
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
                lifetime = 26 + RANDOM.nextInt(18);
                density = (0.34f + RANDOM.nextFloat() * 0.34f) * fade * (0.55f + corruption * 0.65f);
            }
            case WISP -> {
                baseSize = (0.16f + RANDOM.nextFloat() * 0.18f) * scale;
                lifetime = 22 + RANDOM.nextInt(16);
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
            if (ACTIVE.size() >= MAX_PUFFS) {
                ACTIVE.remove(0);
            }
            ACTIVE.add(puff);
        }
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
        float fade = 1.0f;
        float corruption = 1.0f;
        boolean hasLastBodyCenter;
        final float seed = RANDOM.nextFloat() * Mth.TWO_PI;
        final List<Vec3> lastBoneAnchors = new ArrayList<>();

        SourceState(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private record BoneAnchor(Vec3 pos, int depth, float sizeWeight) {}

    private static final class Puff {
        double x, y, z;
        double xo, yo, zo;
        double xd, yd, zd;
        double xdo, ydo, zdo;

        final float baseSize;
        final int lifetime;
        final float baseDensity;
        final float rotation;
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
        }
    }
}
