package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.api.entity.PokemonSender;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.integration.rctmod.RCTBridgeHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Shared authoring and runtime target boundary. PokemonSender only advertises send/recall callbacks;
 * discovering it never invokes those callbacks or creates a party. Such entities are candidates,
 * and settings take effect only when their integration supplies an EntityBackedBattleActor.
 */
public final class NPCShadowTargets {
    private static final NPCShadowTargetPolicy.Observations<Entity> OBSERVED = new NPCShadowTargetPolicy.Observations<>();

    private NPCShadowTargets() {}

    public static boolean isSupported(Entity entity) {
        return kind(entity) != NPCShadowTargetPolicy.Kind.UNSUPPORTED;
    }

    public static Component labelText(Entity entity) {
        return Component.translatable("gui.shadowedhearts.npc_target." + capability(entity) + ".label");
    }

    public static Component helpText(Entity entity) {
        return Component.translatable("gui.shadowedhearts.npc_target." + capability(entity) + ".help");
    }

    public static String label(Entity entity) { return kind(entity).label(); }

    public static String capability(Entity entity) { return kind(entity).name().toLowerCase(java.util.Locale.ROOT); }

    public static String help(Entity entity) { return kind(entity).help(); }

    /** Returns the supported entity while retaining every previously eligible runtime actor. */
    public static Entity observeBattleActor(BattleActor actor) {
        if (!(actor instanceof EntityBackedBattleActor<?> backed)) return null;
        Entity entity = backed.getEntity();
        if (excluded(entity)) return null;
        // Exact instances only: an observed villager must not qualify every villager or its next spawn.
        OBSERVED.observe(entity);
        return isSupported(entity) ? entity : null;
    }

    private static NPCShadowTargetPolicy.Kind kind(Entity entity) {
        if (excluded(entity)) return NPCShadowTargetPolicy.Kind.UNSUPPORTED;
        return NPCShadowTargetPolicy.classify(false, entity instanceof NPCEntity,
                RCTBridgeHolder.INSTANCE.isTrainer(entity), entity instanceof PokemonSender, OBSERVED.contains(entity));
    }

    private static boolean excluded(Entity entity) {
        return !(entity instanceof LivingEntity) || entity instanceof Player || entity instanceof PokemonEntity;
    }
}
