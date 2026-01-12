package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.player.PlayerDataExtensionRegistry;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteEmitters;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.core.*;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import com.jayemceekay.shadowedhearts.properties.ShadowPropertyRegistration;
import com.jayemceekay.shadowedhearts.restrictions.ShadowRestrictions;
import com.jayemceekay.shadowedhearts.server.*;
import com.jayemceekay.shadowedhearts.snag.SnagEvents;
import com.jayemceekay.shadowedhearts.util.ShadowedHeartsPlayerData;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

public final class Shadowedhearts {

    public static final String MOD_ID = "shadowedhearts";

    public static void init() {
        // ModConfig.load() and SnagConfig.load() are now handled by platform-specific config registration
        System.out.println("[ShadowedHearts] Initializing mod...");
        ModItemComponents.init();
        ModBlocks.init();
        ModItems.init();
        ModCreativeTabs.init();
        SnagAccessoryBridgeHolder.init();
        ModBlockEntities.init();
        ModMenuTypes.init();
        AuraServerSync.init();
        ModSounds.init();
        ShadowAspectValidator.init();
        SnagEvents.init();
        ModCommands.init();
        ModParticleTypes.register();
        ShadowPropertyRegistration.register();
        ShadowProgressionManager.init();
        ShadowRestrictions.init();
        PurificationStepTracker.INSTANCE.init();
        BattleSentOnceListener.INSTANCE.init();
        WildShadowSpawnListener.init();
        AuraBroadcastQueue.init();
        ShadowDropListener.init();
        NPCShadowInjector.init();
        LuminousMoteEmitters.init();
        PlayerDataExtensionRegistry.INSTANCE.register(ShadowedHeartsPlayerData.NAME, ShadowedHeartsPlayerData.class, false);
        HeartGaugeConfig.ensureLoaded();
        ReloadListenerRegistry.register(PackType.SERVER_DATA, SpeciesTagManager.INSTANCE);

        ElementalTypes.register(new ElementalType(
                "shadow", Component.literal("Shadow"),
                0x604E82, 19, ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, "textures/gui/types.png"),
                "Shadow"
        ));

        ElementalTypes.register(new ElementalType("shadow-locked", Component.literal("Locked"), 0x1F1F1F, 20, ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, "textures/gui/types.png"), "shadow-locked"));
    }
}
