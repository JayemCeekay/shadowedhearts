# Pokémon Tactical Order and Stratagem System

## 🎯 Overview
The Tactical Order and Stratagem System lets trainers issue **real-time commands** to Pokémon both in the overworld and at their bases. Orders can be directed at individual Pokémon, party members, or entire groups. This blends *RTS-style control*, *Helldivers-style stratagem input*, and **immersive world interaction** through blocks and items.

---

## 🧩 Command Issuing Methods

### 1. Party Pokémon
- The **focused Pokémon** (highlighted in UI, selected via hotkey, cycling, or a targeting item) receives orders directly.  
- Orders can cascade to the entire party via **macro commands** (e.g., “All Follow”).  

### 2. Wandering/Base Pokémon
- Introduce a **Command Post Block** (or “Command Post”):  
  - Pokémon stored here are **released into the world** to freely wander a defined area (your base, farm, town).  
  - Trainers can issue orders to them **while they roam**, useful for chores (resource collection, guarding, etc.).  
  - Acts like a **stable or workstation** — Pokémon return to it when idle.  

### 3. Order-Issuing Item
- A new tool, e.g. **Trainer’s Whistle / Command Rod / Pokécomm Device**, lets the trainer:
  - **Select a specific Pokémon** in the world to receive the next order.  
  - Quick-target by pointing at the Pokémon and right-clicking.  
  - Opens the radial/stratagem menu when used, or accepts input codes.  
- When targeting **party Pokémon**, defaults to the current focus slot.  
- When targeting **wandering Pokémon**, the whistle links temporarily to them.  

---

## 🧩 Command Categories & Orders

### Positioning Orders
- **Follow** → Stay within radius of trainer.  
- **Hold Position** → Stay exactly at commanded location.  
- **Patrol** → Circle within a set radius, engaging hostiles.  
- **Move To** → Relocate to specific coordinates/waypoint.  
- **Regroup** → All Pokémon return to trainer immediately.  

### Combat Orders
- **Attack Target** → Focus offensive moves on designated enemy Pokémon/NPC.  
- **Guard Target** → Protect trainer, ally, or objective from attacks.  
- **Suppress Area** → Attack enemies entering a defined zone.  
- **Defend Objective** → Hold position and intercept approaching threats.  
- **Disengage** → Retreat and avoid combat.  

### Support & Utility Orders
- **Assist Ally** → Prioritize healing/support moves on target Pokémon.  
- **Interact (Field Ability)** → Use Pokémon’s field skill (Cut, Dig, Rock Smash, Flash, etc.).  
- **Scout** → Move ahead and alert trainer to hostiles or points of interest.  
- **Transport** → Carry items, Pokémon, or NPCs between points.  
- **Illuminate** → Pokémon emits light (e.g., Flash, Glow) for dark areas.  

### Resource Collection Orders
- **Forage Plants** → Pick berries, herbs, mushrooms, etc.  
- **Mine Ores** → Break stone/ore blocks with moves (Rock Smash, Metal Claw).  
- **Fish/Surf Hunt** → Retrieve fish or water items.  
- **Woodcutting** → Fell/log trees using cutting moves.  
- **Harvest Crops** → Collect grown crops in farms.  
- **Loot Items** → Pick up dropped items in an area.  
- **Auto-Collect** → Continuously grab nearby items and bring them to trainer.  

### Storage & Logistics Orders
- **Deposit in Container** → Move items into a chest/barrel/shulker.  
- **Retrieve from Container** → Fetch specific items when commanded.  
- **Supply Chain** → Pokémon maintains back-and-forth trips between two points (e.g., mine → base chest).  
- **Organize Inventory** → Auto-sort trainer’s or chest inventory by item type.  
- **Escort Cargo** → Defend another Pokémon carrying items.  

### Environmental Orders
- **Clear Path** → Remove grass, leaves, snow, or obstacles.  
- **Build/Place Blocks** → Pokémon places carried blocks at designated coordinates.  
- **Terraform (Minor)** → Flatten small area, dig shallow trenches, fill holes.  
- **Signal Point** → Pokémon marks a location for trainer/minimap.  

