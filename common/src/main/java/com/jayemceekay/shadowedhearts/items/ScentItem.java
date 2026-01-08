package com.jayemceekay.shadowedhearts.items;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowService;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.heart.HeartGaugeDeltas;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Base Scent item. Applies heart gauge reduction to a targeted Shadow Pokémon based on nature.
 */
public class ScentItem extends Item {
    private final int multiplier; // 1=Joy, 2=Excite, 3=Vivid

    public ScentItem(Properties props, int multiplier) {
        super(props);
        this.multiplier = Math.max(1, multiplier);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(target instanceof PokemonEntity pe)) return InteractionResult.PASS;
        Pokemon pokemon = pe.getPokemon();
        if (pokemon == null) return InteractionResult.PASS;
        if (!PokemonAspectUtil.hasShadowAspect(pokemon)) return InteractionResult.PASS;

        // Check cooldown
        long now = level.getGameTime(); // using game time ticks
        long lastUse = PokemonAspectUtil.getScentCooldown(pokemon);
        int cooldownTicks = ShadowedHeartsConfigs.getInstance().getShadowConfig().scentCooldownSeconds() * 20;

        if (now - lastUse < cooldownTicks) {
            if (!level.isClientSide) {
                long remainingTicks = cooldownTicks - (now - lastUse);
                long remainingSeconds = remainingTicks / 20;
                player.displayClientMessage(Component.translatable("message.shadowedhearts.scent_cooldown", remainingSeconds).withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }

        // Compute delta and apply
        int base = HeartGaugeDeltas.getDelta(pokemon, HeartGaugeDeltas.EventType.SCENT);
        int delta = base * this.multiplier; // base is negative to open heart
        int current = PokemonAspectUtil.getHeartGaugeMeter(pokemon);
        int next = current + delta; // delta negative => reduces meter
        ShadowService.setHeartGauge(pokemon, pe, next);

        // Update cooldown
        PokemonAspectUtil.setScentCooldown(pokemon, now);

        // Consume one scent
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.CONSUME;
    }
}
