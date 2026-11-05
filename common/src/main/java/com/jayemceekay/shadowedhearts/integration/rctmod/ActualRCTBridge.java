package com.jayemceekay.shadowedhearts.integration.rctmod;

import net.minecraft.network.chat.Component;
import static com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages.message;

import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.gitlab.srcmc.rctapi.api.util.Text;
import com.gitlab.srcmc.rctmod.ModCommon;
import com.gitlab.srcmc.rctmod.api.RCTMod;
import com.gitlab.srcmc.rctmod.api.data.pack.TrainerType;
import com.gitlab.srcmc.rctmod.api.utils.LevelUtils;
import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import com.google.gson.JsonParser;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActualRCTBridge implements RCTBridge {
    private final Map<String, Component> failures = new ConcurrentHashMap<>();
    private final RCTCatalogCache catalogCache = new RCTCatalogCache();
    private Map<String, String> bundledNames;

    @Override
    public int getLevelCap(ServerPlayer player) {
        try {
            int cap = LevelUtils.levelCap(player);
            failures.remove("level cap");
            return cap;
        } catch (RuntimeException | LinkageError failure) {
            warn("level cap", failure);
            return 100;
        }
    }

    @Override
    public boolean isTrainer(Entity entity) {
        try {
            boolean trainer = entity instanceof TrainerMob;
            failures.remove("trainer detection");
            return trainer;
        } catch (RuntimeException | LinkageError failure) {
            warn("trainer detection", failure);
            return false;
        }
    }

    @Override
    public TrainerIdentity trainerIdentity(Entity entity) {
        if (!isTrainer(entity)) return null;
        try {
            TrainerMob mob = (TrainerMob) entity;
            var data = RCTMod.getInstance().getTrainerManager().getData(mob);
            var type = data == null ? null : data.getType();
            TrainerIdentity identity = new TrainerIdentity(mob.getTrainerId(), type == null ? null : type.id());
            failures.remove("trainer identity");
            return identity;
        } catch (RuntimeException | LinkageError failure) {
            warn("trainer identity", failure);
            return null;
        }
    }

    @Override
    public RCTTrainerCatalog catalog(MinecraftServer server) {
        if (server == null) return RCTTrainerCatalog.unavailable(List.of(message("rct.diagnostic.server_required")));
        if (!server.isSameThread()) {
            return new RCTTrainerCatalog(true, true, List.of(), List.of(),
                    List.of(message("rct.diagnostic.server_thread_required")));
        }
        try {
            var manager = RCTMod.getInstance().getTrainerManager();
            boolean loading = manager.isLoading() || manager.isReloadRequired();
            RCTTrainerCatalog result = catalogCache.get(server, server.getResourceManager(), loading, server.getTickCount(), () -> {
                Map<String, Object> identities = new HashMap<>();
                manager.getAllData().forEach(entry -> {
                    if (entry.getValue() != null) identities.put("trainer/" + entry.getKey(), entry.getValue());
                    var registered = ModCommon.RCT.getTrainerRegistry().getById(entry.getKey());
                    if (registered != null) identities.put("registered/" + entry.getKey(), registered);
                });
                for (String id : TrainerType.ids()) identities.put("type/" + id, TrainerType.valueOf(id));
                return identities;
            }, () -> buildCatalog(server));
            failures.remove("catalog");
            return result;
        } catch (RuntimeException | LinkageError failure) {
            warn("catalog", failure);
            return catalogCache.failure(server, message("rct.diagnostic.refresh_failed"));
        }
    }

    private RCTTrainerCatalog buildCatalog(MinecraftServer server) {
        var manager = RCTMod.getInstance().getTrainerManager();
        CatalogResources resources = new CatalogResources(server.getResourceManager());
        Set<String> typeIds = new TreeSet<>(TrainerType.ids());
        List<RCTTrainerCatalog.Entry> types = new ArrayList<>();
        for (String id : typeIds) {
            List<Component> details = new ArrayList<>();
            List<Component> diagnostics = new ArrayList<>();
            TrainerType type = TrainerType.valueOf(id);
            String source = resources.describeDefinition("trainer_types/", id, message("rct.type_definition"), details, diagnostics);
            resources.addCollisionDiagnostics("trainer_types/", id, diagnostics);
            addTranslationDetail(type.name(), details);
            if (type.symbol() != null && !type.symbol().isBlank()) details.add(message("rct.symbol", type.symbol()));
            details.add(message("rct.color", String.format(Locale.ROOT, "%06X", type.color() & 0xFFFFFF)));
            String name = name(type.name(), id);
            types.add(new RCTTrainerCatalog.Entry(id, name, id, source, details, diagnostics, displayName(type.name(), name)));
        }

        List<RCTTrainerCatalog.Entry> trainers = new ArrayList<>();
        Set<String> trainerIds = new TreeSet<>();
        manager.getAllData().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String id = entry.getKey();
            trainerIds.add(id);
            List<Component> details = new ArrayList<>();
            List<Component> diagnostics = new ArrayList<>();
            String display = humanName(id);
            Component displayName = null;
            String typeId = "";
            String source = "";
            try {
                var data = entry.getValue();
                var team = data == null ? null : data.getTrainerTeam();
                var type = data == null ? null : data.getType();
                typeId = type == null || type.id() == null ? "" : type.id();
                source = resources.describeDefinition("trainers/", id, message("rct.team_definition"), details, diagnostics);
                resources.addCollisionDiagnostics("trainers/", id, diagnostics);
                var metadata = manager.findResource(id, "mobs");
                if (metadata.isPresent()) {
                    ResourceLocation metadataResource = metadata.get();
                    resources.describe(metadataResource, message("rct.trainer_metadata"), details, diagnostics);
                    String configuredType = resources.configuredType(metadataResource, diagnostics);
                    if (configuredType != null && !typeIds.contains(configuredType)) {
                        diagnostics.add(message("rct.diagnostic.unregistered_type", configuredType));
                    }
                } else {
                    diagnostics.add(message("rct.diagnostic.metadata_missing"));
                }
                if (typeId.isBlank() || !typeIds.contains(typeId)) {
                    diagnostics.add(message("rct.diagnostic.resolved_type_missing"));
                } else {
                    ResourceLocation typeResource = ResourceLocation.fromNamespaceAndPath("rctmod", "trainer_types/" + typeId + ".json");
                    resources.describe(typeResource, message("rct.type_definition"), details, diagnostics);
                }
                if (team == null) {
                    diagnostics.add(message("rct.diagnostic.team_missing"));
                } else {
                    display = name(team.getName(), id);
                    displayName = displayName(team.getName(), display);
                    addTranslationDetail(team.getName(), details);
                    if (team.getIdentity() != null && !team.getIdentity().isBlank()) details.add(message("rct.identity", team.getIdentity()));
                    if (team.getTeam() != null) {
                        Component members = team.getTeam().stream().limit(6)
                                .map(pokemon -> message("rct.member", pokemon.getSpecies(), pokemon.getLevel()))
                                .map(value -> (Component) value)
                                .reduce((left, right) -> left.copy().append(", ").append(right))
                                .orElseGet(() -> message("value.none"));
                        details.add(message("rct.party", team.getTeam().size(), members));
                    }
                }
                if (data != null) {
                    String series = data.getSeries().sorted().limit(8).reduce((left, right) -> left + ", " + right).orElse("");
                    details.add(message("rct.series", series.isEmpty() ? message("value.none") : Component.literal(series)));
                }
                if (!(ModCommon.RCT.getTrainerRegistry().getById(id) instanceof TrainerNPC)) {
                    diagnostics.add(message("rct.diagnostic.npc_not_registered"));
                }
            } catch (RuntimeException | LinkageError failure) {
                diagnostics.add(message("rct.diagnostic.details_unreadable", failure.getClass().getSimpleName()));
                warn("catalog entry", failure);
            }
            trainers.add(new RCTTrainerCatalog.Entry(id, display, typeId, source, details, diagnostics, displayName));
        });
        resources.summarize("trainers/", trainerIds, "trainer");
        resources.summarize("trainer_types/", typeIds, "type");
        return new RCTTrainerCatalog(true, false, trainers, types, resources.diagnostics);
    }

    private String name(Text text, String id) {
        if (text != null) {
            String literal = text.getLiteral();
            if (literal != null && !literal.isBlank()) return literal;
            String key = text.getTranslatable();
            if (key != null && !key.isBlank()) {
                String bundled = bundledNames().get(key);
                if (bundled != null && !bundled.isBlank()) return bundled;
            }
        }
        return humanName(id);
    }

    private static Component displayName(Text text, String fallback) {
        if (text != null && (text.getLiteral() == null || text.getLiteral().isBlank())) {
            String key = text.getTranslatable();
            if (key != null && !key.isBlank()) return Component.translatableWithFallback(key, fallback);
        }
        return null; // The catalog keeps literal names and readable fallbacks unchanged.
    }

    private Map<String, String> bundledNames() {
        if (bundledNames != null) return bundledNames;
        Map<String, String> names = new HashMap<>();
        try (InputStream input = RCTMod.class.getResourceAsStream("/assets/rctmod/lang/en_us.json")) {
            if (input != null) {
                var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                if (json.isJsonObject()) json.getAsJsonObject().entrySet().forEach(entry -> {
                    if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                        names.put(entry.getKey(), entry.getValue().getAsString());
                    }
                });
            }
        } catch (Exception failure) {
            warn("catalog labels", failure);
        }
        bundledNames = Map.copyOf(names);
        return bundledNames;
    }

    private static void addTranslationDetail(Text text, List<Component> details) {
        if (text != null && text.getTranslatable() != null && !text.getTranslatable().isBlank()) {
            details.add(message("rct.translation", text.getTranslatable()));
        }
    }

    private static String humanName(String id) {
        String value = id.replace('_', ' ').replace('-', ' ');
        StringBuilder label = new StringBuilder();
        for (String word : value.split(" +")) {
            if (word.isEmpty()) continue;
            if (!label.isEmpty()) label.append(' ');
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.isEmpty() ? id : label.toString();
    }

    /** Mirrors the pinned RCT pack scan, including its generated-pack exclusion and override order. */
    private static final class CatalogResources {
        private final Map<ResourceLocation, List<Definition>> definitions = new HashMap<>();
        private final RCTCatalogSources sources = new RCTCatalogSources();
        private final Map<ResourceLocation, String> configuredTypes = new HashMap<>();
        private final Map<ResourceLocation, Component> metadataErrors = new HashMap<>();
        private final List<Component> diagnostics = new ArrayList<>();

        private CatalogResources(ResourceManager manager) {
            manager.listPacks().filter(pack -> !pack.packId().startsWith("mod/generated_"))
                    .filter(pack -> pack.getNamespaces(PackType.SERVER_DATA).contains("rctmod"))
                    .forEach(pack -> {
                        for (String directory : List.of("trainers", "trainer_types", "mobs/trainers")) {
                            pack.listResources(PackType.SERVER_DATA, "rctmod", directory, (id, stream) -> {
                                if (id.getPath().endsWith(".json")) {
                                    definitions.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new Definition(pack.packId(), stream));
                                    sources.add(id.getPath(), pack.packId());
                                }
                            });
                        }
                    });
        }

        private String describe(ResourceLocation id, Component label, List<Component> details, List<Component> issues) {
            List<Definition> stack = definitions.getOrDefault(id, List.of());
            if (stack.isEmpty()) {
                details.add(message("rct.resource", label, id.toString()));
                issues.add(message("rct.diagnostic.source_missing", id.toString()));
                return "";
            }
            String active = stack.getLast().pack;
            details.add(message("rct.resource_pack", label, id.toString(), active));
            if (stack.size() > 1) {
                details.add(message("rct.overrides", label, stack.subList(0, stack.size() - 1).stream().map(Definition::pack).distinct().limit(5).reduce((a, b) -> a + ", " + b).orElse("")));
            }
            return active;
        }

        private String describeDefinition(String prefix, String id, Component label, List<Component> details, List<Component> issues) {
            List<String> paths = sources.paths(prefix, id);
            if (paths.size() <= 1) {
                describe(ResourceLocation.fromNamespaceAndPath("rctmod", prefix + id + ".json"), label, details, issues);
                return sources.sourcePack(prefix, id);
            }
            issues.add(message("rct.diagnostic.source_ambiguous", id));
            for (String path : paths.stream().limit(3).toList()) {
                ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath("rctmod", path);
                List<Definition> stack = definitions.get(candidate);
                details.add(message("rct.candidate", label, candidate.toString(), stack.getLast().pack));
            }
            return "";
        }

        private void summarize(String prefix, Set<String> loadedIds, String kind) {
            diagnostics.addAll(sources.summaries(prefix, loadedIds, kind));
        }

        private void addCollisionDiagnostics(String prefix, String id, List<Component> issues) {
            List<String> paths = sources.paths(prefix, id);
            if (paths.size() > 1) issues.add(message("rct.diagnostic.id_collision", id, String.join(", ", paths)));
            if (paths.stream().anyMatch(path -> !path.equals(prefix + id + ".json"))) {
                issues.add(message("rct.diagnostic.flat_ids"));
            }
        }

        private String configuredType(ResourceLocation id, List<Component> issues) {
            if (!configuredTypes.containsKey(id) && !metadataErrors.containsKey(id)) {
                List<Definition> stack = definitions.getOrDefault(id, List.of());
                if (!stack.isEmpty()) {
                    try (InputStream input = stack.getLast().stream.get();
                         InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                        var json = JsonParser.parseReader(reader);
                        String type = json.isJsonObject() && json.getAsJsonObject().has("type")
                                ? json.getAsJsonObject().get("type").getAsString() : "";
                        configuredTypes.put(id, type);
                    } catch (Exception error) {
                        metadataErrors.put(id, message("rct.diagnostic.metadata_unreadable", id.toString(), error.getClass().getSimpleName()));
                    }
                }
            }
            if (metadataErrors.containsKey(id)) issues.add(metadataErrors.get(id));
            String value = configuredTypes.get(id);
            return value == null || value.isBlank() ? null : value;
        }

        private record Definition(String pack, IoSupplier<InputStream> stream) {}
    }

    @Override
    public List<Component> diagnostics() {
        return failures.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
    }

    private void warn(String operation, Throwable failure) {
        Component message = message("rct.diagnostic.lookup_failed", message("rct.operation." + operation.replace(' ', '_')), failure.getClass().getSimpleName());
        Component previous = failures.put(operation, message);
        if (!message.equals(previous)) {
            Shadowedhearts.LOGGER.warn("Shadowed Hearts RCT {} lookup failed; using the integration fallback", operation, failure);
        }
    }
}
