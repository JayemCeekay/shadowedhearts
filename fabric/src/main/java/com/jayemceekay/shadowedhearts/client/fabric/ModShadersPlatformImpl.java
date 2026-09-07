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

                // Poké Ball glow overlay — texture-based (additive + fullbright)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/ball_glow"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.BALL_GLOW = program
                );

                // Procedural orb glow + starburst + halo (split from ball_glow)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/ball_orb_glow"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.BALL_ORB_GLOW = program
                );

                // Ball trail ribbon
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/ball_trail"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.BALL_TRAIL = program
                );

                // Lens flare (procedural streak + spike pattern)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/snag_flare"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.SNAG_FLARE = program
                );

                // Orb shell (sphere-projected noise with shell band)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/snag_orb"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.SNAG_ORB = program
                );

                // Pokémon dissolve (noise-based dissolution with glowing edge)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/snag_dissolve"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.SNAG_DISSOLVE = program
                );

                // Bloom pipeline — simple Gaussian blur (no depth awareness)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/snag_bloom_blur"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SNAG_BLOOM_BLUR = program
                );

                // Bloom pipeline — additive composite over scene
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/snag_bloom_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SNAG_BLOOM_COMPOSITE = program
                );

                // Snag beam density metaball pipeline (density splat + composite)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/snag_beam_density"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SNAG_BEAM_DENSITY = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/snag_beam_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SNAG_BEAM_COMPOSITE = program
                );

                // Dark Ball cohesive volumetric siphon pipeline
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:darkball/dark_ball_mask"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.DARK_BALL_MASK = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:darkball/dark_ball_proxy_depth"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.DARK_BALL_PROXY_DEPTH = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:darkball/dark_ball_volume_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.DARK_BALL_VOLUME_COMPOSITE = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:darkball/dark_ball_silhouette_distance_seed"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.DARK_BALL_SILHOUETTE_DISTANCE_SEED = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:darkball/dark_ball_silhouette_distance_jump"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.DARK_BALL_SILHOUETTE_DISTANCE_JUMP = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:darkball/dark_ball_reduced_upsample"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.DARK_BALL_REDUCED_UPSAMPLE = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:darkball/dark_ball_surface_splat"),
                        ModShaders.DARK_BALL_MANUAL_ENTITY_FORMAT,
                        program -> ModShaders.DARK_BALL_SURFACE_SPLAT = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:darkball/dark_ball_surface_splat_resolve"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.DARK_BALL_SURFACE_SPLAT_RESOLVE = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:darkball/dark_ball_siphon_surface_mesh"),
                        ModShaders.DARK_BALL_MANUAL_ENTITY_FORMAT,
                        program -> ModShaders.DARK_BALL_SIPHON_SURFACE_MESH = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:darkball/dark_ball_edge_tongues"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.DARK_BALL_EDGE_TONGUES = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:darkball/dark_ball_fbo_preview"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.DARK_BALL_FBO_PREVIEW = program
                );

                // Snag trail orange smoke density pipeline (FBM noise + warm orange composite)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/snag_trail_density"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SNAG_TRAIL_DENSITY = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/snag_trail_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SNAG_TRAIL_COMPOSITE = program
                );

                // Snag trail purple mote density pipeline (separate from capture beam)
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/snag_mote_density"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SNAG_MOTE_DENSITY = program
                );
                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:snag/snag_mote_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SNAG_MOTE_COMPOSITE = program
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

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_density"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SHADOW_POKEMON_AURA_DENSITY = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_gui_density"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SHADOW_POKEMON_AURA_GUI_DENSITY = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_mask"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModShaders.SHADOW_POKEMON_AURA_MASK = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_COMPOSITE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_gui_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_GUI_COMPOSITE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_pixel_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_PIXEL_COMPOSITE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_gui_pixel_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_GUI_PIXEL_COMPOSITE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_colosseum_density"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_DENSITY = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_colosseum_gui_density"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_GUI_DENSITY = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_colosseum_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_COMPOSITE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_colosseum_gui_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_GUI_COMPOSITE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_colosseum_pixel_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_PIXEL_COMPOSITE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_colosseum_gui_pixel_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_GUI_PIXEL_COMPOSITE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_density"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_DENSITY = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_gui_density"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_DENSITY = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_filament_density"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_FILAMENT_DENSITY = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_gui_filament_density"),
                        DefaultVertexFormat.PARTICLE,
                        program -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_FILAMENT_DENSITY = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_COMPOSITE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_gui_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_COMPOSITE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_pixel_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_PIXEL_COMPOSITE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_xd_faithful_gui_pixel_composite"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_PIXEL_COMPOSITE = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_present"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_PRESENT = program
                );

                registrationContext.register(
                        ResourceLocation.parse("shadowedhearts:aura/shadow_pokemon_aura_gui_present"),
                        DefaultVertexFormat.POSITION_TEX,
                        program -> ModShaders.SHADOW_POKEMON_AURA_GUI_PRESENT = program
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
