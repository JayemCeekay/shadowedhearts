({
  name: "Reverse Mode",
  effectType: "VolatileStatus",
  onStart(pokemon) {
    this.add('-start', pokemon, 'Reverse Mode');
    if (pokemon.volatiles['hypermode']) {
      pokemon.removeVolatile('hypermode');
    }
    if (this.turn && this.turn >= 1) {
      this.add('reverse', 'start', pokemon.getSlot());
    }
  },
  onEnd(pokemon) {
    this.add('-end', pokemon, 'Reverse Mode');
    this.add('reverse', 'end', pokemon.getSlot());
  },
  onResidualOrder: 5,
  onResidual(pokemon) {
    this.damage(pokemon.baseMaxhp / 16, pokemon, pokemon);
    this.add('sh_message', 'cobblemon.battle.reversemode.hurt', pokemon.getSlot());
  },
  onTryMove(source, target, move) {
    if (!move || move.type === 'Shadow') return;
    const config = this.dex.data.Scripts.shadowedhearts_config?.Config?.reverseMode || {};
    if (config.debugReverseModeFailure || source.set?.debugReverseModeFailure || this.randomChance(1, 5)) {
      this.add('sh_message', 'cobblemon.battle.reversemode.disobey', source.getSlot());
      return false;
    }
  }
})
