package com.jayemceekay.shadowedhearts.content.items;

import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers;
import com.jayemceekay.shadowedhearts.common.heart.HeartGaugeDeltas;
import com.jayemceekay.shadowedhearts.common.heart.HeartGaugeEvents;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.jayemceekay.shadowedhearts.common.shadow.ScentModeRecovery;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.config.GameplayTuningConfig;
import com.jayemceekay.shadowedhearts.config.ProgressionEconomyConfig;
import com.jayemceekay.shadowedhearts.config.PresetCompatibilityConfig;
import com.jayemceekay.shadowedhearts.common.heart.HeartGaugeProgressionMath;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base Scent item. Applies nature-based Heart Gauge reduction to the user's own Shadow Pokémon.
 */
public class ScentItem extends Item {
    private final int multiplier; // 1=Joy, 2=Excite, 3=Vivid
    private final String scentType;
    private final int color;

    private enum AffinityTier {
        STRONG_LIKE(1.50),
        LIKE(1.25),
        NEUTRAL(1.00),
        DISLIKE(0.75),
        STRONG_DISLIKE(0.50);

        final double multiplier;

        AffinityTier(double multiplier) {
            this.multiplier = multiplier;
        }
    }

    // Canonical scent types (string ids used by your items/recipes/tooltips)
    private static final String TYPE_SOOTHING = "soothing";
    private static final String TYPE_STIMULATING = "stimulating";
    private static final String TYPE_AFFECTIONATE = "affectionate";
    private static final String TYPE_CLARIFYING = "clarifying";
    private static final String TYPE_RESOLUTE = "resolute";

    /**
     * Nature -> Primary scent type.
     * FULL coverage for all 25 natures. (5 natures per type)
     */
    private static final Map<Nature, String> PRIMARY_TYPE_BY_NATURE = Map.ofEntries(
            // Soothing (5)
            Map.entry(Natures.CALM, TYPE_SOOTHING),
            Map.entry(Natures.GENTLE, TYPE_SOOTHING),
            Map.entry(Natures.RELAXED, TYPE_SOOTHING),
            Map.entry(Natures.BASHFUL, TYPE_SOOTHING),
            Map.entry(Natures.TIMID, TYPE_SOOTHING),

            // Stimulating (5)
            Map.entry(Natures.HASTY, TYPE_STIMULATING),
            Map.entry(Natures.JOLLY, TYPE_STIMULATING),
            Map.entry(Natures.NAIVE, TYPE_STIMULATING),
            Map.entry(Natures.LAX, TYPE_STIMULATING),
            Map.entry(Natures.RASH, TYPE_STIMULATING),

            // Affectionate (5)
            Map.entry(Natures.LONELY, TYPE_AFFECTIONATE),
            Map.entry(Natures.DOCILE, TYPE_AFFECTIONATE),
            Map.entry(Natures.HARDY, TYPE_AFFECTIONATE),
            Map.entry(Natures.QUIRKY, TYPE_AFFECTIONATE),
            Map.entry(Natures.CAREFUL, TYPE_AFFECTIONATE),

            // Clarifying (5)
            Map.entry(Natures.MODEST, TYPE_CLARIFYING),
            Map.entry(Natures.MILD, TYPE_CLARIFYING),
            Map.entry(Natures.QUIET, TYPE_CLARIFYING),
            Map.entry(Natures.SERIOUS, TYPE_CLARIFYING),
            Map.entry(Natures.SASSY, TYPE_CLARIFYING),

            // Resolute (5)
            Map.entry(Natures.BRAVE, TYPE_RESOLUTE),
            Map.entry(Natures.ADAMANT, TYPE_RESOLUTE),
            Map.entry(Natures.IMPISH, TYPE_RESOLUTE),
            Map.entry(Natures.NAUGHTY, TYPE_RESOLUTE),
            Map.entry(Natures.BOLD, TYPE_RESOLUTE)
    );

    /**
     * Nature -> (scent type -> affinity tier)
     * FULL coverage: every nature has an affinity tier for all 5 types.
     */
    private static final Map<Nature, Map<String, AffinityTier>> AFFINITY = buildAffinityTable();

