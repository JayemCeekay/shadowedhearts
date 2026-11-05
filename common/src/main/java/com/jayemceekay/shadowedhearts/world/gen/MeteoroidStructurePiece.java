package com.jayemceekay.shadowedhearts.world.gen;

import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallShapeBuilder;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallGeometry;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallSiteGenerator;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
    private final UmbrafallTier tier;
    private final long seed;
    private final UmbrafallGeometry geometry;
    private final CompoundTag rejectedShapeData;

    public MeteoroidStructurePiece(BlockPos center, UmbrafallTier tier, long seed) {
        this(center, tier, seed, UmbrafallGeometry.CURRENT);
    }

    public MeteoroidStructurePiece(BlockPos center, UmbrafallTier tier, long seed, UmbrafallGeometry geometry) {
        super(ModStructures.METEOROID_PIECE.get(), 0,
                UmbrafallSiteGenerator.boundsAt(center, new UmbrafallShapeBuilder(tier, seed, geometry)));
        this.center = center;
        this.tier = tier;
        this.seed = seed;
        this.geometry = geometry;
        this.rejectedShapeData = null;
    }

    public MeteoroidStructurePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.METEOROID_PIECE.get(), tag);
        this.center = new BlockPos(tag.getInt("CPX"), tag.getInt("CPY"), tag.getInt("CPZ"));
        this.seed = tag.getLong("UmbrafallSeed");
        UmbrafallTier loadedTier = null;
        UmbrafallGeometry loadedGeometry = UmbrafallGeometry.LEGACY_V1;
        CompoundTag rejected = null;
        // Pre-layered crater pieces have no shape fields and remain loadable but inert.
        // Partial metadata is preserved separately rather than treated as a supported geometry.
        if (tag.contains("UmbrafallTier") || tag.contains("UmbrafallShapeVersion") || tag.contains("UmbrafallSeed")) {
            try {
                if (!tag.contains("UmbrafallTier", Tag.TAG_STRING) || !tag.contains("UmbrafallSeed", Tag.TAG_LONG)
                        || (tag.contains("UmbrafallShapeVersion") && !tag.contains("UmbrafallShapeVersion", Tag.TAG_INT))) {
                    throw new IllegalArgumentException("Malformed Umbrafall shape metadata");
                }
                loadedTier = UmbrafallTier.fromId(tag.getString("UmbrafallTier"));
                loadedGeometry = tag.contains("UmbrafallShapeVersion")
                        ? UmbrafallGeometry.fromVersion(tag.getInt("UmbrafallShapeVersion")) : UmbrafallGeometry.LEGACY_V1;
            } catch (IllegalArgumentException exception) {
                rejected = new CompoundTag();
                for (String key : new String[]{"UmbrafallTier", "UmbrafallSeed", "UmbrafallShapeVersion"}) {
                    if (tag.contains(key)) rejected.put(key, tag.get(key).copy());
                }
            }
        }
        this.tier = loadedTier;
        this.geometry = loadedGeometry;
        this.rejectedShapeData = rejected;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CPX", center.getX());
        tag.putInt("CPY", center.getY());
        tag.putInt("CPZ", center.getZ());
        if (rejectedShapeData != null) {
            for (String key : rejectedShapeData.getAllKeys()) tag.put(key, rejectedShapeData.get(key).copy());
        } else if (tier != null) {
            tag.putInt("UmbrafallShapeVersion", geometry.version());
            tag.putString("UmbrafallTier", tier.id());
            tag.putLong("UmbrafallSeed", seed);
        }
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        // Retired crater starts retain their NBT and bounds without carving more terrain.
        if (rejectedShapeData != null || tier == null) return;
        // Every supported tiered geometry keeps its saved seed, profile, and chunk slices.
        UmbrafallSiteGenerator.placeSlice(level, center, tier, seed, geometry, box);
    }
}
