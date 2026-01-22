package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.summary.Summary;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Summary.class)
public interface SummaryAccessor {
    @Accessor("mainScreen")
    AbstractWidget getMainScreen();

    @Accessor("sideScreen")
    GuiEventListener getSideScreen();
}
