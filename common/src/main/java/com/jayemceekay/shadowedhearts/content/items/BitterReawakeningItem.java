package com.jayemceekay.shadowedhearts.content.items;

import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionSettings;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleSettings;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleFeedback;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.callback.PartySelectCallbacks;
import com.cobblemon.mod.common.api.callback.PartySelectPokemonDTO;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.healing.PokemonHealedEvent;
import com.cobblemon.mod.common.api.item.HealingSource;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.trade.TradeManager;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonData;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import kotlin.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native party-selected field revival paid with a previewed, fully payable Heart Gauge setback. */
public final class BitterReawakeningItem extends Item implements HealingSource {
    private static final String MESSAGE = "message.shadowedhearts.bitter_reawakening.";
    private static final long SELECTION_LIFETIME_TICKS = 20 * 60;

    public BitterReawakeningItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (ItemRuleFeedback.appendDisabled(stack, tooltip)) return;
        tooltip.add(Component.translatable("tooltip.shadowedhearts.bitter_reawakening.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.shadowedhearts.bitter_reawakening.cost").withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide) return ItemRuleFeedback.permitsClient("bitterReawakening")
                ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
        if (!(user instanceof ServerPlayer player)) return InteractionResultHolder.fail(stack);
        String refusal = contextRefusal(player, stack, hand);
        if (refusal != null) {
            refuse(player, refusal);
            return InteractionResultHolder.fail(stack);
        }

