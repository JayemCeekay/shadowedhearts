package com.jayemceekay.shadowedhearts.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public class MeteoroidStructurePiece extends StructurePiece {
    private final BlockPos center;

    public MeteoroidStructurePiece(BlockPos center) {
        super(ModStructures.METEOROID_PIECE.get(), 0, new BoundingBox(center.getX() - 32, center.getY() - 32, center.getZ() - 32, center.getX() + 32, center.getY() + 32, center.getZ() + 32));
        this.center = center;
    }

    public MeteoroidStructurePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.METEOROID_PIECE.get(), tag);
        this.center = new BlockPos(tag.getInt("CPX"), tag.getInt("CPY"), tag.getInt("CPZ"));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CPX", center.getX());
        tag.putInt("CPY", center.getY());
        tag.putInt("CPZ", center.getZ());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        CraterGenerator.generateSlicedCrater(level, chunkPos, center, random);
    }
}
