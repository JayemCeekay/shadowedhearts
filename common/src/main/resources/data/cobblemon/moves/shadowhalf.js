({
    accuracy: 100,
    basePower: 0,
    category: "Status",
    desc: "A shadowy aura's energy cuts everyone's HP by half.",
    name: "Shadow Half",
    pp: 100,
    priority: 0,
    flags: {
        contact: 0,
        protect: 1,
        mirror: 0,
        metronome: 0,
        reflectable: 0,
        snatch: 0,
        recharge: 1
    },
    onTryMove(pokemon, target, move) {
        if (pokemon.volatiles["taunt"]) {
            this.add("cant", pokemon, "move: Shadow Half", "[wTaunt]");
            return false;
        }
    },
    onHitField(target, source, move) {
        /* Route each occupant through native hit checks: a field-only callback bypasses Protect. */
        const hit = {...move, target: 'allAdjacent', category: 'Special', self: undefined, onHitField: undefined,
            damageCallback(user, mon) { return Math.floor(mon.hp / 2); }};
        this.actions.trySpreadMoveHit(this.getAllActive(), source, hit);
    },
    self: {
        volatileStatus: "mustrecharge",
    },
    target: "all",
    type: "Shadow",
    zMove: {effect: "heal"},
    contestType: "Tough",
    isNonstandard: "Custom"
})
