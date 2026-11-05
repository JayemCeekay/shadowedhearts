package com.jayemceekay.shadowedhearts.content.infusion

/** Native texture coordinates shared by the menu slots and the client screen. */
object UmbraInfuserLayout {
    const val WIDTH = 349
    const val HEIGHT = 205
    const val CENTER_X = 172
    const val CHAMBER_WIDTH = 98
    const val CHAMBER_HEIGHT = 101
    const val CHAMBER_X = CENTER_X - CHAMBER_WIDTH / 2
    const val CHAMBER_Y = 43

    const val INPUT_SLOT = 0
    const val FUEL_SLOT = 1
    const val OUTPUT_SLOT = 2
    const val PLAYER_SLOT_START = 3
    const val HOTBAR_SLOT_START = 30
    const val SLOT_COUNT = 39
    const val SLOT_SIZE = 16
    const val SLOT_PITCH = 18

    const val INPUT_X = 279
    const val INPUT_Y = 142
    const val FUEL_X = 279
    const val FUEL_Y = 107
    const val OUTPUT_X = 320
    const val OUTPUT_Y = 142

    const val DRAWER_X = 85
    const val DRAWER_Y = 86
    const val DRAWER_WIDTH = 174
    const val DRAWER_HEIGHT = 96
    const val INVENTORY_X = 92
    const val INVENTORY_Y = 103
    const val HOTBAR_Y = 160
    const val PARTY_SLOT_SIZE = 25
    // The authored 25px flat fin edges exactly match the party columns below them.
    // Their centers are x97.5 and x246.5, mirrored around the chamber center.
    const val PARTY_LEFT_X = 85
    const val PARTY_RIGHT_X = 234

    data class SlotPosition(val x: Int, val y: Int)

    /** Reflow above the drawer so every party member remains visible and clickable. */
    @JvmStatic
    fun partyPosition(partyIndex: Int, drawerVisible: Boolean): SlotPosition {
        require(partyIndex in 0..5) { "Unknown party slot: $partyIndex" }
        return if (drawerVisible) SlotPosition(87 + 29 * partyIndex, 49)
        else SlotPosition(if (partyIndex < 3) PARTY_LEFT_X else PARTY_RIGHT_X, 48 + partyIndex % 3 * 42)
    }

    /** Positions describe the 16px item area; slot frames begin one pixel before it. */
    @JvmStatic
    fun slotPosition(menuSlot: Int): SlotPosition {
        require(menuSlot in 0 until SLOT_COUNT) { "Unknown infuser menu slot: $menuSlot" }
        return when (menuSlot) {
            INPUT_SLOT -> SlotPosition(INPUT_X, INPUT_Y)
            FUEL_SLOT -> SlotPosition(FUEL_X, FUEL_Y)
            OUTPUT_SLOT -> SlotPosition(OUTPUT_X, OUTPUT_Y)
            in PLAYER_SLOT_START until HOTBAR_SLOT_START -> {
                val index = menuSlot - PLAYER_SLOT_START
                SlotPosition(INVENTORY_X + index % 9 * SLOT_PITCH, INVENTORY_Y + index / 9 * SLOT_PITCH)
            }
            else -> SlotPosition(INVENTORY_X + (menuSlot - HOTBAR_SLOT_START) * SLOT_PITCH, HOTBAR_Y)
        }
    }

    /** Presentation flags never constrain a server menu or its item transfer destinations. */
    @JvmStatic
    fun isSlotActive(menuSlot: Int, serverMenu: Boolean, supplyVisible: Boolean, inventoryVisible: Boolean): Boolean {
        if (menuSlot !in 0 until SLOT_COUNT) return false
        return serverMenu || if (menuSlot < PLAYER_SLOT_START) supplyVisible else inventoryVisible
    }
}
