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
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:shadow_aura_fog"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> { ModShaders.SHADOW_AURA_FOG = program; ModShaders.SHADOW_AURA_FOG_UNIFORMS = com.jayemceekay.shadowedhearts.client.ShadowFogUniforms.from(program); }
                );
                // Cylinder variant for pillar-style aura bounds
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:shadow_aura_fog_cylinder"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> { ModShaders.SHADOW_AURA_FOG_CYLINDER = program; ModShaders.SHADOW_AURA_FOG_CYLINDER_UNIFORMS = com.jayemceekay.shadowedhearts.client.ShadowFogUniforms.from(program); }
                );

                // Other shaders
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:shadow_pool"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SHADOW_POOL = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:whistle_ground_overlay"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.WHISTLE_GROUND_OVERLAY = program
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to load shaders (Fabric)", e);
            }
        });
    }
}