package com.jayemceekay.shadowedhearts.content.umbrafall;

import com.jayemceekay.shadowedhearts.content.items.ShadowSignalDataItem;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.world.umbrafall.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Read-only, cancellable preflight over an explicitly bounded number of candidate footprints. */
final class UmbrafallSiteSearch {
    private final UUID owner;
    private final InteractionHand hand;
    private final UmbrafallTier tier;
    private final ResourceLocation dimension;
    private final BlockPos origin;
    private final Random random;
    private final double startAngle;
    private final Map<Component,Integer> rejections = new LinkedHashMap<>();
    private BlockPos center;
    private UmbrafallShapeBuilder shape;
    private int attempts;
    private int checked;
    private int checkedColumns;
    private int[] terrainOffsets;
    private boolean terrainFitted;
    private boolean centerResolved;
    private int surveyY = Integer.MIN_VALUE;
    private AuraReaderSearchProgress.Stage stage = AuraReaderSearchProgress.Stage.SEARCHING;
    private UUID reservedSiteId;

    UmbrafallSiteSearch(ServerPlayer player, InteractionHand hand, UmbrafallTier tier) {
        owner = player.getUUID(); this.hand = hand; this.tier = tier;
        dimension = player.level().dimension().location(); origin = player.blockPosition();
        random = new Random(player.getRandom().nextLong());
        startAngle = random.nextDouble()*Math.PI*2;
    }