---

## ⚙️ Block & Item Mechanics

### Command Post Block
- **Placement**: Crafted or given by NPCs; placed in base.  
- **Functions**:
  - Stores multiple Pokémon (like PC Box but physical).  
  - Defines a **wandering radius** — Pokémon roam freely within.  
  - Acts as a **command hub**: right-clicking the block lets you select and issue orders to stationed Pokémon.  
  - Pokémon idle animations = eating, napping, playing.  

- **Upgrades**:
  - Expand wandering radius.  
  - Assign different “roles” (Farmer, Guard, Worker).  

### Trainer’s Whistle / Pokécomm Device
- **Default Mode**: Point + right-click selects Pokémon.  
- **Order Mode**: Hold to open radial menu or input stratagem code.  
- **Party Focus Shortcut**: Scroll/cycle through active party Pokémon.  
- **Feedback**:  
  - Beep/visual confirmation when linked.  
  - Selected Pokémon’s icon glows in party UI.  

---

## ⚙️ System Mechanics

- **Execution Radius**: Pokémon obey orders within X blocks of trainer.  
- **Persistence**: Some orders are one-time (e.g., Attack Target), others ongoing (Follow, Patrol, Auto-Collect).  
- **AI Priority**: Pokémon queue actions and resolve conflicts (Guard > Follow > Idle).  
- **Loyalty/Personality Modifiers**:  
  - Timid Pokémon may refuse "Attack Target".  
  - Careful Pokémon may over-gather resources before returning.  
- **Feedback System**:  
  - Icons hover above Pokémon to show current order (⚔️ attack, 🛡️ guard, 📦 carrying, 🌱 foraging).  
  - Map markers for patrol/resource collection zones.  

---

## 🧠 Example Gameplay Flow

1. Trainer places a **Command Post Block** in base.  
   - Releases Machoke, Bulbasaur, and Arcanine.  
   - Sets Machoke to “Mine Ore”, Bulbasaur to “Harvest Crops”, Arcanine to “Patrol Base”.  

2. In the wild, trainer cycles to focused party Pokémon (Gengar).  
   - Uses **Trainer’s Whistle** to open the order wheel.  
   - Issues “Scout Ahead” → Gengar glides forward, checking for threats.  

3. Another order: “All Regroup!” macro → all active Pokémon converge on the trainer.  

---

## 🔮 Expansion Paths
- **Multiple Pastures**: Specialized hubs (Farm Pasture, Guard Tower, Quarry Post).  
- **Pokémon Synergy**: Orders enhanced if certain Pokémon types are assigned (e.g., Grass-types improve farming).  
- **Trainer Progression**: Unlock more complex orders via badges/quests.  
- **NPC Integration**: Villagers/NPC trainers can use their own Command Post Blocks to make towns feel alive.  


---

## ✅ Implementation Specs (Per Order)
This section turns the high-level order list into concrete implementation notes that match the current codebase (selection brush, radial wheel, target/position selection clients, C2S packets, and the Brain bridge via TossOrderActivity). Use this to guide incremental development.

### Legend
- UI: Where it sits in the wheel and how it’s selected
- Input: Targeting/position flow after choosing from the wheel
- Packet: Network message (current or proposed)
- Server: What happens on arrival (selection set → per-Pokémon orders)
- Brain mapping (Now): How TossOrderActivity maps to memories today
- Brain mapping (Planned): Future first-class tasks/memories
- Complete when: How/when the order finishes
- Notes: Caveats and follow-ups

---

### Positioning

#### Follow (Party macro / single later)
- UI: Position category (Left) — entry: “Follow” (to be added)
- Input: No extra targeting for MVP. Uses the issuing player as anchor.
- Packet: IssuePosOrderC2S (reuse) or a small IssueSimpleOrderC2S(FOLLOW) packet
- Server: For each selected Pokémon → TacticalOrder.follow(anchor=player, radius=3–5)
- Brain mapping (Now):
  - Clear ATTACK_TARGET
  - BehaviorUtils.setWalkAndLookTargetMemories(anchor, speed=1.1, completionRadius=3–5)
