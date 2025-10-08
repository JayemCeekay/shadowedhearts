package com.jayemceekay.shadowedhearts.client;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Caches Uniform handles for the SHADOW_AURA_FOG shader to avoid per-frame name lookups.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
public record ShadowFogUniforms(
        Uniform uModel,
        Uniform uInvModel,
        Uniform uView,
        Uniform uProj,
        Uniform uMVP,
        Uniform uInvView,
        Uniform uInvProj,
        Uniform uCameraPosWS,
        Uniform uEntityPosWS,
        Uniform uEntityVelWS,
        Uniform uVelLagWS,
        Uniform uSpeed,
        Uniform uTime,
        Uniform uExpand,
        Uniform uProxyRadius,
        Uniform uProxyHalfHeight,
        Uniform uAuraFade,
        Uniform uDensity,
        Uniform uMaxThickness,
        Uniform uThicknessFeather,
        Uniform uEdgeKill
) {
    public static ShadowFogUniforms from(ShaderInstance sh) {
        if (sh == null) return null;
        return new ShadowFogUniforms(
                sh.getUniform("uModel"),
                sh.getUniform("uInvModel"),
                sh.getUniform("uView"),
                sh.getUniform("uProj"),
                // Some mappings expose safeGetUniform; getUniform(null-safe) is fine — may return null if absent
                sh.getUniform("uMVP"),
                sh.getUniform("uInvView"),
                sh.getUniform("uInvProj"),
                sh.getUniform("uCameraPosWS"),
                sh.getUniform("uEntityPosWS"),
                sh.getUniform("uEntityVelWS"),
                sh.getUniform("uVelLagWS"),
                sh.getUniform("uSpeed"),
                sh.getUniform("uTime"),
                sh.getUniform("uExpand"),
                sh.getUniform("uProxyRadius"),
                sh.getUniform("uProxyHalfHeight"),
                sh.getUniform("uAuraFade"),
                sh.getUniform("uDensity"),
                sh.getUniform("uMaxThickness"),
                sh.getUniform("uThicknessFeather"),
                sh.getUniform("uEdgeKill")
        );
    }
}
