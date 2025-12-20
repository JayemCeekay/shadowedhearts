package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.core.*;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import com.jayemceekay.shadowedhearts.network.ModNetworking;
import com.jayemceekay.shadowedhearts.properties.ShadowPropertyRegistration;
import com.jayemceekay.shadowedhearts.restrictions.ShadowRestrictions;
import com.jayemceekay.shadowedhearts.server.*;
import com.jayemceekay.shadowedhearts.showdown.ShowdownRuntimePatcher;
import com.jayemceekay.shadowedhearts.snag.SnagEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class Shadowedhearts {

    public static final String MOD_ID = "shadowedhearts";

    public static void init() {
        // Load config first
        ModConfig.load();

        // Common init across platforms
        ModItemComponents.init();
        ModBlocks.init();
        ModItems.init();
        SnagAccessoryBridgeHolder.init();
        ModBlockEntities.init();
        ModMenuTypes.init();
        ModNetworking.register();
        AuraServerSync.init();
        ModSounds.init();
        // Server-side validator to ensure required Shadow aspects exist when aspects change
        ShadowAspectValidator.init();
        SnagEvents.init();
        ModCommands.init();
        // Ensure particle types are registered cross-platform
        ModParticleTypes.register();
        ShadowPropertyRegistration.register();
        ShadowProgressionManager.init();
        ShadowRestrictions.init();
        PurificationStepTracker.INSTANCE.init();
        BattleSentOnceListener.INSTANCE.init();
        //WildShadowSpawnListener.init();
        // Enable NPC Shadow Aspects injector (pre-battle conversion based on NPC tags)
        NPCShadowInjector.init();

        ElementalTypes.register(new ElementalType(
                "shadow", Component.literal("Shadow"),
                0x604E82, 19, ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, "textures/gui/types.png"),
                "Shadow"
        ));

        ElementalTypes.register(new ElementalType("shadow-locked", Component.literal("Locked"), 0x1F1F1F, 20, ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, "textures/gui/types.png"), "shadow-locked"));


        // Always attempt to (re)apply Showdown runtime patches; the patcher is idempotent and upgrade-safe.
        // This ensures new patches (like starting Shadow Engine) are applied even if an older flag was set.
        com.cobblemon.mod.common.Cobblemon.INSTANCE.getShowdownThread().queue(service -> {
            System.out.println("Applying Showdown patches...");
            ShowdownRuntimePatcher.applyPatches();
            if (!ModConfig.get().showdownPatched) {
                ModConfig.get().showdownPatched = true;
                ModConfig.save();
            }
            return kotlin.Unit.INSTANCE; // Kotlin interop: Function1 requires a Unit return
        });
    }
}