- Brain mapping (Planned):
  - Memory: SH_TOSS_ANCHOR=player, SH_TOSS_ORDER=FOLLOW, SH_TOSS_ORDER_RADIUS=r
  - Task: MaintainFollowTask keeps near anchor, reasserts walk target
- Complete when: Persistent until canceled or replaced
- Notes: Teleport failsafe can remain Cobblemon’s domain

#### Regroup (macro)
- UI: Utility (Right) — “Regroup to Me” (already wired to MOVE_TO at player)
- Input: None; uses player BlockPos
- Packet: IssuePosOrderC2S(MOVE_TO, pos=player.pos, radius=2.0, persistent=false)
- Server: Already implemented; each selected Pokémon issued MOVE_TO with small radius
- Brain mapping (Now): Walk target near player
- Complete when: They arrive within radius once
- Notes: Consider clearing ATTACK_TARGET when regrouping

#### Move To
- UI: Position (Left) — “Move To” (wired)
- Input: Ground position selection (PositionSelectionClient)
- Packet: IssuePosOrderC2S(MOVE_TO, pos, radius=2.0, persistent=false)
- Server: Implemented
- Brain mapping (Now): WALK_TARGET to pos, completionRange=1–2
- Complete when: Close enough; order is one-shot

#### Hold Position
- UI: Position (Left) — “Hold Position” (wired)
- Input: Ground position selection (PositionSelectionClient)
- Packet: IssuePosOrderC2S(HOLD_POSITION, pos, radius=2.5, persistent=true)
- Server: Implemented
- Brain mapping (Now): WALK_TARGET to pos with completionRange=radius; reassert periodically
- Planned: SH_TOSS_ORDER=HOLD, SH_TOSS_ORDER_RADIUS, MaintainHoldTask
- Complete when: Canceled or replaced

#### Patrol (radius)
- UI: Position (Left) — entry: “Patrol” (to be added)
- Input: Ground position selection → then choose radius (later), MVP uses default 6
- Packet: IssuePosOrderC2S(HOLD_POSITION with metadata) or new IssueAreaOrderC2S(PATROL, pos, radius)
- Server: TacticalOrder.patrol(center, radius)
- Brain mapping (Now): Re-choose small random WALK_TARGETs around center periodically
- Planned: SH_TOSS_ORDER=PATROL + MaintainPatrolTask
- Complete when: Persistent until canceled
- Notes: Engage hostiles if nearby (vanilla ATTACK_TARGET rules continue to apply)

---

### Combat

#### Attack Target
- UI: Combat (Top) — “Attack” (wired)
- Input: Target selection mode (TargetSelectionClient); highlights viable Pokémon in red
- Packet: IssueTargetOrderC2S(ATTACK_TARGET, entityId) (implemented)
- Server: For each selected → TacticalOrder.attack(targetUUID)
- Brain mapping (Now):
  - Set ATTACK_TARGET=LivingEntity
  - Set CobblemonMemories.ATTACK_TARGET_DATA= CobblemonAttackTargetData()
  - Clear WALK_TARGET
- Complete when: Target dead/invalid or order canceled/replaced

#### Guard Target
- UI: Combat (Top) — “Guard” (wired)
- Input: Target selection mode; select ally/entity to protect
- Packet: IssueTargetOrderC2S(GUARD_TARGET, entityId) (implemented)
- Server: TacticalOrder.guard(targetUUID, radius≈6, persistent=true)
- Brain mapping (Now): WALK/LOOK around anchor with completionRange=radius, clear ATTACK_TARGET
- Complete when: Persistent; can opportunistically fight attackers

