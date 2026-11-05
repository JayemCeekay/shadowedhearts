({
    name: "Ruin Policy",
    spritename: "ruinpolicy",
    onStart(pokemon) {
        this.singleEvent("Update", pokemon.getItem(), pokemon.itemState, pokemon);
    },
    onUpdate(pokemon) {
        if (pokemon.set?.isShadow && pokemon.volatiles["reversemode"]) {
            pokemon.useItem();
        }
    },
    boosts: {atk: 2, spa: 2},
    onUse(pokemon) {
        this.damage(pokemon.baseMaxhp / 8, pokemon, pokemon, this.dex.items.get("ruinpolicy"));
    },
    num: -1007,
    gen: 9,
    isNonstandard: "Custom",
})
