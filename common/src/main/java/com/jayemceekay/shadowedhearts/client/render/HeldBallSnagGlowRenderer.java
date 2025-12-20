package com.jayemceekay.shadowedhearts.client.render;

import com.google.common.base.MoreObjects;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.ball.BallEmitters;
import com.jayemceekay.shadowedhearts.client.trail.BallTrailManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders a ball_glow billboard and three orbiting ball_trail quads around a held Poké Ball
 * when the Snag Machine is armed. First- and third-person friendly by approximating the
 * hand position from player pose. Uses existing shaders via BallRenderTypes.
 *
 * 02 §5 Mission Entrance flow — not applicable; this is a client FX utility.
 */
public final class HeldBallSnagGlowRenderer {
    private HeldBallSnagGlowRenderer() {}

    // Cobblemon exposes #cobblemon:poke_balls (and nested tiers); match on the broad tag.
    private static final TagKey<net.minecraft.world.item.Item> POKE_BALLS_TAG = TagKey.create(Registries.ITEM, ResourceLocation.parse("cobblemon:poke_balls"));

    public static boolean isPokeball(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(POKE_BALLS_TAG);
    }


    private static float mainHandHeight = 0.9f;
    private static float oMainHandHeight = 0.9f;
    private static float offHandHeight = 0.9f;
    private static float oOffHandHeight = 0.9f;
    private final Minecraft minecraft = Minecraft.getInstance();
    private static ItemStack mainHandItem = ItemStack.EMPTY;
    private static ItemStack offHandItem = ItemStack.EMPTY;

    public static void renderHeldBallRingsFirstPerson(PoseStack poseStack,
                                                      MultiBufferSource buffers,
                                                      float partialTicks) {


        if(Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            BallEmitters.apply(ModShaders.BALL_GLOW);

            poseStack.pushPose();
            LocalPlayer playerEntity = Minecraft.getInstance().player;

            poseStack.setIdentity();

            Quaternionf quaternionf = Minecraft.getInstance().gameRenderer.getMainCamera().rotation().conjugate(new Quaternionf());
            Matrix4f matrix4f2 = (new Matrix4f()).rotation(quaternionf);
            bobView(poseStack, partialTicks);

            boolean bl = Minecraft.getInstance().player.getUsedItemHand() == InteractionHand.MAIN_HAND;
            HumanoidArm humanoidArm = bl ? Minecraft.getInstance().player.getMainArm() : Minecraft.getInstance().player.getMainArm().getOpposite();


            float f = playerEntity.getAttackAnim(partialTicks);
            InteractionHand interactionHand = MoreObjects.firstNonNull(playerEntity.swingingArm, InteractionHand.MAIN_HAND);


            float g = Mth.lerp(partialTicks, playerEntity.xRotO, Minecraft.getInstance().player.getXRot());
            float h = Mth.lerp(partialTicks, playerEntity.xBobO, playerEntity.xBob);
            float i = Mth.lerp(partialTicks, playerEntity.yBobO, playerEntity.yBob);


            float j = interactionHand == InteractionHand.MAIN_HAND ? f : 0.0F;
            float k = 1.0F - Mth.lerp(partialTicks, oMainHandHeight, mainHandHeight);

            poseStack.mulPose(Axis.XP.rotationDegrees((playerEntity.getViewXRot(partialTicks) - h) * 0.1F));
            poseStack.mulPose(Axis.YP.rotationDegrees((playerEntity.getViewYRot(partialTicks) - i) * 0.1F));

            float n = -0.4F * Mth.sin(Mth.sqrt(j) * (float) Math.PI);
            float m = 0.2F * Mth.sin(Mth.sqrt(j) * ((float) Math.PI * 2F));
            float f1 = -0.2F * Mth.sin(j * (float) Math.PI);
            int o = -1;

            poseStack.translate((float) o * n, m, f1);
            applyItemArmTransform(poseStack, humanoidArm, k);
            applyItemArmAttackTransform(poseStack, humanoidArm, j);



            ResourceLocation itemResourceLocation = BuiltInRegistries.ITEM.getKey(mainHandItem.getItem());

            BakedModel bakedmodel =  Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.vanilla(itemResourceLocation.getNamespace().toString(), itemResourceLocation.getPath()));
            //Minecraft.getInstance().getItemRenderer().getModel(mainHandItem, Minecraft.getInstance().level, playerEntity, 0);
            bakedmodel.getTransforms().getTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).apply(false, poseStack);

