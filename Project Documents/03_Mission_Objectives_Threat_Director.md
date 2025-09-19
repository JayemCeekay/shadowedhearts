# Mission Objectives & Threat Director (Canonical)

Purpose: Define mission objective types, the server-side Mission Controller, Threat/Wave Director, and UX hooks that make missions dynamic and fair. See 05_Aggro_Combat_System.md for aggro/combat rules. Consolidates mission_system_design.md and aligns with canonical terminology.

Related:
- 02_Signal_Missions_System.md — overall signals → missions loop and Mission Entrance flow
- 01_Core_Mechanics_Shadow_Purity.md — corruption effects and faction hooks

---

## 1) Mission Controller (Server-Side)
- Tracks mission phase, objectives, timers, threat budget, and active waves
- Owns a local Aggro Budget separate from global per-player caps
- Objectives implement an `Objective` interface with lifecycle hooks: start, tick, progress, complete, fail

Objective State
- Each objective reports progress and fail reasons and can feed the Mission HUD
- Hybrid objectives chain or gate multiple objective types

---

## 2) Objective Types
- PointDefenseObjective: Defend a target block/entity (e.g., Purity Beacon) within a radius and duration, with allowable damage threshold
- EscortObjective: Move an NPC/entity along a path with tethering, waits, and ambush events
- PurgeZoneObjective: Eliminate a quota of corrupted Pokémon in an area
- TrainerBattleObjective: Defeat designated NPC trainers (e.g., Shadow team members)
- ResourceCollectionObjective: Gather specified items from nodes/loot/Pokémon drops
- RescueObjective: Find an injured/lost Pokémon and escort it to safety
- CaptureObjective: Catch/contain a specific Shadow Pokémon or seal a corruption node
- SurvivalObjective: Hold out for a time against waves
- ZoneControlObjective: Stand in marked areas to purify/claim them
- SabotageObjective: Destroy/disable Shadow structures (generators, caches)
- BossRaidObjective: Cooperative fight vs a powerful Shadow boss Pokémon
- PuzzleObjective: Solve environmental tasks (runes, mirrors, pressure plates, pathing)
- StealthObjective: Avoid detection by patrols
- ChaseObjective: Pursue a fleeing Pokémon or beat NPCs to an objective
- Hybrid: Combine multiple in sequence or parallel (e.g., defeat trainers → rescue → defend exit)

---

## 3) Threat & Wave Director
Threat Budget
- Pool of points for spawning waves (small: 1, elites: 2–3, bosses: 5+)

Wave Specs (JSON/Codec)
- Triggers: time, HP%, phase, objective state
- Spawn sets: `{species_tag, count, cost, ai_role, entry_rule}`
- AI roles: `SAPPER`, `HARASSER`, `BRUISER`, `RUNNER`
- Entry rules: `FROM_PORTALS`, `DROPSHIP`, `AMBIENT_GRASS` (flavor)

Director Enforcement
- Honors threat budget, per-player aggro slots, and local density caps
- Cancels/defers spawns if limits are exceeded; steers spawns to less-defended angles

---

## 4) AI Goals & Defender Mechanics
Summary: See 05_Aggro_Combat_System.md for full aggro/AI/defender rules.
- AI Goals: AttackStructureGoal, AttackEscortVIPGoal, DisengageOnLeashGoal, CallForHelp (capped)
- Defender Mechanics: Auto-Guard toggle, stamina/exhaustion, priority filters

---

## 5) Anti-Swarm & Aggro Limits
Summary: See 05_Aggro_Combat_System.md for per-player aggro slots, density caps, spawn steering, help cooldowns, and disengage rules.

---

## 6) Weapon Combat Toggle (Server Option)
Summary: See 05_Aggro_Combat_System.md for server configuration details. Default: OFF.

---

## 7) Mission UI & Fail States
HUD Elements
- Bossbar-style objective progress: time left, target HP, wave meter
- Show current objective, next triggers, party status, and corruption timer (from 01_Core Mechanics)

Fail/Success Conditions
- Vary by objective: Beacon HP too low, VIP downed, quota not met, timer expired
- Rewards: type essences, signal fragments, Shadow/Purity reputation, unlocks

---

## 8) Shadow vs Purity Hooks
- Purity Beacon: Pacifying aura; reduces aggro and defender slot costs
- Shadow Corruption Surge: Temporary spike in spawn rates and aggression
- Reputation Tracks: Different rewards for aggressive (Shadow) vs defensive (Purity) success

---

## 9) Example Wave JSON
```json
{
  "id": "shadowedhearts:point_defense_t1",
  "type": "POINT_DEFENSE",
  "target": {"pos": [0,64,0], "block": "shadowedhearts:purity_beacon"},
  "radius": 18,
  "duration_ticks": 3600,
  "allowed_damage_pct": 35,
  "director": {
    "threat_max": 24,
    "pulse_ticks": 200,
    "per_player_aggro_slots": 2,
    "area_density_cap": 6
  },
  "waves": [
    {"when": {"t": 0},    "spawn": [{"species_tag": "#shadow_corrupted_small", "count": 5, "cost": 1, "ai_role": "SAPPER", "entry_rule": "AMBIENT_GRASS"}]},
    {"when": {"t": 600},  "spawn": [{"species_tag": "#shadow_corrupted_medium", "count": 3, "cost": 2, "ai_role": "BRUISER", "entry_rule": "FROM_PORTALS"}]},
    {"when": {"hp_lt_pct": 70}, "spawn": [{"species_tag": "#shadow_corrupted_runner", "count": 4, "cost": 1, "ai_role": "RUNNER"}]},
    {"when": {"t": 2400}, "spawn": [{"species_tag": "#shadow_elite", "count": 1, "cost": 5, "ai_role": "BRUISER"}]}
  ]
}
```

---

## 10) Implementation Notes (MVP)
- Prototype Point Defense with Beacon HP and 3 waves
- Add Escort with tethering and ambushes
- Integrate Auto-Guard defenders tied to director priorities
- Introduce stamina/exhaustion for defender Pokémon
- Keep Weapon Combat Toggle default OFF; expose server config

See 90_Implementation_Roadmap.md for the broader MVP/backlog.
