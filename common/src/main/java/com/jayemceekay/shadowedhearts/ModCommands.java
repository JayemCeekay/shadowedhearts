package com.jayemceekay.shadowedhearts;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Registers the mod's commands on both platforms via Architectury's command event.
 */
public final class ModCommands {
    private ModCommands() {}

    public static void init() {
        // Subscribe once; Architectury relays to the correct platform callbacks.
        CommandRegistrationEvent.EVENT.register(ModCommands::register);
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registry, Commands.CommandSelection selection) {
        ShadowedHeartsCommands.register(dispatcher);
    }
}
