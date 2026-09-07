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

            // Poké Ball glow overlay — texture-based (additive + fullbright)
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/ball_glow", DefaultVertexFormat.NEW_ENTITY),
                    shader -> ModShaders.BALL_GLOW = shader);

            // Procedural orb glow + starburst + halo (split from ball_glow)
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/ball_orb_glow", DefaultVertexFormat.NEW_ENTITY),
                    shader -> ModShaders.BALL_ORB_GLOW = shader);

            // Ball trail ribbon
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/ball_trail", DefaultVertexFormat.NEW_ENTITY),
                    shader -> ModShaders.BALL_TRAIL = shader);

            // Lens flare (procedural streak + spike pattern)
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/snag_flare", DefaultVertexFormat.NEW_ENTITY),
                    shader -> ModShaders.SNAG_FLARE = shader);

            // Orb shell (sphere-projected noise with shell band)
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/snag_orb", DefaultVertexFormat.NEW_ENTITY),
                    shader -> ModShaders.SNAG_ORB = shader);

            // Pokémon dissolve (noise-based dissolution with glowing edge)
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/snag_dissolve", DefaultVertexFormat.NEW_ENTITY),
                    shader -> ModShaders.SNAG_DISSOLVE = shader);

            // Bloom pipeline — simple Gaussian blur (no depth awareness)
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/snag_bloom_blur", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SNAG_BLOOM_BLUR = shader);

            // Bloom pipeline — additive composite over scene
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/snag_bloom_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SNAG_BLOOM_COMPOSITE = shader);

            // Snag beam density metaball pipeline (density splat + composite)
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/snag_beam_density", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.SNAG_BEAM_DENSITY = shader);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/snag_beam_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SNAG_BEAM_COMPOSITE = shader);

            // Dark Ball captured-mask, fused-surface-splat, and siphon pipeline
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:darkball/dark_ball_mask", DefaultVertexFormat.NEW_ENTITY),
                    shader -> ModShaders.DARK_BALL_MASK = shader);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:darkball/dark_ball_proxy_depth", DefaultVertexFormat.NEW_ENTITY),
                    shader -> ModShaders.DARK_BALL_PROXY_DEPTH = shader);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:darkball/dark_ball_volume_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.DARK_BALL_VOLUME_COMPOSITE = shader);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:darkball/dark_ball_silhouette_distance_seed", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.DARK_BALL_SILHOUETTE_DISTANCE_SEED = shader);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:darkball/dark_ball_silhouette_distance_jump", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.DARK_BALL_SILHOUETTE_DISTANCE_JUMP = shader);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:darkball/dark_ball_reduced_upsample", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.DARK_BALL_REDUCED_UPSAMPLE = shader);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:darkball/dark_ball_surface_splat", ModShaders.DARK_BALL_MANUAL_ENTITY_FORMAT),
                    shader -> ModShaders.DARK_BALL_SURFACE_SPLAT = shader);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:darkball/dark_ball_surface_splat_resolve", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.DARK_BALL_SURFACE_SPLAT_RESOLVE = shader);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:darkball/dark_ball_siphon_surface_mesh", ModShaders.DARK_BALL_MANUAL_ENTITY_FORMAT),
                    shader -> ModShaders.DARK_BALL_SIPHON_SURFACE_MESH = shader);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:darkball/dark_ball_edge_tongues", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.DARK_BALL_EDGE_TONGUES = shader);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:darkball/dark_ball_fbo_preview", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.DARK_BALL_FBO_PREVIEW = shader);

            // Snag trail orange smoke density pipeline (FBM noise + warm orange composite)
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/snag_trail_density", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.SNAG_TRAIL_DENSITY = shader);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/snag_trail_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SNAG_TRAIL_COMPOSITE = shader);

            // Snag trail purple mote density pipeline (separate from capture beam)
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/snag_mote_density", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.SNAG_MOTE_DENSITY = shader);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:snag/snag_mote_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SNAG_MOTE_COMPOSITE = shader);

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

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_density", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_DENSITY = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_gui_density", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_GUI_DENSITY = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_mask", DefaultVertexFormat.NEW_ENTITY),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_MASK = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_COMPOSITE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_gui_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_GUI_COMPOSITE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_pixel_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_PIXEL_COMPOSITE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_gui_pixel_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_GUI_PIXEL_COMPOSITE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_colosseum_density", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_DENSITY = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_colosseum_gui_density", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_GUI_DENSITY = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_colosseum_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_COMPOSITE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_colosseum_gui_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_GUI_COMPOSITE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_colosseum_pixel_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_PIXEL_COMPOSITE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_colosseum_gui_pixel_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_GUI_PIXEL_COMPOSITE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_density", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_DENSITY = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_gui_density", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_DENSITY = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_filament_density", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_FILAMENT_DENSITY = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_gui_filament_density", DefaultVertexFormat.PARTICLE),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_FILAMENT_DENSITY = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_COMPOSITE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_gui_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_COMPOSITE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_pixel_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_PIXEL_COMPOSITE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_gui_pixel_composite", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_PIXEL_COMPOSITE = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_present", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_PRESENT = shader);

            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), "shadowedhearts:aura/shadow_pokemon_aura_gui_present", DefaultVertexFormat.POSITION_TEX),
                    shader -> ModShaders.SHADOW_POKEMON_AURA_GUI_PRESENT = shader);

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
