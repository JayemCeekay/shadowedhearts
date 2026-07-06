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
            // Cylinder variant for pillar-style aura bounds
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_aura_fog_cylinder", DefaultVertexFormat.NEW_ENTITY),
                    shader -> {
                        ModShaders.SHADOW_AURA_FOG_CYLINDER = shader;
                        ModShaders.SHADOW_AURA_FOG_CYLINDER_UNIFORMS = com.jayemceekay.shadowedhearts.client.ShadowFogUniforms.from(shader);
                    });

            // XD variant (filament-style)
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_aura_xd_cylinder", DefaultVertexFormat.NEW_ENTITY),
                    shader -> {
                        ModShaders.SHADOW_AURA_XD_CYLINDER = shader;
                        ModShaders.SHADOW_AURA_XD_CYLINDER_UNIFORMS = com.jayemceekay.shadowedhearts.client.ShadowFogUniforms.from(shader);
                    });

            // Purification Chamber background
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:purification/purification_chamber_background", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.PURIFICATION_CHAMBER_BACKGROUND = shader);

            // Poké Ball glow overlay (additive + fullbright)
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/ball_glow", DefaultVertexFormat.NEW_ENTITY),
                    shader -> ModShaders.BALL_GLOW = shader);

            // Ball trail ribbon
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/ball_trail", DefaultVertexFormat.NEW_ENTITY),
                    shader -> ModShaders.BALL_TRAIL = shader);

            // Shadow aura trail (fullscreen quad, sphere-traced SDF raymarching)
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_aura_trail", DefaultVertexFormat.POSITION),
                    shader -> ModShaders.SHADOW_AURA_TRAIL = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/aura_pulse", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.AURA_PULSE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/luminous_mote", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.LUMINOUS_MOTE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/penumbra_trail", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.PENUMBRA_TRAIL = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/penumbra_trail_ribbon", DefaultVertexFormat.NEW_ENTITY),
                    shader -> ModShaders.PENUMBRA_TRAIL_RIBBON = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/penumbra_density_splat", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.PENUMBRA_DENSITY_SPLAT = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/penumbra_blur", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.PENUMBRA_BLUR = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/penumbra_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.PENUMBRA_COMPOSITE = shader);

            // Screen-space electromagnetic static overlay
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:interference/aura_static_interference", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.AURA_STATIC_INTERFERENCE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:interference/heat_haze_interference", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.HEAT_HAZE_INTERFERENCE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:hud/barrel_distortion", DefaultVertexFormat.POSITION_COLOR),
                    shader -> ModShaders.HUD_BARREL_DISTORTION = shader);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shaders", e);
        }
    }
}
