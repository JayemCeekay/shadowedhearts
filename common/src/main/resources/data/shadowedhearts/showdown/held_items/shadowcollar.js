({
    name: "Shadow Collar",
    spritename: "shadowcollar",
    onStart(pokemon) {
        pokemon.removeVolatile("choicelock");
    },
    onModifyMove(move, pokemon) {
        if (this.dex.moves.get(move.baseMove || move.id).type === "Shadow") {
            pokemon.addVolatile("choicelock");
        }
    },
    onDisableMove(pokemon) {
        // Preserve the native Choice-item exception during Dynamax.
        if (pokemon.volatiles["dynamax"]) return;
        for (const moveSlot of pokemon.moveSlots) {
            if (this.dex.moves.get(moveSlot.id).type !== "Shadow") {
                pokemon.disableMove(moveSlot.id);
            }
        }
    },
    onBeforeMovePriority: 5,
    onBeforeMove(pokemon, target, move) {
        if (pokemon.volatiles["dynamax"] || move.id === "struggle") return;
        // Check the original move type, before abilities or effects change it.
        if (this.dex.moves.get(move.baseMove || move.id).type !== "Shadow") {
            this.add("cant", pokemon, "item: Shadow Collar", move);
            return false;
        }
    },
    onTryMove(pokemon, target, move) {
        // Called moves can bypass BeforeMove; Struggle must remain usable.
        if (pokemon.volatiles["dynamax"] || move.id === "struggle") return;
        if (this.dex.moves.get(move.baseMove || move.id).type !== "Shadow") {
            this.add("cant", pokemon, "item: Shadow Collar", move);
            return false;
        }
    },
    onBasePowerPriority: 16,
    onBasePower(basePower, source, target, move) {
        if (source.set?.isShadow && move.type === "Shadow" && move.category !== "Status"
            && !source.volatiles["dynamax"]) {
            return this.chainModify(1.5);
        }
    },
    isChoice: true,
    num: -1004,
    gen: 9,
    isNonstandard: "Custom",
})
