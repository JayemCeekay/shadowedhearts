package com.jayemceekay.shadowedhearts.client.moonwound;

import static com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundText.tr;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Phase;
import com.jayemceekay.shadowedhearts.config.MoonwoundObjectiveDisplay;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client-thread-only, short-lived mirrors of server state; never predicts event progress. */
public final class MoonwoundClient {
    private static final int MAX_SITES = 64;
    private static final int EXPIRY_TICKS = 200;
    private static final Map<UUID, Entry> SITES = new LinkedHashMap<>();
    private static final MoonwoundChatUpdates CHAT_UPDATES = new MoonwoundChatUpdates();
    private static ClientLevel level;
    private static long ticks;
    private static long portalTicks;
    private static MoonwoundObjectiveDisplay previousDisplay;

    private record Entry(MoonwoundSnapshot snapshot, long expires, MoonwoundPortalClock portalClock) { }

    private MoonwoundClient() { }

    public static void onClientTick(Minecraft minecraft) {
        ensureLevel(minecraft);
        // Integrated-server broadcasts stop during a local pause, so their client leases must stop too.
        if (minecraft.isPaused()) return;
        ticks++;
        if (level != null) portalTicks++;
        SITES.values().removeIf(entry -> entry.expires() < ticks);
        var portals = portals();
        MoonwoundSoundManager.tick(minecraft, portals, ticks);
        MoonwoundSurfaceMiasma.tick(minecraft, portals);
        var display = ShadowedHeartsConfigs.getInstance().getClientConfig().moonwoundObjectiveDisplay();
        if (display != previousDisplay) {
            CHAT_UPDATES.clear();
            previousDisplay = display;
        }
        if (display == MoonwoundObjectiveDisplay.CHAT && minecraft.player != null && level != null) {
            var snapshots = freshSnapshots();
            var playerId = minecraft.player.getUUID();
            var dimension = level.dimension().location();
            var playerPos = minecraft.player.blockPosition();
            displayChatUpdate(minecraft, MoonwoundHudPresentation.select(snapshots, playerId, dimension, playerPos));
            // A nearer finished site must not hide another site's newly completed result.
            for (var snapshot : snapshots) {
                if (snapshot.phase() == Phase.COMPLETE
                        && MoonwoundHudPresentation.isParticipantInRange(snapshot, playerId, dimension, playerPos)) {
                    displayChatUpdate(minecraft, snapshot);
                }
            }
        }
    }

    private static void displayChatUpdate(Minecraft minecraft, MoonwoundSnapshot snapshot) {
        String update = CHAT_UPDATES.poll(snapshot, ticks);
        if (update != null) minecraft.player.displayClientMessage(Component.literal(update), false);
    }

    private static void ensureLevel(Minecraft minecraft) {
        if (level != minecraft.level) {
            MoonwoundSoundManager.clear();
            SITES.clear();
            CHAT_UPDATES.clear();
            MoonwoundSurfaceMiasma.clear();
            MoonwoundPortalRenderer.clearPixelationResources();
            level = minecraft.level;
            ticks = 0;
            portalTicks = 0;
        }
    }

    public static void accept(MoonwoundSnapshot snapshot, boolean openScreen) {
        Minecraft minecraft = Minecraft.getInstance();
        ensureLevel(minecraft);
        if (level == null || minecraft.player == null
                || !level.dimension().location().equals(snapshot.dimension())) return;
        if (!SITES.containsKey(snapshot.siteId()) && SITES.size() >= MAX_SITES) {
            Entry evicted = SITES.remove(SITES.keySet().iterator().next());
            MoonwoundSoundManager.stop(evicted.snapshot().eventId());
        }
        Entry previous = SITES.get(snapshot.siteId());
        if (previous != null && (!previous.snapshot().eventId().equals(snapshot.eventId()) || !snapshot.portalActive())) {
            MoonwoundSoundManager.stop(previous.snapshot().eventId());
        }
        var portalClock = MoonwoundPortalClock.accept(previous == null ? null : previous.portalClock(),
                snapshot.eventId(), snapshot.portalActive(), snapshot.portalAgeTicks(), portalTicks);
        SITES.put(snapshot.siteId(), new Entry(snapshot, ticks + EXPIRY_TICKS, portalClock));
        MoonwoundSoundManager.accept(minecraft, snapshot, previous == null ? null : previous.portalClock(), ticks);
        if (minecraft.screen instanceof MoonwoundScreen screen && screen.siteId().equals(snapshot.siteId())) {
            screen.update(snapshot);
        } else if (openScreen) {
            minecraft.setScreen(new MoonwoundScreen(snapshot));
        }
    }

    public static List<MoonwoundSnapshot> portals() {
        ensureLevel(Minecraft.getInstance());
        return SITES.values().stream().filter(entry -> entry.expires() >= ticks)
                .map(Entry::snapshot).filter(MoonwoundSnapshot::portalActive).toList();
    }

