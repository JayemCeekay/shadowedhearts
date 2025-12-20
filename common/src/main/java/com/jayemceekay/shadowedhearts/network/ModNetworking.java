package com.jayemceekay.shadowedhearts.network;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters;
import com.jayemceekay.shadowedhearts.network.payload.*;
import com.jayemceekay.shadowedhearts.snag.*;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Architectury-based networking registration and helpers, shared across platforms.
 */
@SuppressWarnings({"unused", "removal"})
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
            var pkt = SnagArmedS2C.STREAM_CODEC.decode(buf);
            ctx.queue(() -> {
                // Update local client flag for snag-armed visual feedback
                com.jayemceekay.shadowedhearts.client.snag.ClientSnagState.setArmed(pkt.armed());
            });
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
                // must be holding a Snag Machine or have it equipped as an accessory
                if (!cap.hasSnagMachine())
                    return;
                if (cap.cooldown() > 0) return;

                boolean requestArmed = pkt.armed();

                // Debug: gating info
                // Note: minimal debug; consider guarding by config if needed
                // sp.sendSystemMessage(Component.literal("[Snag] Arm request=" + requestArmed));

                if (requestArmed) {
                    // Only arm inside trainer battle with eligible shadow target
                    if (!SnagBattleUtil.isInTrainerBattle(sp) || !SnagBattleUtil.hasEligibleShadowOpponent(sp)) {
                        return; // silently ignore invalid arm
                    }
                    var machineStack = cap instanceof com.jayemceekay.shadowedhearts.snag.SimplePlayerSnagData sd ? 
                            sd.getMachineStack() : net.minecraft.world.item.ItemStack.EMPTY;

                    if (!machineStack.isEmpty() && machineStack.getItem() instanceof SnagMachineItem sm) {
                        // Initialize energy store but do NOT consume on arm; consumption happens on throw
                        SnagEnergy.ensureInitialized(machineStack, sm.capacity());

                        // Prevent arming if there isn't enough energy for an attempt
                        int currentEnergy = SnagCaps.get(sp).energy();
                        int required = SnagConfig.ENERGY_PER_ATTEMPT;
                        if (currentEnergy < required) {
                            cap.setCooldown(SnagConfig.TOGGLE_COOLDOWN_TICKS);
                            sp.sendSystemMessage(Component.translatable(
                                    "message.shadowedhearts.snag_machine.not_enough_energy",
                                    currentEnergy, required
                            ));
                            return;
                        }

                        cap.setArmed(true);
                        cap.setCooldown(SnagConfig.TOGGLE_COOLDOWN_TICKS);
                        var out = new RegistryFriendlyByteBuf(Unpooled.buffer(), sp.registryAccess());
                        SnagArmedS2C.STREAM_CODEC.encode(out, new SnagArmedS2C(true));
                        NetworkManager.sendToPlayer(sp, SnagArmedS2C.TYPE.id(), out);
                    }
                } else {
                    // Disarm request — allowed anytime; no cost
                    cap.setArmed(false);
                    cap.setCooldown(SnagConfig.TOGGLE_COOLDOWN_TICKS);
                    var out = new RegistryFriendlyByteBuf(Unpooled.buffer(), sp.registryAccess());
                    SnagArmedS2C.STREAM_CODEC.encode(out, new SnagArmedS2C(false));
                    NetworkManager.sendToPlayer(sp, SnagArmedS2C.TYPE.id(), out);
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
                    entity.getKnownMovement().x, entity.getKnownMovement().y, entity.getKnownMovement().z,
                    entity.getBbWidth(), entity.getBbHeight(), entity.getBoundingBox().getSize(),
                    tick,
                    PokemonAspectUtil.getHeartGauge(pokemonEntity.getPokemon())
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
                    entity.getKnownMovement().x, entity.getKnownMovement().y, entity.getKnownMovement().z,
                    entity.getBbWidth(), entity.getBbHeight(), entity.getBoundingBox().getSize(),
                    PokemonAspectUtil.getHeartGauge(pokemonEntity.getPokemon()));
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
                    entity.getKnownMovement().x, entity.getKnownMovement().y, entity.getKnownMovement().z,
                    entity.getBbWidth(), entity.getBbHeight(), entity.getBoundingBox().getSize(),
                    PokemonAspectUtil.getHeartGauge(pokemonEntity.getPokemon()));
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