#### Disengage
- UI: Combat (Top) — “Disengage” (entry present but not fully wired)
- Input: None
- Packet: CancelOrdersC2S or new IssueSimpleOrderC2S(DISENGAGE)
- Server (MVP): Clear current order for selected; erase ATTACK_TARGET/ATTACK_TARGET_DATA; optionally set temporary AVOID_TARGET=attacker
- Brain mapping (Now): Clear ATTACK_TARGET and WALK_TARGET to return to idle
- Complete when: Immediate; may set brief AVOID behavior if implemented later

#### Suppress Area (AoE deny)
- UI: Combat (Top) — “Suppress Area” (to be added)
- Input: Ground position selection; radius default 6
- Packet: IssueAreaOrderC2S(SUPPRESS_AREA, pos, radius)
- Server: TacticalOrder.suppressArea(center, radius)
- Brain mapping (Now): Patrol-like maintain inside ring and attack intruders when seen; set ATTACK_TARGET when enemies within radius; otherwise loiter
- Planned: Custom sensor to detect hostile-in-zone, task to orbit/face edges
- Complete when: Canceled or expired

#### Defend Objective
- UI: Combat (Top) — “Defend Objective” (to be added)
- Input: Either select an entity (like Guard) or a position; MVP uses position
- Packet: IssueAreaOrTargetOrderC2S(DEFEND_OBJECTIVE, target/pos, radius)
- Server: TacticalOrder.defend(center/anchor, radius)
- Brain mapping: HOLD with aggressive reaction; same memories as Guard + attack any hostile within radius
- Complete when: Canceled

---

### Support & Utility

#### Assist Ally
- UI: Utility (Right) — “Assist Ally” (to be added)
- Input: Target Pokémon selection
- Packet: IssueTargetOrderC2S(ASSIST_ALLY, targetId)
- Server: TacticalOrder.assist(targetUUID)
- Brain mapping (Now): Not available by default. MVP: follow/guard anchor and prefer support moves if Cobblemon exposes hooks; otherwise placeholder to stay near
- Planned: Task to bias move selection toward status/heal
- Complete when: Canceled or target fully healed/out of combat

#### Interact (Field Ability)
- UI: Utility (Right) — “Field Ability” (to be added; context-sensitive)
- Input: Target block (raycast) or position
- Packet: IssueBlockOrderC2S(INTERACT, pos, abilityType)
- Server: Validate species/ability → start task
- Brain mapping: New task to run ability animation + block interaction

#### Scout
- UI: Utility (Right) — “Scout” (present in enum; add to wheel)
- Input: Directional cone or simple “ahead” with range; MVP: MoveTo a point 12–20 blocks forward
- Packet: IssuePosOrderC2S(MOVE_TO, posForward, radius=2, persistent=false) with SCOUT tag in ATTACK_TARGET_DATA (optional)
- Server: Move ahead; set temporary memory to ping back if hostiles seen
- Brain mapping: WALK_TARGET forward; sensing handled by existing sensors
- Complete when: Reached; optionally returns/Regroup automatically

#### Transport / Illuminate (future hooks)
- Define stubs similar to Interact; require new tasks/memories

---

### Resource Collection (MVP outlines)
- Forage Plants / Harvest Crops / Mine Ores / Woodcutting / Loot Items / Auto-Collect
- UI: Utility or a dedicated “Work” submenu in the future
- Input: Area (center+radius) or anchor block (e.g., chest, farm row)
- Packets: IssueAreaOrderC2S(WORK_TYPE, pos, radius), IssueTargetBlockC2S(WORK_TYPE, blockPos)
- Server: TacticalOrder.work(type, pos, radius)
- Brain mapping (Now): Minimal wander inside area and interact heuristically
- Planned: Dedicated tasks that locate appropriate blocks/items and perform interactions, plus WANDER_CONTROL memory to disable unrelated seeks

---

