package com.jayemceekay.shadowedhearts.common.moonwound;

import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Layer;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Outcome;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Phase;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallCoreBlockEntity;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallCoreState;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallSiteData;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

import static com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundSiteQueries.near;

/** Reconciles completed-site receipts and delivers each member's saved reward claim once. */
final class MoonwoundCompletion {
    private MoonwoundCompletion() {}

    /** Reconcile the receipt and encounters without replacing the core or granting party rewards. */
    static void reconcileCompletedSite(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        if (!site.rewardClaimed || site.state.active()) {
            for (Layer layer : Layer.values()) for (UUID id : event.issuedEnemies(layer)) MoonwoundEncounters.discard(level, event.eventId(), id);
            if (event.alphaId() != null) MoonwoundEncounters.discard(level, event.eventId(), event.alphaId());
            site.rewardClaimed = true;
            site.state = event.outcome() == Outcome.SEAL ? UmbrafallCoreState.STABILIZED : UmbrafallCoreState.RUPTURED;
            UmbrafallSiteData.get(level).setDirty();
        }
    }

    static void finish(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        if (event.phase() != Phase.COMPLETE) return;
        reconcileCompletedSite(level, site, event);
        if (!MoonwoundClaimProtection.canRun(level, site, event)) return;
        if (level.hasChunkAt(site.core) && level.getBlockEntity(site.core) instanceof UmbrafallCoreBlockEntity core
                && core.belongsToSite(site.id) && MoonwoundClaimProtection.canRetireCore(level, site, event)) {
            level.setBlockAndUpdate(site.core, ModBlocks.UMBRAFALL_SHELL.get().defaultBlockState());
        }
        for (UUID member : event.activeMembers()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (!near(player, level, site) || !MoonwoundClaimProtection.canInteract(player, level, site)
                    || event.claimedRewards().contains(member)) continue;
            List<ItemStack> rewards = MoonwoundRewards.roll(level, site.core, site.tier, event, player);
            if (!event.claimReward(member)) continue;
            for (ItemStack reward : rewards) grant(player, reward);
            if (event.outcome() != Outcome.OVERLOAD || event.overloadSucceeded()) {
                ModCriteriaTriggers.triggerProgression(player, switch (event.outcome()) {
                    case HARVEST -> "moonwound_harvest";
                    case SEAL -> "moonwound_seal";
                    case OVERLOAD -> "moonwound_overload";
                });
            }
            player.displayClientMessage(Component.translatable("message.shadowedhearts.moonwound.rewards_delivered", Component.translatable("moonwound.outcome." + event.outcome().name().toLowerCase(java.util.Locale.ROOT))), false);
        }
    }

    private static void grant(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
