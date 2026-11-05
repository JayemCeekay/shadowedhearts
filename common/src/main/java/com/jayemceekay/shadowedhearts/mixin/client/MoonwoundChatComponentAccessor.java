package com.jayemceekay.shadowedhearts.mixin.client;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ChatComponent.class)
public interface MoonwoundChatComponentAccessor {
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> shadowedhearts$getTrimmedMessages();

    @Accessor("chatScrollbarPos")
    int shadowedhearts$getChatScrollbarPos();
}
