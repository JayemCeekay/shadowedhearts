# ShadowedHearts — Actionable Improvement Checklist

Notes
- Each item is ordered to build foundations first, then core systems, content, UX, quality, and docs.
- Items are phrased to be directly actionable. Track progress by checking [ ].
- Canonical sources: 00_Project_Goals.md, 01_Core_Mechanics_Shadow_Purity.md, 02_Signal_Missions_System.md, 03_Mission_Objectives_Threat_Director.md, 04_Essence_Affix_Progression.md, 05_Aggro_Combat_System.md, 90_Implementation_Roadmap.md.

Foundations & Architecture
1. [x] Define and document the high-level service boundaries (SignalSystem, EntranceSpawner, RunRegistry, WorldspaceManager, DungeonGenerator, PuzzleSystem, TrainerSystem, RaidBossSystem, AffixEngine, Persistence, Events) per 02 and 90.
2. [x] Create a common events bus class (e.g., ShadowsEvents) with versioned event payloads: SignalSynthesized, RunCreated, RoomCleared, PuzzleSolved, TrainerDefeated, BossPhaseChanged, BossDefeated, CaptureWindowOpened, RunFinished, OverworldTrainerDefeated (per 02 §9, 90 §3/§7).
3. [ ] (in progress) Establish a network packet registry with protocol versioning for minimal client features (HUD, compass, puzzle UIs) and future expansion; base networking in place (ModNetworking), handshake/version guard pending.
4. [ ] (in progress) Introduce a central config class (server + client) for corruption toggles, entrance search radii, generation limits, debug flags (per 01 §6, 02 §6, 90 §5/§7). Minimal ModConfig exists (UI toggles); needs server/client corruption/entrance/generation/debug.
5. [ ] Normalize namespaces and resource locations to shadowedhearts consistently (per 90 §4); add a ResourceIds helper to avoid hard-coded strings.
6. [ ] Define Persistence layer contracts: runtime (RunState) and saved (NBT/DimensionDataStorage) with resume/cleanup semantics (per 90 §3/§6).
7. [x] Establish package structure by system (e.g., signals/, entrances/, runs/, world/, generator/, puzzles/, trainers/, boss/, affixes/, networking/, persistence/, events/, config/) and move existing classes accordingly.
8. [ ] Add a lightweight service locator or dependency wiring (static registries or DI-lite) so systems can reference each other without circular deps.

Signals & Signal Locator
9. [ ] Refactor SignalLocatorBlockEntity to delegate synthesis logic to a server-side SignalSynthesisService (separation of concerns; current BE does inline parsing/seed).
10. [ ] Implement data-driven synthesis rules loader for data/shadowedhearts/signals/synthesis.json (per 02 §4) with validation (no_conflicts, min_total_rarity) and caps (affix ≤ 3).
11. [ ] (in progress) Extend fragment item model to carry IDs/type/value/rarity/affinity/conflicts (align with example JSON in 02 §4) and register via datapack; basic kind/value/rarity items are registered in ModItems (code-driven).
12. [ ] (in progress) Update the UI flow: 3×3 grid, explicit Synthesize button, server validates and consumes; current UI has 3×3 grid and auto-preview via BE; Synthesize button + server validation/consume pending.
13. [ ] Emit SignalSynthesized event with payload (theme, tier, affixes, seed, party) upon successful synthesis.
14. [ ] Grant a bound Signal Compass to the party on synthesis (per 02 §5–§6) and persist EntranceId on the item NBT.
15. [ ] Add sound/particle feedback and optional redstone pulse on successful synthesis.

