package com.jayemceekay.shadowedhearts.content.infusion;

import com.cobblemon.mod.common.Cobblemon;
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore;
import net.minecraft.core.RegistryAccess;

import java.util.UUID;

/** Checks the saved owner's party, PC, and Purification Chamber before returning an Infuser copy. */
public final class InfuserPokemonStorage {
    private InfuserPokemonStorage() {}

    public static boolean contains(UUID owner, UUID pokemonId, RegistryAccess registries) {
        var storage = Cobblemon.INSTANCE.getStorage();
        if (storage.getParty(owner, registries).get(pokemonId) != null
                || storage.getPC(owner, registries).get(pokemonId) != null) return true;

        // Load the owner's persisted store even when offline or no Chamber UI is open.
        // The already-stored Pokemon wins; a stale detached save must never overwrite it.
        var chamber = storage.getCustomStore(PurificationChamberStore.class, owner, registries);
        return chamber != null && chamber.get(pokemonId) != null;
    }
}
