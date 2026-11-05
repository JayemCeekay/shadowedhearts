package com.jayemceekay.shadowedhearts.client.gui

import com.cobblemon.mod.common.client.gui.summary.SummaryButton
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Cobblemon's icon-only SummaryButton suppresses its otherwise valid vanilla narration. */
internal fun narrateSummaryButton(screen: Screen, output: NarrationElementOutput): Boolean {
    val button = (screen.focused as? SummaryButton) ?: if (screen.focused == null)
        screen.children().filterIsInstance<SummaryButton>().firstOrNull { it.isHovered }
    else null
    if (button == null || !button.visible || !button.active || button.message.string.isEmpty()) return false
    output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.button", button.message))
    output.add(NarratedElementType.USAGE, Component.translatable(
        if (button.isFocused) "narration.button.usage.focused" else "narration.button.usage.hovered"))
    return true
}
