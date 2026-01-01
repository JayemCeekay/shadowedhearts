({
  name: 'Shadow Engine',
  effectType: 'Field',
  onFieldRestart(field, source, effect) {
    return true;
  },
  onFieldStart(field, source, effect) {
  },
  onSwitchIn(pokemon) {
    if (pokemon?.set?.isHyper && !pokemon.volatiles['hypermode']) {
      pokemon.addVolatile('hypermode');
    }
    if (pokemon?.set?.isReverse && !pokemon.volatiles['reversemode']) {
      pokemon.addVolatile('reversemode');
    }
  },
  natureTableHyperMode: {
    calm: [30, 25, 20, 15, 10, 5], lonely: [30, 25, 20, 15, 10, 5], modest: [30, 25, 20, 15, 10, 5], timid: [30, 25, 20, 15, 10, 5],
    bold: [50, 40, 30, 20, 10, 5], brave: [50, 40, 30, 20, 10, 5], lax: [50, 40, 30, 20, 10, 5], quirky: [50, 40, 30, 20, 10, 5], sassy: [50, 40, 30, 20, 10, 5],
    hasty: [50, 40, 40, 30, 25, 12], impish: [50, 40, 40, 30, 25, 12], naughty: [50, 40, 40, 30, 25, 12], rash: [50, 40, 40, 30, 25, 12],
    careful: [20, 20, 15, 15, 10, 5], docile: [20, 20, 15, 15, 10, 5], quiet: [20, 20, 15, 15, 10, 5], serious: [20, 20, 15, 15, 10, 5],
    gentle: [50, 50, 50, 50, 50, 50], jolly: [50, 50, 50, 50, 50, 50], mild: [50, 50, 50, 50, 50, 50], naive: [50, 50, 50, 50, 50, 50],
    adamant: [30, 70, 70, 70, 50, 25], bashful: [30, 70, 70, 70, 50, 25], hardy: [30, 70, 70, 70, 50, 25], relaxed: [30, 70, 70, 70, 50, 25]
  },
  natureTableReverseMode: {
    adamant: [0, 5, 10, 20, 30, 30], brave: [0, 5, 10, 20, 30, 30], lonely: [0, 5, 10, 20, 30, 30],
    naughty: [0, 5, 10, 20, 30, 30], rash: [0, 5, 10, 20, 30, 30], hasty: [0, 5, 10, 20, 30, 30],
    naive: [0, 5, 10, 20, 30, 30],
    jolly: [0, 2, 5, 15, 25, 25], mild: [0, 2, 5, 15, 25, 25], lax: [0, 2, 5, 15, 25, 25],
    impish: [0, 2, 5, 15, 25, 25], bold: [0, 2, 5, 15, 25, 25],
    hardy: [0, 0, 5, 10, 20, 20], docile: [0, 0, 5, 10, 20, 20], serious: [0, 0, 5, 10, 20, 20],
    bashful: [0, 0, 5, 10, 20, 20], quirky: [0, 0, 5, 10, 20, 20],
    careful: [0, 0, 2, 5, 10, 10], calm: [0, 0, 2, 5, 10, 10], gentle: [0, 0, 2, 5, 10, 10],
    sassy: [0, 0, 2, 5, 10, 10], relaxed: [0, 0, 2, 5, 10, 10],
    modest: [0, 0, 0, 2, 5, 5], timid: [0, 0, 0, 2, 5, 5], quiet: [0, 0, 0, 2, 5, 5]
  },
  onTryMove(source, target, move) {
    if (!source?.set?.isShadow) return;
    if (source.volatiles['hypermode'] || source.volatiles['reversemode']) return;
    const nat = (source.set.nature || '').toLowerCase();
    let bars = Number(source.set.heartGaugeBars);
    if (!Number.isFinite(bars)) bars = 5;
    if (bars < 0) bars = 0; if (bars > 5) bars = 5;
    const table = this.field.getPseudoWeather('shadowengine').natureTableHyperMode;
    const row = table[nat];
    const idx = 5 - bars;
    const pct = row ? row[idx] : [30, 25, 20, 15, 10, 5][idx];
    if (pct && this.random(100) < pct) {
      source.addVolatile('hypermode');
      return false;
    }
  },
  onAfterMove(source, target, move) {
    if (!move || move.type !== 'Shadow') return;
    for (const pokemon of this.getAllActive()) {
      if (!pokemon?.set?.isShadow) continue;
      if (pokemon.volatiles['hypermode'] || pokemon.volatiles['reversemode']) continue;

      const nat = (pokemon.getNature() || '').toLowerCase();
      let bars = Number(pokemon.set.heartGaugeBars);
      if (!Number.isFinite(bars)) bars = 5;
      if (bars < 0) bars = 0; if (bars > 5) bars = 5;

      const table = this.field.getPseudoWeather('shadowengine').natureTableReverseMode;
      const row = table[nat];
      const idx = 5 - bars;
      const pct = row ? row[idx] : 0;

      if (pct && this.random(100) < pct) {
        pokemon.addVolatile('reversemode');
      }
    }
  }
})