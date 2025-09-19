package com.jayemceekay.shadowedhearts.generator;

import com.jayemceekay.shadowedhearts.runs.RunId;
import com.jayemceekay.shadowedhearts.world.RunBounds;
import com.jayemceekay.shadowedhearts.world.WorldspaceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/**
 * Minimal scaffolding for dungeon generation and structure stamping.
 * Provides helpers to place a structure template at a run's origin and to build a simple demo room.
 */
public final class DungeonGenerator {
    private DungeonGenerator() {}

    /**
     * Attempts to place a structure template id at the origin of the given run.
     * Returns true if placed successfully.
     */
    public static boolean placeStructureAtRunOrigin(RunId runId, ResourceLocation structureId, Rotation rotation) {
        RunBounds bounds = WorldspaceManager.allocateBounds(runId);
        BlockPos origin = bounds.origin();
        return WorldspaceManager.placeTemplate(structureId, origin, rotation);
    }

    /**
     * Builds a simple debug room at the run origin using vanilla blocks (no templates required).
     * Room: 15x7x15 stone box with glowstone corners and a doorway to the south.
     */
    public static boolean demoBuildAtRunOrigin(RunId runId) {
        ServerLevel lvl = WorldspaceManager.missionsLevel();
        if (lvl == null) return false;
        RunBounds bounds = WorldspaceManager.allocateBounds(runId);
        BlockPos o = bounds.origin();
        int w = 15, h = 7, d = 15;
        int minX = o.getX() - w / 2;
        int minY = o.getY();
        int minZ = o.getZ() - d / 2;
        // Floor and ceiling
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                lvl.setBlock(new BlockPos(minX + x, minY, minZ + z), Blocks.STONE.defaultBlockState(), 3);
                lvl.setBlock(new BlockPos(minX + x, minY + h - 1, minZ + z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        // Walls
        for (int y = 1; y < h - 1; y++) {
            for (int x = 0; x < w; x++) {
                lvl.setBlock(new BlockPos(minX + x, minY + y, minZ), Blocks.COBBLESTONE.defaultBlockState(), 3);
                lvl.setBlock(new BlockPos(minX + x, minY + y, minZ + d - 1), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
            for (int z = 0; z < d; z++) {
                lvl.setBlock(new BlockPos(minX, minY + y, minZ + z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                lvl.setBlock(new BlockPos(minX + w - 1, minY + y, minZ + z), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }
        // Light corners
        lvl.setBlock(new BlockPos(minX + 1, minY + 1, minZ + 1), Blocks.GLOWSTONE.defaultBlockState(), 3);
        lvl.setBlock(new BlockPos(minX + w - 2, minY + 1, minZ + 1), Blocks.GLOWSTONE.defaultBlockState(), 3);
        lvl.setBlock(new BlockPos(minX + 1, minY + 1, minZ + d - 2), Blocks.GLOWSTONE.defaultBlockState(), 3);
        lvl.setBlock(new BlockPos(minX + w - 2, minY + 1, minZ + d - 2), Blocks.GLOWSTONE.defaultBlockState(), 3);
        // Doorway on south wall (center, 2 blocks high)
        int doorX = minX + w / 2;
        int doorZ = minZ + d - 1;
        lvl.removeBlock(new BlockPos(doorX, minY + 1, doorZ), false);
        lvl.removeBlock(new BlockPos(doorX, minY + 2, doorZ), false);
        return true;
    }

    // Future stubs
    public static void buildGraphStub() {}
    public static void solvePlacementStub() {}
    public static void stampRoomsStub() {}
}
