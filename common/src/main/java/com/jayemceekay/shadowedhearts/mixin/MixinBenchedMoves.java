package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.BenchedMoves;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = BenchedMoves.class, remap = false)
public abstract class MixinBenchedMoves {

    /**
     * Wraps saveToBuffer to iterate over a defensive copy of the benched moves list,
     * preventing ConcurrentModificationException when the Netty IO thread encodes
     * the packet while the main thread modifies the list.
     */
    @WrapMethod(method = "saveToBuffer")
    private void shadowedhearts$wrapSaveToBuffer(RegistryFriendlyByteBuf buffer, Operation<Void> original) {
        BenchedMoves self = (BenchedMoves) (Object) this;
        List<BenchedMove> snapshot = new ArrayList<>();
        for (BenchedMove bm : self) {
            snapshot.add(bm);
        }
        buffer.writeShort(snapshot.size());
        for (BenchedMove bm : snapshot) {
            bm.saveToBuffer(buffer);
        }
    }
}
