# Aggro & Combat System (Canonical)

Purpose: Define how hostiles acquire and maintain targets, how defender Pokémon behave, anti-swarm protections, and the optional weapon combat toggle. This document centralizes aggro/combat rules referenced by the Mission Director and objectives.

Related:
- 03_Mission_Objectives_Threat_Director.md — wave/threat director integrates these limits
- 01_Core_Mechanics_Shadow_Purity.md — Purity/Shadow effects can bias aggression and visuals

---

## 1) Aggro Model
- Global Per-Player Aggro Slots: cap how many hostiles can actively target a player at once (e.g., 2–3). Large hostiles may cost multiple slots.
- Mission Local Aggro Budget: some objectives allocate a local pool (owned by the Mission Controller) to pace waves independently of global slots.
- Area Density Cap: prevent overcrowding within a radius (e.g., max 6 hostiles within 12 blocks).
- Reservation Flow: hostiles request slots before targeting; failure → idle/prowl behavior.
- Release & Cooldowns: on disengage (leash, LoS loss, pacified), release slots and apply re-aggro cooldown vs the same player.

Notes
- Exact values are data/config driven. Director enforces both local budgets and global caps.

---

## 2) Aggro Rules & Disengage
- Distance Check: only within N blocks may attempt aggro (theme/affix can modify).
- Line-of-Sight Check: world geometry blocks initial aggro and can break it.
- Help Calls (Capped): a hostile may recruit nearby allies if budgets/caps allow; subject to cooldowns.
- Disengage Conditions:
  • Leash Distance exceeded
  • Line-of-sight timeout
  • Patience timer (no progress) 
- Spawn Steering: director biases future spawns to less-defended angles when caps are near.

---

## 3) AI Roles & Goals
- Roles used by director and defender prioritization:
  • SAPPER — targets structures/objectives first (e.g., beacons, obelisks)
  • HARASSER — distracts/kites defenders, spreads debuffs
  • BRUISER — soaks and pushes the frontline
  • RUNNER — chases VIPs or attempts escape/objective reach

- Example Goals:
  • AttackStructureGoal, AttackEscortVIPGoal
  • DisengageOnLeashGoal (drop target when leash broken)
  • CallForHelpGoal (rate-limited)

---

## 4) Defender Mechanics
- Auto-Guard Toggle: optionally auto-send out lead Pokémon during missions when threatened.
- Priority Filters: defenders prioritize mission-relevant threats (e.g., SAPPERS near objective).
- Stamina/Exhaustion: continuous combat drains stamina; resting recovers it to avoid infinite chains.
- Party Coordination: per-player slot usage may be reduced in Purity fields (see hooks).

---

## 5) Anti-Swarm Protections
- Per-Player Aggro Slot Cap: e.g., 2–3 effective hostiles per player.
- Per-Objective Spawn Caps: prevent too many spawns for a single objective arena.
- Local Density Checks: reject/deflect spawns into crowded areas.
- Help Cooldowns: delay successive reinforcement calls.
- Disengage Rules: leash, LoS timeout, patience timer to naturally thin crowds.
- Spawn Steering: bias entries to open lanes.

Director Behavior
- If any cap/budget would be exceeded, spawns are cancelled/deferred; the director may accumulate threat for a later pulse.

---

## 6) Weapon Combat Toggle (Server Option)
- Default: OFF — Players cannot damage Pokémon with Minecraft weapons; combat is Pokémon vs Pokémon.
- ON: Players can damage hostiles directly; still bound by anti-swarm and director caps.
- Configuration: global server config with optional per-mission override/datapack.

---

## 7) Repel/Smoke/De-Aggro Tools (Optional Items)
- Repel Pulse: AoE pacify that clears targets, releases slots, and applies a re-aggro cooldown against the pulsing player.
- Smoke Bomb: short-lived cloud that breaks LoS, causing drop-aggro unless in melee.
- Flash/Noise Pop: brief daze; cancels help calls.
- Diminishing Returns: repeated disruptions within 30s have reduced duration; bosses/elites gain partial immunity.
- Director Hook: brief spawn pause near the pulse (e.g., 2s) to avoid instant re-aggro.

All durations, radii, caps, and immunities are configurable; affixes/biomes may modify them.

---

## 8) Data & Config Hooks
- Server Config: per-player slot cap, density radius/cap, default leash distances, help/call cooldowns, weapon toggle default.
- Datapack: director presets per objective/theme, AI role weights, spawn entry rules, mission local aggro budgets.
- JSON Hints in objectives/director specs:
```json
{
  "director": {
    "per_player_aggro_slots": 2,
    "area_density_cap": 6,
    "leash_distance": 24,
    "help_cooldown_ticks": 200
  }
}
```

---

## 9) Hooks with Shadow vs Purity
- Purity Beacon: reduces effective slot cost of defenders; extends pacify durations slightly.
- Shadow Corruption Surge: temporarily raises aggro intensity and reduces pacify effectiveness.

These effects should be additive modifiers referenced from 01_Core_Mechanics_Shadow_Purity.md and affix systems.