            poseStack.translate(-0.5f, -0.5f, -0.5f);
            poseStack.translate(1.1f, 0.35f, 0f);

            VertexConsumer orb = buffers.getBuffer(BallRenderTypes.ballGlow(null));
            emitUnitQuad(orb, poseStack, bakedmodel.getTransforms().getTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation);

            // Three scrolling rings around the held item (third-person hand pose)
            float time = getTime(Minecraft.getInstance().level, partialTicks);
            // Build a basis from the same camera billboard orientation used by emitUnitQuad(null)
            Vector3f fwd = new Vector3f(0f, 0f, 1f);
            Vector3f upV = new Vector3f(0f, 1f, 0f);

            // Pose is already at the hand; use local center
            Vec3 center = Vec3.ZERO;
            float ringRadius = 0.75f;
            float ringThickness = 0.25f;
            int segments = 12;
            float strength = 1.0f;
            float[] anglesDeg = new float[]{0f, 120f, 240f};
            float[] phase = new float[]{0f, 0.33f, 0.66f};

            for (int rings = 0; rings < 3; rings++) {
                float angRad = (float) Math.toRadians(anglesDeg[rings]);
                Quaternionf rot = new Quaternionf().fromAxisAngleRad(upV.x(), upV.y(), upV.z(), angRad);
                Vector3f nV = new Vector3f(fwd);
                rot.transform(nV);
                Vec3 ringNormal = new Vec3(nV.x(), nV.y(), nV.z());
                float scroll = time * 0.15f + phase[rings];
                BallTrailManager.renderScrollingRing(center, ringNormal, ringRadius, ringThickness, segments, strength, scroll, partialTicks, poseStack, buffers);
            }

