package com.jayemceekay.shadowedhearts.client.render.block;

import com.cobblemon.mod.common.api.scheduling.SchedulingTracker;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.PoseType;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderPulseRenderer;
import com.jayemceekay.shadowedhearts.client.aura.IrisHandler;
import com.jayemceekay.shadowedhearts.content.blocks.UmbraInfuserBlock;
import com.jayemceekay.shadowedhearts.content.blocks.entity.UmbraInfuserBlockEntity;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Renders liquid, floating contents and the authored machine's transparent tank panels. */
public final class UmbraInfuserRenderer implements BlockEntityRenderer<UmbraInfuserBlockEntity> {
    private static final ResourceLocation ENERGY_SPRITE = ResourceLocation.fromNamespaceAndPath(
            Shadowedhearts.MOD_ID, "block/umbra_tank_energy");
    private static final ResourceLocation MACHINE_SPRITE = ResourceLocation.fromNamespaceAndPath(
            Shadowedhearts.MOD_ID, "block/umbra_infuser");
    // Resource-loading fallback for the procedural liquid material.
    private static final RenderType TRANSLUCENT = Sheets.translucentCullBlockSheet();
    private static final Cavity MACHINE = new Cavity(3.25F, 12.25F, 3.25F, 12.75F, 21.75F, 12.75F);
    private static final Cavity MACHINE_PANELS = new Cavity(3, 12, 3, 13, 22, 13);
    private static final Cavity EXPANSION = new Cavity(1.5F, 3, 1.5F, 14.5F, 28, 14.5F);
    private static final float[] FULL_UV = {0, 0, 16, 16};
    private static final float[] SIDE_CORNERS = {0,1, 1,1, 1,0, 0,0};
    private static final float[] TOP_CORNERS = {0,0, 0,1, 1,1, 1,0};
    private static final float[] BOTTOM_CORNERS = {0,0, 1,0, 1,1, 0,1};
    // Authored exterior/interior rectangles in north, east, south, west order.
    private static final float[][] OUTER_PANEL_UVS = {
            {0F, 5.25F, 1.25F, 6.5F},
            {5.375F, 1.375F, 6.625F, 2.625F},
            {2.75F, 6.25F, 4F, 7.5F},
            {4.875F, 4.875F, 6.125F, 6.125F}
    };
    private static final float[][] INNER_PANEL_UVS = {
            {5.375F, 0F, 6.625F, 1.25F},
            {5.375F, 2.75F, 6.625F, 4F},
            {1.375F, 6.125F, 2.625F, 7.375F},
            {3.5F, 4.875F, 4.75F, 6.125F}
    };
    private final ItemRenderer itemRenderer;
    private final Map<UmbraInfuserBlockEntity, TankPokeBallState> ballStates = new WeakHashMap<>();
    private final RenderContext ballContext = new RenderContext();
    private static final float BALL_SCALE = 0.6F;
    private static final float BALL_MARGIN = 0.23F;
    private static final float CONTENTS_Y_OFFSET = 2.0F / 16.0F;

