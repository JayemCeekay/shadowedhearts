# Pokémon Tactical Order and Stratagem System

## 🎯 Overview
The Tactical Order and Stratagem System lets trainers issue **real-time commands** to Pokémon both in the overworld and at their bases. Orders can be directed at individual Pokémon, party members, or entire groups. This blends *RTS-style control*, *Helldivers-style stratagem input*, and **immersive world interaction** through blocks and items.

---

## 🧩 Command Issuing Methods

### 1. Party Pokémon
- The **focused Pokémon** (highlighted in UI, selected via hotkey, cycling, or a targeting item) receives orders directly.  
- Orders can cascade to the entire party via **macro commands** (e.g., “All Follow”).  

### 2. Wandering/Base Pokémon
- Introduce a **Pasture Block** (or “Command Post”):  
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

### Pasture/Command Post Block
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

1. Trainer places a **Pasture Block** in base.  
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
- **NPC Integration**: Villagers/NPC trainers can use their own Pasture Blocks to make towns feel alive.  
