package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import com.cobblemon.mod.common.battles.dispatch.InstructionSet;
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction;
import com.jayemceekay.shadowedhearts.cobblemon.instructions.CaptureInstruction;
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

/**
 * Register handlers for custom PS tokens we emit:
 * - "capture" -> com.jayemceekay.shadowedhearts.cobblemon.instructions.CaptureInstruction
 * - "sending request" -> com.jayemceekay.shadowedhearts.cobblemon.instructions.NoOpInstruction (debug-only line)
 * - fallback "error" under update channel -> com.jayemceekay.shadowedhearts.cobblemon.instructions.NoOpInstruction
 */
@Mixin(value = ShowdownInterpreter.class)
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
    }
}