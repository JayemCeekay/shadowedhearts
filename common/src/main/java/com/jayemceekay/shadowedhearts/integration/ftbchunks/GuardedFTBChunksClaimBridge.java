package com.jayemceekay.shadowedhearts.integration.ftbchunks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;

import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Server-thread boundary for optional API initialization and query failures. */
final class GuardedFTBChunksClaimBridge implements FTBChunksClaimBridge {
    private final Supplier<FTBChunksClaimBridge> initializer;
    private final Consumer<Throwable> reportFailure;
    // Once per failure type for this bridge's lifetime. A successful chunk lookup does not
    // prove another chunk's failing ownership lookup has recovered; do not spam each scan.
    private final Set<Class<? extends Throwable>> reportedFailures = new HashSet<>();
    private FTBChunksClaimBridge delegate;

    GuardedFTBChunksClaimBridge(Supplier<FTBChunksClaimBridge> initializer, Consumer<Throwable> reportFailure) {
        this.initializer = Objects.requireNonNull(initializer);
        this.reportFailure = Objects.requireNonNull(reportFailure);
    }

    @Override
    public ChunkClaim lookupClaim(ServerLevel level, ChunkPos chunkPos) {
        try {
            return Objects.requireNonNull(delegate().lookupClaim(level, chunkPos));
        } catch (RuntimeException | LinkageError error) {
            report(error);
            return ChunkClaim.unavailable();
        }
    }

    @Override
    public boolean canInteractBlock(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        try {
            return delegate().canInteractBlock(player, pos, hand);
        } catch (RuntimeException | LinkageError error) {
            report(error);
            return false;
        }
    }

    @Override
    public boolean canEditBlock(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        try {
            return delegate().canEditBlock(player, pos, hand);
        } catch (RuntimeException | LinkageError error) {
            report(error);
            return false;
        }
    }

    @Override
    public boolean canExplosionDestroyBlock(ServerLevel level, BlockPos pos, Explosion explosion) {
        try {
            return delegate().canExplosionDestroyBlock(level, pos, explosion);
        } catch (RuntimeException | LinkageError error) {
            report(error);
            return false;
        }
    }

    private FTBChunksClaimBridge delegate() {
        if (delegate == null) delegate = Objects.requireNonNull(initializer.get());
        return delegate;
    }

    private void report(Throwable error) {
        if (reportedFailures.add(error.getClass())) reportFailure.accept(error);
    }
}
