package com.jayemceekay.shadowedhearts.mixin.client;

import com.cobblemon.mod.common.client.entity.PokemonClientDelegate;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.client.ball.DarkBallCaptureVfx;
import com.jayemceekay.shadowedhearts.client.ball.SnagCaptureVfx;
import com.jayemceekay.shadowedhearts.client.render.DissolveBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the default Cobblemon red capture beam for Snag Balls and
 * delays the Pokémon shrink effect until the snag beams converge.
 *
 * <p>When a ball that carries the {@code snag_ball} aspect is the beam target,
 * we cancel the vanilla {@code renderBeaconBeam} call so our custom
 * {@link SnagCaptureVfx} Bezier-ribbon beams take over instead.
 *
 * <p>We also override the Pokémon's {@code entityScaleModifier} every frame
 * so that Cobblemon's default shrink (which starts at 0.2 s) is replaced by
 * our own timing that delays the shrink until {@link SnagCaptureVfx#DISSOLVE_START}.
 *
 * <p>During the Snag Ball absorption window, we wrap the
 * {@link MultiBufferSource} with a {@link DissolveBufferSource} for its noisy
 * purple-edge dissolve. On a Dark Ball hit, one capture-only render records
 * the model's texture-aware silhouette without submitting its color or depth;
 * later model renders are suppressed while the FBO representation takes ove    r.
 */
@Mixin(value = PokemonRenderer.class, remap = false)
public abstract class MixinPokemonRenderer {

    /**
     * Injects at the head of {@code render}. If the Pokémon is being captured
     * by a Snag Ball (has an active {@link SnagCaptureVfx}), override the
     * {@code entityScaleModifier} so the Pokémon stays full-size until the
     * beams converge, then shrinks on our timeline instead of Cobblemon's.
     */
    @Inject(
            method = "render(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void shadowedhearts$overrideSnagScale(
            PokemonEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseMatrix,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (DarkBallCaptureVfx.shouldHideOriginalModel(entity)) {
            ci.cancel();
            return;
        }

        if (entity.getBeamMode() != 3) return;

        // Override Cobblemon's default shrink with our own timing
        PokemonClientDelegate delegate = (PokemonClientDelegate) entity.getDelegate();
        DarkBallCaptureVfx darkVfx = DarkBallCaptureVfx.getByPokemonId(entity.getId());
        if (darkVfx != null) {
            delegate.setEntityScaleModifier(darkVfx.getDesiredPokemonScale());
            return;
        }

        SnagCaptureVfx vfx = SnagCaptureVfx.getByPokemonId(entity.getId());
        if (vfx != null) {
            delegate.setEntityScaleModifier(vfx.getDesiredPokemonScale());
        }
    }

    /**
     * Wraps the {@link MultiBufferSource} parameter of {@code render} with a
     * {@link DissolveBufferSource} when a snag dissolve is active.
     * This redirects entity render types to the dissolve shader.
     *
     * <p>We use {@code @Inject} + {@code @Local} to capture both the entity
     * argument and the buffer argument, then mutate the buffer local directly.
     */
    @Inject(
            method = "render(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void shadowedhearts$wrapDissolveBuffer(
            PokemonEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseMatrix,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci,
            @Local(argsOnly = true, ordinal = 0) LocalRef<MultiBufferSource> bufferRef
    ) {
        if (DarkBallCaptureVfx.shouldHideOriginalModel(entity)) return;

        if (DarkBallCaptureVfx.wantsSnapshotCapture(entity)) {
            PokemonClientDelegate delegate = (PokemonClientDelegate) entity.getDelegate();
            ResourceLocation texture = VaryingModelRepository.INSTANCE.getTexture(
                    entity.getPokemon().getSpecies().getResourceIdentifier(), delegate);
            bufferRef.set(DarkBallCaptureVfx.wrapSnapshotBuffer(entity, texture, bufferRef.get()));
            return;
        }

        // Dark Ball model suppression is handled by the cancellable HEAD
        // injection. Do not route it through the Snag dissolve mesh.
        if (DarkBallCaptureVfx.getByPokemonId(entity.getId()) != null) return;

        if (entity.getBeamMode() != 3) return;
        SnagCaptureVfx vfx = SnagCaptureVfx.getByPokemonId(entity.getId());
        if (vfx == null) return;

        float progress = vfx.getDissolveProgress();
        if (progress <= 0f || progress >= 1f) return;

        // Check config: dissolve shader can be disabled
        if (!com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                .getInstance().getClientConfig().snagDissolveEnabled()) return;

        // Get the entity's texture to pass to the dissolve render type
        PokemonClientDelegate delegate = (PokemonClientDelegate) entity.getDelegate();
        ResourceLocation texture = VaryingModelRepository.INSTANCE.getTexture(
                entity.getPokemon().getSpecies().getResourceIdentifier(), delegate);

        bufferRef.set(new DissolveBufferSource(buffer, texture, progress, vfx.getAge()));
    }

    @Inject(
            method = "render(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL")
    )
    private void shadowedhearts$finishDarkBallSnapshotCapture(
            PokemonEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseMatrix,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        DarkBallCaptureVfx.finishTexturedSnapshotCapture(entity);
    }

    /**
     * Injects at the head of {@code renderBeam}. If the beam target is a Snag Ball
     * (has the {@code snag_ball} aspect), cancel the entire method — our VFX system
     * running in {@link com.jayemceekay.shadowedhearts.client.ball.BallEmitters}
     * takes over the visual.
     */
    @Inject(
            method = "renderBeam(Lcom/mojang/blaze3d/vertex/PoseStack;FLcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void shadowedhearts$suppressSnagBeam(
            PoseStack matrixStack,
            float partialTicks,
            PokemonEntity entity,
            Entity beamTarget,
            MultiBufferSource buffer,
            Vec3 offset,
            CallbackInfo ci
    ) {
        if (beamTarget instanceof EmptyPokeBallEntity ball) {
            // Only suppress the beam if this specific ball has an active Snag VFX sequence
            if (SnagCaptureVfx.get(ball.getId()) != null
                    || DarkBallCaptureVfx.isReplacementReady(ball.getId())) {
                ci.cancel(); // Suppress the vanilla red beacon beam — our VFX takes over
            }
        }
    }

}
