package com.jayemceekay.shadowedhearts.client.purification

import com.cobblemon.mod.common.client.gui.pc.PCGUI
import com.jayemceekay.shadowedhearts.client.gui.PurificationChamberGUI
import com.jayemceekay.shadowedhearts.network.purification.UnlinkPlayerFromPurificationChamberPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/** Owns PC selection and recipe-viewer detours without reopening server authorization. */
object PurificationChamberClientSession {
    private val session = PurificationScreenSession()

    fun open(client: Minecraft, chamber: PurificationChamberGUI) {
        session.open(client.connection, chamber)
        client.setScreen(chamber)
    }

    fun select(chamber: PurificationChamberGUI, pc: PCGUI) {
        session.select(chamber, pc)
        Minecraft.getInstance().setScreen(pc)
    }

    fun returnTo(chamber: PurificationChamberGUI) {
        session.returnTo(chamber)
        Minecraft.getInstance().setScreen(chamber)
    }

    fun ownsScreen(screen: Screen?): Boolean = session.ownsScreen(Minecraft.getInstance().connection, screen)

    fun onClientTick(client: Minecraft) {
        if (session.tick(client.connection, client.screen, ChamberRecipeViewerSupport::returnsTo)) {
            UnlinkPlayerFromPurificationChamberPacket().sendToServer()
        }
    }
}
