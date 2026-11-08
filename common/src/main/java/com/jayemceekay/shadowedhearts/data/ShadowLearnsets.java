package com.jayemceekay.shadowedhearts.data;

import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonParser;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.network.items.ShadowLearnsetsS2CPacket;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Stream;

/** Server datapack at data/&lt;species namespace&gt;/shadow_learnsets/&lt;species path&gt;.json. */
public final class ShadowLearnsets implements ResourceManagerReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShadowLearnsets.class);
    private static final ShadowLearnsets INSTANCE = new ShadowLearnsets();
    private static final FileToIdConverter FILES = FileToIdConverter.json("shadow_learnsets");
    private static volatile MinecraftServer runningServer;

    // A failed reload must not publish its data. Resolve against the resource manager the server actually committed.
    private final Map<ResourceManager, Map<ResourceLocation, ShadowLearnset>> prepared = new WeakHashMap<>();
    private ResourceManager activeManager;
    private Map<ResourceLocation, ShadowLearnset> active = Map.of();
    private Map<ResourceLocation, ShadowLearnset> lastSent = Map.of();

    private ShadowLearnsets() {}

    public static void init() {
        ReloadListenerRegistry.register(PackType.SERVER_DATA, INSTANCE);
        LifecycleEvent.SERVER_BEFORE_START.register(server -> runningServer = server);
        PlayerEvent.PLAYER_JOIN.register(player -> {
            var server = player.getServer();
            if (server != null) ShadowLearnsetsS2CPacket.send(player, INSTANCE.snapshot(server));
        });
        TickEvent.SERVER_POST.register(server -> {
            var table = INSTANCE.snapshot(server);
            if (table == INSTANCE.lastSent) return;
            INSTANCE.lastSent = table;
            for (var player : server.getPlayerList().getPlayers()) ShadowLearnsetsS2CPacket.send(player, table);
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            runningServer = null;
            INSTANCE.clear();
        });
    }

    @Nullable
    public static ShadowLearnset get(Pokemon pokemon) {
        ResourceLocation species = pokemon.getSpecies().getResourceIdentifier();
        return pokemon.isClient$common() ? ShadowLearnsetClientState.INSTANCE.get(species) : getServer(species);
    }

    @Nullable
    public static ShadowLearnset getServer(ResourceLocation species) {
        var server = runningServer;
        return server == null ? null : INSTANCE.snapshot(server).get(species);
    }

    @Override
    public synchronized void onResourceManagerReload(ResourceManager manager) {
        var parsed = read(manager);
        // Moves and species are stable on a running Cobblemon server. Throwing here rejects /reload
        // before Minecraft switches resource managers or this mod sends any new profiles.
        var server = runningServer;
        if (server != null && server.isReady()) validate(parsed);
        prepared.put(manager, parsed);
        if (activeManager == manager) activeManager = null;
    }

    private static Map<ResourceLocation, ShadowLearnset> read(ResourceManager manager) {
        Map<ResourceLocation, ShadowLearnset> parsed = new HashMap<>();
        FILES.listMatchingResources(manager).forEach((file, resource) -> {
            var id = FILES.fileToId(file);
            try (var reader = resource.openAsReader()) {
                ShadowLearnset profile = ShadowLearnset.parse(JsonParser.parseReader(reader));
                ShadowLearnsetsS2CPacket.checkSize(id, profile);
                parsed.put(id, profile);
            } catch (IOException | RuntimeException ex) {
                throw new IllegalArgumentException("Invalid " + file + " in pack " + resource.sourcePackId() + ": " + ex.getMessage(), ex);
            }
        });
        return Map.copyOf(parsed);
    }

    private synchronized Map<ResourceLocation, ShadowLearnset> snapshot(MinecraftServer server) {
        ResourceManager manager = server.getResourceManager();
        if (manager == activeManager) return active;
        try {
            // Some loader hooks hand listeners a wrapped manager. Use the committed resources if its identity differs.
            var parsed = prepared.computeIfAbsent(manager, ShadowLearnsets::read);
            if (!parsed.isEmpty() && (PokemonSpecies.INSTANCE.getSpecies().isEmpty() || Moves.count() == 0)) return active;
            validate(parsed);
            active = parsed;
        } catch (IllegalArgumentException ex) {
            // Registry references can only be checked after Cobblemon loads during initial startup.
            LOGGER.error("Could not activate shadow_learnsets: {}", ex.getMessage());
        }
        activeManager = manager;
        return active;
    }

    private static void validate(Map<ResourceLocation, ShadowLearnset> profiles) {
        profiles.forEach((id, profile) -> {
            if (PokemonSpecies.INSTANCE.getByIdentifier(id) == null) {
                throw new IllegalArgumentException("Unknown species in shadow_learnsets: " + id);
            }
            var invalidMove = Stream.concat(profile.levelUp().stream(), profile.tm().stream())
                    .map(ShadowLearnset.Entry::move)
                    .filter(move -> !ShadowMoveUtil.isShadowMove(Moves.getByName(move)))
                    .findFirst();
            if (invalidMove.isPresent()) {
                throw new IllegalArgumentException("Invalid shadow_learnsets profile " + id + ": '" + invalidMove.get()
                        + "' is not a registered Shadow-type move");
            }
        });
    }

    private synchronized void clear() {
        prepared.clear();
        activeManager = null;
        active = Map.of();
        lastSent = Map.of();
    }
}
