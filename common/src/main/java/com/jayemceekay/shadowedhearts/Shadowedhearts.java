package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.player.PlayerDataExtensionRegistry;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderBattleDetection;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderEvents;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderService;
import com.jayemceekay.shadowedhearts.common.command.ModCommands;
import com.jayemceekay.shadowedhearts.common.event.battle.BattleSentOnceListener;
import com.jayemceekay.shadowedhearts.common.event.battle.ShadowDropListener;
import com.jayemceekay.shadowedhearts.common.event.player.PurificationStepTracker;
import com.jayemceekay.shadowedhearts.common.event.pokemon.ShadowAspectValidator;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundService;
import com.jayemceekay.shadowedhearts.common.shadow.NPCShadowInjector;
import com.jayemceekay.shadowedhearts.common.shadow.StarterShadowListener;
import com.jayemceekay.shadowedhearts.common.shadow.WildShadowSpawnListener;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowHyperTraining;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictions;
import com.jayemceekay.shadowedhearts.common.snag.DeferredSnagDelivery;
import com.jayemceekay.shadowedhearts.common.snag.ShadowCatchRateListener;
import com.jayemceekay.shadowedhearts.common.snag.SnagEvents;
import com.jayemceekay.shadowedhearts.common.voidshadow.VoidShadowService;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.content.infusion.InfuserPokemonRecoveryData;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallSites;
import com.jayemceekay.shadowedhearts.data.ShadowAspectPresets;
import com.jayemceekay.shadowedhearts.data.ShadowPools;
import com.jayemceekay.shadowedhearts.data.ShadowLearnsets;
import com.jayemceekay.shadowedhearts.data.SpeciesTagManager;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import com.jayemceekay.shadowedhearts.integration.ftbchunks.FTBChunksClaimBridgeHolder;
import com.jayemceekay.shadowedhearts.integration.mega_showdown.MegaShowdownBridgeHolder;
import com.jayemceekay.shadowedhearts.integration.rctmod.RCTBridgeHolder;
import com.jayemceekay.shadowedhearts.network.aura.AuraBroadcastQueue;
import com.jayemceekay.shadowedhearts.network.aura.AuraServerSync;
import com.jayemceekay.shadowedhearts.network.sync.AuraStyleDefaultSync;
import com.jayemceekay.shadowedhearts.network.sync.DarkBallSettingsSync;
import com.jayemceekay.shadowedhearts.network.sync.HeartGaugeSettingsSync;
import com.jayemceekay.shadowedhearts.network.sync.ItemRuleSettingsSync;
import com.jayemceekay.shadowedhearts.network.sync.MoveLifecycleSettingsSync;
import com.jayemceekay.shadowedhearts.network.sync.NullCharmEffectSync;
import com.jayemceekay.shadowedhearts.network.sync.ShadowBattleSettingsSync;
import com.jayemceekay.shadowedhearts.network.sync.ShadowRestrictionSettingsSync;
import com.jayemceekay.shadowedhearts.network.sync.SnagMachineSettingsSync;
import com.jayemceekay.shadowedhearts.pokemon.properties.PropertyRegistration;
import com.jayemceekay.shadowedhearts.registry.ModBlockEntities;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import com.jayemceekay.shadowedhearts.registry.ModCauldronInteractions;
import com.jayemceekay.shadowedhearts.registry.ModCreativeTabs;
import com.jayemceekay.shadowedhearts.registry.ModInfusionRecipes;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import com.jayemceekay.shadowedhearts.registry.ModMenus;
import com.jayemceekay.shadowedhearts.registry.ModPoiTypes;
import com.jayemceekay.shadowedhearts.registry.ModSounds;
import com.jayemceekay.shadowedhearts.registry.util.ModItemComponents;
import com.jayemceekay.shadowedhearts.registry.util.ModParticleTypes;
import com.jayemceekay.shadowedhearts.showdown.ShowdownLifecycle;
import com.jayemceekay.shadowedhearts.storage.player.ShadowedHeartsPlayerData;
import com.jayemceekay.shadowedhearts.storage.purification.link.PurificationLinkManager;
import com.jayemceekay.shadowedhearts.world.gen.ModStructures;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Shadowedhearts {

    public static final String MOD_ID = "shadowedhearts";

    public static final Logger LOGGER = LoggerFactory.getLogger(Shadowedhearts.class);

    public static ElementalType SH_SHADOW_TYPE;

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
        initializeIntegrations();
        initializeContent();
        initializeSynchronization();
        initializeGameplay();
        registerDataReloadListeners();
        registerShadowRulesAndTypes();
        ShowdownLifecycle.initialize();
    }

    private static void initializeIntegrations() {
        SnagAccessoryBridgeHolder.init();
        FTBChunksClaimBridgeHolder.init();
        MegaShowdownBridgeHolder.init();
        RCTBridgeHolder.init();
    }

    private static void initializeContent() {
        ModItemComponents.init();
        ModBlocks.init();
        ModItems.init();
        LifecycleEvent.SETUP.register(ModCauldronInteractions::register);
        ModCreativeTabs.init();
        ModBlockEntities.init();
        ModInfusionRecipes.init();
        ModMenus.init();
        TickEvent.SERVER_POST.register(server -> {
            if (server.getTickCount() % 100 == 0) {
                InfuserPokemonRecoveryData.retry(server);
            }
        });
        ModPoiTypes.init();
        ModCriteriaTriggers.init();
    }

    private static void initializeSynchronization() {
        AuraServerSync.init();
        AuraStyleDefaultSync.init();
        NullCharmEffectSync.init();
        DarkBallSettingsSync.init();
        ShadowRestrictionSettingsSync.init();
        HeartGaugeSettingsSync.init();
        SnagMachineSettingsSync.init();
        MoveLifecycleSettingsSync.init();
        ShadowBattleSettingsSync.init();
        ItemRuleSettingsSync.init();
    }

    private static void initializeGameplay() {
        AuraReaderService.init();
        AuraReaderEvents.init();
        AuraReaderBattleDetection.init();
        ModSounds.init();
        ShadowAspectValidator.init();
        ShadowAspectPresets.init();
        ShadowPools.init();
        ShadowLearnsets.init();
        SnagEvents.init();
        DeferredSnagDelivery.init();
        ModCommands.init();
        ModParticleTypes.register();
        LifecycleEvent.SETUP.register(PropertyRegistration::register);
        ShadowRestrictions.init();
        ShadowHyperTraining.init();
        PurificationStepTracker.INSTANCE.init();
        PurificationLinkManager.INSTANCE.init();
        BattleSentOnceListener.INSTANCE.init();
        WildShadowSpawnListener.init();
        VoidShadowService.init();
        UmbrafallSites.init();
        MoonwoundService.init();
        ShadowCatchRateListener.init();
        StarterShadowListener.init();
        AuraBroadcastQueue.init();
        ShadowDropListener.init();
        NPCShadowInjector.init();
        ModStructures.init();
        PlayerDataExtensionRegistry.INSTANCE.register(ShadowedHeartsPlayerData.NAME, ShadowedHeartsPlayerData.class, false);
    }

    private static void registerDataReloadListeners() {
        HeartGaugeConfig.ensureLoaded();
        ReloadListenerRegistry.register(PackType.SERVER_DATA, SpeciesTagManager.INSTANCE);
        ReloadListenerRegistry.register(PackType.SERVER_DATA,
                (ResourceManagerReloadListener) resources -> HeartGaugeConfig.reload());
    }

    private static void registerShadowRulesAndTypes() {
        RULE_SHADOW_STARTERS = GameRules.register("doShadowStarters", GameRules.Category.MISC, GameRules.BooleanValue.create(false));

        SH_SHADOW_TYPE = ElementalTypes.register(new ElementalType(
                "shadow", Component.translatable("type.shadowedhearts.shadow"),
                0x604E82, 19, 0x604E82, 0x604E82, ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, "textures/gui/types.png"),
                "shadow", ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "shadow_gem"), 1000
        ));

        ElementalTypes.register(new ElementalType("shadow-locked", Component.translatable("type.shadowedhearts.shadow_locked"), 0x1F1F1F, 20, 0x1F1F1F,0x1F1F1F, ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, "textures/gui/types.png"), "shadow-locked", ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "shadow_gem"), 1000));
    }

    /** Retained as the common entry point for loader and client configuration callbacks. */
    public static void injectShowdownConfig() {
        ShowdownLifecycle.injectConfig();
    }
}
