package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import com.cobblemon.mod.common.battles.dispatch.InstructionSet;
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.cobblemon.instructions.*;
import kotlin.jvm.functions.Function4;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Register handlers for custom PS tokens we emit and handle micro one-turn auto-close.
 * - "capture" -> com.jayemceekay.shadowedhearts.cobblemon.instructions.CaptureInstruction
 * - one-turn battles: when we see "|turn|2" for a marked battle, send >forcetie and clear on natural end
 */
@Mixin(value = ShowdownInterpreter.class, remap = false)
public abstract class MixinShowdownInterpreter {
    // Kotlin: private val updateInstructionParser = mutableMapOf<String, (PokemonBattle, InstructionSet, BattleMessage, Iterator<BattleMessage>) -> InterpreterInstruction>()
    @Shadow @Final @Mutable
    private static Map<String, Function4<PokemonBattle, InstructionSet, BattleMessage, Iterator<BattleMessage>, InterpreterInstruction>> updateInstructionParser;

    /**
     * Runs after Kotlin’s static init finishes building the parser tables.
     */
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void cobblemon$registerExtraHandlers(CallbackInfo ci) {
        updateInstructionParser.put("capture",
                (battle, instructionSet, message, ignored) -> new CaptureInstruction(message)
        );
        updateInstructionParser.put("hyper",
                (battle, instructionSet, message, ignored) -> new HyperInstruction(message)
        );
        updateInstructionParser.put("reverse",
                (battle, instructionSet, message, ignored) -> new ReverseInstruction(message)
        );
        updateInstructionParser.put("call",
                (battle, instructionSet, message, ignored) -> new CallInstruction(message)
        );
        updateInstructionParser.put("sh_message",
                (battle, instructionSet, message, ignored) -> new ShMessageInstruction(message)
        );
        // Consume custom debug token emitted by our Showdown patches (conditions.js)
        // This prevents it from being treated as an unknown token and keeps logs clean.
        updateInstructionParser.put("shdebug",
                (battle, instructionSet, message, ignored) -> new NoOpInstruction(message)
        );
    }

    @Inject(method = "interpret", at = @At("HEAD"), remap = false)
    private void shadowedhearts$oneTurnAutoClose(com.cobblemon.mod.common.api.battles.model.PokemonBattle battle, String rawMessage, CallbackInfo ci) {
        if (battle == null) return;
        UUID battleId = battle.getBattleId();
        if (rawMessage == null) return;
        // Surface Showdown-side debug messages to the server console so they are visible during testing.
        // This helps when '-message' lines are not rendered in the current UI/log sink.
        if (rawMessage.contains("|shdebug|")) {
          Shadowedhearts.LOGGER.debug("[ShadowedHearts Showdown DEBUG] " + rawMessage);
        }
    }
}