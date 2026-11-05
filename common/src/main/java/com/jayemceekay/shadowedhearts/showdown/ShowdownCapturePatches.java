package com.jayemceekay.shadowedhearts.showdown;

/** Supported capture source shapes, shared by runtime patching and packaged-runtime tests. */
final class ShowdownCapturePatches {
    private ShowdownCapturePatches() {}

    static final LiteralShowdownPatch CAPTURE = new LiteralShowdownPatch("battle.capture",
            "  capture(pokemon) {", """
              capture(pokemon) {
                if (this.ended)
                  return;
                if (!pokemon.fainted) {
                  this.add("capture", pokemon);
                  if (pokemon.side.pokemonLeft)
                    pokemon.side.pokemonLeft--;
                  this.singleEvent("End", pokemon.getAbility(), pokemon.abilityState, pokemon);
                  pokemon.clearVolatile(false);
                  pokemon.fainted = true;
                  pokemon.illusion = null;
                  pokemon.isActive = false;
                  pokemon.isStarted = false;
                  pokemon.side.faintedThisTurn = pokemon;
                  if (this.checkWin())
                    return true;
                }
                return false;
              }
            """.stripTrailing(), """
              capture(pokemon) {
                if (this.ended)
                  return;
                if (!pokemon.fainted) {
                  this.add("capture", pokemon);
                  if (pokemon.side.pokemonLeft) pokemon.side.pokemonLeft--;
                  this.singleEvent("End", pokemon.getAbility(), pokemon.abilityState, pokemon);
                  pokemon.clearVolatile(false);
                  pokemon.fainted = true;
                  pokemon.captured = true;
                  pokemon.status = "fnt";
                  pokemon.illusion = null;
                  pokemon.isActive = false;
                  pokemon.isStarted = false;
                  pokemon.side.faintedThisTurn = pokemon;
                  if (this.checkWin()) return true;
                  this.checkFainted();
                }
                return false;
              }
            """.stripTrailing());

    static final LiteralShowdownPatch POSSIBLE_SWITCHES = new LiteralShowdownPatch("battle.possibleSwitches",
            "  possibleSwitches(side) {", """
              possibleSwitches(side) {
                if (!side.pokemonLeft)
                  return [];
                const canSwitchIn = [];
                for (let i = side.active.length; i < side.pokemon.length; i++) {
                  const pokemon = side.pokemon[i];
                  if (!pokemon.fainted) {
                    canSwitchIn.push(pokemon);
                  }
                }
                return canSwitchIn;
              }
            """.stripTrailing(), """
              possibleSwitches(side) {
                if (!side.pokemonLeft)
                  return [];
                const canSwitchIn = [];
                for (let i = side.active.length; i < side.pokemon.length; i++) {
                  const pokemon = side.pokemon[i];
                  if (!pokemon.fainted && !pokemon.captured) {
                    canSwitchIn.push(pokemon);
                  }
                }
                return canSwitchIn;
              }
            """.stripTrailing());

    static final LiteralShowdownPatch CAPTURED_FLAG = new LiteralShowdownPatch("pokemon.capturedFlag",
            "    this.m = {};", """
                this.m = {};
                const pokemonScripts = this.battle.format.pokemon || this.battle.dex.data.Scripts.pokemon;
            """.stripTrailing(), """
                this.m = {};
                this.captured = false;
                const pokemonScripts = this.battle.format.pokemon || this.battle.dex.data.Scripts.pokemon;
            """.stripTrailing());
}
