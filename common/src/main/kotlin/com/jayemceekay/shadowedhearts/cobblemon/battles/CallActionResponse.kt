package com.jayemceekay.shadowedhearts.cobblemon.battles

import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.ShowdownActionResponse
import com.cobblemon.mod.common.battles.ShowdownActionResponseType
import com.cobblemon.mod.common.battles.ShowdownMoveset
import com.cobblemon.mod.common.net.IntSize
import com.cobblemon.mod.common.util.writeSizedInt
import net.minecraft.network.RegistryFriendlyByteBuf

class CallActionResponse : ShowdownActionResponse(ShowdownActionResponseType.PASS) {
    override fun saveToBuffer(buffer: RegistryFriendlyByteBuf) {
        buffer.writeSizedInt(IntSize.U_BYTE, 99) // Custom ordinal
    }

    override fun isValid(activeBattlePokemon: ActiveBattlePokemon, showdownMoveSet: ShowdownMoveset?, forceSwitch: Boolean): Boolean {
        return !forceSwitch
    }

    override fun toShowdownString(activeBattlePokemon: ActiveBattlePokemon, showdownMoveSet: ShowdownMoveset?): String {
        return "call"
    }
}