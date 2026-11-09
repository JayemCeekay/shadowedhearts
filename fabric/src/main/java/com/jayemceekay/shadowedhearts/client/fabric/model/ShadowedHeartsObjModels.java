package com.jayemceekay.shadowedhearts.client.fabric.model;

import com.google.gson.JsonObject;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ShadowedHeartsObjModels {
    // Packs for earlier releases may supply the old parent when replacing the model.
    private static final Set<String> OBJ_PARENTS = Set.of("shadowedhearts:builtin/obj", "special-model-loader:builtin/obj");
    private static final List<ResourceLocation> MODELS = List.of(
            ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "block/relic_stone"));

    private final Map<ResourceLocation, BlockModel> roots = new HashMap<>();
    private final Map<BlockModel, Geometry> geometryByRoot = new IdentityHashMap<>();

    private record Geometry(ShadowedHeartsObjMesh mesh, boolean flipV) {}

    private ShadowedHeartsObjModels(ResourceManager resources) {
        for (ResourceLocation id : MODELS) {
            try (var reader = resources.openAsReader(ModelBakery.MODEL_LISTER.idToFile(id))) {
                JsonObject json = GsonHelper.parse(reader);
                if (!OBJ_PARENTS.contains(GsonHelper.getAsString(json, "parent", ""))) {
                    continue;
                }

                ResourceLocation path = ResourceLocation.parse(GsonHelper.getAsString(json, "model"));
                var geometry = new Geometry(ShadowedHeartsObjMesh.read(resources, path),
                        GsonHelper.getAsBoolean(json, "flip_v", false));
                // A real BlockModel root lets vanilla resolve child item textures, overrides and display transforms.
                json.remove("parent");
                BlockModel root = BlockModel.fromString(json.toString());
                root.name = id.toString();
                roots.put(id, root);
                geometryByRoot.put(root, geometry);
            } catch (IOException | RuntimeException e) {
                Shadowedhearts.LOGGER.warn("Could not load OBJ model {}", id, e);
                roots.put(id, BlockModel.fromString(ModelBakery.MISSING_MODEL_MESH));
            }
        }
    }

    public static void register() {
        PreparableModelLoadingPlugin.register(
                (resources, executor) -> CompletableFuture.supplyAsync(() -> new ShadowedHeartsObjModels(resources), executor),
                (models, context) -> {
                    context.resolveModel().register(request -> models.roots.get(request.id()));
                    context.modifyModelAfterBake().register(ModelModifier.OVERRIDE_PHASE, models::bake);
                });
    }

    private BakedModel bake(BakedModel baked, ModelModifier.AfterBake.Context context) {
        if (baked == null || !(context.sourceModel() instanceof BlockModel model)) {
            return baked;
        }
        Geometry geometry = geometryByRoot.get(model.getRootModel());
        if (geometry == null || !model.getElements().isEmpty()) {
            return baked;
        }
        try {
            var result = new SimpleBakedModel.Builder(model, baked.getOverrides(), baked.isGui3d())
                    .particle(baked.getParticleIcon());
            geometry.mesh().bake(context.settings(), geometry.flipV(), diffuseMap ->
                    context.textureGetter().apply(material(model, diffuseMap)))
                    .forEach(result::addUnculledFace);
            return result.build();
        } catch (RuntimeException e) {
            Shadowedhearts.LOGGER.warn("Could not bake OBJ model {}", model.name, e);
            return context.baker().bake(ModelBakery.MISSING_MODEL_LOCATION, context.settings());
        }
    }

    private static Material material(BlockModel model, String diffuseMap) {
        if (diffuseMap == null || diffuseMap.isEmpty()) {
            return new Material(TextureAtlas.LOCATION_BLOCKS, MissingTextureAtlasSprite.getLocation());
        }
        if (diffuseMap.startsWith("#")) {
            return model.getMaterial(diffuseMap);
        }
        return new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.parse(diffuseMap));
    }
}