Mission Entrances & Compass
16. [ ] Implement EntranceSpawner.request(signal, party): ring search (R=600–3000, config), candidate sampling, biome filters, scoring, selection, reserve, and stamp structure (per 02 §6).
17. [ ] Create data-driven entrance templates under data/shadowedhearts/structures and metadata JSONs (biomes, avoidance radii, footprint, flatness; 02 §6 example JSON).
18. [ ] Implement Signal Compass item: binds to EntranceId, points to entrance/proxy, HUD distance bands (Near/Far/Very Far); add client-side compass indicator and tooltip (per 02 §6).
19. [ ] (in progress) Replace MissionGatewayBlock stub with interaction flow: verify bound signal, call RunRegistry.createRun, teleport party, consume signal, and despawn entrance (per 02 §5). Current stub consumes Mission Signal and notifies player.
20. [ ] Enforce party-only usage, TTL expiry cleanup, and relocation on claim/protection changes (anti-grief; 02 §6).

Run Lifecycle & Worldspace
21. [ ] (in progress) Implement RunRegistry: allocate RunId, form RunParty, build RunConfig from Mission Signal payload, and create ActiveRun/RunState (per 90 §3 Iteration B). MVP RunRegistry/ActiveRun/RunConfig exist; party/config building minimal.
22. [ ] Persist ActiveRun in DimensionDataStorage (NBT) with helpers to fetch by player UUID; save/load on world save/load (90 §3, §6).
23. [ ] (in progress) Implement WorldspaceManager: allocate per-run AABB, apply chunk tickets limits, stamp entry pad, track occupied regions, and cleanup (air wipe, despawns, ticket release) (90 §3, §6 Risks). Basic allocateBounds and template stamping helpers exist; cleanup/tickets pending.
24. [ ] (in progress) Add commands: /shadowmission seed <id> (print config), /shadowmission abort <id> (cleanup) (90 §3, §7/§8). Command tree exists with stubs (seed/abort) and demo/gen helpers.

Dungeon Generation & Content
25. [ ] (in progress) Create datapack scaffolding directories: rooms/, rosters/, puzzles/, bosses/, affixes/, themes/, structures/, signals/, loot_tables/, advancements/, tags/ (per 02 §3, 90 §4). Initial rooms/test and signals/_EXAMPLE_synthesis.json present.
26. [ ] Implement a small room set (3–6 rooms) for one theme (e.g., Grave) with NBT templates, sockets/bounds/weights.
27. [ ] (in progress) Build a generator pipeline MVP: graph selection (chain/T-branch), spatial placement with backtracking and AABB checks, corridors/doors rules (per 90 §3 Iteration C). Basic stamping helpers and demo room exist.
28. [ ] Add populate step: simple chests, puzzle anchors, trainer spawn placeholders with tags (90 §3 Iteration C).
29. [ ] Add AffixEngine stubs to influence room weights/content and log applied effects (90 §3 Iteration C).

