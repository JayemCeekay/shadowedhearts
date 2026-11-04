({
  name: "Shadowy Aura",
  effectType: "Weather",
  duration: 5,
  onFieldStart(field, source, effect) {
    this.add("-weather", "Shadowy Aura");
  },
  onFieldResidualOrder: 1,
  onFieldResidual() {
    this.add("-weather", "Shadowy Aura", "[upkeep]");
    if (this.field.isWeather("shadowyaura")) this.eachEvent("Weather");
  },
  onWeather(target) {
    if (target.set.isShadow || target.volatiles.dig || target.volatiles.dive) return;
    this.damage(target.baseMaxhp / 16);
  },
  onEffectiveness(typeMod, target, type, move) {
    const config = this.dex.data.Scripts.shadowedhearts?.Config?.shadowMoves || {superEffectiveEnabled: true};
    if (!config.superEffectiveEnabled || !target || move?.type !== "Shadow") return;
    // Match Shadow Engine: the configured matchup applies once to the Pokemon,
    // rather than once per defensive type, and weather must respect its switch.
    if (type !== target.getTypes()[0]) return 0;
    const isShadowTarget = !!(target?.set?.isShadow);
    return isShadowTarget ? -1 : 1;
  },
  onModifyType(move) {
    if (move?.id === "weatherball") move.type = "???";
  },
  onBasePower(basePower, attacker, defender, move) {
    if (move?.id === "weatherball") {
      return this.chainModify(2);
    }
    if (move?.id === "solarbeam") {
      return this.chainModify(0.5);
    }
    if (move?.type === "Shadow") {
      return this.chainModify(1.5);
    }
  },
  onTryHeal(damage, target, source, effect) {
    if (!effect) return;
    const id = effect.id;
    if (id === "moonlight" || id === "synthesis" || id === "morningsun") {
      return Math.floor(target.baseMaxhp / 4);
    }
  },
  onDamagingHit(damage, target, source, move) {
    if (!target?.hp) return;
    if (move?.id === "weatherball" && move.type === "???" && target.hasAbility?.("colorchange")) {
      if (!target.setType("Normal")) return;
      this.add("-start", target, "typechange", "Normal", "[from] ability: Color Change");
    }
  },
  onFieldEnd() {
    this.add("-weather", "none");
  }
})
