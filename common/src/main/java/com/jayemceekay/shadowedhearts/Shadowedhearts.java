package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.platform.events.RenderEvent;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.core.ModBlockEntities;
import com.jayemceekay.shadowedhearts.core.ModBlocks;
import com.jayemceekay.shadowedhearts.core.ModItems;
import com.jayemceekay.shadowedhearts.core.ModMenuTypes;
import com.jayemceekay.shadowedhearts.core.ModParticleTypes;
import com.jayemceekay.shadowedhearts.network.ModNetworking;
import com.jayemceekay.shadowedhearts.properties.ShadowPropertyRegistration;
import com.jayemceekay.shadowedhearts.server.AuraServerSync;
import com.jayemceekay.shadowedhearts.snag.SnagEvents;
import com.jayemceekay.shadowedhearts.showdown.ShowdownRuntimePatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class Shadowedhearts {

    public static final String MOD_ID = "shadowedhearts";

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
        ElementalTypes.INSTANCE.register(new ElementalType(
                "shadow", Component.literal("Shadow"),
                0x604E82, 19, ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, "textures/gui/types.png"),
                "shadow"
        ));

        // Patch Showdown files at runtime once per run directory
        if (!ModConfig.get().showdownPatched) {
            ShowdownRuntimePatcher.applyPatches();
            ModConfig.get().showdownPatched = true;
            ModConfig.save();
        }

        // Debug banner for micro battle instrumentation
        try {
            com.jayemceekay.shadowedhearts.showdown.MicroDebug.log("ShadowedHearts debug build loaded: micro battle instrumentation enabled");
        } catch (Throwable ignored) {}
    }
}
