package com.jayemceekay.shadowedhearts.neoforge.net;

import com.cobblemon.mod.neoforge.net.NeoForgePacketInfo;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

/**
 * Java-based registrar to avoid classpath issues with Kotlin outputs during dev runs.
 */
public final class ShadowedHeartsNeoForgeNetworkRegistrar {
    private static final String PROTOCOL_VERSION = "1.0.0";

    private ShadowedHeartsNeoForgeNetworkRegistrar() {}

    public static void registerMessages(RegisterPayloadHandlersEvent event) {
        var registrar = event
                .registrar(Shadowedhearts.MOD_ID)
                .versioned(PROTOCOL_VERSION);

        // C2S handlers read and mutate world, inventory, battle and Pokemon storage state.
        // Cobblemon's wrapper invokes them directly; let NeoForge enqueue them on the server thread.
        var serverRegistrar = event
                .registrar(Shadowedhearts.MOD_ID)
                .versioned(PROTOCOL_VERSION)
                .executesOn(HandlerThread.MAIN);

        // Register S2C payloads to client
        for (var info : ShadowedHeartsNetwork.getS2cPayloads()) {
            var neoInfo = new NeoForgePacketInfo<>(info);
            neoInfo.registerToClient(registrar);
        }

        // Register C2S payloads to server
        for (var info : ShadowedHeartsNetwork.getC2sPayloads()) {
            var neoInfo = new NeoForgePacketInfo<>(info);
            neoInfo.registerToServer(serverRegistrar);
        }
    }
}