    public UmbraInfuserRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
        ballContext.put(RenderContext.Companion.getRENDER_STATE(), RenderContext.RenderState.WORLD);
        ballContext.put(RenderContext.Companion.getASPECTS(), Set.of());
        ballContext.put(RenderContext.Companion.getDO_QUIRKS(), false);
    }

    @Override
    public void render(UmbraInfuserBlockEntity entity, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = entity.getLevel();
        if (level == null) return;
        boolean machine = entity.getBlockState().is(ModBlocks.UMBRA_INFUSER.get());
        Cavity cavity = machine ? MACHINE : EXPANSION;
        float fill = Mth.clamp(entity.getClientFill(partialTick), 0.0F, 1.0F);
        float surfaceY = Mth.lerp(fill, cavity.minY(), cavity.maxY());
        float yaw = machine ? -entity.getBlockState().getValue(UmbraInfuserBlock.FACING).toYRot() - 180.0F : 0;
        poses.pushPose();
        try {
            if (machine) {
                poses.translate(0.5, 0, 0.5);
                poses.mulPose(Axis.YP.rotationDegrees(yaw));
                poses.translate(-0.5, 0, -0.5);
            }
            ItemStack displayItem = entity.getDisplayItem();
            ResourceLocation displayBall = entity.getDisplayPokeBall();
            if (displayBall == null) ballStates.remove(entity);
            if (displayBall != null || !displayItem.isEmpty()) {
                double ticks = level.getGameTime() + (double) partialTick;
                double bob = Math.sin(ticks * 0.08) * 0.025;
                // Include the native ball's lid/details and bob in the chamber clearance.
                float margin = displayBall == null ? 0.16F : BALL_MARGIN;
                float itemY = Mth.clamp(surfaceY - 0.08F, cavity.minY() + margin, cavity.maxY() - margin);
                if (machine) {
                    // Lower both contents by two model pixels, keeping clearance above the tank floor.
                    itemY = Math.max(cavity.minY() + margin, itemY - CONTENTS_Y_OFFSET);
                }
                poses.pushPose();
                try {
                    poses.translate((cavity.minX() + cavity.maxX()) * 0.5F, itemY + bob,
                            (cavity.minZ() + cavity.maxZ()) * 0.5F);
                    poses.mulPose(Axis.YP.rotationDegrees((float) ((ticks * 0.5) % 360.0)));
                    if (displayBall != null) {
                        renderPokeBall(entity, displayBall, partialTick, poses, buffers, packedLight);
                    } else {
                        float scale = machine ? 0.45F : 0.65F;
                        poses.scale(scale, scale, scale);
                        itemRenderer.renderStatic(displayItem, ItemDisplayContext.FIXED, packedLight,
                                OverlayTexture.NO_OVERLAY, poses, buffers, level, entity.getBlockPos().hashCode());
                    }
                } finally {
                    poses.popPose();
                }
            }
            if (fill > 0.0001F) {
                renderLiquid(entity, poses.last(), buffers, cavity, surfaceY, yaw, packedLight);
            }
            if (machine) {
                // Match the four authored two-sided panes; the tank has no glass lid or floor.
                renderMachinePanels(poses.last(), sprite(MACHINE_SPRITE).wrap(buffers.getBuffer(Sheets.cutoutBlockSheet())),
                        packedLight);
            }
        } finally {
            poses.popPose();
        }
    }

    private static void renderLiquid(UmbraInfuserBlockEntity entity, PoseStack.Pose pose,
                                     MultiBufferSource buffers, Cavity cavity, float top, float yaw, int light) {
        IrisHandler iris = AuraReaderPulseRenderer.IRIS_HANDLER;
        if (iris != null && iris.isShadowRenderActive()) return;
        float seed = UmbraLiquidSeed.at(entity.getBlockPos().getX(), entity.getBlockPos().getY(),
                entity.getBlockPos().getZ());
        int quarterTurns = Math.floorMod(Math.round(yaw / 90.0F), 4);
        if (ModShaders.UMBRA_LIQUID != null) {
            if (iris != null && iris.isShaderPackInUse()) {
                if (UmbraLiquidIrisRenderer.capture(entity.getBlockPos(), pose,
                        cavity.minX(), cavity.minY(), cavity.minZ(), cavity.maxX(), top, cavity.maxZ(),
                        seed, quarterTurns, light)) return;
            } else {
                RenderType liquid = Minecraft.useShaderTransparency()
                        ? UmbraLiquidRenderTypes.FABULOUS_LIQUID : UmbraLiquidRenderTypes.LIQUID;
                UmbraLiquidRenderTypes.emitVolume(buffers.getBuffer(liquid), pose,
                        cavity.minX(), cavity.minY(), cavity.minZ(), cavity.maxX(), top, cavity.maxZ(),
                        seed, quarterTurns, light);
                return;
            }
        }
        // Keep the tank visible while resources load or a pack frame cannot be captured.
        renderFallbackVolume(pose, sprite(ENERGY_SPRITE).wrap(buffers.getBuffer(TRANSLUCENT)), cavity, top,
                light, 0.16F, 0.07F, 0.25F, 0.45F, null);
    }

    /** Uses the same model, texture and layers as Cobblemon's thrown ball, without a projectile entity. */
    private void renderPokeBall(UmbraInfuserBlockEntity entity, ResourceLocation ball, float partialTick,
                                PoseStack poses, MultiBufferSource buffers, int packedLight) {
        TankPokeBallState state = ballStates.computeIfAbsent(entity, ignored -> new TankPokeBallState());
        state.update(entity.getLevel().getGameTime(), partialTick);
        VaryingModelRepository repository = VaryingModelRepository.INSTANCE;
        // Resolve every frame so resource reloads can replace the shared native model and texture.
        PosableModel model = repository.getPoser(ball, state);
        ResourceLocation texture = repository.getTexture(ball, state);
        ballContext.put(RenderContext.Companion.getPOSABLE_STATE(), state);
        ballContext.put(RenderContext.Companion.getTEXTURE(), texture);
        float red = model.getRed(), green = model.getGreen(), blue = model.getBlue(), alpha = model.getAlpha();
        try (var ignored = new PosableModelContextScope(model, ballContext)) {
            try {
                // Pose transitions may evaluate model queries, so attach the context first.
                if (state.getCurrentModel() != model) {
                    state.setCurrentModel(model);
                    state.setCurrentPose(null);
                    state.setPoseToFirstSuitable(PoseType.PORTRAIT); // Closed ball, rather than its throw/capture animation.
                }
                model.setRed(1); model.setGreen(1); model.setBlue(1); model.setAlpha(1);
                model.setLayerContext(buffers, state, repository.getLayers(ball, state));
                model.applyAnimations(null, state, 0, 0, state.getAnimationSeconds() * 20, 0, 0);
                // Native ball bodies span Y=0..8 pixels; center the body and allow for lid details in BALL_MARGIN.
                poses.translate(0, -BALL_SCALE * 0.25F, 0);
                poses.scale(BALL_SCALE, -BALL_SCALE, -BALL_SCALE);
                model.render(ballContext, poses, buffers.getBuffer(RenderType.entityCutout(texture)),
                        packedLight, OverlayTexture.NO_OVERLAY, -1);
            } finally {
                model.resetLayerContext();
                model.setDefault();
                model.setRed(red); model.setGreen(green); model.setBlue(blue); model.setAlpha(alpha);
            }
        }
    }

    /** World time replaces the animation clock even if shaders render a tank more than once per frame. */
    private static final class TankPokeBallState extends PosableState {
        private final SchedulingTracker tracker = new SchedulingTracker();
        private long startedAt = -1;

        private void update(long gameTime, float partialTick) {
            if (startedAt < 0 || gameTime < startedAt) startedAt = gameTime;
            updateAge((int) ((gameTime - startedAt) % Integer.MAX_VALUE));
            updatePartialTicks(partialTick);
        }

        @Override public Entity getEntity() { return null; }
        @Override public SchedulingTracker getSchedulingTracker() { return tracker; }
        @Override public void updatePartialTicks(float partialTick) { setCurrentPartialTicks(partialTick); }
    }

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    @Override
    public boolean shouldRenderOffScreen(UmbraInfuserBlockEntity entity) {
        // Lower block entity contents extend into the upper world block.
        return true;
    }

    private static void renderMachinePanels(PoseStack.Pose pose, VertexConsumer vertices, int light) {
        float x0 = MACHINE_PANELS.minX(), x1 = MACHINE_PANELS.maxX();
        float y0 = MACHINE_PANELS.minY(), y1 = MACHINE_PANELS.maxY();
        float z0 = MACHINE_PANELS.minZ(), z1 = MACHINE_PANELS.maxZ();
        panel(vertices, pose, new float[]{x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0}, 0, 0, 0, -1, light);
        panel(vertices, pose, new float[]{x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1}, 1, 1, 0, 0, light);
        panel(vertices, pose, new float[]{x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1}, 2, 0, 0, 1, light);
        panel(vertices, pose, new float[]{x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0}, 3, -1, 0, 0, light);
    }

    private static void panel(VertexConsumer vertices, PoseStack.Pose pose, float[] exterior,
                              int panelIndex, float nx, float ny, float nz, int light) {
        face(vertices, pose, exterior, OUTER_PANEL_UVS[panelIndex], SIDE_CORNERS, nx, ny, nz, light, 1, 1, 1, 0.18F);
        // Reverse the horizontal vertex pairs so the inward face keeps the authored upright UVs.
        float[] interior = {
                exterior[3], exterior[4], exterior[5], exterior[0], exterior[1], exterior[2],
                exterior[9], exterior[10], exterior[11], exterior[6], exterior[7], exterior[8]
        };
        face(vertices, pose, interior, INNER_PANEL_UVS[panelIndex], SIDE_CORNERS, -nx, -ny, -nz, light, 1, 1, 1, 0.18F);
    }

    private static void renderFallbackVolume(PoseStack.Pose pose, VertexConsumer vertices, Cavity cavity, float top,
                                     int light, float red, float green, float blue, float alpha, float[][] uvs) {
        float x0 = cavity.minX(), x1 = cavity.maxX(), y0 = cavity.minY(), z0 = cavity.minZ(), z1 = cavity.maxZ();
        face(vertices, pose, new float[]{x1,y0,z0, x0,y0,z0, x0,top,z0, x1,top,z0}, uv(uvs, 0),
                SIDE_CORNERS, 0,0,-1, light, red,green,blue,alpha);
        face(vertices, pose, new float[]{x1,y0,z1, x1,y0,z0, x1,top,z0, x1,top,z1}, uv(uvs, 1),
                SIDE_CORNERS, 1,0,0, light, red,green,blue,alpha);
        face(vertices, pose, new float[]{x0,y0,z1, x1,y0,z1, x1,top,z1, x0,top,z1}, uv(uvs, 2),
                SIDE_CORNERS, 0,0,1, light, red,green,blue,alpha);
        face(vertices, pose, new float[]{x0,y0,z0, x0,y0,z1, x0,top,z1, x0,top,z0}, uv(uvs, 3),
                SIDE_CORNERS, -1,0,0, light, red,green,blue,alpha);
        face(vertices, pose, new float[]{x0,top,z0, x0,top,z1, x1,top,z1, x1,top,z0}, uv(uvs, 4),
                TOP_CORNERS, 0,1,0, light, red,green,blue,alpha);
        face(vertices, pose, new float[]{x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1}, uv(uvs, 5),
                BOTTOM_CORNERS, 0,-1,0, light, red,green,blue,alpha);
    }

    private static float[] uv(float[][] uvs, int face) { return uvs == null ? FULL_UV : uvs[face]; }

    private static void face(VertexConsumer vertices, PoseStack.Pose pose, float[] positions, float[] uv, float[] corners,
                             float nx, float ny, float nz, int light, float red, float green, float blue, float alpha) {
        for (int i = 0; i < 4; i++) {
            // SpriteCoordinateExpander returns its delegate, so chaining would bypass the sprite's UV mapping.
            vertices.addVertex(pose.pose(), positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]);
            vertices.setColor(red, green, blue, alpha);
            vertices.setUv(Mth.lerp(corners[i * 2], uv[0], uv[2]) / 16.0F,
                    Mth.lerp(corners[i * 2 + 1], uv[1], uv[3]) / 16.0F);
            vertices.setOverlay(OverlayTexture.NO_OVERLAY);
            vertices.setLight(light);
            vertices.setNormal(pose, nx, ny, nz);
        }
    }

    /** Blockbench pixel bounds are converted once, relative to the lower block. */
    private record Cavity(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        private Cavity {
            minX /= 16.0F; minY /= 16.0F; minZ /= 16.0F;
            maxX /= 16.0F; maxY /= 16.0F; maxZ /= 16.0F;
        }
    }
}
