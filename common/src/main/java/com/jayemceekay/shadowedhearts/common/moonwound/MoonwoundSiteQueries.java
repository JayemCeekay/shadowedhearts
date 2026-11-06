package com.jayemceekay.shadowedhearts.common.moonwound;

import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Layer;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallSiteData;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallShapeBuilder;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallSiteGenerator;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/** Read-only terrain, proximity, and original-block queries for Moonwound sites. */
final class MoonwoundSiteQueries {
    private static final int RANGE = 64;

    private MoonwoundSiteQueries() {}

    static boolean eligible(UmbrafallSiteData.Site site) {
        return site.hasValidTerrainProfile() && site.placement == UmbrafallSiteData.Placement.READY
                && site.geometry.usesStratifiedLayers() && (site.tier == UmbrafallTier.STRONG || site.tier == UmbrafallTier.RESONANT);
    }

    static boolean near(ServerPlayer player, ServerLevel level, UmbrafallSiteData.Site site) {
        return player != null && player.isAlive() && player.serverLevel() == level && site != null
                && player.distanceToSqr(Vec3.atCenterOf(site.center)) <= RANGE * RANGE;
    }

    static boolean loaded(ServerLevel level, UmbrafallSiteData.Site site) {
        int radius = site.footprintRadius() + 16;
        for (int x = (site.center.getX() - radius) >> 4; x <= (site.center.getX() + radius) >> 4; x++)
            for (int z = (site.center.getZ() - radius) >> 4; z <= (site.center.getZ() + radius) >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    static boolean coreExposed(ServerLevel level, UmbrafallSiteData.Site site) {
        if (!level.hasChunkAt(site.core) || !level.getBlockState(site.core).is(ModBlocks.UMBRAFALL_CORE.get())) return false;
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = site.core.relative(direction);
            if (level.getChunkSource().getChunkNow(adjacent.getX() >> 4, adjacent.getZ() >> 4) != null
                    && level.getBlockState(adjacent).isAir()) return true;
        }
        return false;
    }

    static UmbrafallSiteData.Site siteAt(ServerLevel level, BlockPos pos) {
        return UmbrafallSiteData.get(level).sites.values().stream().filter(site -> eligible(site) && inside(site, pos))
                .min(Comparator.comparingDouble(site -> site.core.distSqr(pos))).orElse(null);
    }

    static boolean inside(UmbrafallSiteData.Site site, BlockPos pos) {
        return Math.abs(pos.getX() - site.center.getX()) <= site.footprintRadius()
                && Math.abs(pos.getZ() - site.center.getZ()) <= site.footprintRadius()
                && Math.abs(pos.getY() - site.core.getY()) <= 24;
    }

    static Layer layerAt(UmbrafallSiteData.Site site, BlockPos pos) {
        return layer(site.shape().structuralLayerAt(pos.getX() - site.center.getX(), pos.getY() - site.center.getY(), pos.getZ() - site.center.getZ()));
    }

    static Layer layer(UmbrafallShapeBuilder.StructuralLayer layer) {
        return switch (layer) { case OUTER_SHELL -> Layer.SHELL; case LATTICE_MANTLE -> Layer.MANTLE; case CORE_CASING -> Layer.CASING; default -> null; };
    }

    static boolean originalAt(ServerLevel level, UmbrafallSiteData.Site site, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        var material = site.shape().materialAt(pos.getX() - site.center.getX(), pos.getY() - site.center.getY(), pos.getZ() - site.center.getZ());
        return switch (material) {
            case SHELL, CRACKED_SHELL, UMBRAL_LATTICE, CORE_CASING, VEIN, CRYSTAL, CORE ->
                    level.getBlockState(pos).is(UmbrafallSiteGenerator.stateFor(material, level, pos).getBlock());
            default -> false;
        };
    }

    static boolean meteorMaterial(BlockState state) {
        return state.is(ModBlocks.UMBRAFALL_SHELL.get()) || state.is(ModBlocks.CRACKED_UMBRAFALL_SHELL.get())
                || state.is(ModBlocks.UMBRAL_LATTICE.get()) || state.is(ModBlocks.UMBRAFALL_CORE_CASING.get())
                || state.is(ModBlocks.UMBRAFALL_VEIN.get()) || state.is(ModBlocks.UMBRAFALL_CRYSTAL.get())
                || state.is(ModBlocks.UMBRAFALL_CORE.get());
    }
}
