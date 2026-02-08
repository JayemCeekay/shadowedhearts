package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.player.PlayerDataExtensionRegistry;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;
import com.cobblemon.mod.relocations.graalvm.polyglot.Context;
import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers;
import com.jayemceekay.shadowedhearts.aura.AuraReaderEvents;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.core.*;
import com.jayemceekay.shadowedhearts.data.ShadowAspectPresets;
import com.jayemceekay.shadowedhearts.data.ShadowPools;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import com.jayemceekay.shadowedhearts.integration.mega_showdown.MegaShowdownBridgeHolder;
import com.jayemceekay.shadowedhearts.pokemon.properties.PropertyRegistration;
import com.jayemceekay.shadowedhearts.restrictions.ShadowRestrictions;
import com.jayemceekay.shadowedhearts.server.*;
import com.jayemceekay.shadowedhearts.server.listeners.ShadowCatchRateListener;
import com.jayemceekay.shadowedhearts.server.listeners.StarterShadowListener;
import com.jayemceekay.shadowedhearts.showdown.ShowdownRuntimePatcher;
import com.jayemceekay.shadowedhearts.snag.SnagEvents;
import com.jayemceekay.shadowedhearts.util.ShadowedHeartsPlayerData;
import com.jayemceekay.shadowedhearts.worldgen.ImpactScheduler;
import com.jayemceekay.shadowedhearts.worldgen.ModStructures;
import com.jayemceekay.shadowedhearts.worldgen.PlayerActivityHeatmap;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import kotlin.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

public final class Shadowedhearts {

    public static final String MOD_ID = "shadowedhearts";

    public static final Logger LOGGER = LoggerFactory.getLogger(Shadowedhearts.class);

    public interface FeatureAdder {
        void add(ResourceKey<PlacedFeature> feature, GenerationStep.Decoration step, TagKey<Biome> validTag);
    }

    public static FeatureAdder featureAdder;
    public static GameRules.Key<GameRules.BooleanValue> RULE_SHADOW_STARTERS;

    public static void addFeatureToWorldGen(ResourceKey<PlacedFeature> feature, GenerationStep.Decoration step, TagKey<Biome> validTag) {
        if (featureAdder != null) {
            featureAdder.add(feature, step, validTag);
        }
    }

    public static void init() {
        LOGGER.info("[ShadowedHearts] Initializing mod...");
        SnagAccessoryBridgeHolder.init();
        MegaShowdownBridgeHolder.init();
        ModItemComponents.init();
        ModBlocks.init();
        ModItems.init();
        LifecycleEvent.SETUP.register(ModCauldronInteractions::register);
        ModCreativeTabs.init();
        ModBlockEntities.init();
        ModPoiTypes.init();
        ModMenuTypes.init();
        ModCriteriaTriggers.init();
        AuraServerSync.init();
        AuraReaderEvents.init();
        ModSounds.init();
        ShadowAspectValidator.init();
        ShadowAspectPresets.init();
        ShadowPools.init();
        SnagEvents.init();
        ModCommands.init();
        ModParticleTypes.register();
        LifecycleEvent.SETUP.register(PropertyRegistration::register);
        ShadowProgressionManager.init();
        ShadowRestrictions.init();
        PurificationStepTracker.INSTANCE.init();
        BattleSentOnceListener.INSTANCE.init();
        WildShadowSpawnListener.init();
        ShadowCatchRateListener.init();
        StarterShadowListener.init();
        AuraBroadcastQueue.init();
        ShadowDropListener.init();
        NPCShadowInjector.init();
        PlayerActivityHeatmap.init();
        ImpactScheduler.init();
        ShadowMeteoroidProximityHandler.init();
        ModStructures.init();
        PlayerDataExtensionRegistry.INSTANCE.register(ShadowedHeartsPlayerData.NAME, ShadowedHeartsPlayerData.class, false);
        HeartGaugeConfig.ensureLoaded();
        ReloadListenerRegistry.register(PackType.SERVER_DATA, SpeciesTagManager.INSTANCE);

        RULE_SHADOW_STARTERS = GameRules.register("doShadowStarters", GameRules.Category.MISC, GameRules.BooleanValue.create(false));

        ElementalTypes.register(new ElementalType(
                "shadow", Component.literal("Shadow"),
                0x604E82, 19, ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, "textures/gui/types.png"),
                "shadow"
        ));
        


        ElementalTypes.register(new ElementalType("shadow-locked", Component.literal("Locked"), 0x1F1F1F, 20, ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, "textures/gui/types.png"), "shadow-locked"));

        Cobblemon.INSTANCE.getShowdownThread().queue(showdownService -> {
            if (showdownService instanceof GraalShowdownService service) {
                Field field = null;
                try {
                    field = GraalShowdownService.class.getDeclaredField("context");
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }
                field.setAccessible(true);
                Context context = null;
                try {
                    context = (Context) field.get(service);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                ShowdownRuntimePatcher.DynamicInjector.prepareContext(context);
                if (ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) {
                    ShowdownRuntimePatcher.DynamicInjector.injectConfig(context);
                }
            }
            return Unit.INSTANCE;
        });
    }

    public static void injectShowdownConfig() {
        if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) {
            return;
        }
        Cobblemon.INSTANCE.getShowdownThread().queue(showdownService -> {
            if (showdownService instanceof GraalShowdownService service) {
                Field field = null;
                try {
                    field = GraalShowdownService.class.getDeclaredField("context");
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }
                field.setAccessible(true);
                Context context = null;
                try {
                    context = (Context) field.get(service);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                ShowdownRuntimePatcher.DynamicInjector.injectConfig(context);
            }
            return Unit.INSTANCE;
        });
    }
}
