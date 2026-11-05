package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.common.umbrafall.LegacyMeteoroidMigration;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkSerializer.class)
public abstract class MixinUmbrafallChunkMigration {
    @WrapMethod(method = "read")
    private static ProtoChunk shadowedhearts$migrateLegacyPalettes(ServerLevel level, PoiManager poiManager,
            RegionStorageInfo storageInfo, ChunkPos pos, CompoundTag tag, Operation<ProtoChunk> original) {
        boolean migrated = LegacyMeteoroidMigration.migrateChunkPalettes(tag);
        ProtoChunk chunk = original.call(level, poiManager, storageInfo, pos, tag);
        if (migrated) {
            // Full chunks are returned as an ImposterProtoChunk; its setter forwards to the wrapped chunk.
            chunk.setUnsaved(true);
        }
        return chunk;
    }
}
