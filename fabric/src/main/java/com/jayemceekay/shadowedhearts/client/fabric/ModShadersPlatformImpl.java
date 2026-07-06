package com.jayemceekay.shadowedhearts.client.fabric;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.resources.ResourceLocation;

public final class ModShadersPlatformImpl {

    public static void registerShaders() {
        CoreShaderRegistrationCallback.EVENT.register((registrationContext) -> {
            try {
                // Register base and variant aura fog shaders; trail reuses the same program per-mode
                /*registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:shadow_aura_fog"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> { ModShaders.SHADOW_AURA_FOG = program; ModShaders.SHADOW_AURA_FOG_UNIFORMS = com.jayemceekay.shadowedhearts.client.ShadowFogUniforms.from(program); }
                );*/
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_aura_fog_cylinder"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> { ModShaders.SHADOW_AURA_FOG_CYLINDER = program; ModShaders.SHADOW_AURA_FOG_CYLINDER_UNIFORMS = com.jayemceekay.shadowedhearts.client.ShadowFogUniforms.from(program); }
                );

                // XD variant (filament-style)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_aura_xd_cylinder"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> { ModShaders.SHADOW_AURA_XD_CYLINDER = program; ModShaders.SHADOW_AURA_XD_CYLINDER_UNIFORMS = com.jayemceekay.shadowedhearts.client.ShadowFogUniforms.from(program); }
                );

                // Purification Chamber background (screen-space quad)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:purification/purification_chamber_background"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.PURIFICATION_CHAMBER_BACKGROUND = program
                );

                // Poké Ball glow overlay (additive + fullbright)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/ball_glow"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.BALL_GLOW = program
                );

                // Ball trail ribbon
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/ball_trail"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.BALL_TRAIL = program
                );

                // Shadow aura trail (fullscreen quad, sphere-traced SDF raymarching)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_aura_trail"),
                        DefaultVertexFormat.POSITION,
                        program -> ModShaders.SHADOW_AURA_TRAIL = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/aura_pulse"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.AURA_PULSE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/luminous_mote"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.LUMINOUS_MOTE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/penumbra_trail"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.PENUMBRA_TRAIL = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/penumbra_trail_ribbon"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.PENUMBRA_TRAIL_RIBBON = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/penumbra_density_splat"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.PENUMBRA_DENSITY_SPLAT = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/penumbra_blur"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.PENUMBRA_BLUR = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/penumbra_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.PENUMBRA_COMPOSITE = program
                );

                // Screen-space electromagnetic static overlay
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:interference/aura_static_interference"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.AURA_STATIC_INTERFERENCE = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:interference/heat_haze_interference"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.HEAT_HAZE_INTERFERENCE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:hud/barrel_distortion"),
                        DefaultVertexFormat.POSITION_COLOR,
                        program -> ModShaders.HUD_BARREL_DISTORTION = program
                );

            } catch (Exception e) {
                throw new RuntimeException("Failed to load shaders (Fabric)", e);
            }
        });
    }
}