    private static Map<Nature, Map<String, AffinityTier>> buildAffinityTable() {
        // Deterministic template per primary type (easy to tweak later).
        record Template(String primary, String like, String neutral, String dislike, String strongDislike) {
        }

        Map<String, Template> templates = Map.of(
                TYPE_SOOTHING, new Template(TYPE_SOOTHING, TYPE_AFFECTIONATE, TYPE_CLARIFYING, TYPE_STIMULATING, TYPE_RESOLUTE),
                TYPE_STIMULATING, new Template(TYPE_STIMULATING, TYPE_CLARIFYING, TYPE_RESOLUTE, TYPE_SOOTHING, TYPE_AFFECTIONATE),
                TYPE_AFFECTIONATE, new Template(TYPE_AFFECTIONATE, TYPE_SOOTHING, TYPE_RESOLUTE, TYPE_CLARIFYING, TYPE_STIMULATING),
                TYPE_CLARIFYING, new Template(TYPE_CLARIFYING, TYPE_STIMULATING, TYPE_AFFECTIONATE, TYPE_RESOLUTE, TYPE_SOOTHING),
                TYPE_RESOLUTE, new Template(TYPE_RESOLUTE, TYPE_CLARIFYING, TYPE_STIMULATING, TYPE_AFFECTIONATE, TYPE_SOOTHING)
        );

        Map<Nature, Map<String, AffinityTier>> out = new HashMap<>();
        for (var entry : PRIMARY_TYPE_BY_NATURE.entrySet()) {
            Nature nature = entry.getKey();
            String primaryType = entry.getValue();
            Template t = templates.get(primaryType);
            if (t == null) continue;

            Map<String, AffinityTier> map = new HashMap<>();
            map.put(t.primary(), AffinityTier.STRONG_LIKE);
            map.put(t.like(), AffinityTier.LIKE);
            map.put(t.neutral(), AffinityTier.NEUTRAL);
            map.put(t.dislike(), AffinityTier.DISLIKE);
            map.put(t.strongDislike(), AffinityTier.STRONG_DISLIKE);

            // Safety: ensure all five exist
            map.putIfAbsent(TYPE_SOOTHING, AffinityTier.NEUTRAL);
            map.putIfAbsent(TYPE_STIMULATING, AffinityTier.NEUTRAL);
            map.putIfAbsent(TYPE_AFFECTIONATE, AffinityTier.NEUTRAL);
            map.putIfAbsent(TYPE_CLARIFYING, AffinityTier.NEUTRAL);
            map.putIfAbsent(TYPE_RESOLUTE, AffinityTier.NEUTRAL);

            out.put(nature, java.util.Collections.unmodifiableMap(map));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    private static AffinityTier getAffinityTier(@org.jetbrains.annotations.Nullable Nature nature, String scentType) {
        if (nature == null) return AffinityTier.NEUTRAL;
        return AFFINITY.getOrDefault(nature, Map.of()).getOrDefault(scentType, AffinityTier.NEUTRAL);
    }

    public ScentItem(Properties props, int multiplier) {
        this(props, multiplier, "none", 0xFFFFFF);
    }

    public ScentItem(Properties props, int multiplier, String scentType, int color) {
        super(props);
        this.multiplier = Math.max(1, multiplier);
        this.scentType = scentType;
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        if (ShadowedHeartsConfigs.getInstance().getShadowConfig().expandedScentSystemEnabled()) {
            if (!scentType.equals("none")) {
                String typeKey = "tooltip.shadowedhearts.scent_type." + scentType;
                tooltip.add(Component.translatable(typeKey).withStyle(ChatFormatting.GRAY));
            }
        } else {
            Item item = stack.getItem();
            if (item == ModItems.JOY_SCENT.get()) {
                tooltip.add(Component.translatable("tooltip.shadowedhearts.joy_scent.description").withStyle(ChatFormatting.GRAY));
            } else if (item == ModItems.EXCITE_SCENT.get()) {
                tooltip.add(Component.translatable("tooltip.shadowedhearts.excite_scent.description").withStyle(ChatFormatting.GRAY));
            } else if (item == ModItems.VIVID_SCENT.get()) {
                tooltip.add(Component.translatable("tooltip.shadowedhearts.vivid_scent.description").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(target instanceof PokemonEntity pe)) return InteractionResult.PASS;
        Pokemon pokemon = pe.getPokemon();
        if (pokemon == null) return InteractionResult.PASS;
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return InteractionResult.PASS;

        if (!PresetCompatibilityConfig.INSTANCE.expandedScentItemsEnabled()
                && stack.getItem() != ModItems.JOY_SCENT.get() && stack.getItem() != ModItems.EXCITE_SCENT.get()
                && stack.getItem() != ModItems.VIVID_SCENT.get()) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.scent_disabled")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (!pokemon.isPlayerOwned() || !player.getUUID().equals(pokemon.getOwnerUUID())) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.scent_not_owned")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        int current = ShadowAspectUtil.getHeartGaugeMeter(pokemon);
        boolean canCalm = ProgressionEconomyConfig.INSTANCE.scentsClearModes() && ScentModeRecovery.hasMode(pokemon);
        if (!canCalm && (current <= 0 || GameplayTuningConfig.INSTANCE.scentMultiplier() <= 0.0)) return noEffect(player);

        // Check cooldown
        long now = level.getGameTime(); // using game time ticks
        long lastUse = ShadowAspectUtil.getScentCooldown(pokemon);
        long cooldownTicks = ShadowedHeartsConfigs.getInstance().getShadowConfig().scentCooldownSeconds() * 20L;

        if (cooldownTicks > 0 && lastUse >= 0 && now - lastUse < cooldownTicks) {
            long remainingTicks = cooldownTicks - (now - lastUse);
            long remainingSeconds = (remainingTicks + 19) / 20;
            player.displayClientMessage(Component.translatable("message.shadowedhearts.scent_cooldown", remainingSeconds).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        // Compute delta and apply
        int base = ShadowedHeartsConfigs.getInstance().getShadowConfig().expandedScentSystemEnabled() ? -100 : HeartGaugeDeltas.getDelta(pokemon, HeartGaugeDeltas.EventType.SCENT);
        double finalMultiplier = this.multiplier;
        AffinityTier tier = AffinityTier.NEUTRAL;

        // Expanded system: FULL coverage across all 25 natures & all 5 scent types.
        if (ShadowedHeartsConfigs.getInstance().getShadowConfig().expandedScentSystemEnabled() && !scentType.equals("none")) {
            Nature nature = pokemon.getNature();
            tier = getAffinityTier(nature, scentType);
            finalMultiplier *= tier.multiplier;
        }

        int delta = (int) (base * finalMultiplier); // base is negative to open heart
        delta = HeartGaugeProgressionMath.scaleReduction(delta, GameplayTuningConfig.INSTANCE.scentMultiplier());
        if (current > 0 && delta < 0) {
            int next = Math.max(0, current + delta);
            ShadowService.setHeartGauge(pokemon, pe, next);
        }
        boolean reduced = ShadowAspectUtil.getHeartGaugeMeter(pokemon) < current;
        boolean calmed = canCalm && ScentModeRecovery.calm(pokemon, pe);
        if (!reduced && !calmed) return noEffect(player);

        if (pokemon.getOwnerPlayer() instanceof ServerPlayer owner) {
            HeartGaugeEvents.recordCareProgress(owner, pokemon, current);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ModCriteriaTriggers.triggerProgression(serverPlayer, "scent_used");
        }

        // A real gauge reduction or mode recovery starts cooldown and consumes one dose.
        ShadowAspectUtil.setScentCooldown(pokemon, now);

        // Consume one scent
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        if (tier == AffinityTier.STRONG_LIKE || tier == AffinityTier.LIKE) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.scent_preferred").withStyle(ChatFormatting.GREEN), true);
        } else if (tier == AffinityTier.DISLIKE || tier == AffinityTier.STRONG_DISLIKE) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.scent_disliked").withStyle(ChatFormatting.RED), true);
        }

        return InteractionResult.CONSUME;
    }

    private static InteractionResult noEffect(Player player) {
        player.displayClientMessage(Component.translatable("message.shadowedhearts.scent_no_effect").withStyle(ChatFormatting.RED), true);
        return InteractionResult.FAIL;
    }
}
