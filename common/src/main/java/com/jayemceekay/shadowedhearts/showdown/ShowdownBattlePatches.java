package com.jayemceekay.shadowedhearts.showdown;

import java.util.List;

/** Small, exact edits shared by stock Cobblemon and the configured Mega Showdown runtimes. */
final class ShowdownBattlePatches {
    private ShowdownBattlePatches() {}

    /** Resolve disobedience inside one native action, without replaying BeforeMove checks. */
    static final LiteralShowdownPatch HYPER_ACTION = new LiteralShowdownPatch(
            "battleActions.hyperAction", "    if (!willTryMove) {", """
                const willTryMove = this.battle.runEvent("BeforeMove", pokemon, target, move);
                if (!willTryMove) {
                  this.battle.runEvent("MoveAborted", pokemon, target, move);
                  this.battle.clearActiveMove(true);
                  pokemon.moveThisTurnResult = willTryMove;
                  return;
                }
                if (move.flags["cantusetwice"] && pokemon.lastMove?.id === move.id) {
            """.stripTrailing(), """
                move.shadowedheartsBaseMove = baseMove.id;
                const willTryMove = this.battle.runEvent("BeforeMove", pokemon, target, move);
                if (!willTryMove) {
                  this.battle.runEvent("MoveAborted", pokemon, target, move);
                  this.battle.clearActiveMove(true);
                  pokemon.moveThisTurnResult = willTryMove;
                  return;
                }
                const shadowAction = move.shadowedheartsAction;
                if (shadowAction) {
                  delete move.shadowedheartsAction;
                  if (shadowAction.move) {
                    baseMove = this.dex.getActiveMove(shadowAction.move);
                    move = zMove ? this.getActiveZMove(baseMove, pokemon)
                      : maxMove ? this.getActiveMaxMove(baseMove, pokemon) : baseMove;
                    move.isExternal = externalMove;
                  }
                  target = shadowAction.target || this.battle.getRandomTarget(pokemon, move);
                  targetLoc = target ? pokemon.getLocOf(target) : 0;
                  this.battle.setActiveMove(move, pokemon, target);
                }
                if (move.flags["cantusetwice"] && pokemon.lastMove?.id === move.id) {
            """.stripTrailing(), List.of("""
                const willTryMove = this.battle.runEvent(
                  "BeforeMove",
                  pokemon,
                  target,
                  move
                );
                if (!willTryMove) {
                  this.battle.runEvent("MoveAborted", pokemon, target, move);
                  this.battle.clearActiveMove(true);
                  pokemon.moveThisTurnResult = willTryMove;
                  return;
                }
                if (move.flags["cantusetwice"] && pokemon.lastMove?.id === move.id) {
            """.stripTrailing()));

    static final LiteralShowdownPatch HEART_GAUGE_UPDATE = new LiteralShowdownPatch(
            "battleStream.heartGauge", "      case \"player\":", """
                  case "player":
                    const [slot, optionsText] = splitFirst(message, " ");
                    this.battle.setPlayer(slot, JSON.parse(optionsText));
                    break;
                  case "capture":
            """.stripTrailing(), """
                  case "player":
                    const [slot, optionsText] = splitFirst(message, " ");
                    this.battle.setPlayer(slot, JSON.parse(optionsText));
                    break;
                  case "shheartgauge": {
                    const tokens = message.trim().split(/\\s+/);
                    if (!this.battle || tokens.length !== 2 || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(tokens[0])) break;
                    const fraction = Number(tokens[1]);
                    if (!Number.isFinite(fraction)) break;
                    const clamped = Math.max(0, Math.min(1, fraction));
                    let changed = false;
                    for (const side of this.battle.sides) {
                      const target = side?.pokemon.find(pokemon => pokemon.uuid === tokens[0]);
                      if (target && target.set.heartGaugeFraction !== clamped) {
                        target.set.heartGaugeFraction = clamped;
                        changed = true;
                      }
                    }
                    if (changed) this.battle.inputLog.push(`>shheartgauge ${tokens[0]} ${clamped}`);
                    break;
                  }
                  case "capture":
            """.stripTrailing());

