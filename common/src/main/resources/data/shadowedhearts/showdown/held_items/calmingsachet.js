({
    name: "Calming Sachet",
    spritename: "calmingsachet",
    onStart(pokemon) {
        this.singleEvent("Update", pokemon.getItem(), pokemon.itemState, pokemon);
    },
    onUpdate(pokemon) {
        if (!pokemon.set?.isShadow) return;
        const mode = pokemon.volatiles["hypermode"] ? "hypermode"
            : pokemon.volatiles["reversemode"] ? "reversemode" : null;
        if (!mode || !pokemon.useItem()) return;
        // Keep the saved entry flags aligned with the end protocol emitted by the mode.
        pokemon.set[mode === "hypermode" ? "isHyper" : "isReverse"] = false;
        pokemon.removeVolatile(mode);
    },
    num: -1005,
    gen: 9,
    isNonstandard: "Custom",
})