        List<Selection> choices = new ArrayList<>();
        List<PartySelectPokemonDTO> shown = new ArrayList<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon == null) continue;
            int current = ShadowAspectUtil.getHeartGaugeMeter(pokemon);
            int maximum = HeartGaugeConfig.getMax(pokemon);
            int maximumHealth = pokemon.getMaxHealth();
            String reason = targetRefusal(player, pokemon);
            if (reason == null && !BitterReawakeningRules.canPay(current, maximum)) reason = "full_heart";
            if (reason == null && maximumHealth <= 0) reason = "changed";
            boolean enabled = reason == null;
            Selection selection = new Selection(pokemon, current, maximum, maximumHealth,
                    enabled ? BitterReawakeningRules.afterPayment(current, maximum) : current,
                    enabled ? BitterReawakeningRules.revivedHealth(maximumHealth) : 0);
            choices.add(selection);
            List<Component> hover = enabled ? preview(selection) : List.of(
                    Component.translatable(MESSAGE + reason).withStyle(ChatFormatting.RED));
            shown.add(new PartySelectPokemonDTO(pokemon, enabled, hover));
        }
        if (shown.stream().noneMatch(PartySelectPokemonDTO::getEnabled)) {
            refuse(player, "no_targets");
            return InteractionResultHolder.fail(stack);
        }

        player.displayClientMessage(Component.translatable(MESSAGE + "inspect").withStyle(ChatFormatting.YELLOW), true);
        long expiresAt = level.getGameTime() + SELECTION_LIFETIME_TICKS;
        AtomicBoolean finished = new AtomicBoolean();
        PartySelectCallbacks.INSTANCE.create(player, stack.getHoverName(), shown,
                ignored -> {
                    finished.set(true);
                    return Unit.INSTANCE;
                },
                (respondingPlayer, index) -> {
                    // The native registry also removes the token before dispatch. This
                    // guard protects the captured action against replay or reentrancy.
                    if (!finished.compareAndSet(false, true)) return Unit.INSTANCE;
                    if (respondingPlayer != player || index < 0 || index >= choices.size()
                            || !shown.get(index).getEnabled()) return Unit.INSTANCE;
                    Selection selection = choices.get(index);
                    if (!validateSelection(player, stack, hand, level, expiresAt, selection)) return Unit.INSTANCE;

                    PokemonHealedEvent event = new PokemonHealedEvent(selection.pokemon(), selection.health(), this);
                    CobblemonEvents.POKEMON_HEALED.postThen(event,
                            cancelled -> {
                                refuse(player, "healing_cancelled");
                                return Unit.INSTANCE;
                            },
                            allowed -> {
                                // Listeners may cancel, change the proposed healing, or
                                // mutate ownership/HP/gauge while this event is dispatched.
                                // Never charge against a preview that is no longer accurate.
                                if (allowed.getAmount() != selection.health()) {
                                    refuse(player, "changed");
                                    return Unit.INSTANCE;
                                }
                                if (!validateSelection(player, stack, hand, level, expiresAt, selection)) return Unit.INSTANCE;

                                Pokemon pokemon = selection.pokemon();
                                pokemon.setCurrentHealth(selection.health());
                                if (pokemon.getCurrentHealth() != selection.health()) {
                                    refuse(player, "changed");
                                    return Unit.INSTANCE;
                                }
                                // Reclosing changes ordinary-move availability; opening-only
                                // promotion must not replace moves or heal their PP here.
                                ShadowAspectUtil.setHeartGaugeValue(pokemon, selection.next(), false);
                                PokemonEntity entity = pokemon.getEntity();
                                if (entity != null) ShadowPokemonData.syncFromPokemon(entity);
                                if (!player.getAbilities().instabuild) stack.shrink(1);
                                ShadowService.syncAll(pokemon);
                                player.getInventory().setChanged();
                                player.containerMenu.broadcastChanges();
                                player.displayClientMessage(Component.translatable(MESSAGE + "revived",
                                        pokemon.getDisplayName(false), selection.health(), selection.next(), selection.maximum())
                                        .withStyle(ChatFormatting.GREEN), true);
                                return Unit.INSTANCE;
                            });
                    return Unit.INSTANCE;
                });
        return InteractionResultHolder.consume(stack);
    }

    private static List<Component> preview(Selection selection) {
        return List.of(
                Component.translatable(MESSAGE + "preview.health", selection.health(), selection.maximumHealth()).withStyle(ChatFormatting.GREEN),
                Component.translatable(MESSAGE + "preview.heart", selection.current(), selection.next(), selection.maximum()).withStyle(ChatFormatting.YELLOW),
                Component.translatable(MESSAGE + "preview.moves",
                        ShadowAspectUtil.getAllowedVisibleNonShadowMoves(selection.pokemon()),
                        BitterReawakeningRules.allowedNonShadowMoves(selection.next(), selection.maximum(),
                                ShadowRestrictionSettings.serverCurrent())).withStyle(ChatFormatting.YELLOW),
                Component.translatable(MESSAGE + "preview.confirm").withStyle(ChatFormatting.GRAY));
    }

    private boolean validateSelection(ServerPlayer player, ItemStack stack, InteractionHand hand,
                                      Level originalLevel, long expiresAt, Selection selection) {
        String reason = contextRefusal(player, stack, hand);
        if (reason == null) reason = targetRefusal(player, selection.pokemon());
        if (reason != null) {
            refuse(player, reason);
            return false;
        }
        if (player.level() != originalLevel || originalLevel.getGameTime() > expiresAt
                || ShadowAspectUtil.getHeartGaugeMeter(selection.pokemon()) != selection.current()
                || HeartGaugeConfig.getMax(selection.pokemon()) != selection.maximum()
                || selection.pokemon().getMaxHealth() != selection.maximumHealth()
                || !BitterReawakeningRules.canPay(selection.current(), selection.maximum())) {
            refuse(player, "changed");
            return false;
        }
        return true;
    }

    private String contextRefusal(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        if (!ItemRuleSettings.serverCurrent().effectEnabled("bitterReawakening")) return "disabled";
        if (BattleRegistry.getBattleByParticipatingPlayerId(player.getUUID()) != null) return "in_battle";
        if (TradeManager.INSTANCE.getActiveTrade(player.getUUID()) != null) return "in_trade";
        if (!player.isAlive() || player.isRemoved() || stack.isEmpty() || stack.getItem() != this
                || player.getItemInHand(hand) != stack) return "changed";
        return null;
    }

    private static String targetRefusal(ServerPlayer player, Pokemon pokemon) {
        if (!player.getUUID().equals(pokemon.getOwnerUUID())
                || Cobblemon.INSTANCE.getStorage().getParty(player).get(pokemon.getUuid()) != pokemon) return "not_owned";
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return "not_shadow";
        if (pokemon.isBattleClone() || pokemon.getEntity() != null && pokemon.getEntity().isBattling()) return "in_battle";
        if (!pokemon.isFainted()) return "not_fainted";
        return null;
    }

    private static void refuse(Player player, String reason) {
        player.displayClientMessage(Component.translatable(MESSAGE + reason).withStyle(ChatFormatting.RED), true);
    }

    private record Selection(Pokemon pokemon, int current, int maximum, int maximumHealth, int next, int health) { }
}
