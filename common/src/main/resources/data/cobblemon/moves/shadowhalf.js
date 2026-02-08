({
    accuracy: 100,
        basePower: 0,
    category: "Status",
    desc: "A shadowy aura's energy cuts everyone's HP by half.",
    name: "Shadow Half",
    pp: 100,
    priority: 0,
    flags: { contact: 0, protect: 1, mirror: 0, metronome: 0, reflectable: 0, snatch: 0, recharge: 1 },
    onTryMove(pokemon, target, move) {
    if (pokemon.volatiles["taunt"]) {
        this.add("cant", pokemon, "move: Shadow Half", "[wTaunt]");
        return false;
    }
},
    onHitField(target, source, move) {
    const allMons = this.getAllActive();

    for (const mon of allMons) {
        if (!mon || !mon.hp) continue;
        const damage = this.trunc(mon.hp / 2);

        if (damage <= 0) continue;

        this.damage(damage, mon, source, move);
    }
},
    self: {
        volatileStatus: "mustrecharge",
    },
    target: "all",
    type: "Shadow",
    zMove: { effect: "heal" },
    contestType: "Tough",
    isNonstandard: "Custom"
})