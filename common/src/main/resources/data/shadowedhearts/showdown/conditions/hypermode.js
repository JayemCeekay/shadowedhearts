({
  name: "Hyper Mode",
  effectType: "VolatileStatus",
  onStart(pokemon) {
    this.add('-start', pokemon, 'Hyper Mode');
    if (pokemon.volatiles['reversemode']) {
      pokemon.removeVolatile('reversemode');
    }
    if (this.turn && this.turn >= 1) {
      this.add('hyper', 'start', pokemon.getSlot());
    }
  },
  onEnd(pokemon) {
    this.add('-end', pokemon, 'Hyper Mode');
    this.add('hyper', 'end', pokemon.getSlot());
  },
  onResidualOrder: 6,
  onResidual(pokemon) {
    if (this.randomChance(1, 256)) {
      this.add('sh_message', 'cobblemon.battle.hypermode.calm', pokemon.getSlot());
      pokemon.removeVolatile('hypermode');
      this.add('hyper', 'end', pokemon.getSlot());
    }
  },
  onFaint(pokemon) {
    if (pokemon.volatiles['hypermode']) {
      pokemon.removeVolatile('hypermode');
      this.add('hyper', 'end', pokemon.getSlot());
    }
  },
  onTryMove(source, target, move) {
    if (!move || move.type === 'Shadow') return;
    if (this.randomChance(1, 10)) {
      const outcomes = ['otherMove', 'attackAlly', 'selfHurt', 'fakeItem', 'yellAtTrainer', 'doNothing', 'returnBall'];
      const pick = outcomes[this.random(outcomes.length)];
      const otherKnown = source.moveSlots.filter(m => m.id !== move.id);
      switch (pick) {
        case 'otherMove': {
          if (otherKnown.length) {
            const chosen = otherKnown[this.random(otherKnown.length)].id;
            this.add('sh_message', 'cobblemon.battle.hypermode.disobey', source.getSlot());
            move.id = chosen;
            move.name = this.dex.moves.get(chosen).name;
            return false;
          }
        }
        case 'attackAlly': {
          const ally = source.side.active[1] && !source.side.active[1].fainted ? source.side.active[1] : null;
          if (ally) {
            this.add('sh_message', 'cobblemon.battle.hypermode.attack_ally', source.getSlot());
            if (target && target !== ally) target = ally;
            return false;
          }
        }
        case 'selfHurt': {
          this.add('sh_message', 'cobblemon.battle.hypermode.thrashing', source.getSlot());
          this.damage(Math.max(1, Math.floor(source.baseMaxhp / 8)), source, source);
          return false;
        }
        case 'fakeItem': {
          this.add('sh_message', 'cobblemon.battle.hypermode.fumble', source.getSlot());
          return false;
        }
        case 'yellAtTrainer': {
          this.add('sh_message', 'cobblemon.battle.hypermode.lash_out', source.getSlot());
          return false;
        }
        case 'returnBall': {
          const sw = source.side.pokemon.some(p => !p.fainted && !p.active);
          this.add('sh_message', 'cobblemon.battle.hypermode.return_ball', source.getSlot());
          if (!sw) this.add('sh_message', 'cobblemon.battle.hypermode.switch_fail', source.getSlot());
          return false;
        }
        default: {
          this.add('sh_message', 'cobblemon.battle.hypermode.refuse', source.getSlot());
          return false;
        }
      }
    }
  },
  onBasePower(basePower, attacker, defender, move) {
    if (move?.type === 'Shadow') {
      return this.chainModify(1.5);
    }
  }
})