### Storage & Logistics (MVP outlines)
- Deposit in Container / Retrieve from Container / Supply Chain / Organize / Escort Cargo
- UI: Likely through Pasture/Command Post UI or context submenu
- Input: Target container(s) and filters
- Packets: IssueContainerOrderC2S(orderType, fromPos, toPos, filter)
- Brain mapping: New tasks to path and transfer items; Escort uses Guard on carrier entity

---

### Environmental (MVP outlines)
- Clear Path / Build / Terraform / Signal Point
- Input: Area or blueprint positions
- Packets: IssueAreaOrderC2S or IssueBuildPlanC2S
- Brain mapping: New tasks for block interactions; Signal sets a marker entity or map ping

---

## Networking & Packets (Current vs Proposed)
- Current:
  - WhistleSelectionC2S(int[] entityIds) — client selection brush → server selection set
  - IssueTargetOrderC2S(TacticalOrderType, int entityId) — target-based orders (Attack/Guard)
  - IssuePosOrderC2S(TacticalOrderType, BlockPos pos, float radius, boolean persistent) — position orders (Move/Hold)
  - CancelOrdersC2S() — clears orders for current selection
- Proposed (for later):
  - IssueSimpleOrderC2S(TacticalOrderType) — for Follow/Disengage without extra data
  - IssueAreaOrderC2S(TacticalOrderType, BlockPos center, float radius)
  - IssueBlockOrderC2S(TacticalOrderType, BlockPos pos, optional payload)
  - IssueContainerOrderC2S(TacticalOrderType, BlockPos from, BlockPos to, optional filter)

---

## Brain Integration Summary
- Now (Bridge in TossOrderActivity):
  - ATTACK_TARGET set/cleared; ATTACK_TARGET_DATA provided
  - WALK_TARGET used for Move/Hold/Guard(follow-like)
  - Conflicts avoided by clearing the opposite memory family each tick
- Planned (First-class):
  - Memories: SH_TOSS_ORDER, SH_TOSS_ORDER_RADIUS, SH_TOSS_ANCHOR, SH_TOSS_AREA_CENTER
  - Tasks: InterpretTossOrder, MaintainHold, MaintainGuard/Follow, PatrolArea, SuppressArea, WorkGather, LogisticsMove
  - Behaviour configs can wire tasks to activities and make it data-driven

---

## UI Wheel Mapping (Current + To Add)
- Combat (Top): Attack ✓, Guard ✓, Disengage ◻, Suppress Area ◻, Defend Objective ◻
- Position (Left): Move To ✓, Hold Position ✓, Patrol ◻, Follow ◻
- Utility (Right): Regroup to Me ✓, Scout ◻, Assist Ally ◻, Interact (Field Ability) ◻
- Context (Bottom): Hold At Me ✓

Legend: ✓ implemented in code; ◻ planned in this doc

---

## Completion & Cancel Rules (Quick Reference)
- One-shot: Move To, Regroup, Scout (MVP)
- Persistent: Hold, Guard, Follow, Patrol, Suppress, Defend Objective
- Cancel hierarchy: Cancel/Disengage > New explicit order > Natural completion

---

## Next Steps (Dev Checklist)
1) Add Disengage to wheel and wire to CancelOrdersC2S (server clears ATTACK_TARGET/ATTACK_TARGET_DATA). 
2) Add Follow to wheel; implement IssueSimpleOrderC2S(FOLLOW) or reuse IssuePosOrder at player with persistent loiter. 
3) Add Patrol spec using IssueAreaOrderC2S; temporary behavior via periodic WALK_TARGETs around center. 
4) Add Suppress Area and Defend Objective specs as variations of Patrol/Hold with aggressive reaction. 
5) Add Scout as a simple forward MOVE_TO with return Regroup.
6) Introduce optional Simple/Area/Block order packets when needed.
7) Begin first-class Brain tasks (InterpretTossOrder + MaintainHold/Guard) and add lightweight SH_TOSS_* memories.

This document section is intended to remain ahead of code; keep it updated as new packets and tasks land.

---

## MVP Order Implementation Details (Concrete Todo per Order)

These items expand the “Next Steps” into exact actions to land each order with current systems.

