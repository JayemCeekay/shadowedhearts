package com.jayemceekay.shadowedhearts.advancements;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.mojang.serialization.Codec;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class ModCriteriaTriggers {
    private static final DeferredRegister<net.minecraft.advancements.CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.TRIGGER_TYPE);

    public static final RegistrySupplier<GenericTrigger> WILD_SHADOW_DEFEATED =
            TRIGGERS.register("wild_shadow_defeated", GenericTrigger::new);
    public static final RegistrySupplier<GenericTrigger> SNAG_FROM_NPC =
            TRIGGERS.register("snag_from_npc", GenericTrigger::new);
    public static final RegistrySupplier<GenericTrigger> SHADOW_PURIFIED =
            TRIGGERS.register("shadow_purified", GenericTrigger::new);
    public static final RegistrySupplier<GenericTrigger> RELIC_STONE_INTERACT =
            TRIGGERS.register("relic_stone_interact", GenericTrigger::new);

    public static void init() {
        TRIGGERS.register();
    }

    public static void triggerWildShadowDefeated(ServerPlayer player) {
        WILD_SHADOW_DEFEATED.get().trigger(player);
    }

    public static void triggerSnagFromNpc(ServerPlayer player) {
        SNAG_FROM_NPC.get().trigger(player);
    }

    public static void triggerShadowPurified(ServerPlayer player) {
        SHADOW_PURIFIED.get().trigger(player);
    }

    public static void triggerRelicStoneInteract(ServerPlayer player) {
        RELIC_STONE_INTERACT.get().trigger(player);
    }

    public static class GenericTrigger extends SimpleCriterionTrigger<GenericTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player) {
            super.trigger(player, instance -> true);
        }

        public record Instance() implements SimpleInstance {
            public static final Codec<Instance> CODEC = Codec.unit(new Instance());

            @Override
            public Optional<ContextAwarePredicate> player() {
                return Optional.empty();
            }
        }
    }
}
