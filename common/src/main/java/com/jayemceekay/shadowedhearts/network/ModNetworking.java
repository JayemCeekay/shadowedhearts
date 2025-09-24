package com.jayemceekay.shadowedhearts.network;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters;
import com.jayemceekay.shadowedhearts.core.ModItems;
import com.jayemceekay.shadowedhearts.network.payload.*;
import com.jayemceekay.shadowedhearts.poketoss.PokeToss;
import com.jayemceekay.shadowedhearts.poketoss.TacticalOrder;
import com.jayemceekay.shadowedhearts.snag.SnagCaps;
import com.jayemceekay.shadowedhearts.snag.SnagConfig;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Architectury-based networking registration and helpers, shared across platforms.
 */
public final class ModNetworking {
    private ModNetworking() {}

    /**
     * Register all packet receivers using Architectury's NetworkManager.
     * Call from common init (both Fabric and NeoForge launchers call Shadowedhearts.init()).
     */
    public static void register() {
        // Server -> Client listeners
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AuraStateS2C.TYPE.id(), (buf, ctx) -> {
            var pkt = AuraStateS2C.STREAM_CODEC.decode(buf);
            ctx.queue(() -> AuraEmitters.receiveState(pkt));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AuraLifecycleS2C.TYPE.id(), (buf, ctx) -> {
            var pkt = AuraLifecycleS2C.STREAM_CODEC.decode(buf);
            ctx.queue(() -> AuraEmitters.receiveLifecycle(pkt));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, SnagArmedS2C.TYPE.id(), (buf, ctx) -> {
            // currently no-op on client; decode to keep stream aligned
            SnagArmedS2C.STREAM_CODEC.decode(buf);
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, SnagResultS2C.TYPE.id(), (buf, ctx) -> {
            // currently no-op on client; decode to keep stream aligned
            SnagResultS2C.STREAM_CODEC.decode(buf);
        });

        // Client -> Server listeners
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SnagArmC2S.TYPE.id(), (buf, ctx) -> {
            var pkt = SnagArmC2S.STREAM_CODEC.decode(buf);
            ctx.queue(() -> {
                if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return;
                var cap = SnagCaps.get(sp);
                // must be holding the Snag Machine in offhand
                if (!sp.getOffhandItem().is(ModItems.SNAG_MACHINE.get())) return;
                if (cap.cooldown() > 0) return;
                boolean newVal = pkt.armed();
                cap.setArmed(newVal);
                cap.setCooldown(SnagConfig.TOGGLE_COOLDOWN_TICKS);
                var out = new RegistryFriendlyByteBuf(Unpooled.buffer(), sp.registryAccess());
                SnagArmedS2C.STREAM_CODEC.encode(out, new SnagArmedS2C(newVal));
                NetworkManager.sendToPlayer(sp, SnagArmedS2C.TYPE.id(), out);
            });
        });

        // Whistle selection brush: client -> server selection results
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, WhistleSelectionC2S.TYPE.id(), (buf, ctx) -> {
            var pkt = WhistleSelectionC2S.STREAM_CODEC.decode(buf);
            ctx.queue(() -> {
                if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return;
                if (!(sp.level() instanceof ServerLevel level)) return;
                java.util.Set<java.util.UUID> sel = new java.util.HashSet<>();
                for (int id : pkt.entityIds()) {
                    Entity e = level.getEntity(id);
                    if (e instanceof PokemonEntity pe) {
                        // Basic sanity: within 32 blocks of player
                        if (sp.distanceToSqr(e) <= (32.0 * 32.0)) {
                            sel.add(pe.getUUID());
                        }
                    }
                }
                com.jayemceekay.shadowedhearts.poketoss.PokeToss.setSelection(sp, sel);
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal("Whistle: Selected " + sel.size() + " Pokémon."), true);
            });
        });

