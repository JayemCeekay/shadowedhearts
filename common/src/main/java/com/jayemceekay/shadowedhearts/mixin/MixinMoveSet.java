package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = MoveSet.class, remap = false, priority = 1001)
public abstract class MixinMoveSet {

    /**
     * Wraps MoveSet.copyFrom to preserve any shadow moves that are currently in
     * this moveset. This handles two cases:
     *  1. NBT/codec loading (PokemonP1.into calls MoveSet.copyFrom directly), where
     *     the codec-decoded moveset may not include shadow moves.
     *  2. Client-side MoveSetUpdatePacket, where the server sends a moveset that
     *     has already had shadow moves stripped, and the client blindly applies it.
     *
    @WrapMethod(method = "copyFrom")
    private void shadowedhearts$wrapMoveSetCopyFrom(MoveSet other, Operation<Void> original) {
        MoveSet self = (MoveSet)(Object) this;

        // Snapshot shadow moves currently in this moveset (before the copy clears them)
        Move[] shadowSlots = new Move[MoveSet.MOVE_COUNT];
        boolean hasShadow = false;
        for (int i = 0; i < MoveSet.MOVE_COUNT; i++) {
            Move m = self.get(i);
            if (m != null && m.getType().equals(ElementalTypes.get("shadow"))) {
                shadowSlots[i] = m.copy();
                hasShadow = true;
            }
        }

        // Run the original copyFrom
        original.call(other);

        // Restore any shadow moves that were cleared during the copy
        if (hasShadow) {
            self.doWithoutEmitting(() -> {
                for (int i = 0; i < MoveSet.MOVE_COUNT; i++) {
                    if (shadowSlots[i] != null) {
                        self.setMove(i, shadowSlots[i]);
                    }
                }
                return null;
            });
            // The explicit sync will be handled by MixinPokemon.shadowedhearts$wrapCopyFrom
            // which calls ShadowAspectUtil.syncMoveSet() after all modifications complete
        }
    }*/
}
