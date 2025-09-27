package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import com.cobblemon.mod.common.battles.dispatch.InstructionSet;
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction;
import com.jayemceekay.shadowedhearts.cobblemon.instructions.CaptureInstruction;
import com.jayemceekay.shadowedhearts.showdown.AutoBattleController;
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
    }

    @Inject(method = "interpret", at = @At("HEAD"), remap = false)
    private void shadowedhearts$oneTurnAutoClose(com.cobblemon.mod.common.api.battles.model.PokemonBattle battle, String rawMessage, CallbackInfo ci) {
        if (battle == null) return;
        java.util.UUID battleId = battle.getBattleId();
        if (rawMessage == null) return;
        boolean __sh_isOneTurn = AutoBattleController.isOneTurn(battleId);
        // First sign that turn 1 has fully resolved is the start of turn 2 (only for one-turn battles)
        /*if (__sh_isOneTurn && rawMessage.contains("|turn|2") && OneTurnMicroController.tryMarkClosed(battleId)) {
            try {
                battle.doWhenClear(() -> {
                    battle.stop();
                    OneTurnMicroController.clear(battleId);
                    return Unit.INSTANCE;
                });
            } catch (Throwable ignored) {
            }
        }*/
    }
}