        // Target order confirmation: use current selection set to issue order towards a target entity
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, com.jayemceekay.shadowedhearts.network.payload.IssueTargetOrderC2S.TYPE.id(), (buf, ctx) -> {
            var pkt = com.jayemceekay.shadowedhearts.network.payload.IssueTargetOrderC2S.STREAM_CODEC.decode(buf);
            ctx.queue(() -> {
                if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return;
                if (!(sp.level() instanceof ServerLevel level)) return;
                Entity targetEnt = level.getEntity(pkt.targetEntityId());
                if (!(targetEnt instanceof net.minecraft.world.entity.LivingEntity targetLiving)) return;
                // Currently only allow Pokémon targets
                if (!(targetEnt instanceof PokemonEntity)) return;
                java.util.Set<java.util.UUID> selection = com.jayemceekay.shadowedhearts.poketoss.PokeToss.getSelection(sp);
                int applied = 0;
                for (java.util.UUID selUuid : selection) {
                    Entity allyEnt = level.getEntity(selUuid);
                    if (allyEnt instanceof net.minecraft.world.entity.LivingEntity allyLiving) {
                        com.jayemceekay.shadowedhearts.poketoss.TacticalOrder order;
                        switch (pkt.orderType()) {
                            case GUARD_TARGET -> order = com.jayemceekay.shadowedhearts.poketoss.TacticalOrder.guard(targetLiving.getUUID(), 6.0f, true);
                            case ENGAGE_TARGET -> order = com.jayemceekay.shadowedhearts.poketoss.TacticalOrder.attack(targetLiving.getUUID());
                            default -> order = com.jayemceekay.shadowedhearts.poketoss.TacticalOrder.attack(targetLiving.getUUID());
                        }
                        boolean ok = com.jayemceekay.shadowedhearts.poketoss.PokeToss.issueOrder(level, allyLiving, order, sp);
                        if (ok) applied++;
                    }
                }
                if (applied > 0) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.literal("Orders issued to " + applied + " Pokémon."), true);
                }
            });
        });

        // Cancel orders for all currently selected allies
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, com.jayemceekay.shadowedhearts.network.payload.CancelOrdersC2S.TYPE.id(), (buf, ctx) -> {
            com.jayemceekay.shadowedhearts.network.payload.CancelOrdersC2S.STREAM_CODEC.decode(buf);
            ctx.queue(() -> {
                if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return;
                if (!(sp.level() instanceof ServerLevel level)) return;
                java.util.Set<java.util.UUID> selection = com.jayemceekay.shadowedhearts.poketoss.PokeToss.getSelection(sp);
                int cleared = 0;
                for (java.util.UUID selUuid : selection) {
                    Entity allyEnt = level.getEntity(selUuid);
                    if (allyEnt instanceof net.minecraft.world.entity.LivingEntity allyLiving) {
                        TacticalOrder order = TacticalOrder.cancel();
                        PokeToss.issueOrder(level, allyLiving, order, sp);
                        cleared++;
                    }
                }
                if (cleared > 0) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.literal("Cleared orders for " + cleared + " Pokémon."), true);
                } else {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.literal("No orders to clear."), true);
                }
            });
        });

        // Position order confirmation: use current selection set to issue a MOVE_TO or HOLD_POSITION order at a target BlockPos
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, com.jayemceekay.shadowedhearts.network.payload.IssuePosOrderC2S.TYPE.id(), (buf, ctx) -> {
            var pkt = com.jayemceekay.shadowedhearts.network.payload.IssuePosOrderC2S.STREAM_CODEC.decode(buf);
            ctx.queue(() -> {
                if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return;
                if (!(sp.level() instanceof ServerLevel level)) return;
                java.util.Set<java.util.UUID> selection = com.jayemceekay.shadowedhearts.poketoss.PokeToss.getSelection(sp);
                int applied = 0;
                for (java.util.UUID selUuid : selection) {
                    Entity allyEnt = level.getEntity(selUuid);
                    if (allyEnt instanceof net.minecraft.world.entity.LivingEntity allyLiving) {
                        com.jayemceekay.shadowedhearts.poketoss.TacticalOrder order;
                        switch (pkt.orderType()) {
                            case MOVE_TO -> order = com.jayemceekay.shadowedhearts.poketoss.TacticalOrder.moveTo(pkt.pos(), pkt.radius());
                            case HOLD_POSITION -> order = com.jayemceekay.shadowedhearts.poketoss.TacticalOrder.holdAt(pkt.pos(), pkt.radius(), pkt.persistent());
                            default -> {
                                // Unsupported type here; skip
                                continue;
                            }
                        }
                        boolean ok = com.jayemceekay.shadowedhearts.poketoss.PokeToss.issueOrder(level, allyLiving, order, sp);
                        if (ok) applied++;
                    }
                }
                if (applied > 0) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.literal("Orders issued to " + applied + " Pokémon."), true);
                }
            });
        });
    }

    /** Utility: broadcast to all players tracking the entity (and the entity if it's a player). */
    public static void broadcastStateToTracking(Entity entity, long tick) {
        if (entity instanceof PokemonEntity pokemonEntity) {
            var pkt = new AuraStateS2C(
                    entity.getId(),
                    entity.getX(), entity.getY(), entity.getZ(),
                    entity.getDeltaMovement().x, entity.getDeltaMovement().y, entity.getDeltaMovement().z,
                    entity.getBbWidth(), entity.getBbHeight(), entity.getBoundingBox().getSize(),
                    tick,
                    PokemonAspectUtil.getCorruption(pokemonEntity.getPokemon())
            );
            ServerLevel level = (ServerLevel) entity.level();
            for (ServerPlayer sp : level.players()) {
                var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                AuraStateS2C.STREAM_CODEC.encode(buf, pkt);
                NetworkManager.sendToPlayer(sp, AuraStateS2C.TYPE.id(), buf);
            }
        }
    }

    public static void broadcastAuraStartToTracking(Entity entity) {
        if (entity instanceof PokemonEntity pokemonEntity) {
            var pkt = new AuraLifecycleS2C(entity.getId(), AuraLifecycleS2C.Action.START, 0,
                    entity.getX(), entity.getY(), entity.getZ(),
                    entity.getDeltaMovement().x, entity.getDeltaMovement().y, entity.getDeltaMovement().z,
                    entity.getBbWidth(), entity.getBbHeight(), entity.getBoundingBox().getSize(),
                    PokemonAspectUtil.getCorruption(pokemonEntity.getPokemon()));
            if (entity.level() instanceof ServerLevel level) {
                for (ServerPlayer sp : level.players()) {
                    var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                    AuraLifecycleS2C.STREAM_CODEC.encode(buf, pkt);
                    NetworkManager.sendToPlayer(sp, AuraLifecycleS2C.TYPE.id(), buf);
                }
            }
        }
    }

    public static void broadcastAuraFadeOutToTracking(Entity entity, int outTicks) {
        if (entity instanceof PokemonEntity pokemonEntity) {
            var pkt = new AuraLifecycleS2C(entity.getId(), AuraLifecycleS2C.Action.FADE_OUT, Math.max(1, outTicks),
                    entity.getX(), entity.getY(), entity.getZ(),
                    entity.getDeltaMovement().x, entity.getDeltaMovement().y, entity.getDeltaMovement().z,
                    entity.getBbWidth(), entity.getBbHeight(), entity.getBoundingBox().getSize(),
                    PokemonAspectUtil.getCorruption(pokemonEntity.getPokemon()));
            if (entity.level() instanceof ServerLevel level) {
                for (ServerPlayer sp : level.players()) {
                    var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                    AuraLifecycleS2C.STREAM_CODEC.encode(buf, pkt);
                    NetworkManager.sendToPlayer(sp, AuraLifecycleS2C.TYPE.id(), buf);
                }
            }
        }
    }
}