    static final LiteralShowdownPatch SCENT_CALM = new LiteralShowdownPatch(
            "battleStream.scentCalm", "      case \"forcewin\":", """
                  case "forcewin":
                  case "forcetie":
                    this.battle.win(type === "forcewin" ? message : null);
                    if (message) {
                      this.battle.inputLog.push(`>forcewin ${message}`);
                    } else {
                      this.battle.inputLog.push(`>forcetie`);
                    }
                    break;
            """.stripTrailing(), """
                  case "shcalm": {
                    const uuid = message.trim();
                    if (!this.battle || this.battle.ended || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(uuid)) break;
                    let changed = false;
                    for (const side of this.battle.sides) {
                      const target = side?.pokemon.find(pokemon => pokemon.uuid === uuid);
                      if (!target || !target.set?.isShadow) continue;
                      for (const [mode, flag] of [['hypermode', 'isHyper'], ['reversemode', 'isReverse']]) {
                        if (target.set[flag] || target.volatiles[mode]) changed = true;
                        if (target.volatiles[mode]) {
                          if (!target.removeVolatile(mode)) delete target.volatiles[mode];
                        }
                        target.set[flag] = false;
                      }
                    }
                    if (changed) this.battle.inputLog.push(`>shcalm ${uuid}`);
                    break;
                  }
                  case "forcewin":
                  case "forcetie": {
                    this.battle.win(type === "forcewin" ? message : null);
                    if (message) {
                      this.battle.inputLog.push(`>forcewin ${message}`);
                    } else {
                      this.battle.inputLog.push(`>forcetie`);
                    }
                    break;
                  }
            """.stripTrailing());

    static final LiteralShowdownPatch DEX_FIELD = new LiteralShowdownPatch(
            "dexConditions.field", "class Condition extends import_dex_data.BasicEffect {", """
            class Condition extends import_dex_data.BasicEffect {
              constructor(data) {
                super(data);
                data = this;
                this.effectType = ["Weather", "Status"].includes(data.effectType) ? data.effectType : "Condition";
              }
            }
            """.stripTrailing(), """
            class Condition extends import_dex_data.BasicEffect {
              constructor(data) {
                super(data);
                data = this;
                this.effectType = ["Weather", "Status", "Field"].includes(data.effectType) ? data.effectType : "Condition";
              }
            }
            """.stripTrailing());

    static final LiteralShowdownPatch SHADOW_ENGINE = new LiteralShowdownPatch(
            "battle.shadowEngine", "    this.add(\"gametype\", this.gameType);", """
                this.inputLog.push(`>start ` + JSON.stringify(inputOptions));
                this.add("gametype", this.gameType);
                for (const rule of this.ruleTable.keys()) {
            """.stripTrailing(), """
                this.inputLog.push(`>start ` + JSON.stringify(inputOptions));
                this.add("gametype", this.gameType);
                this.field.addPseudoWeather('shadowengine');
                for (const rule of this.ruleTable.keys()) {
            """.stripTrailing());

    static final LiteralShowdownPatch CALL_CHOICE = new LiteralShowdownPatch(
            "side.callChoice", "      switch (choiceType) {", """
                  data = data.trim();
                  switch (choiceType) {
                    case "move":
                      const original = data;
            """.stripTrailing(), """
                  data = data.trim();
                  switch (choiceType) {
                    case "call":
                      const index = this.getChoiceIndex();
                      if (index >= this.active.length) return this.emitChoiceError("Can't call: All Pokemon have already acted");
                      this.choice.actions.push({choice: "call", pokemon: this.active[index]});
                      break;
                    case "move":
                      const original = data;
            """.stripTrailing());

    static final LiteralShowdownPatch CALL_ORDER = new LiteralShowdownPatch(
            "battleQueue.callOrder", "        // default is 200 (for moves)", """
                    // default is 200 (for moves)
                    residual: 300
                  };
                  if (action.choice in orders) {
            """.stripTrailing(), """
                    // default is 200 (for moves)
                    residual: 300,
                    call: 200
                  };
                  if (action.choice in orders) {
            """.stripTrailing());

    static final LiteralShowdownPatch CALL_ACTION = new LiteralShowdownPatch(
            "battle.callAction", "        this.midTurn = true;", """
                    this.midTurn = true;
                    break;
                  }
                  case "move":
                    if (!action.pokemon.isActive)
                      return false;
            """.stripTrailing(), """
                    this.midTurn = true;
                    break;
                  }
                  case "call":
                    (this.dex.data.Scripts.shadowedhearts?.call || this.scripts.call).call(this, action.pokemon);
                    break;
                  case "move":
                    if (!action.pokemon.isActive)
                      return false;
            """.stripTrailing());
}
