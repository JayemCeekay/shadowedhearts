package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;
import com.cobblemon.mod.relocations.graalvm.polyglot.Context;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.showdown.ShowdownRuntimePatcher;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import kotlin.Unit;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ShadowedHeartsCommands {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("shadowedhearts")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("reloadConfigs")
                        .executes(ctx -> {
                            try {
                                ShadowedHeartsConfigs.getInstance().getShadowConfig().load();
                                ShadowedHeartsConfigs.getInstance().getSnagConfig().load();
                                Cobblemon.INSTANCE.getShowdownThread().queue(showdownService -> {
                                    if (showdownService instanceof GraalShowdownService service) {
                                        Context context = null;
                                        try {
                                            context = (Context) service.getClass().getDeclaredField("context").get(service);
                                        } catch (IllegalAccessException | NoSuchFieldException e) {
                                            throw new RuntimeException(e);
                                        }
                                        ShowdownRuntimePatcher.DynamicInjector.inject(context);
                                    }
                                    return Unit.INSTANCE;
                                });
                                ctx.getSource().sendSuccess(() -> Component.literal("Shadowed Hearts configurations reloaded successfully."), true);
                                return 1;
                            } catch (Exception e) {
                                ctx.getSource().sendFailure(Component.literal("Failed to reload configurations: " + e.getMessage()));
                                e.printStackTrace();
                                return 0;
                            }
                        })
                );

        ShadowCommands.registerSubcommands(root);

        d.register(root);
        d.register(Commands.literal("sh").requires(src -> src.hasPermission(2)).redirect(d.register(root)));
    }
}
