({
  name: "Shadowed Hearts Howl",
  onAfterSetStatus(status, caller) {
    caller.removeVolatile("shadowedheartshowl");
  },
  onResidualOrder: 30,
  onResidual(caller) {
    caller.removeVolatile("shadowedheartshowl");
    const partner = caller.side.allySide;
    if (!partner || !caller.hp || caller.status || partner.pokemon.some(p => p.hp)) return;
    // SOS Encounters owns the response animation, insertion, and next request.
    this.add("-sos", caller, caller.side.n + 1, 1, 1);
    this.awaitTurnEnd = true;
  },
})
