package com.jayemceekay.shadowedhearts.showdown;

import java.util.List;

/** Narrow native simulator seams for optional Shadow PP and Colosseum battle rules. */
final class ShowdownShadowBattlePatches {
    private ShowdownShadowBattlePatches() {}

    static final LiteralShowdownPatch COLOSSEUM_WONDER_GUARD = new LiteralShowdownPatch(
            "battle.colosseumWonderGuard", "      const effectHolder = handler.effectHolder;", """
                  const effect = handler.effect;
                  const effectHolder = handler.effectHolder;
                  if (effect.effectType === "Status" && effectHolder.status !== effect.id) {
                    continue;
                  }
            """.stripTrailing(), """
                  const effect = handler.effect;
                  const effectHolder = handler.effectHolder;
                  // Colosseum Shadow Rush bypasses only Wonder Guard's hit immunity.
                  // Do not use ignoreAbility: other abilities and protection still apply.
                  if (eventid === "TryHit" && effect.effectType === "Ability" && effect.id === "wonderguard"
                      && sourceEffect?.id === "shadowrush" && sourceEffect.shadowedheartsColosseumRush) {
                    continue;
                  }
                  if (effect.effectType === "Status" && effectHolder.status !== effect.id) {
                    continue;
                  }
            """.stripTrailing());

    static final LiteralShowdownPatch DEDUCT_PP = new LiteralShowdownPatch(
            "pokemon.shadowDeductPP", "  deductPP(move, amount, target) {", """
              deductPP(move, amount, target) {
                const gen = this.battle.gen;
                move = this.battle.dex.moves.get(move);
                const ppData = this.getMoveData(move);
                if (!ppData)
                  return 0;
                ppData.used = true;
                if (!ppData.pp && gen > 1)
            """.stripTrailing(), """
              deductPP(move, amount, target) {
                const gen = this.battle.gen;
                move = this.battle.dex.moves.get(move);
                const ppData = this.getMoveData(move);
                if (!ppData)
                  return 0;
                ppData.used = true;
                if (this.battle.dex.data.Scripts.shadowedhearts?.usesUnlimitedShadowPP?.(this, move)) {
                  // Native move execution passes no amount; explicit drains report no PP lost.
                  return amount ? 0 : 1;
                }
                if (!ppData.pp && gen > 1)
            """.stripTrailing());

    static final LiteralShowdownPatch MOVE_REQUEST = new LiteralShowdownPatch(
            "pokemon.shadowMoveRequest", "      let disabled = moveSlot.disabled;", """
                  let disabled = moveSlot.disabled;
                  if (this.volatiles["dynamax"]) {
                    const canCauseStruggle = ["Encore", "Disable", "Taunt", "Assault Vest", "Belch", "Stuff Cheeks"];
                    disabled = this.maxMoveDisabled(moveSlot.id) || disabled && canCauseStruggle.includes(moveSlot.disabledSource);
                  } else if (moveSlot.pp <= 0 && !this.volatiles["partialtrappinglock"] || disabled && this.side.active.length >= 2 && this.battle.actions.targetTypeChoices(target)) {
            """.stripTrailing(), """
                  let disabled = moveSlot.disabled;
                  const shadowUnlimitedPP = this.battle.dex.data.Scripts.shadowedhearts?.usesUnlimitedShadowPP?.(this, moveSlot.id);
                  if (this.volatiles["dynamax"]) {
                    const canCauseStruggle = ["Encore", "Disable", "Taunt", "Assault Vest", "Belch", "Stuff Cheeks"];
                    disabled = this.maxMoveDisabled(moveSlot.id) || disabled && canCauseStruggle.includes(moveSlot.disabledSource);
                  } else if (!shadowUnlimitedPP && moveSlot.pp <= 0 && !this.volatiles["partialtrappinglock"] || disabled && this.side.active.length >= 2 && this.battle.actions.targetTypeChoices(target)) {
            """.stripTrailing());

    static final LiteralShowdownPatch REQUEST_PP = new LiteralShowdownPatch(
            "pokemon.shadowRequestPP", "        maxpp: moveSlot.maxpp,", """
                    pp: moveSlot.pp,
                    maxpp: moveSlot.maxpp,
            """.stripTrailing(), """
                    // Positive request PP keeps native clients and AI from treating this as Struggle.
                    pp: shadowUnlimitedPP ? Math.max(1, moveSlot.maxpp) : moveSlot.pp,
                    maxpp: moveSlot.maxpp,
            """.stripTrailing());

