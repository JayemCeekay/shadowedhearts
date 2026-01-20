({
  name: "Reverse Mode",
  effectType: "VolatileStatus",
  onStart(pokemon) {
    const config = this.dex.data.Scripts.shadowedhearts?.Config?.reverseMode || { enabled: false };
    if (!config.enabled) {
      delete pokemon.volatiles['reversemode'];
      return;
    }
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
    const config = this.dex.data.Scripts.shadowedhearts?.Config?.reverseMode || { enabled: false };
    if (!config.enabled) return;
    this.damage(pokemon.baseMaxhp / 16, pokemon, pokemon);
    this.add('sh_message', 'cobblemon.battle.reversemode.hurt', pokemon.getSlot());
  },
  onBeforeMove(source, target, move) {
    if (!move || move.type === 'Shadow') return;
    const config = this.dex.data.Scripts.shadowedhearts?.Config?.reverseMode || { enabled: false };
    if (!config.enabled) return;
    if (this.randomChance(1, 5)) {
      this.add('sh_message', 'cobblemon.battle.reversemode.disobey', source.getSlot());
      this.damage(source.baseMaxhp / 8, source, source);
      return false;
    }
  }
})
