# ShadowedHearts Service Boundaries (per 02 and 90)

This document defines high-level boundaries, contracts, and interactions between core services. It aligns with:
- 02_Signal_Missions_System.md ("02")
- 90_Implementation_Roadmap.md ("90")

Scope covered: SignalSystem, EntranceSpawner, RunRegistry, WorldspaceManager, DungeonGenerator, PuzzleSystem, TrainerSystem, RaidBossSystem, AffixEngine, Persistence, Events.

Conventions
- All services are server-authoritative unless stated otherwise.
- Event payloads are versioned. Services must not rely on unversioned payload shapes.
- Dependencies listed are directional; avoid cycles. Use Events bus or Persistence as cross-cutting concerns when needed.
- Inputs/Outputs describe public API (methods, data) and events emitted/consumed.

1. SignalSystem
- Purpose: Validate and resolve Mission Signals from fragments; produce Mission Signal payload used to request entrances and runs. (02 §4; 90 §3 Iteration A)
- Responsibilities:
  - Load synthesis rules from datapack (data/shadowedhearts/signals/synthesis.json).
  - Validate fragment grid (conflicts, caps, rarity sums).
  - Deterministically compute seed (server salt + party UUID) and resolved attributes (theme, tier, affixes).
  - Emit SignalSynthesized event.
- Inputs:
  - 3×3 fragment grid; server context (party, server seed, timestamp).
- Outputs:
  - MissionSignal { theme, tier, affixes[], seed, requestedBy, party[] }.
  - Event: SignalSynthesized(v1).
- Dependencies: AffixEngine (for modifiers & validation), Events, Persistence (optional metrics).

2. EntranceSpawner
- Purpose: Place mission entrance proxy structure in overworld and bind it to a MissionSignal. (02 §5–§6)
- Responsibilities:
  - Ring search (R config 600–3000), candidate sampling, biome filters, scoring.
  - Reserve location and stamp entrance template with metadata.
  - TTL & relocation handling; anti-grief rules (claims/protection changes).
- Inputs:
  - MissionSignal; party; world reference; config.
- Outputs:
  - EntranceId; physical structure; bind to Signal Compass.
  - Event: EntranceSpawned(v1) [optional future].
- Dependencies: WorldspaceManager (for reservation utilities if shared), Persistence (record entrance state), Events.

3. RunRegistry
- Purpose: Orchestrate run lifecycle and track active runs. (90 §3 Iteration B)
- Responsibilities:
  - createRun(signal, party, entranceId) → RunId, ActiveRun.
  - Build RunConfig from MissionSignal; allocate worldspace; handoff to DungeonGenerator.
  - Track per-player RunState mapping; provide queries by player UUID.
  - Finish/abort flows; emit lifecycle events.
- Inputs:
  - MissionSignal, party, entrance metadata.
- Outputs:
  - ActiveRun/RunState persisted; teleports; lifecycle Events.
- Dependencies: WorldspaceManager, DungeonGenerator, Persistence, Events, AffixEngine.

4. WorldspaceManager
- Purpose: Manage spatial allocation and chunk tickets for per-run instances. (90 §3, §6 Risks)
- Responsibilities:
  - Allocate AABB region for a run, apply chunk tickets, stamp entry pad.
  - Track occupancy, prevent overlaps, and cleanup (air wipe, despawns, ticket release).
- Inputs:
  - RunConfig; server world reference; requested size.
- Outputs:
  - WorldspaceHandle { region, tickets, entryPos }.
- Dependencies: Persistence (tracking allocations), Events (debug notifications).

5. DungeonGenerator
- Purpose: Generate room graph and place content per theme/tier/affixes. (90 §3 Iteration C; 02 §3)
- Responsibilities:
  - Graph selection (chain/T-branch) and spatial placement with backtracking & AABB checks.
  - Apply corridors/doors rules; place sockets; populate with chests, puzzle anchors, trainer spawns.
  - Expose deterministic generation by seed.
- Inputs:
  - RunConfig { theme, tier, affixes, seed, scaling } and WorldspaceHandle.
- Outputs:
  - Generated structure in world; GeneratorReport.
  - Events: RoomPlaced(v1) [debug], GeneratorFinished(v1).
