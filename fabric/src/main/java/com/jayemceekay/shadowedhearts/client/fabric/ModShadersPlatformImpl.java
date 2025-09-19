package com.jayemceekay.shadowedhearts.client.fabric;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.resources.ResourceLocation;

public final class ModShadersPlatformImpl {

    public static void registerShaders() {
        CoreShaderRegistrationCallback.EVENT.register((registrationContext) -> {
            try {
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:shadow_aura_fog"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.SHADOW_AURA_FOG = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:shadow_pool"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SHADOW_POOL = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:shadow_darken_layer"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.SHADOW_DARKEN_LAYER = program
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to load shaders (Fabric)", e);
            }
        });
    }
}