Puzzles, Trainers, Boss (MVP)
30. [ ] Implement PuzzleController (Rune Sequence) server logic with deterministic seeding; expose a minimal client screen and puzzles/runes.json (90 §3 Iteration D).
31. [ ] Implement Trainer spawns for rooms from rosters/*.json; scale by tier/party; integrate door/lock progression (90 §3 Iteration D).
32. [ ] Implement one Boss MVP with sequential mini-encounters draining a shared HP pool; one boss JSON; simple shield phases; stub Capture/Purify window (90 §3 Iteration D).

UX & HUD
33. [ ] (in progress) Enrich Mission HUD with timer, theme/tier/affixes, keys/obelisks, compass hint, and party status (90 §3 Iteration E). Minimal dimension banner HUD present.
34. [ ] Add feedback flows: chat toasts, titles, and screen messages for synthesize, entrance spawned, run start, boss phases, run finished.
35. [ ] Provide accessibility toggles for shader intensity, screen flashes, and camera shake.

Shadow/Purity Mechanics Integration
36. [x] Implement Shadow aspect data model + sync utilities per 01 §4: corruption float (0–1), shadow_state boolean; expose get/set helpers and display mapping 0–100.
37. [ ] Add server config toggles for threshold restrictions (OFF by default) and rate multipliers; gate breed/trade/XP when enabled (01 §1 §6).
38. [ ] Hook dungeon environments to corruption: ambient miasma increases; purity pools decrease; items to reduce or manipulate (01 §1/§5, 02 §7).
39. [ ] Add client visual shaders/particles driven by entity-synced data (01 §3, §7 Notes).

Aggro & Combat System Hooks
40. [ ] Implement per-player aggro slots/budgets and density caps; expose APIs for room spawns and director (05 summary refs in 03 doc).
41. [ ] Integrate aggro steering and helper roles into Trainer/AI tags for dungeon rooms; add server configs for caps (03 §Summary refs, 05 doc).

Scaling, Rewards, and Balancing
42. [ ] Implement a scaling function mapping party size, tier, affixes → enemy level delta, room count, timer, boss HP; expose data-driven curves (02 §8, 90 §3 Iteration E).
43. [ ] Configure loot tables for trainers/rooms/bosses; add advancement hooks for key beats (02 §8, §3 layout).

Quality: Testing, Debug, Perf
44. [ ] Add unit tests for synthesis validation/resolution logic using sample fragments and synthesis.json.
45. [ ] Add integration tests for RunRegistry persistence and cleanup on server restart (mock/save-load cycle).
46. [ ] Add snapshot tests for generator pipeline placement on a fixed seed with a micro room set.
47. [ ] Add debug commands and logs with [DEBUG_LOG] prefix for runs, generator decisions, and affix applications (90 §7).
48. [ ] Add a tiny debug structure validator and worldspace visualizer (e.g., particle boxes) to validate bounds and sockets (90 §6 Risks).
49. [ ] Profile chunk tickets and generator on a small world; cache StructureTemplates; background stamp distant branches (90 §6 Performance).

Networking & Error Handling
50. [ ] Define and register packets for: HUD state update, compass target update, puzzle state sync; add client handlers and version checks (90 §6 Networking).
51. [ ] (in progress) Add robust error paths: missing missions dimension, failed entrance placement, invalid run state; surface actionable errors to players and logs (02 §5 step 4). Command feedback exists for dimension/placement; broaden coverage.

Codebase Hygiene & Refactors
52. [ ] Replace deprecated PokemonArgument with a richer argument provider (supports selectors and suggestions) or move to util with @Deprecated and javadoc linking preferred APIs.
53. [ ] (in progress) Add null-safety and server-side checks throughout BE/blocks (e.g., SignalLocatorBlockEntity recomputePreview already guards; extend with validation results to client). Basic server-side checks present in several classes; expand coverage.
54. [ ] Extract seed computation into a reusable utility with test coverage; include server salt and party UUID per spec (02 §4 resolve.seed).
55. [ ] Ensure MissionGatewayBlock uses proper flow: verify held Mission Signal payload, call EntranceSpawner/RunRegistry, and server-only messages.
56. [ ] Audit and centralize ResourceLocation construction; avoid hard-coded strings scattered across code.
57. [ ] Introduce a small Result<T, Error> pattern or use Optional + error enums for synthesis/entrance/generator APIs to avoid silent failures.

Tooling & CI
58. [ ] Add Gradle tasks for data pack validation and run configuration bootstraps; wire to CI.
59. [ ] Integrate CI (GitHub Actions) to build on PRs, run tests, and archive debug artifacts (room graphs, NBT dumps).
60. [ ] Add a data-driven content linter for JSON schema conformity (rooms, rosters, puzzles, bosses, affixes, themes, signals).

Documentation & Onboarding
61. [ ] Keep Project Documents updated; link each implemented feature back to canonical sections and mark pending clarifications resolved.
62. [ ] Add developer READMEs per subsystem (signals/, entrances/, runs/, generator/, puzzles/) with data formats and extension points.
63. [ ] Provide a minimal “MVP Playbook” doc: how to synthesize a signal, find the entrance, complete a run, and verify cleanup.

Stability & Ops
64. [ ] On server start, scan and resume/purge orphaned runs; attempt cleanup of stale worldspace (90 §6 Persistence).
65. [ ] Add telemetry counters (opt-in): runs started/finished, entrance placements, generator retries, boss wipes.
66. [ ] Gate experimental features behind config flags and annotate code paths; provide a single switch for enabling advanced content locally.