    /** Seconds since activation, kept across broadcasts and unaffected by resource reload or visibility. */
    public static float portalOpeningAge(MoonwoundSnapshot snapshot, float partialTicks) {
        ensureLevel(Minecraft.getInstance());
        Entry entry = SITES.get(snapshot.siteId());
        if (entry == null || !entry.snapshot().eventId().equals(snapshot.eventId())) return 60f;
        return entry.portalClock().seconds(portalTicks, partialTicks);
    }

    /** Discard a view after leaving; a later server broadcast may install a fresh snapshot. */
    public static void dismiss(UUID siteId) {
        Entry removed = SITES.remove(siteId);
        if (removed != null) MoonwoundSoundManager.stop(removed.snapshot().eventId());
        CHAT_UPDATES.forget(siteId);
    }

    /** The server closed this event; a newer lobby at its site must remain untouched. */
    public static void close(UUID eventId) {
        Minecraft minecraft = Minecraft.getInstance();
        ensureLevel(minecraft);
        MoonwoundSoundManager.stop(eventId);
        SITES.entrySet().removeIf(entry -> {
            if (!entry.getValue().snapshot().eventId().equals(eventId)) return false;
            CHAT_UPDATES.forget(entry.getKey());
            return true;
        });
        if (minecraft.screen instanceof MoonwoundScreen screen && screen.eventId().equals(eventId)) screen.onClose();
    }

    /** A fresh participant objective; presentation preferences never change server state. */
    public static MoonwoundSnapshot hudSnapshot() {
        Minecraft minecraft = Minecraft.getInstance();
        ensureLevel(minecraft);
        if (minecraft.player == null || level == null || minecraft.options.hideGui || minecraft.screen != null
                || minecraft.gui.getDebugOverlay().showDebugScreen() || minecraft.options.keyPlayerList.isDown()
                || CobblemonClient.INSTANCE.getBattle() != null
                || ShadowedHeartsConfigs.getInstance().getClientConfig().moonwoundObjectiveDisplay()
                    != MoonwoundObjectiveDisplay.HUD) return null;
        return MoonwoundHudPresentation.select(freshSnapshots(),
                minecraft.player.getUUID(), level.dimension().location(), minecraft.player.blockPosition());
    }

    private static List<MoonwoundSnapshot> freshSnapshots() {
        return SITES.values().stream().filter(entry -> entry.expires() >= ticks).map(Entry::snapshot).toList();
    }

    public static void renderHud(GuiGraphics graphics) {
        var snapshot = hudSnapshot();
        if (snapshot != null) MoonwoundObjectiveHud.render(graphics, snapshot);
    }

    public static String phaseLabel(Phase phase) {
        return switch (phase) {
            case LOBBY -> tr("gui.shadowedhearts.moonwound.client.party_lobby");
            case EXCAVATE_SHELL -> tr("gui.shadowedhearts.moonwound.client.excavate_the_shell");
            case WAVE_SHELL -> tr("gui.shadowedhearts.moonwound.client.shell_defenders");
            case EXCAVATE_MANTLE -> tr("gui.shadowedhearts.moonwound.client.excavate_the_mantle");
            case WAVE_MANTLE -> tr("gui.shadowedhearts.moonwound.client.mantle_defenders");
            case EXCAVATE_CASING -> tr("gui.shadowedhearts.moonwound.client.excavate_the_core_casing");
            case WAVE_CASING -> tr("gui.shadowedhearts.moonwound.client.core_defenders");
            case VOTE -> tr("gui.shadowedhearts.moonwound.client.choose_the_core_s_fate");
            case OVERLOAD -> tr("gui.shadowedhearts.moonwound.client.overload_encounter");
            case COMPLETE -> tr("gui.shadowedhearts.moonwound.client.complete");
            case ABANDONED -> tr("gui.shadowedhearts.moonwound.client.abandoned");
        };
    }

    public static String progress(MoonwoundSnapshot snapshot) {
        return switch (snapshot.phase()) {
            case LOBBY -> tr("gui.shadowedhearts.moonwound.progress.lobby", snapshot.members().stream().filter(MoonwoundSnapshot.Member::ready).count(), snapshot.members().size());
            case EXCAVATE_SHELL, EXCAVATE_MANTLE, EXCAVATE_CASING ->
                    tr("gui.shadowedhearts.moonwound.progress.excavated", snapshot.mined(), snapshot.miningTarget());
            case WAVE_SHELL, WAVE_MANTLE, WAVE_CASING ->
                    tr("gui.shadowedhearts.moonwound.progress.defeated", snapshot.defeated(), snapshot.waveTarget());
            case VOTE -> tr("gui.shadowedhearts.moonwound.progress.vote", snapshot.voteRound());
            case OVERLOAD -> snapshot.raidFinale() ? tr("gui.shadowedhearts.moonwound.client.defeat_the_shadow_raid_boss_together")
                    : tr("gui.shadowedhearts.moonwound.client.one_shared_alpha_first_successful_party_capture");
            case COMPLETE, ABANDONED -> snapshot.status();
        };
    }
}
