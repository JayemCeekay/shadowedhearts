package com.jayemceekay.shadowedhearts.world;

import com.jayemceekay.shadowedhearts.runs.RunId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the Missions dimension worldspace and per-run bounds.
 * Minimal scaffold per design doc: allocate per-run rectangles and keep a reference to the missions level.
 */
public final class WorldspaceManager {
    private WorldspaceManager() {}

    public static final int CELL = 1024; // spacing between runs
    public static final ResourceKey<Level> MISSIONS_LEVEL_KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("shadowedhearts", "missions"));

    // Assigned when the missions ServerLevel loads.
    private static @Nullable ServerLevel missionsLevel;

    public static void onMissionsLevelLoaded(ServerLevel level) {
        missionsLevel = level;
    }

    public static @Nullable ServerLevel missionsLevel() {
        return missionsLevel;
    }

    public static RunBounds allocateBounds(RunId runId) {
        int base = Math.toIntExact(runId.id() * CELL);
        return new RunBounds(new BlockPos(base, 64, base), new Vec3i(512, 128, 512));
    }

    /**
     * Places a structure template into the missions level at the specified position and rotation.
     * Returns true if placed; false if level or template missing.
     */
    public static boolean placeTemplate(ResourceLocation id, BlockPos pos, Rotation rot) {
        ServerLevel lvl = missionsLevel;
        if (lvl == null) return false;
        StructureTemplateManager mgr = lvl.getServer().getStructureManager();
        StructureTemplate template = mgr.get(id).orElse(null);
        if (template == null) return false;
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rot);
        return template.placeInWorld(lvl, pos, pos, settings, lvl.getRandom(), 2);
    }

    // Placeholder: in future, stamp structures into missionsLevel
    public static void cleanup(RunBounds bounds) {
        // TODO: iterate AABB, replace with air, despawn entities, clear tickets
    }
}
