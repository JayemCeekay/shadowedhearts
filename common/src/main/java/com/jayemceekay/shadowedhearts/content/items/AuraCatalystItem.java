package com.jayemceekay.shadowedhearts.content.items;

import com.cobblemon.mod.common.api.interaction.PokemonEntityInteraction;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.aura.AuraStyleProperties;
import com.jayemceekay.shadowedhearts.common.aura.AuraStyleSelection;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Consumes one catalyst to replace an owned Pokemon's saved cosmetic aura selection. */
public final class AuraCatalystItem extends Item implements PokemonEntityInteraction {
    private static final String MESSAGE_PREFIX = "message.shadowedhearts.aura_style_item.";
    private static final String TOOLTIP_PREFIX = "tooltip.shadowedhearts.aura_style_item.";

    private final AuraStyleSelection style;

    public AuraCatalystItem(Properties properties, AuraStyleSelection style) {
        super(properties);
        this.style = Objects.requireNonNull(style, "style");
    }

    public AuraStyleSelection getStyle() {
        return style;
    }

    @Override
    public Set<Ownership> getAccepted() {
        return Set.of(Ownership.OWNER);
    }

    @Override
    public SoundEvent getSound() {
        return null;
    }

    /** Native Cobblemon item interaction; rejected and unchanged selections still handle the click. */
    @Override
    public boolean processInteraction(ServerPlayer player, PokemonEntity entity, ItemStack stack) {
        return interactLivingEntity(stack, player, entity, InteractionHand.MAIN_HAND) != InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (style == AuraStyleSelection.DEFAULT) {
            tooltip.add(Component.translatable(TOOLTIP_PREFIX + "reset").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(TOOLTIP_PREFIX + "style", styleName())
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(TOOLTIP_PREFIX + "use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(TOOLTIP_PREFIX + "cosmetic").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof PokemonEntity pokemonEntity)) return InteractionResult.PASS;
        if (stack.isEmpty()) return InteractionResult.FAIL;
        // Client Pokemon snapshots do not carry every custom property; the server validates and writes state.
        if (player.level().isClientSide) return InteractionResult.SUCCESS;

        Pokemon pokemon = pokemonEntity.getPokemon();
        if (pokemon == null) return InteractionResult.PASS;
        if (!player.getUUID().equals(pokemon.getOwnerUUID())) {
            return refuse(player, "not_owned", ChatFormatting.RED);
        }
        if (pokemon.isBattleClone() || pokemonEntity.isBattling()
                || BattleRegistry.getBattleByParticipatingPlayerId(player.getUUID()) != null) {
            return refuse(player, "in_battle", ChatFormatting.RED);
        }
        if (pokemon.isFainted() || !pokemonEntity.isAlive()) {
            return refuse(player, "fainted", ChatFormatting.RED);
        }
        if (AuraStyleProperties.get(pokemon) == style) {
            return refuse(player, "already_selected", ChatFormatting.YELLOW);
        }

        AuraStyleProperties.set(pokemon, style);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        String message = ShadowAspectUtil.shouldHaveShadowAura(pokemon) ? "changed" : "changed_hidden";
        player.displayClientMessage(Component.translatable(MESSAGE_PREFIX + message,
                pokemon.getDisplayName(false), styleName()).withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.CONSUME;
    }

    private Component styleName() {
        return Component.translatable("aura_style.shadowedhearts." + style.serializedName());
    }

    private static InteractionResult refuse(Player player, String reason, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(MESSAGE_PREFIX + reason).withStyle(color), true);
        return InteractionResult.FAIL;
    }
}
