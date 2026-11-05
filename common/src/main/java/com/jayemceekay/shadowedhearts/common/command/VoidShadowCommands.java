package com.jayemceekay.shadowedhearts.common.command;

import com.jayemceekay.shadowedhearts.common.voidshadow.VoidShadowService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Operator encounter fixture; natural distribution is authored separately. */
public final class VoidShadowCommands {
    private VoidShadowCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("voidshadow")
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawn(ctx.getSource(), 30))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level")))));
    }

    private static int spawn(CommandSourceStack source, int level) {
        try {
            VoidShadowService.spawn(source.getLevel(), source.getPosition(), level);
        } catch (IllegalStateException failure) {
            source.sendFailure(Component.translatable("message.shadowedhearts.command.void_shadow.failed", com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException.component(failure)));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.void_shadow.spawned", level), true);
        return 1;
    }
}