            poseStack.popPose();
        }
    }

    private static void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm hand, float swingProgress) {
        int i = hand == HumanoidArm.RIGHT ? 1 : -1;
        float f = Mth.sin(swingProgress * swingProgress * (float)Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees((float)i * (45.0F + f * -20.0F)));
        float g = Mth.sin(Mth.sqrt(swingProgress) * (float)Math.PI);
        poseStack.mulPose(Axis.ZP.rotationDegrees((float)i * g * -20.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(g * -80.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees((float)i * -45.0F));
    }

    private static void applyItemArmTransform(PoseStack poseStack, HumanoidArm hand, float equippedProg) {
        int i = hand == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate((float)i * 0.56F, -0.52F + equippedProg * -0.6F, -0.72F);
    }

    private static void bobView(PoseStack poseStack, float f) {
        if (Minecraft.getInstance().getCameraEntity() instanceof Player) {
            Player player = (Player) Minecraft.getInstance().getCameraEntity();
            float g = player.walkDist - player.walkDistO;
            float h = -(player.walkDist + g * f);
            float i = Mth.lerp(f, player.oBob, player.bob);
            poseStack.translate(Mth.sin(h * (float)Math.PI) * i * 0.5F, -Math.abs(Mth.cos(h * (float)Math.PI) * i), 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(h * (float)Math.PI) * i * 3.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(h * (float)Math.PI - 0.2F) * i) * 5.0F));
        }
    }

    public static void tick() {
        oMainHandHeight = mainHandHeight;
        oOffHandHeight = offHandHeight;
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        ItemStack itemStack = localPlayer.getMainHandItem();
        ItemStack itemStack2 = localPlayer.getOffhandItem();
        if (ItemStack.matches(mainHandItem, itemStack)) {
            mainHandItem = itemStack;
        }

        if (ItemStack.matches(offHandItem, itemStack2)) {
            offHandItem = itemStack2;
        }

        if (localPlayer.isHandsBusy()) {
            mainHandHeight = Mth.clamp(mainHandHeight - 0.4F, 0.0F, 1.0F);
            offHandHeight = Mth.clamp(offHandHeight - 0.4F, 0.0F, 1.0F);
        } else {
            float f = localPlayer.getAttackStrengthScale(1.0F);
            mainHandHeight += Mth.clamp((mainHandItem == itemStack ? f * f * f : 0.0F) - mainHandHeight, -0.4F, 0.4F);
            offHandHeight += Mth.clamp((float)(offHandItem == itemStack2 ? 1 : 0) - offHandHeight, -0.4F, 0.4F);
        }

        if (mainHandHeight < 0.1F) {
            mainHandItem = itemStack;
        }

        if (offHandHeight < 0.1F) {
            offHandItem = itemStack2;
        }

    }

    public static void renderAtHandPoseThirdPerson(
            float partialTicks, PoseStack poseStack, MultiBufferSource buffers
    ) {
        poseStack.pushPose();
        BallEmitters.apply(ModShaders.BALL_GLOW);

        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();

        //poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.translate(0f, -0.05f, 0f);
        VertexConsumer orb = buffers.getBuffer(BallRenderTypes.ballGlow(null));
        // Don’t apply extra per-quad rotations; the facing is handled above
        emitUnitQuad(orb, poseStack, null);

        // Three scrolling rings around the held item (third-person hand pose)
        float time = getTime(Minecraft.getInstance().level, partialTicks);
        // Build a basis from the same camera billboard orientation used by emitUnitQuad(null)
        Quaternionf camQ = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        Vector3f fwd = new Vector3f(0f, 0f, 1f); //camQ.transform(fwd);
        Vector3f upV = new Vector3f(0f, 1f, 0f); //camQ.transform(upV);

        // Pose is already at the hand; use local center
        Vec3 center = Vec3.ZERO;
        float ringRadius = 0.30f;
        float ringThickness = 0.25f;
        int segments = 12;
        float strength = 1.0f;
        float[] anglesDeg = new float[] { 0f, 120f, 240f };
        float[] phase = new float[] { 0f, 0.33f, 0.66f };
        for (int i = 0; i < 3; i++) {
            float angRad = (float) Math.toRadians(anglesDeg[i]);
            Quaternionf rot = new Quaternionf().fromAxisAngleRad(upV.x(), upV.y(), upV.z(), angRad);
            Vector3f nV = new Vector3f(fwd);
            rot.transform(nV);
            Vec3 ringNormal = new Vec3(nV.x(), nV.y(), nV.z());
            float scroll = time * 0.2f + phase[i];
            BallTrailManager.renderScrollingRing(center, ringNormal, ringRadius, ringThickness, segments, strength, scroll, partialTicks, poseStack, buffers);
        }

        poseStack.popPose();
    }

    private static float getTime(Level level, float partial) {
        if (level == null) return partial;
        return level.getGameTime() + partial;
    }

    private static void emitUnitQuad(VertexConsumer vc, PoseStack stack, Vector3f rotation) {
        stack.pushPose();
        var last = stack.last();
        Matrix4f pose = last.pose();

        if (rotation != null) {
            float rx = rotation.x();
            float ry = rotation.y()+124f;
            float rz = rotation.z();
            // Heuristic: if any component looks larger than 2π, treat input as degrees and convert to radians
            float twoPi = (float) (Math.PI * 2.0);
            if (Math.abs(rx) > twoPi || Math.abs(ry) > twoPi || Math.abs(rz) > twoPi) {
                rx = (float) Math.toRadians(rx);
                ry = (float) Math.toRadians(ry);
                rz = (float) Math.toRadians(rz);
            }
            pose.rotate(new Quaternionf().rotationXYZ(rx, ry, rz));
            pose.translate(0f, 0f, 0.5f);
        } else {
            Vector3f itemPosition = new Vector3f(0f, 0f, 0f);
            pose.getTranslation(itemPosition);
            pose.identity();
            pose.translate(itemPosition.x, itemPosition.y, itemPosition.z);
            pose.rotate(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            pose.scale(0.5f);
        }

        float x0 = -1f, y0 = -1f, x1 = 1f, y1 = 1f;
        vc.addVertex(pose, x0, y0, 0f).setColor(1f, 1f, 1f, 1f).setUv(0f, 1f)
                .setOverlay(0).setLight(0x00F000F0).setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x1, y0, 0f).setColor(1f, 1f, 1f, 1f).setUv(1f, 1f)
                .setOverlay(0).setLight(0x00F000F0).setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x1, y1, 0f).setColor(1f, 1f, 1f, 1f).setUv(1f, 0f)
                .setOverlay(0).setLight(0x00F000F0).setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x0, y1, 0f).setColor(1f, 1f, 1f, 1f).setUv(0f, 0f)
                .setOverlay(0).setLight(0x00F000F0).setNormal(last, 0f, 0f, 1f);

        stack.popPose();
    }
}
