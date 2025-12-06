package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.core.*;
import com.jayemceekay.shadowedhearts.network.ModNetworking;
import com.jayemceekay.shadowedhearts.properties.ShadowPropertyRegistration;
import com.jayemceekay.shadowedhearts.server.AuraServerSync;
import com.jayemceekay.shadowedhearts.server.ShadowProgressionManager;
import com.jayemceekay.shadowedhearts.showdown.ShowdownRuntimePatcher;
import com.jayemceekay.shadowedhearts.snag.SnagEvents;
import com.jayemceekay.shadowedhearts.world.WorldspaceManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class Shadowedhearts {

    public static final String MOD_ID = "shadowedhearts";
    public static WorldspaceManager WORLDSPACE_MANAGER;

    public static void init() {
        // Load config first
        ModConfig.load();

        // Common init across platforms
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModMenuTypes.init();
        ModNetworking.register();
        AuraServerSync.init();
        SnagEvents.init();
        ModCommands.init();
        // Ensure particle types are registered cross-platform
        ModParticleTypes.register();
        ShadowPropertyRegistration.register();
        ShadowProgressionManager.init();

        WORLDSPACE_MANAGER = new WorldspaceManager();

        ElementalTypes.INSTANCE.register(new ElementalType(
                "shadow", Component.literal("Shadow"),
                0x604E82, 19, ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, "textures/gui/types.png"),
                "Shadow"
        ));

        ElementalTypes.INSTANCE.register(new ElementalType("shadow-locked", Component.literal("Locked"), 0x1F1F1F, 20, ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, "textures/gui/types.png"), "shadow-locked"));


        // Patch Showdown files at runtime once per run directory
        if (!ModConfig.get().showdownPatched) {
            com.cobblemon.mod.common.Cobblemon.INSTANCE.getShowdownThread().queue(service -> {
                ShowdownRuntimePatcher.applyPatches();
                ModConfig.get().showdownPatched = true;
                ModConfig.save();
                return kotlin.Unit.INSTANCE; // Kotlin interop: Function1 requires a Unit return
            });
        }

        /*CobblemonEvents.POKEMON_RECALL_PRE.subscribe(Priority.NORMAL, post -> {
            if( post.getOldEntity().getBattle() == null) {
                return Unit.INSTANCE;
            }
            if (OneTurnMicroController.isOneTurn(post.getPokemon().getEntity().getBattle().getBattleId())) {
                post.getOldEntity().getBattle().stop();
            }
            return Unit.INSTANCE;
        });*/
    }
}
