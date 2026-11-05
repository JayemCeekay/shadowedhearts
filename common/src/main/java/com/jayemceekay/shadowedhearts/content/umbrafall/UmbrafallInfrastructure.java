package com.jayemceekay.shadowedhearts.content.umbrafall;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.block.BerryBlock;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.block.habitat.HabitatBlock;
import com.cobblemon.mod.common.util.DataKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Read-only clearance checks against existing machine and pasture block entities. */
public final class UmbrafallInfrastructure {
    public static final int MACHINE_CLEARANCE = 16;
    public static final int PASTURE_BUFFER = 8;
    private static final int MINIMUM_PASTURE_RANGE = 32;

    private UmbrafallInfrastructure() { }

    /** Additional horizontal area needed to check a site's infrastructure clearance. */
    public static int maxClearance(ServerLevel world) {
        return Math.max(MINIMUM_PASTURE_RANGE,
                Cobblemon.INSTANCE.getConfig().getPastureMaxWanderDistance()) + PASTURE_BUFFER;
    }

    /**
     * Returns the first nearby machine/pasture, without loading chunks or instantiating block entities.
     * The caller must establish that the clearance area is loaded before treating null as safe.
     * Height is deliberately ignored: an underground or elevated machine deserves the same margin.
     */
    public static BlockPos obstruction(ServerLevel world, BlockPos center, int footprintRadius) {
        int configuredRange = maxClearance(world) - PASTURE_BUFFER;
        int searchRadius = footprintRadius + configuredRange + PASTURE_BUFFER;
        for (int cx = (center.getX() - searchRadius) >> 4; cx <= (center.getX() + searchRadius) >> 4; cx++) {
            for (int cz = (center.getZ() - searchRadius) >> 4; cz <= (center.getZ() + searchRadius) >> 4; cz++) {
                LevelChunk chunk = world.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                // The combined position set includes both live BEs and deferred, serialized BEs.
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    // The actual indexed block is authoritative, including for DUMMY/malformed NBT.
                    // This neither scans terrain nor asks the chunk to instantiate a deferred BE.
                    BlockState state = chunk.getBlockState(pos);
                    Block block = state.getBlock();
                    if (!state.hasBlockEntity() || block instanceof BerryBlock || block instanceof HabitatBlock) continue;
                    ResourceLocation type = BuiltInRegistries.BLOCK.getKey(block);
                    if (!isInfrastructure(type)) continue;
                    BlockEntity entity = chunk.getBlockEntities().get(pos);
                    CompoundTag pending = entity == null ? chunk.getBlockEntityNbt(pos) : null;
                    if (isPasture(type)) {
                        BlockPos min = pos.offset(-configuredRange, 0, -configuredRange);
                        BlockPos max = pos.offset(configuredRange, 0, configuredRange);
                        if (entity instanceof PokemonPastureBlockEntity pasture) {
                            min = componentMin(min, pasture.getMinRoamPos());
                            max = componentMax(max, pasture.getMaxRoamPos());
                        } else if (pending != null) {
                            min = componentMin(min, NbtUtils.readBlockPos(pending, DataKeys.TETHER_MIN_ROAM_POS).orElse(min));
                            max = componentMax(max, NbtUtils.readBlockPos(pending, DataKeys.TETHER_MAX_ROAM_POS).orElse(max));
                        }
                        if (overlaps(center, footprintRadius + PASTURE_BUFFER, min, max)) return pos.immutable();
                    } else if (overlaps(center, footprintRadius + MACHINE_CLEARANCE, pos, pos)) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }

    /** Infrastructure registry IDs cannot be opted into terrain replacement by an allow tag. */
    public static boolean isInfrastructure(ResourceLocation registryId) {
        if (registryId == null) return false;
        return switch (registryId.getNamespace()) {
            // Berry trees and native habitat anchors belong to the natural environment.
            case "cobblemon" -> !registryId.getPath().equals("berry")
                    && !registryId.getPath().equals("habitat_block");
            case "shadowedhearts" -> !registryId.getPath().equals("umbrafall_core");
            default -> false;
        };
    }

    private static boolean isPasture(ResourceLocation type) {
        return type.getNamespace().equals("cobblemon") && type.getPath().equals("pasture");
    }

    private static boolean overlaps(BlockPos center, int radius, BlockPos min, BlockPos max) {
        return (long) center.getX() + radius >= min.getX()
                && (long) center.getX() - radius <= max.getX()
                && (long) center.getZ() + radius >= min.getZ()
                && (long) center.getZ() - radius <= max.getZ();
    }

    private static BlockPos componentMin(BlockPos first, BlockPos second) {
        return new BlockPos(Math.min(first.getX(), second.getX()), first.getY(), Math.min(first.getZ(), second.getZ()));
    }

    private static BlockPos componentMax(BlockPos first, BlockPos second) {
        return new BlockPos(Math.max(first.getX(), second.getX()), first.getY(), Math.max(first.getZ(), second.getZ()));
    }
}