### Disengage (Cancel current combat and clear orders)
- UI:
  - Combat (Top) → “Disengage” button.
- Input:
  - None.
- Packet:
  - Reuse CancelOrdersC2S for MVP; consider IssueSimpleOrderC2S(DISENGAGE) later for clarity.
- Server (on arrival):
  - For each entity in current selection:
    - Clear current TacticalOrder (if any).
    - Clear ATTACK_TARGET and ATTACK_TARGET_DATA memories.
    - Optionally clear WALK_TARGET to force idle; optionally set a brief AVOID behavior if/when available.
- Brain mapping (Now):
  - Returns Pokémon to idle; aggro resumes only via native sensors/AI.
- Completion:
  - Immediate.
- Notes:
  - If any entity is mid-move from a previous order (e.g., HOLD), the cancel should supersede it.

### Follow (Persistent loiter around player)
- UI:
  - Position (Left) → “Follow”.
- Input:
  - None; anchor is issuing player.
- Packet:
  - MVP A: IssueSimpleOrderC2S(FOLLOW).
  - MVP B (fallback): IssuePosOrderC2S(FOLLOW, pos=player.pos, radius=4.0, persistent=true) with anchor semantics implied by type.
- Server (on arrival):
  - For each selected Pokémon:
    - Create TacticalOrder.follow(anchor=playerUUID, radius=3–5).
    - Clear ATTACK_TARGET; set/refresh WALK_TARGET toward anchor if beyond radius.
- Brain mapping (Now):
  - Maintain WALK_TARGET near anchor; reassert periodically if exceeded.
- Completion:
  - Persistent until replaced/canceled.
- Notes:
  - Teleport failsafe remains under base game behavior for stuck cases.

### Patrol (Persistent area roam)
- UI:
  - Position (Left) → “Patrol”.
- Input:
  - Select ground center; default radius=6 for MVP.
- Packet:
  - IssueAreaOrderC2S(PATROL, center, radius) or temporarily IssuePosOrderC2S(PATROL, center, radius, persistent=true).
- Server (on arrival):
  - For each selected Pokémon:
    - TacticalOrder.patrol(center, radius).
    - Implementation: periodically pick random valid points within radius and set WALK_TARGET; clear ATTACK_TARGET unless hostiles seen.
- Brain mapping (Now):
  - “MaintainPatrol-lite”: timer-driven nudge to new patrol points; opportunistically attack if native sensors set ATTACK_TARGET.
- Completion:
  - Persistent until replaced/canceled.
- Notes:
  - Avoid picking unreachable points; bias to ground at Pokémon’s Y or nearest navigable.

### Suppress Area (Aggressive area denial)
- UI:
  - Combat (Top) → “Suppress Area”.
- Input:
  - Select ground center; default radius=6.
- Packet:
  - IssueAreaOrderC2S(SUPPRESS_AREA, center, radius).
- Server (on arrival):
  - For each selected Pokémon:
    - TacticalOrder.suppressArea(center, radius).
    - Behavior: roam within ring; if hostile enters radius and is visible, set ATTACK_TARGET; otherwise face edges/loiter.
- Brain mapping (Now):
  - Patrol-like movement + willingness to set ATTACK_TARGET when intruders detected.
- Completion:
  - Persistent until canceled or duration limit (optional) expires.
- Notes:
  - Future: custom sensor to detect intruders not currently targeted by others.

### Defend Objective (Hold with aggressive reaction)
- UI:
  - Combat (Top) → “Defend Objective”.
- Input:
  - MVP: position selection + radius; later allow entity target (ally/object).
- Packet:
  - IssueAreaOrTargetOrderC2S(DEFEND_OBJECTIVE, pos/target, radius).
- Server (on arrival):
  - For each selected Pokémon:
    - TacticalOrder.defend(centerOrAnchor, radius).
    - Behavior: act like HOLD inside radius; engage any hostile entering radius; resume defensive position after skirmish.