    boolean step(MinecraftServer server, int budget) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player == null) return true;
        ItemStack stack = player.getItemInHand(hand);
        if (!player.level().dimension().location().equals(dimension) || !player.mayBuild()
                || !UmbrafallSignalPolicy.canGenerate(ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration(), tier)
                || !(stack.getItem() instanceof ShadowSignalDataItem signal) || signal.tier() != tier || stack.isEmpty()) {
            UmbrafallSites.publishProgress(player, AuraReaderSearchProgress.of(tier, AuraReaderSearchProgress.Stage.CANCELLED, attempts));
            UmbrafallSites.message(player,Component.translatable("message.shadowedhearts.umbrafall.search_cancelled_requirements")); return true;
        }
        ServerLevel world = player.serverLevel();
        if (center == null) {
            if (++attempts > 32) {
                MutableComponent reasons = Component.empty();
                rejections.entrySet().stream().sorted(Map.Entry.<Component,Integer>comparingByValue().reversed())
                        .limit(3).forEach(entry -> {
                            if (!reasons.getSiblings().isEmpty()) reasons.append(Component.literal(", "));
                            reasons.append(Component.translatable("message.shadowedhearts.umbrafall.obstacle_count", entry.getKey(), entry.getValue()));
                        });
                UmbrafallSites.publishProgress(player, AuraReaderSearchProgress.of(tier, AuraReaderSearchProgress.Stage.NO_LOCATION, 32));
                UmbrafallSites.message(player,Component.translatable("message.shadowedhearts.umbrafall.no_site", reasons)); return true;
            }
            var offset = UmbrafallPlacementPolicy.candidateOffset(tier,attempts-1,startAngle);
            BlockPos column = origin.offset(offset.x(),0,offset.z());
            if (!world.hasChunkAt(column)) return reject(Component.translatable("message.shadowedhearts.umbrafall.obstacle.unloaded"));
            center = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE,column).below();
            shape = new UmbrafallShapeBuilder(tier,random.nextLong()); checked = 0; checkedColumns = 0;
            terrainOffsets = new int[shape.diameter()*shape.diameter()]; terrainFitted = false;
            centerResolved = false; surveyY = center.getY();
            stage = AuraReaderSearchProgress.Stage.SURVEYING;
            if (conflictsWithReservedSite(world)) return reject(Component.translatable("message.shadowedhearts.umbrafall.obstacle.nearby_sites"));
        }
        var footprintFailure = UmbrafallSites.footprintFailure(world,center,shape.radius(),true);
        if (footprintFailure != null) return reject(footprintFailure.description);
        int diameter = shape.diameter();
        int columnBudget = Math.min(256,budget);
        int columnWork = 0;
        // Descending through trunks and canopy is resumable and charged to the survey budget.
        // Both the anchor and crater columns use the soil beneath vegetation as their elevation.
        while (!centerResolved && columnWork < columnBudget) {
            if (surveyY < world.getMinBuildHeight()) return reject(Component.translatable("message.shadowedhearts.umbrafall.obstacle.no_ground"));
            var state = world.getBlockState(new BlockPos(center.getX(),surveyY,center.getZ()));
            columnWork++;
            if (state.isAir() || UmbrafallTerrainMaterials.vegetation(state)) { surveyY--; continue; }
            center = new BlockPos(center.getX(),surveyY,center.getZ());
            centerResolved = true; surveyY = Integer.MIN_VALUE;
            var bounds = shape.bounds();
            if (center.getY()+bounds.minY() <= world.getMinBuildHeight() || center.getY()+bounds.maxY() >= world.getMaxBuildHeight())
                return reject(Component.translatable("message.shadowedhearts.umbrafall.obstacle.height"));
        }
        if (!centerResolved) return false;
        while (columnWork < columnBudget && checkedColumns < diameter*diameter) {
            int x = checkedColumns%diameter-shape.radius(), z = checkedColumns/diameter-shape.radius();
            if (!UmbrafallPlacementPolicy.requiresSurfaceCheck(shape,x,z)) { columnWork++; checkedColumns++; continue; }
            if (surveyY == Integer.MIN_VALUE)
                surveyY = world.getHeight(Heightmap.Types.WORLD_SURFACE,center.getX()+x,center.getZ()+z)-1;
            if (surveyY < world.getMinBuildHeight()) return reject(Component.translatable("message.shadowedhearts.umbrafall.obstacle.no_ground"));
            var state = world.getBlockState(new BlockPos(center.getX()+x,surveyY,center.getZ()+z));
            columnWork++;
            if (state.isAir() || UmbrafallTerrainMaterials.vegetation(state)) { surveyY--; continue; }
            int delta = surveyY-center.getY();
            if (!UmbrafallPlacementPolicy.acceptsSurfaceDelta(tier,delta)) return reject(Component.translatable("message.shadowedhearts.umbrafall.obstacle.relief"));
            terrainOffsets[checkedColumns++] = delta;
            surveyY = Integer.MIN_VALUE;
        }
        if (checkedColumns < diameter*diameter) return false;
        if (!terrainFitted) {
            // Capture the original terrain once. Every validation, write and resumed tick uses this plan.
            shape = new UmbrafallShapeBuilder(tier,shape.seed(),shape.geometry(),terrainOffsets);
            terrainFitted = true;
            if (center.getY()+shape.bounds().minY() <= world.getMinBuildHeight()
                    || center.getY()+shape.bounds().maxY() >= world.getMaxBuildHeight()) return reject(Component.translatable("message.shadowedhearts.umbrafall.obstacle.height"));
        }
        stage = AuraReaderSearchProgress.Stage.CHECKING;
        var bounds = shape.bounds();
        int width = bounds.maxX()-bounds.minX()+1, height = bounds.maxY()-bounds.minY()+1;
        // Containers/build blocks still veto the whole volume. Only isolated, untouched fluids are exempt.
        for (int work=columnWork; work<budget && checked<bounds.volume(); work++,checked++) {
            int x = checked%width+bounds.minX(), y = (checked/width)%height+bounds.minY(), z = checked/(width*height)+bounds.minZ();
            var state = world.getBlockState(center.offset(x,y,z));
            if (!UmbrafallSites.safeTerrain(state,shape,x,y,z)) return reject(state.hasBlockEntity()
                    ? Component.translatable("message.shadowedhearts.umbrafall.obstacle.containers") : !state.getFluidState().isEmpty() ? Component.translatable("message.shadowedhearts.umbrafall.obstacle.fluids")
                    : Component.translatable("message.shadowedhearts.umbrafall.obstacle.protected_block", state.getBlock().getName()));
        }
        if (checked < bounds.volume()) return false;
        // Other round-robin searches can reserve while this footprint is being checked.
        // Recheck on the server thread immediately before insertion/consumption so one wins.
        if (conflictsWithReservedSite(world)) return reject(Component.translatable("message.shadowedhearts.umbrafall.obstacle.nearby_sites"));
        var site = new UmbrafallSiteData.Site(UUID.randomUUID(),center,UmbrafallSiteGenerator.coreAt(center,shape),tier,shape.seed(),shape.geometry(),terrainOffsets);
        reservedSiteId = site.id;
        UmbrafallSites.reserve(world,site,player,hand);
        return true;
    }

    private boolean conflictsWithReservedSite(ServerLevel world) {
        return UmbrafallSiteData.get(world).sites.values().stream().anyMatch(site ->
                UmbrafallPlacementPolicy.overlaps(site.center.getX()-center.getX(),site.center.getZ()-center.getZ(),site.footprintRadius(),shape.radius()));
    }

    private boolean reject(Component reason) {
        rejections.merge(reason,1,Integer::sum);
        center = null;
        stage = AuraReaderSearchProgress.Stage.SEARCHING;
        return false;
    }

    AuraReaderSearchProgress progress() {
        return AuraReaderSearchProgress.of(tier, stage, attempts);
    }

    UUID reservedSiteId() { return reservedSiteId; }
    ResourceLocation dimension() { return dimension; }
    BlockPos origin() { return origin; }
}