    static final LiteralShowdownPatch UPDATE_PP = new LiteralShowdownPatch(
            "pokemon.shadowUpdatePP", "  updatePP() {", """
              updatePP() {
                this.battle.add("pp_update", `${this.side.id}: ${this.uuid}`, this.moveSlots.map((move) => `${move.id}: ${move.pp}`).join(", "));
              }
            """.stripTrailing(), """
              updatePP() {
                // Do not overwrite native saved PP with an unlimited move's virtual battle state.
                const moves = this.moveSlots.filter(move => !this.battle.dex.data.Scripts.shadowedhearts?.usesUnlimitedShadowPP?.(this, move.id));
                if (moves.length) this.battle.add("pp_update", `${this.side.id}: ${this.uuid}`, moves.map((move) => `${move.id}: ${move.pp}`).join(", "));
              }
            """.stripTrailing());

    static final LiteralShowdownPatch MAX_PP = new LiteralShowdownPatch(
            "pokemon.shadowMaxPP", "  maxMoveDisabled(baseMove) {", """
              maxMoveDisabled(baseMove) {
                baseMove = this.battle.dex.moves.get(baseMove);
                if (!this.getMoveData(baseMove.id)?.pp)
            """.stripTrailing(), """
              maxMoveDisabled(baseMove) {
                baseMove = this.battle.dex.moves.get(baseMove);
                if (!this.getMoveData(baseMove.id)?.pp && !this.battle.dex.data.Scripts.shadowedhearts?.usesUnlimitedShadowPP?.(this, baseMove))
            """.stripTrailing());

    static final LiteralShowdownPatch Z_PP = new LiteralShowdownPatch(
            "battleActions.shadowZPP", "      const moveData = pokemon.getMoveData(move);", """
                  const moveData = pokemon.getMoveData(move);
                  if (!moveData?.pp)
                    return;
            """.stripTrailing(), """
                  const moveData = pokemon.getMoveData(move);
                  if (!moveData?.pp && !this.dex.data.Scripts.shadowedhearts?.usesUnlimitedShadowPP?.(pokemon, move))
                    return;
            """.stripTrailing(), List.of("""
                  const moveData = pokemon.getMoveData(move);
                  if (!moveData?.pp) return;
            """.stripTrailing()));

    static final LiteralShowdownPatch Z_REQUEST_PP = new LiteralShowdownPatch(
            "battleActions.shadowZRequestPP", "    const zMoves = [];", """
                const zMoves = [];
                for (const moveSlot of pokemon.moveSlots) {
                  if (moveSlot.pp <= 0) {
                    zMoves.push(null);
                    continue;
                  }
            """.stripTrailing(), """
                const zMoves = [];
                for (const moveSlot of pokemon.moveSlots) {
                  if (moveSlot.pp <= 0 && !this.dex.data.Scripts.shadowedhearts?.usesUnlimitedShadowPP?.(pokemon, moveSlot.id)) {
                    zMoves.push(null);
                    continue;
                  }
            """.stripTrailing());

    static final LiteralShowdownPatch CRIT_TABLE = new LiteralShowdownPatch(
            "battleActions.shadowCritTable", "      critMult = [0, 16, 8, 4, 3, 2];", """
                if (this.battle.gen <= 5) {
                  critRatio = this.battle.clampIntRange(critRatio, 0, 5);
                  critMult = [0, 16, 8, 4, 3, 2];
            """.stripTrailing(), """
                const shadowHyperCrit = this.dex.data.Scripts.shadowedhearts?.usesColosseumCritical?.(source, move);
                if (this.battle.gen <= 5 || shadowHyperCrit) {
                  critRatio = this.battle.clampIntRange(critRatio, 0, 5);
                  critMult = [0, 16, 8, 4, 3, 2];
            """.stripTrailing());

    static final LiteralShowdownPatch CRIT_ROLL = new LiteralShowdownPatch(
            "battleActions.shadowCritRoll", "    if (move.willCrit === void 0) {", """
                if (move.willCrit === void 0) {
                  if (critRatio) {
                    moveHit.crit = this.battle.randomChance(1, critMult[critRatio]);
                  }
                }
            """.stripTrailing(), """
                if (move.willCrit === void 0) {
                  if (critRatio) {
                    moveHit.crit = (shadowHyperCrit && this.battle.randomChance(9, 10)) || this.battle.randomChance(1, critMult[critRatio]);
                  }
                }
            """.stripTrailing());
}