- Brain mapping (Now):
  - Maintain position within radius; set ATTACK_TARGET opportunistically; clear WALK_TARGET after fights to re-center.
- Completion:
  - Persistent until replaced/canceled.
- Notes:
  - If a target entity is used as the anchor, re-center on the anchor’s position periodically.

### Scout (One-shot forward probe)
- UI:
  - Utility (Right) → “Scout”.
- Input:
  - None post-selection; compute a forward point from player facing (distance 12–20; MVP=16).
- Packet:
  - IssuePosOrderC2S(MOVE_TO, posForward, radius=2.0, persistent=false) optionally tagged as SCOUT.
- Server (on arrival):
  - For each selected Pokémon:
    - MoveTo the computed point; optionally set a short-lived memory to notify/return if hostiles seen.
- Brain mapping (Now):
  - Simple WALK_TARGET; rely on existing sensors for detection.
- Completion:
  - One-shot on arrival; optionally auto-issue Regroup to return.

---

## Order Payload Schema (Draft)

This matrix clarifies minimal payloads per order type for the current MVP.

- FOLLOW
  - Simple: IssueSimpleOrderC2S(FOLLOW) → anchor=player, radius=default(4).
  - Alt: IssuePosOrderC2S(FOLLOW, player.pos, radius, persistent=true).
- MOVE_TO
  - IssuePosOrderC2S(MOVE_TO, pos, radius=~2, persistent=false).
- HOLD_POSITION
  - IssuePosOrderC2S(HOLD_POSITION, pos, radius=~2.5, persistent=true).
- PATROL
  - IssueAreaOrderC2S(PATROL, center, radius=~6).
- ATTACK_TARGET
  - IssueTargetOrderC2S(ATTACK_TARGET, entityId).
- GUARD_TARGET
  - IssueTargetOrderC2S(GUARD_TARGET, entityId) with implicit radius (≈6) on server.
- DISENGAGE
  - CancelOrdersC2S (MVP) or IssueSimpleOrderC2S(DISENGAGE).
- SUPPRESS_AREA
  - IssueAreaOrderC2S(SUPPRESS_AREA, center, radius=~6).
- DEFEND_OBJECTIVE
  - IssueAreaOrTargetOrderC2S(DEFEND_OBJECTIVE, posOrTarget, radius=~6).
- SCOUT
  - IssuePosOrderC2S(MOVE_TO, posForwardFromPlayer, radius=2, persistent=false), optional SCOUT tag.
- ASSIST_ALLY (planned)
  - IssueTargetOrderC2S(ASSIST_ALLY, entityId); server biases support behaviors.
- INTERACT (planned)
  - IssueBlockOrderC2S(INTERACT, blockPos, abilityType).

Notes:
- Where “Area” is used but the packet doesn’t yet exist, fallback to Pos + persistent semantics is acceptable for MVP.
- Radii listed are defaults; allow override in UI later.

---

## Wheel Wiring Checklist (Short)

- Combat (Top):
  - Attack ✓
  - Guard ✓
  - Disengage ☐ → wire to CancelOrdersC2S
  - Suppress Area ☐ → area select → IssueAreaOrderC2S(SUPPRESS_AREA)
  - Defend Objective ☐ → position select → IssueAreaOrTargetOrderC2S(DEFEND_OBJECTIVE)
- Position (Left):
  - Move To ✓
  - Hold Position ✓
  - Patrol ☐ → position select → IssueAreaOrderC2S(PATROL)
  - Follow ☐ → IssueSimpleOrderC2S(FOLLOW) or Follow-via-Pos fallback
- Utility (Right):
  - Regroup to Me ✓
  - Scout ☐ → compute forward pos → IssuePosOrderC2S(MOVE_TO)
  - Assist Ally ☐ → target select → IssueTargetOrderC2S(ASSIST_ALLY)
  - Interact (Field Ability) ☐ → block/pos select → IssueBlockOrderC2S(INTERACT)

Legend: ✓ implemented; ☐ to implement in this sprint.