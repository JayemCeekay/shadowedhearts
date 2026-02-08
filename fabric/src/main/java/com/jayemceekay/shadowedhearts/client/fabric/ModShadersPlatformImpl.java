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
                // Cylinder variant for pillar-style aura bounds
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:shadow_aura_fog_cylinder"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> { ModShaders.SHADOW_AURA_FOG_CYLINDER = program; ModShaders.SHADOW_AURA_FOG_CYLINDER_UNIFORMS = com.jayemceekay.shadowedhearts.client.ShadowFogUniforms.from(program); }
                );

                // XD variant (filament-style)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:shadow_aura_xd_cylinder"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> { ModShaders.SHADOW_AURA_XD_CYLINDER = program; ModShaders.SHADOW_AURA_XD_CYLINDER_UNIFORMS = com.jayemceekay.shadowedhearts.client.ShadowFogUniforms.from(program); }
                );

                // Other shaders
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:shadow_pool"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SHADOW_POOL = program
                );

                // Purification Chamber background (screen-space quad)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:purification_chamber_background"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.PURIFICATION_CHAMBER_BACKGROUND = program
                );

                // Poké Ball glow overlay (additive + fullbright)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:ball_glow"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.BALL_GLOW = program
                );

                // Ball trail ribbon
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:ball_trail"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.BALL_TRAIL = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura_pulse"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.AURA_PULSE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:luminous_mote"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.LUMINOUS_MOTE = program
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to load shaders (Fabric)", e);
            }
        });
    }
}