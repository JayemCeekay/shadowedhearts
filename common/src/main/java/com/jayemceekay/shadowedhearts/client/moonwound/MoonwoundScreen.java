package com.jayemceekay.shadowedhearts.client.moonwound;

import static com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundText.tr;

import com.cobblemon.mod.common.client.CobblemonResources;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Outcome;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Phase;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.PaceBonus;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundActionPacket;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundActionPacket.Action;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Centered Cobblemon field console. All actions remain server-authoritative. */
public final class MoonwoundScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 220;
    private static final int DETAIL_X = 140;
    private static final int DETAIL_WIDTH = 202;
    private static final int REQUESTS_PER_PAGE = 2;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFE2E7E9;
    private static final int READY = 0xFFD9F2BD;
    private static final ResourceLocation FRAME = cobblemonTexture("npc/base");
    private static final ResourceLocation ALPHA = cobblemonTexture("summary/icon_size_alpha");

    private MoonwoundSnapshot state;
    private final List<Hint> hints = new ArrayList<>();
    private boolean requestsOpen;
    private boolean confirmingAbandon;
    private int requestPage;
    private int left, top, refreshTicks;
    private float panelScale;

    private record Hint(int x, int y, int width, int height, Component text) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    public MoonwoundScreen(MoonwoundSnapshot snapshot) {
        super(Component.translatable("gui.shadowedhearts.moonwound.screen.moonwound"));
        state = snapshot;
    }

    public UUID siteId() { return state.siteId(); }
    public UUID eventId() { return state.eventId(); }

    public void update(MoonwoundSnapshot snapshot) {
        if (!snapshot.equals(state)) {
            boolean controlsChanged = MoonwoundRecoveryPresentation.controlsChanged(state, snapshot);
            state = snapshot;
            if (controlsChanged && minecraft != null) rebuildWidgets();
        }
    }

    private UUID playerId() { return minecraft.player == null ? null : minecraft.player.getUUID(); }
    private boolean host() { return state.host().equals(playerId()); }
    private boolean lobby() { return state.phase() == Phase.LOBBY; }
    private int requestPages() { return Math.max(1, (state.requests().size() + REQUESTS_PER_PAGE - 1) / REQUESTS_PER_PAGE); }

    @Override
    protected void init() {
        // Shrink only when necessary; every draw and physical widget bound uses this same transform.
        panelScale = Math.max(0.1F, Math.min(1F, Math.min((width - 16F) / PANEL_WIDTH,
                (height - 16F) / PANEL_HEIGHT)));
        left = Math.round((width - PANEL_WIDTH * panelScale) / 2F);
        top = Math.round((height - PANEL_HEIGHT * panelScale) / 2F);
        requestsOpen &= lobby() && host();
        requestPage = Math.clamp(requestPage, 0, requestPages() - 1);
        var self = state.member(playerId());
        confirmingAbandon &= self != null && !lobby() && !state.phase().isTerminal();

        if (lobby()) {
            if (host()) {
                localButton(requestsOpen ? tr("gui.shadowedhearts.moonwound.screen.back_to_lobby") : tr("gui.shadowedhearts.moonwound.requests.count", state.requests().size()),
                        18, 166, 104, tr("gui.shadowedhearts.moonwound.screen.review_and_approve_requests_to_join_your_party"), button -> {
                            requestsOpen = !requestsOpen;
                            rebuildWidgets();
                        });
            }
            if (requestsOpen) {
                initRequests();
            } else if (self != null) {
                int y = host() ? 151 : 174;
                action(self.ready() ? tr("gui.shadowedhearts.moonwound.screen.not_ready") : tr("gui.shadowedhearts.moonwound.screen.ready"), DETAIL_X, y, 98, Action.READY, null, true,
                        tr("gui.shadowedhearts.moonwound.screen.ready_tooltip"));
                action(tr("gui.shadowedhearts.moonwound.screen.leave_lobby"), DETAIL_X + 104, y, 98, Action.LEAVE, null, true,
                        tr("gui.shadowedhearts.moonwound.screen.leave_this_lobby_closing_the_menu_keeps_you"));
                if (host()) action(tr("gui.shadowedhearts.moonwound.screen.start_moonwound"), DETAIL_X, 174, DETAIL_WIDTH, Action.START, null,
                        !state.members().isEmpty() && state.members().stream().allMatch(member -> member.ready()
                                && member.availability() == MoonwoundSnapshot.Availability.PRESENT),
                        tr("gui.shadowedhearts.moonwound.screen.start_when_everyone_is_ready_nearby_and_out"));
            } else {
                boolean pending = state.requests().stream().anyMatch(request -> request.id().equals(playerId()));
                action(pending ? tr("gui.shadowedhearts.moonwound.screen.withdraw_request") : tr("gui.shadowedhearts.moonwound.screen.request_to_join"), DETAIL_X, 174, DETAIL_WIDTH,
                        pending ? Action.LEAVE : Action.JOIN, null,
                        pending || state.members().size() < MoonwoundSnapshot.MAX_MEMBERS
                                && state.requests().size() < MoonwoundSnapshot.MAX_REQUESTS,
                        pending ? tr("gui.shadowedhearts.moonwound.screen.withdraw_your_pending_request") : state.requests().size() >= MoonwoundSnapshot.MAX_REQUESTS
                                ? tr("gui.shadowedhearts.moonwound.screen.the_request_queue_is_full_wait_for_the")
                                : tr("gui.shadowedhearts.moonwound.screen.ask_the_host_to_admit_you_to_this"));
            }
        } else if (confirmingAbandon) {
            action(tr("gui.shadowedhearts.moonwound.screen.abandon"), DETAIL_X, 174, 98, Action.LEAVE, null, true,
                    tr("gui.shadowedhearts.moonwound.screen.abandon_confirm_tooltip"));
            localButton(tr("gui.shadowedhearts.moonwound.screen.stay"), DETAIL_X + 104, 174, 98, tr("gui.shadowedhearts.moonwound.screen.return_to_your_event"), button -> {
                confirmingAbandon = false;
                rebuildWidgets();
            });
        } else if (state.phase() == Phase.VOTE) {
            for (Outcome outcome : Outcome.values()) {
                boolean selected = self != null && self.vote() == outcome;
                String tooltip = tr("gui.shadowedhearts.moonwound.vote.tooltip", outcomeLabel(outcome), outcomeDetail(outcome));
                var button = action(selected ? tr("gui.shadowedhearts.moonwound.screen.voted") : tr("gui.shadowedhearts.moonwound.screen.vote"), 292, 83 + outcome.ordinal() * 26, 50,
                        voteAction(outcome), null, self != null && !self.votingSuspended()
                                && self.availability() == MoonwoundSnapshot.Availability.PRESENT
                                && !state.recovery().paused() && (outcome != Outcome.SEAL || state.canSeal()), tooltip);
                button.setNarrationLabel(Component.translatable(selected ? "gui.shadowedhearts.moonwound.vote.selected_narration" : "gui.shadowedhearts.moonwound.vote.narration", outcomeLabel(outcome)));
            }
        }
        if (!lobby() && !state.phase().isTerminal() && self != null) {
            localButton(confirmingAbandon ? tr("gui.shadowedhearts.moonwound.screen.back_to_event") : tr("gui.shadowedhearts.moonwound.screen.abandon_event"), 18, 176, 104,
                    tr("gui.shadowedhearts.moonwound.screen.abandon_tooltip"), button -> {
                        confirmingAbandon = !confirmingAbandon;
                        rebuildWidgets();
                    });
        }
        localButton(lobby() || state.phase() == Phase.VOTE || state.phase().isTerminal() ? tr("gui.shadowedhearts.moonwound.screen.close") : tr("gui.shadowedhearts.moonwound.screen.back_to_world"),
                266, 200, 82, tr("gui.shadowedhearts.moonwound.screen.close_the_menu_without_leaving_the_party"), button -> onClose());
    }

    private void initRequests() {
        int first = requestPage * REQUESTS_PER_PAGE;
        for (int i = first; i < Math.min(first + REQUESTS_PER_PAGE, state.requests().size()); i++) {
            var applicant = state.requests().get(i);
            int y = 86 + (i - first) * 46;
            action(tr("gui.shadowedhearts.moonwound.screen.accept"), DETAIL_X, y, 98, Action.ACCEPT_JOIN, applicant.id(),
                    state.members().size() < MoonwoundSnapshot.MAX_MEMBERS, tr("gui.shadowedhearts.moonwound.requests.admit", applicant.name()));
            action(tr("gui.shadowedhearts.moonwound.screen.decline"), DETAIL_X + 104, y, 98, Action.DECLINE_JOIN, applicant.id(), true,
                    tr("gui.shadowedhearts.moonwound.requests.decline", applicant.name()));
        }
        if (requestPages() > 1) {
            var previous = localButton(tr("gui.shadowedhearts.moonwound.screen.previous"), DETAIL_X, 174, 62, tr("gui.shadowedhearts.moonwound.screen.previous_requests"), button -> changeRequestPage(-1));
            previous.active = requestPage > 0;
            var next = localButton(tr("gui.shadowedhearts.moonwound.screen.next"), 280, 174, 62, tr("gui.shadowedhearts.moonwound.screen.next_requests"), button -> changeRequestPage(1));
            next.active = requestPage + 1 < requestPages();
        }
    }

    private void changeRequestPage(int delta) {
        requestPage = Math.clamp(requestPage + delta, 0, requestPages() - 1);
        rebuildWidgets();
    }

    private MoonwoundButton localButton(String label, int x, int y, int width, String tooltip,
                                        net.minecraft.client.gui.components.Button.OnPress press) {
        var button = new MoonwoundButton(left + x * panelScale, top + y * panelScale,
                width, panelScale, Component.literal(label), Component.literal(label), press);
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return addRenderableWidget(button);
    }

    private MoonwoundButton action(String label, int x, int y, int width, Action action, UUID target,
                                   boolean active, String tooltip) {
        var button = localButton(label, x, y, width, tooltip, clicked -> send(action, target));
        button.active = active;
        return button;
    }

    private void send(Action action, UUID target) {
        ShadowedHeartsNetwork.sendToServer(new MoonwoundActionPacket(state.siteId(), state.eventId(), action, target));
        if (action == Action.LEAVE) {
            MoonwoundClient.dismiss(state.siteId());
            onClose();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double x = (mouseX - left) / panelScale;
        double y = (mouseY - top) / panelScale;
        if (requestsOpen && x >= 134 && x < 348 && y >= 35 && y < 195 && vertical != 0) {
            changeRequestPage(vertical > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft.player == null || minecraft.level == null
                || !minecraft.level.dimension().location().equals(state.dimension())) {
            onClose();
            return;
        }
        if (++refreshTicks >= 40) {
            refreshTicks = 0;
            send(Action.REFRESH, null);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The console supplies its own frame; never invoke Screen's world blur or menu background.
    }

    @Override
    public void renderBlurredBackground(float partialTick) {
        // Also opt out if the surrounding screen renderer requests blur directly.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hints.clear();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(left, top, 0);
            graphics.pose().scale(panelScale, panelScale, 1F);
            graphics.blit(FRAME, 0, 0, 0F, 0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
            text(graphics, tr("gui.shadowedhearts.moonwound.screen.moonwound"), 18, 9, 190, TEXT, true);
            Component phase = styled(phaseLabel(), false);
            graphics.drawString(font, phase, 348 - font.width(phase), 9, TEXT, true);
            renderParty(graphics);
            renderSidebar(graphics);
            if (confirmingAbandon) renderAbandon(graphics);
            else if (requestsOpen) renderRequests(graphics);
            else if (lobby()) renderLobby(graphics);
            else if (state.phase() == Phase.VOTE) renderVote(graphics);
            else renderObjective(graphics);
            renderFooter(graphics);
        } finally {
            graphics.pose().popPose();
        }
        // Native widgets and deferred tooltips use physical GUI coordinates, including on small windows.
        super.render(graphics, mouseX, mouseY, partialTick);
        double localX = (mouseX - left) / panelScale;
        double localY = (mouseY - top) / panelScale;
        for (Hint hint : hints) {
            if (hint.contains(localX, localY)) {
                setTooltipForNextRenderPass(font.split(hint.text(), Math.min(260, width - 24)));
                break;
            }
        }
    }

    private void renderParty(GuiGraphics graphics) {
        text(graphics, tr("gui.shadowedhearts.moonwound.party.count", state.members().size()), 18, 41, 65, TEXT, true);
        String badge = lobby() ? tr("gui.shadowedhearts.moonwound.party.ready_count", state.members().stream().filter(MoonwoundSnapshot.Member::ready).count(), state.members().size()) : tr("gui.shadowedhearts.moonwound.screen.locked");
        Component badgeText = styled(badge, false);
        graphics.drawString(font, badgeText, 122 - font.width(badgeText), 41, MUTED, true);
        for (int i = 0; i < MoonwoundSnapshot.MAX_MEMBERS; i++) {
            int y = 58 + i * 21;
            if (i >= state.members().size()) {
                graphics.fill(18, y, 34, y + 16, 0xFF738084);
                Component plus = styled("+", false);
                graphics.drawString(font, plus, 18 + (16 - font.width(plus)) / 2,
                        y + (16 - font.lineHeight) / 2, MUTED, true);
                text(graphics, lobby() ? tr("gui.shadowedhearts.moonwound.screen.open_slot") : tr("gui.shadowedhearts.moonwound.screen.unused_slot"), 39,
                        y + (16 - font.lineHeight) / 2, 83, MUTED, false);
                continue;
            }
            var member = state.members().get(i);
            var connection = minecraft.getConnection();
            var info = connection == null ? null : connection.getPlayerInfo(member.id());
            PlayerFaceRenderer.draw(graphics, info == null ? DefaultPlayerSkin.get(member.id()) : info.getSkin(), 18, y, 16);
            int textY = y + (16 - font.lineHeight * 2) / 2;
            text(graphics, member.name(), 39, textY, 83, TEXT, false);
            String detail = MoonwoundRecoveryPresentation.memberDetail(member, state.phase());
            if (member.id().equals(state.host())) detail = tr("gui.shadowedhearts.moonwound.party.host_detail", detail);
            text(graphics, detail, 39, textY + font.lineHeight, 83,
                    lobby() && member.ready() && member.availability() == MoonwoundSnapshot.Availability.PRESENT ? READY : MUTED, false);
            hints.add(new Hint(18, y, 104, 18,
                    Component.literal(MoonwoundRecoveryPresentation.memberExplanation(member, state.phase()))));
        }
    }

    private void renderSidebar(GuiGraphics graphics) {
        if (lobby()) {
            if (!host()) {
                text(graphics, tr("gui.shadowedhearts.moonwound.screen.host_approves_joins"), 18, 164, 104, TEXT, false);
                text(graphics, tr("gui.shadowedhearts.moonwound.screen.up_to_4_players"), 18, 177, 104, MUTED, false);
            }
            return;
        }
        int current = state.phase().layer() == null ? 3 : state.phase().layer().ordinal();
        String[] names = {tr("gui.shadowedhearts.moonwound.screen.shell"), tr("gui.shadowedhearts.moonwound.screen.mantle"), tr("gui.shadowedhearts.moonwound.screen.casing"), tr("gui.shadowedhearts.moonwound.screen.core")};
        for (int i = 0; i < names.length; i++) {
            int centerX = 18 + i * 26 + 13;
            int color = i < current || state.phase() == Phase.COMPLETE ? READY : i == current ? 0xFFE5CAF5 : 0xFFB8C8CD;
            graphics.fill(centerX - 3, 157, centerX + 3, 160, color);
            Component label = styled(names[i], false);
            graphics.drawString(font, label, centerX - font.width(label) / 2, 164, color, true);
        }
    }

    private void renderAbandon(GuiGraphics graphics) {
        text(graphics, tr("gui.shadowedhearts.moonwound.screen.abandon_this_moonwound"), DETAIL_X, 41, DETAIL_WIDTH, TEXT, true);
        wrapped(graphics, tr("gui.shadowedhearts.moonwound.screen.abandon_warning"),
                DETAIL_X, 66, DETAIL_WIDTH, 3, TEXT);
        wrapped(graphics, state.members().size() == 1
                        ? tr("gui.shadowedhearts.moonwound.screen.abandon_last_member_warning")
                        : tr("gui.shadowedhearts.moonwound.screen.abandon_party_continues"),
                DETAIL_X, 104, DETAIL_WIDTH, 5, MUTED);
    }

    private void renderLobby(GuiGraphics graphics) {
        var self = state.member(playerId());
        text(graphics, self == null ? tr("gui.shadowedhearts.moonwound.screen.join_the_expedition") : tr("gui.shadowedhearts.moonwound.screen.gather_your_party"), DETAIL_X, 41, DETAIL_WIDTH, TEXT, true);
        text(graphics, tr("gui.shadowedhearts.moonwound.screen.up_to_4_players_host_approves_joins"), DETAIL_X, 54, DETAIL_WIDTH, MUTED, false);
        wrapped(graphics, tr("gui.shadowedhearts.moonwound.screen.lobby_description"), DETAIL_X, 77, DETAIL_WIDTH, 4, TEXT);
        wrapped(graphics, tr("gui.shadowedhearts.moonwound.screen.start_requirements"), DETAIL_X, 120, DETAIL_WIDTH, 3, MUTED);
        if (self == null) {
            boolean pending = state.requests().stream().anyMatch(request -> request.id().equals(playerId()));
            text(graphics, pending ? tr("gui.shadowedhearts.moonwound.screen.waiting_for_host_approval") : state.members().size() >= 4 ? tr("gui.shadowedhearts.moonwound.screen.this_party_is_full")
                            : state.requests().size() >= MoonwoundSnapshot.MAX_REQUESTS ? tr("gui.shadowedhearts.moonwound.screen.the_request_queue_is_full") : tr("gui.shadowedhearts.moonwound.screen.request_a_place_in_this_party"),
                    DETAIL_X, 156, DETAIL_WIDTH, MUTED, false);
        } else if (!host()) {
            text(graphics, tr("gui.shadowedhearts.moonwound.screen.the_host_starts_when_everyone_is_ready"), DETAIL_X, 156, DETAIL_WIDTH, MUTED, false);
        }
    }

    private void renderRequests(GuiGraphics graphics) {
        text(graphics, tr("gui.shadowedhearts.moonwound.screen.join_requests"), DETAIL_X, 41, DETAIL_WIDTH, TEXT, true);
        text(graphics, tr("gui.shadowedhearts.moonwound.party.slots_filled", state.members().size()), DETAIL_X, 54, DETAIL_WIDTH, MUTED, false);
        if (state.requests().isEmpty()) text(graphics, tr("gui.shadowedhearts.moonwound.screen.no_pending_requests"), DETAIL_X, 80, DETAIL_WIDTH, MUTED, false);
        int first = requestPage * REQUESTS_PER_PAGE;
        for (int i = first; i < Math.min(first + REQUESTS_PER_PAGE, state.requests().size()); i++) {
            text(graphics, state.requests().get(i).name(), DETAIL_X, 71 + (i - first) * 46, DETAIL_WIDTH, TEXT, false);
        }
        if (requestPages() > 1) {
            Component page = styled((requestPage + 1) + " / " + requestPages(), false);
            graphics.drawString(font, page, 241 - font.width(page) / 2, 178, TEXT, true);
        }
    }

    private void renderVote(GuiGraphics graphics) {
        text(graphics, tr("gui.shadowedhearts.moonwound.screen.choose_the_core_s_fate"), DETAIL_X, 41, DETAIL_WIDTH, TEXT, true);
        // The server can enter VOTE while the core still needs exposing. Keep that instruction visible;
        // presentation must not infer permission by parsing the server's prose.
        wrapped(graphics, state.status(), DETAIL_X, 54, DETAIL_WIDTH, 2, MUTED);
        for (Outcome outcome : Outcome.values()) {
            int y = 81 + outcome.ordinal() * 26;
            if (outcome == Outcome.OVERLOAD) finaleIcon(graphics, DETAIL_X + 8, y + 2);
            else graphics.renderFakeItem((outcome == Outcome.HARVEST ? ModItems.CORE_SAMPLE.get() : ModItems.PURE_STABILIZER.get()).getDefaultInstance(), DETAIL_X, y + 2);
            text(graphics, outcomeLabel(outcome), DETAIL_X + 22, y, 124, TEXT, false);
            String detail = switch (outcome) {
                case HARVEST -> tr("gui.shadowedhearts.moonwound.screen.core_materials");
                case SEAL -> tr("gui.shadowedhearts.moonwound.screen.recovery_1_stabilizer");
                case OVERLOAD -> state.raidFinale() ? tr("gui.shadowedhearts.moonwound.screen.shadow_raid_boss") : tr("gui.shadowedhearts.moonwound.screen.alpha_shadow_battle");
            };
            text(graphics, detail, DETAIL_X + 22, y + 11, 124, MUTED, false);
            hints.add(new Hint(DETAIL_X, y, 148, 25, Component.literal(outcomeDetail(outcome))));
        }
        text(graphics, state.canSeal() ? tr("gui.shadowedhearts.moonwound.screen.seal_1_stabilizer_core_only_if_it_wins") : tr("gui.shadowedhearts.moonwound.screen.seal_needs_a_p_u_r_e_stabilizer"),
                DETAIL_X, 162, DETAIL_WIDTH, state.canSeal() ? TEXT : 0xFFFFDFC0, false);
        text(graphics, state.raidFinale() ? tr("gui.shadowedhearts.moonwound.screen.raid_defeat_the_shadow_boss_together") : tr("gui.shadowedhearts.moonwound.screen.alpha_first_successful_catch_keeps_it"),
                DETAIL_X, 174, DETAIL_WIDTH, TEXT, false);
        if (state.member(playerId()) == null) text(graphics, tr("gui.shadowedhearts.moonwound.screen.only_party_members_can_vote"), DETAIL_X, 185, DETAIL_WIDTH, MUTED, false);
        else if (state.pace().bonus() == PaceBonus.COMBAT_HEAL) bonusText(graphics, 185);
    }

    private void renderObjective(GuiGraphics graphics) {
        Phase phase = state.phase();
        if (phase.isExcavation() || phase.isWave()) {
            String layer = switch (phase.layer()) {
                case SHELL -> tr("gui.shadowedhearts.moonwound.screen.outer_shell");
                case MANTLE -> tr("gui.shadowedhearts.moonwound.screen.lattice_mantle");
                case CASING -> tr("gui.shadowedhearts.moonwound.screen.core_casing");
            };
            text(graphics, phase.isWave() ? tr("gui.shadowedhearts.moonwound.screen.defeat_the_void_shadows") : tr("gui.shadowedhearts.moonwound.objective.excavate_" + phase.layer().name().toLowerCase(java.util.Locale.ROOT)),
                    DETAIL_X, 41, DETAIL_WIDTH, TEXT, true);
            text(graphics, (phase.isWave() ? tr("gui.shadowedhearts.moonwound.objective.layer_wave", layer) : tr("gui.shadowedhearts.moonwound.objective.layer_number", layer, phase.layer().ordinal() + 1)),
                    DETAIL_X, 54, DETAIL_WIDTH, MUTED, false);
            ItemStack icon = phase.isWave() ? ModItems.CONDENSED_SHADOW.get().getDefaultInstance()
                    : new ItemStack(switch (phase.layer()) {
                        case SHELL -> ModBlocks.UMBRAFALL_SHELL.get();
                        case MANTLE -> ModBlocks.UMBRAL_LATTICE.get();
                        case CASING -> ModBlocks.UMBRAFALL_CORE_CASING.get();
                    });
            graphics.renderFakeItem(icon, 233, 79);
            int progress = phase.isWave() ? state.defeated() : state.mined();
            int target = phase.isWave() ? state.waveTarget() : state.miningTarget();
            text(graphics, phase.isWave() ? tr("gui.shadowedhearts.moonwound.screen.shadows_defeated") : tr("gui.shadowedhearts.moonwound.screen.original_blocks_mined"), DETAIL_X, 111, 150, TEXT, false);
            Component counter = styled(progress + " / " + target, false);
            graphics.drawString(font, counter, 342 - font.width(counter), 111, TEXT, true);
            graphics.fill(DETAIL_X, 126, 342, 131, 0xFF68767C);
            int filled = target <= 0 ? 0 : (int) Math.round(DETAIL_WIDTH * Math.clamp(progress / (double) target, 0D, 1D));
            graphics.fill(DETAIL_X, 126, DETAIL_X + filled, 131, 0xFFDEC8F2);
            if (!renderPaceDetails(graphics)) {
                wrapped(graphics, phase.isWave() ? tr("gui.shadowedhearts.moonwound.screen.win_your_battles_to_continue_losing_or_fleeing")
                        : tr("gui.shadowedhearts.moonwound.screen.a_void_shadow_wave_follows_this_layer"), DETAIL_X, 145, DETAIL_WIDTH, 3, TEXT);
            }
            text(graphics, tr("gui.shadowedhearts.moonwound.objective.next", switch (phase.layer()) {
                case SHELL -> tr("gui.shadowedhearts.moonwound.screen.lattice_mantle");
                case MANTLE -> tr("gui.shadowedhearts.moonwound.screen.core_casing");
                case CASING -> tr("gui.shadowedhearts.moonwound.screen.core_decision");
            }), DETAIL_X, 181, DETAIL_WIDTH, MUTED, false);
        } else if (phase == Phase.OVERLOAD) {
            text(graphics, state.raidFinale() ? tr("gui.shadowedhearts.moonwound.screen.shadow_raid_boss") : tr("gui.shadowedhearts.moonwound.screen.alpha_title"), DETAIL_X, 41, DETAIL_WIDTH, TEXT, true);
            text(graphics, tr("gui.shadowedhearts.moonwound.screen.the_core_is_overloaded"), DETAIL_X, 54, DETAIL_WIDTH, MUTED, false);
            finaleIcon(graphics, DETAIL_X + DETAIL_WIDTH / 2F, 79);
            text(graphics, state.raidFinale() ? tr("gui.shadowedhearts.moonwound.screen.defeat_the_boss_together") : tr("gui.shadowedhearts.moonwound.screen.catch_or_defeat_it"), DETAIL_X, 115, DETAIL_WIDTH, TEXT, true);
            wrapped(graphics, state.raidFinale()
                    ? tr("gui.shadowedhearts.moonwound.screen.raid_rules")
                    : tr("gui.shadowedhearts.moonwound.screen.alpha_capture_rules"),
                    DETAIL_X, 134, DETAIL_WIDTH, 4, TEXT);
            if (state.pace().bonus() != PaceBonus.NONE) bonusText(graphics, 182);
            else text(graphics, state.raidFinale() ? state.status() : tr("gui.shadowedhearts.moonwound.screen.lost_or_fled_retry_the_same_opponent"),
                        DETAIL_X, 182, DETAIL_WIDTH, MUTED, false);
        } else {
            text(graphics, tr("gui.shadowedhearts.moonwound.screen.moonwound_closed"), DETAIL_X, 41, DETAIL_WIDTH, TEXT, true);
            text(graphics, phase == Phase.ABANDONED ? tr("gui.shadowedhearts.moonwound.screen.this_event_is_closed") : tr("gui.shadowedhearts.moonwound.screen.the_event_is_complete"),
                    DETAIL_X, 54, DETAIL_WIDTH, MUTED, false);
            if (phase != Phase.ABANDONED) graphics.renderFakeItem(ModItems.CORE_SAMPLE.get().getDefaultInstance(), 233, 79);
            wrapped(graphics, state.status(), DETAIL_X, 111, DETAIL_WIDTH, 7, TEXT);
        }
    }

    private boolean renderPaceDetails(GuiGraphics graphics) {
        boolean target = state.pace().targetTicks() > 0;
        boolean bonus = state.pace().bonus() != PaceBonus.NONE;
        if (!target && !bonus) return false;
        String guidance = state.phase().isWave()
                ? tr("gui.shadowedhearts.moonwound.screen.win_battles_to_continue_losing_or_fleeing_does")
                : tr("gui.shadowedhearts.moonwound.screen.a_void_shadow_wave_follows_this_layer");
        // Register the complete explanation before clipped-label hints so it remains discoverable.
        hints.add(new Hint(DETAIL_X, 145, DETAIL_WIDTH, 30, Component.literal(
                (target ? MoonwoundPacePresentation.targetExplanation(state) + "\n" : "")
                        + guidance + (bonus ? "\n" + MoonwoundPacePresentation.bonusExplanation(state) : ""))));
        if (target) {
            text(graphics, MoonwoundPacePresentation.targetLine(state), DETAIL_X, 145, DETAIL_WIDTH, TEXT, false);
            text(graphics, MoonwoundPacePresentation.nextBonusLine(state), DETAIL_X, 155, DETAIL_WIDTH, MUTED, false);
            if (bonus) bonusText(graphics, 165);
            else text(graphics, state.phase().isWave() ? tr("gui.shadowedhearts.moonwound.screen.defeat_every_remaining_shadow") : guidance,
                    DETAIL_X, 165, DETAIL_WIDTH, MUTED, false);
        } else {
            bonusText(graphics, 145);
            wrapped(graphics, guidance, DETAIL_X, 155, DETAIL_WIDTH, 2, MUTED);
        }
        return true;
    }

    private void bonusText(GuiGraphics graphics, int y) {
        hints.add(new Hint(DETAIL_X, y, DETAIL_WIDTH, 10,
                Component.literal(MoonwoundPacePresentation.bonusExplanation(state))));
        text(graphics, MoonwoundPacePresentation.bonusLine(state), DETAIL_X, y, DETAIL_WIDTH, READY, false);
    }

    private void renderFooter(GuiGraphics graphics) {
        long eligibleVoters = MoonwoundRecoveryPresentation.eligibleVoters(state);
        String footer = state.recovery().paused() ? tr("gui.shadowedhearts.moonwound.screen.paused_return_nearby_to_resume")
                : lobby() && state.recovery().lobbyRemainingTicks() > 0
                ? tr("gui.shadowedhearts.moonwound.lobby.expires", MoonwoundRecoveryPresentation.duration(state.recovery().lobbyRemainingTicks()))
                : lobby() ? host() ? tr("gui.shadowedhearts.moonwound.screen.party_host") : state.member(playerId()) == null ? tr("gui.shadowedhearts.moonwound.screen.not_in_party") : tr("gui.shadowedhearts.moonwound.screen.party_member")
                : state.phase() == Phase.VOTE ? tr("gui.shadowedhearts.moonwound.vote.progress", MoonwoundRecoveryPresentation.recordedVotes(state), eligibleVoters, eligibleVoters / 2 + 1, state.voteRound()) : state.status();
        text(graphics, footer, 12, 204, 244, MUTED, false);
    }

    private void finaleIcon(GuiGraphics graphics, float centerX, int y) {
        if (state.raidFinale()) {
            graphics.renderFakeItem(ModItems.CONDENSED_SHADOW.get().getDefaultInstance(), Math.round(centerX - 8), y);
            return;
        }
        // The 37px asset has a 17px emblem between two 10px transparent margins.
        graphics.pose().pushPose();
        graphics.pose().translate(centerX - 17 / 2F, y, 0);
        graphics.blit(ALPHA, 0, 0, 10F, 0F, 17, 16, 37, 16);
        graphics.pose().popPose();
    }

    private void text(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color, boolean bold) {
        MutableComponent text = styled(value, bold);
        if (font.width(text) > maxWidth) {
            String prefix = font.substrByWidth(text, Math.max(0, maxWidth - font.width(styled("...", bold)))).getString();
            hints.add(new Hint(x, y, maxWidth, 10, text));
            text = styled(prefix + "...", bold);
        }
        graphics.drawString(font, text, x, y, color, true);
    }

    private void wrapped(GuiGraphics graphics, String value, int x, int y, int maxWidth, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(styled(value, false), maxWidth);
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) graphics.drawString(font, lines.get(i), x, y + i * 10, color, true);
        if (lines.size() > maxLines) hints.add(new Hint(x, y, maxWidth, maxLines * 10, Component.literal(value)));
    }

    private static MutableComponent styled(String text, boolean bold) {
        return Component.literal(text).withStyle(style -> style.withFont(CobblemonResources.INSTANCE.getDEFAULT_LARGE()).withBold(bold));
    }

    private String phaseLabel() {
        if (lobby()) return tr("gui.shadowedhearts.moonwound.screen.party_lobby");
        if (state.phase().isExcavation()) return tr("gui.shadowedhearts.moonwound.screen.excavation");
        if (state.phase().isWave()) return tr("gui.shadowedhearts.moonwound.screen.void_shadows");
        return switch (state.phase()) {
            case VOTE -> tr("gui.shadowedhearts.moonwound.screen.core_vote");
            case OVERLOAD -> tr("gui.shadowedhearts.moonwound.screen.overload");
            case COMPLETE -> tr("gui.shadowedhearts.moonwound.screen.complete");
            case ABANDONED -> tr("gui.shadowedhearts.moonwound.screen.abandoned");
            default -> "";
        };
    }

    private static String outcomeLabel(Outcome outcome) {
        return switch (outcome) { case HARVEST -> tr("gui.shadowedhearts.moonwound.screen.outcome.harvest"); case SEAL -> tr("gui.shadowedhearts.moonwound.screen.outcome.seal"); case OVERLOAD -> tr("gui.shadowedhearts.moonwound.screen.outcome.overload"); };
    }

    private String outcomeDetail(Outcome outcome) {
        String detail = switch (outcome) {
            case HARVEST -> tr("gui.shadowedhearts.moonwound.screen.outcome.harvest_description");
            case SEAL -> tr("gui.shadowedhearts.moonwound.screen.outcome.seal_description");
            case OVERLOAD -> state.raidFinale() ? tr("gui.shadowedhearts.moonwound.screen.outcome.raid_description")
                    : tr("gui.shadowedhearts.moonwound.screen.outcome.alpha_description");
        };
        if (outcome == Outcome.OVERLOAD && state.pace().bonus() == PaceBonus.COMBAT_HEAL) {
            detail = tr("gui.shadowedhearts.moonwound.paragraphs", detail, MoonwoundPacePresentation.bonusExplanation(state));
        }
        return detail;
    }

    private static Action voteAction(Outcome outcome) {
        return switch (outcome) { case HARVEST -> Action.VOTE_HARVEST; case SEAL -> Action.VOTE_SEAL; case OVERLOAD -> Action.VOTE_OVERLOAD; };
    }

    private static ResourceLocation cobblemonTexture(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/" + path + ".png");
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
