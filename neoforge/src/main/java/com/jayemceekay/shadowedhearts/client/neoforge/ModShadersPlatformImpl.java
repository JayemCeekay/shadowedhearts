package com.jayemceekay.shadowedhearts.client.neoforge;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class ModShadersPlatformImpl {

    // Architectury expects a no-arg implementation to be present on NeoForge.
    public static void registerShaders() {
        // No-op on NeoForge; shaders are registered via the MOD event bus (see below).
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent evt) {
        try {
            // Base and variants — register and link to router modes
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:shadow_aura_fog", DefaultVertexFormat.NEW_ENTITY),
                    shader -> {
                        ModShaders.SHADOW_AURA_FOG = shader;
                        ModShaders.SHADOW_AURA_FOG_UNIFORMS = com.jayemceekay.shadowedhearts.client.ShadowFogUniforms.from(shader);
                    });

            // Cylinder variant for pillar-style aura bounds
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:shadow_aura_fog_cylinder", DefaultVertexFormat.NEW_ENTITY),
                    shader -> {
                        ModShaders.SHADOW_AURA_FOG_CYLINDER = shader;
                        ModShaders.SHADOW_AURA_FOG_CYLINDER_UNIFORMS = com.jayemceekay.shadowedhearts.client.ShadowFogUniforms.from(shader);
                    });

            // Other shaders
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:shadow_pool", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.SHADOW_POOL = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:whistle_ground_overlay", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.WHISTLE_GROUND_OVERLAY = shader);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load shaders", e);
        }
    }
}
