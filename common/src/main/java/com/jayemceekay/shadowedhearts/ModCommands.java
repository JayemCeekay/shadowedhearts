package com.jayemceekay.shadowedhearts;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;

/**
 * Registers the mod's commands on both platforms via Architectury's command event.
 */
public final class ModCommands {
    private ModCommands() {}

    public static void init() {
        // Subscribe once; Architectury relays to the correct platform callbacks.
        CommandRegistrationEvent.EVENT.register(ModCommands::register);
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, net.minecraft.commands.CommandBuildContext registry, net.minecraft.commands.Commands.CommandSelection selection) {
        ShadowCommands.register(dispatcher);
    }
}