- Dependencies: AffixEngine (weights/content modifiers), Events.

6. PuzzleSystem
- Purpose: Manage puzzle controllers (e.g., Rune Sequence) and their server-side logic. (90 §3 Iteration D)
- Responsibilities:
  - Spawn puzzle instances per anchors; maintain deterministic state.
  - Validate client interactions; gate doors/locks upon solve.
  - Sync state via networking to clients.
- Inputs:
  - Puzzle definitions (puzzles/*.json), RunConfig seed, player interactions.
- Outputs:
  - PuzzleSolved event; door unlock triggers.
- Dependencies: Events, Networking (client sync), Persistence (optional saved state within run).

7. TrainerSystem
- Purpose: Spawn and manage trainer encounters per room rosters. (90 §3 Iteration D)
- Responsibilities:
  - Select encounters via rosters/*.json; scale by tier/party.
  - Integrate with aggro/combat hooks; report defeats.
- Inputs:
  - Room tags, roster data, scaling params.
- Outputs:
  - TrainerDefeated events; rewards hooks.
- Dependencies: Events, Aggro system (03/05), AffixEngine.

8. RaidBossSystem
- Purpose: Coordinate boss fight phases and shared HP pool behavior. (90 §3 Iteration D)
- Responsibilities:
  - Initialize boss from bosses/*.json; manage phases and shields.
  - Emit BossPhaseChanged, BossDefeated; open Capture/Purify window.
- Inputs:
  - RunConfig; boss JSON; combat triggers.
- Outputs:
  - Boss events; room/door triggers; end-of-run handoff.
- Dependencies: Events, TrainerSystem (if using adds), AffixEngine.

9. AffixEngine
- Purpose: Centralize affix effects on generation, scaling, and encounters. (90 §3 Iteration C/E; 04)
- Responsibilities:
  - Interpret affixes from MissionSignal; modify room weights/content and scaling curves.
  - Provide hooks for trainer/boss modifiers; log applied effects for debug.
- Inputs:
  - Affix list; theme/tier; base curves.
- Outputs:
  - Modified weights/config; debug logs.
- Dependencies: Persistence (telemetry), Events (debug messages).

10. Persistence
- Purpose: Store runtime and saved state for runs and services. (90 §3, §6)
- Responsibilities:
  - DimensionDataStorage (NBT) for saved RunState; load/save cycle.
  - Runtime caches with resume/cleanup semantics on server start.
  - Helpers: fetch run by player UUID; purge orphaned runs.
- Inputs:
  - RunState, Worldspace allocations, optional metrics.
- Outputs:
  - Durable records; recovery logs; Persistence events (optional).
- Dependencies: — (cross-cutting).

11. Events
- Purpose: Decoupled communication via versioned payloads. (02 §9; 90 §3/§7)
- Responsibilities:
  - Provide a central bus (ShadowsEvents) with typed, versioned payloads.
  - Core events: SignalSynthesized, EntranceSpawned, RunCreated, RoomCleared, PuzzleSolved, TrainerDefeated, BossPhaseChanged, BossDefeated, CaptureWindowOpened, RunFinished, OverworldTrainerDefeated.
- Inputs:
  - Emissions from services; subscribers from systems.
- Outputs:
  - Dispatch to subscribers; optional audit log.
- Dependencies: — (cross-cutting).

Service Interaction Summary
- Typical flow: SignalSystem → EntranceSpawner → RunRegistry → WorldspaceManager → DungeonGenerator → {PuzzleSystem, TrainerSystem} → RaidBossSystem → RunRegistry (finish) → Persistence cleanup.
- Events mediate cross-service updates; AffixEngine influences generation and encounters; Persistence underpins state and recovery.

Versioning & Contracts
- All public payloads and events must include a version field (e.g., v1) and be forward-extensible.
- Services should expose Result<T, Error> or Optional + error enums to avoid silent failures (90 §7).

Notes and Open Questions
- Exact JSON schemas for puzzles/rosters/bosses/affixes are defined in their respective subsystem READMEs (see tasks 62). Until implemented, treat as MVP stubs.
- Networking specifics for HUD/compass/puzzle UIs are scoped in tasks 50.
