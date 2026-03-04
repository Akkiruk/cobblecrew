# CobbleCrew Architectural Analysis

**Date:** 2026-03-03  
**Codebase:** `cobbleworkers/common/src/main/kotlin/akkiruk/cobblecrew/`  
**Total Lines (Kotlin only):** ~8,541 across 64 files  
**Plus:** 1 Java mixin (~123 lines)

---

## 1. High-Level Architecture

CobbleCrew is a **server-side only** Minecraft mod (Fabric + NeoForge via Architectury) that gives Cobblemon Pokémon autonomous jobs. Pokémon in **Pasture Blocks** or **player parties** scan for nearby work, navigate to targets, perform actions (harvest, produce, fight, etc.), and deposit results in containers.

### Layer Diagram

```
┌─────────────────────────────────────────────────────────────┐
│  Platform Layer (fabric/, neoforge/)                        │
│  └─ Server tick hooks, event registration, mod integration  │
├─────────────────────────────────────────────────────────────┤
│  Mixin Layer (PokemonPastureBlockEntityMixin.java)          │
│  └─ Injects into Cobblemon's pasture block entity tick      │
├─────────────────────────────────────────────────────────────┤
│  Entry Points                                               │
│  ├─ WorkerDispatcher (orchestrates per-Pokémon ticking)     │
│  └─ PartyWorkerManager (tracks sent-out party Pokémon)      │
├─────────────────────────────────────────────────────────────┤
│  Job System                                                 │
│  ├─ Worker interface                                        │
│  ├─ Base classes (BaseHarvester, BaseProducer, etc.)        │
│  ├─ DSL wrappers (GatheringJob, ProductionJob, etc.)        │
│  └─ Registry files (GatheringJobs, ComboJobs, etc.)         │
├─────────────────────────────────────────────────────────────┤
│  Profile & Dispatch                                         │
│  ├─ PokemonProfile (cached eligibility per Pokémon)         │
│  ├─ Priority system (COMBO > MOVE > SPECIES > TYPE)         │
│  └─ Importance system (CRITICAL > HIGH > STANDARD > LOW)    │
├─────────────────────────────────────────────────────────────┤
│  Infrastructure                                             │
│  ├─ CobbleCrewCacheManager (block target cache)             │
│  ├─ DeferredBlockScanner (pasture) / eager scan (party)     │
│  ├─ CobbleCrewNavigationUtils (unified claim system)        │
│  ├─ CobbleCrewInventoryUtils (container deposit/extract)    │
│  ├─ WorkerVisualUtils (arrival delay, particles, anims)     │
│  ├─ PathfindingBudget (server-wide A* cap: 12/tick)         │
│  └─ WorkSpeedBoostManager (support aura stacking)           │
├─────────────────────────────────────────────────────────────┤
│  Config                                                     │
│  ├─ CobbleCrewConfig (AutoConfig — general/party/debug)     │
│  ├─ JobConfig + JobConfigManager (per-job JSON files)       │
│  └─ Schema migration (auto-upgrade stale configs)           │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. The Job Lifecycle (End-to-End)

### 2.1 Pasture Pokémon Path

1. **Mixin hooks into Cobblemon's pasture block ticker** (`PokemonPastureBlockEntityMixin.java`). Every tick it calls:
   - `WorkerDispatcher.tickAreaScan(context)` — drives the deferred block scanner
   - Every 5 ticks (staggered by `blockPos.hashCode()`): iterates tethered Pokémon and calls `WorkerDispatcher.tickPokemon(context, entity)`

2. **Area Scan** (`DeferredBlockScanner`): Iterates `blocksScannedPerTick` (default 50) blocks per tick from a `BlockPos.stream()` iterator over the search volume. Classifies each block into `BlockCategory` buckets via `BlockCategoryValidators`. Results are staged, then atomically committed to `CobbleCrewCacheManager.replaceAllCategoryTargets()` when the iterator completes. Scans repeat on a cooldown (default 8 seconds).

3. **Profile Build** (`WorkerDispatcher.getOrBuildProfile`): On first tick (or if moves changed), builds a `PokemonProfile` from the Pokémon's moves (active + benched), types, species, and ability. Calls `matchPriority()` on every registered worker to sort into four priority buckets: COMBO, MOVE, SPECIES, TYPE. Cached by UUID.

4. **Job Selection** (`WorkerDispatcher.selectBestAvailableJob`): Iterates priority tiers (COMBO first). Within each tier, groups by `JobImportance` (CRITICAL → BACKGROUND), shuffles within same-importance, and returns the first job where `isAvailable()` returns true.

5. **Job Stickiness**: Once assigned, a job stays for at least 100 ticks (5 seconds) OR while it has active state / a claimed target. Prevents thrashing.

6. **Job Tick**: The Worker's `tick(context, entity)` runs. For a typical harvester:
   - **No target?** → Find closest valid block via cache, validate pathfinding (up to 3 attempts, capped by `PathfindingBudget`), claim it
   - **Has target?** → Navigate, renew claim, wait for `WorkerVisualUtils.handleArrival()` (30-tick delay with animation), then harvest
   - **Holding items?** → Find closest container with space, navigate, deposit with chest open/close animation
   - **Party mode?** → Skip container deposit, deliver directly to player inventory

7. **Idle Behavior**: If no jobs are available:
   - Try idle ground pickup (grab nearby dropped items → deposit/deliver)
   - Return to origin (pasture block) if not home
   - Wander randomly near origin after 30s idle
   - Play idle/bored animations

8. **Cleanup**: On recall/pasture break/disconnect, `cleanupPokemon()` cascades through all systems: active jobs, profiles, navigation claims, visual state, inventory utils.

### 2.2 Party Pokémon Path

1. `PartyWorkerManager.init()` subscribes to Cobblemon events: `POKEMON_SENT_POST`, `POKEMON_RECALL_POST`, `BATTLE_STARTED_PRE`, `BATTLE_VICTORY`, `BATTLE_FLED`.

2. When a Pokémon is sent out (non-battle, non-tethered), it's registered as a `PartyWorkerEntry`.

3. `PartyWorkerManager.tick(server)` is called every server tick (from Fabric's `END_SERVER_TICK` / NeoForge equivalent).

4. **Eager scan** (instead of deferred): Synchronously scans all blocks in a small radius (default 6h × 3v) around the player. Only runs every `scanIntervalTicks` (default 40) and only if the player moved ≥3 blocks from the last scan origin.

5. Uses the same `WorkerDispatcher.tickPokemon()` as pasture workers, but with a `JobContext.Party` that:
   - Provides `player` reference for direct item delivery
   - Has a moving `scanOrigin` that pins between scans for cache stability
   - Skips pathfinding validation (small radius = everything reachable)

6. **Battle handling**: All party workers for a battling player are force-recalled (items delivered, entities discarded, state cleaned).

7. **Distance leash**: If a party worker exceeds `maxWorkDistance` from the player, its job is reset and it navigates back. If beyond `teleportDistance`, it teleports.

---

## 3. Job Definition: DSL vs Manual

### 3.1 DSL System (95%+ of jobs)

Most jobs are defined declaratively using DSL classes in `jobs/dsl/`:

| DSL Class | Base Class | Count in Registry |
|-----------|-----------|-------------------|
| `GatheringJob` | `BaseHarvester` | ~38 |
| `ProductionJob` | `BaseProducer` | ~29 |
| `ProcessingJob` | `BaseProcessor` | ~10 |
| `PlacementJob` | `BasePlacer` | ~4 |
| `DefenseJob` | `BaseDefender` | ~7 |
| `SupportJob` | `BaseSupport` | ~10 |
| `EnvironmentalJob` | `Worker` (direct) | ~11 |

A typical DSL definition:

```kotlin
val LOGGER = GatheringJob(
    name = "logger",
    targetCategory = BlockCategory.LOG,
    qualifyingMoves = setOf("cut"),
    particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
    harvestOverride = { world, pos, _ -> treeHarvest(world, pos, ...).drops },
)
```

Each DSL class:
1. Extends the corresponding base class
2. Takes constructor params for all customizable behavior (lambdas for harvest/produce/transform/effect)
3. Registers default `JobConfig` with `JobConfigManager` in its `init` block
4. Delegates `isEligible()` and `matchPriority()` to shared `dslEligible()` / `dslMatchPriority()` functions
5. Overrides abstract methods by forwarding to the constructor lambdas

### 3.2 Eligibility (DslEligibility.kt)

Shared logic for all DSL jobs:

- **Standard job** (`isCombo = false`): Pokémon qualifies if it has ANY qualifying move → `MOVE` priority. Fallback: species name match → `SPECIES` priority.
- **Combo job** (`isCombo = true`): Pokémon must have ALL qualifying moves → `COMBO` priority. No species fallback.
- Config can override qualifying moves and fallback species at runtime.

### 3.3 EnvironmentalJob (Special Case)

`EnvironmentalJob` implements `Worker` directly (not via a base class) because it has a unique pattern: navigate → act on block → optionally stay for cooldown-based repeated actions (e.g., cauldron filling). It has its own `targets` and `lastActionTime` maps, and supports `shouldContinue` for multi-action sequences on the same block.

---

## 4. The Priority / Importance System

### Priority Tiers (how the Pokémon matched)

```
COMBO    — has ALL required moves for a combo job (highest)
MOVE     — has at least one qualifying move
SPECIES  — species is in the fallback species list
TYPE     — type matches (not currently used by DSL, reserved)
```

`WorkerDispatcher.selectBestAvailableJob()` iterates these in order, stopping at the first tier with an available job.

### Importance Tiers (how urgent the job is)

```
CRITICAL   — defense, healing, fire dousing
HIGH       — harvesting mature crops
STANDARD   — processing, production
LOW        — planting, growth acceleration
BACKGROUND — consolidation, cleanup
```

Within a priority tier, jobs are sorted by importance (ascending enum ordinal since it's a `SortedMap`), with same-importance jobs shuffled for fairness.

---

## 5. The Base Classes

### BaseHarvester (236 lines) — The workhorse
- State: `heldItemsByPokemon`, `failedDepositLocations`, `heldItemsSinceTick`
- `tick()`: If not holding items → `handleHarvesting()`. If holding → `handleDepositing()` (or `deliverToPlayer()` for party).
- Target finding: Uses `findReachableTarget()` (pasture) or `findClosestPartyTarget()` (party). Pasture mode validates via `PathfindingBudget` (max 3 attempts, marks unreachable for 10s).
- Overflow protection: Items held >5 minutes are force-dropped.
- Subclass hooks: `harvest()`, `isTargetReady()`, `harvestTool`, `arrivalParticle`, `arrivalTolerance`.
- `harvestWithLoot()`: Uses vanilla loot context for drops.

### BaseProducer (88 lines)
- Pattern: cooldown → produce → hold → deposit
- `WorkSpeedBoostManager.adjustCooldown()` applies support auras
- No target block needed

### BaseProcessor (122 lines)
- State machine: `IDLE → NAVIGATING_INPUT → DEPOSITING`
- Finds barrel with matching input → navigates → extracts → transforms → deposits

### BasePlacer (122 lines)
- State machine: `IDLE → FETCHING → NAVIGATING_PLACEMENT`
- Finds container with item → extracts → finds placement pos → places block

### BaseDefender (95 lines)
- Scans for hostile mobs (throttled to every 20 ticks)
- Claims mob targets (unified claim system), navigates, applies effect

### BaseSupport (134 lines)
- Finds players without the target status effect
- Navigates, applies effect, enters cooldown (half effect duration, min 10s)
- `workBoostPercent`: Registers aura with `WorkSpeedBoostManager` while active

---

## 6. The Config System

### Two-Layer Architecture

**Layer 1: AutoConfig** (`CobbleCrewConfig.kt`, 88 lines)
- `GeneralGroup`: scan speed, search radius/height, blacklist durations
- `PartyGroup`: enabled, scan interval, search radius, max distance, teleport
- `DebugGroup`: master toggle + per-category toggles + species filter
- Accessed via `CobbleCrewConfigHolder.config` (lateinit, set during init)

**Layer 2: Per-Job JSON** (`JobConfigManager.kt`, 136 lines)
- Each job category (gathering, production, etc.) has a JSON file under `config/cobblecrew/`
- `JobConfig` data class: `enabled`, `cooldownSeconds`, `qualifyingMoves`, `fallbackSpecies`, plus optional overrides (`replant`, `effectDurationSeconds`, `radius`, `burnTimeSeconds`, etc.)
- DSL jobs register defaults via `JobConfigManager.registerDefault()` in their `init` block
- On `load()`: missing files are auto-generated, existing files are merged with new defaults
- Schema versioning: `schemaVersion` field. When bumped in code, stale configs auto-migrate (moves/species refreshed from code defaults, other settings preserved).
- Runtime commands: `setEnabled()`, `reload()`, `allJobsByCategory()`

### Config Migration
`CobbleCrewConfigInitializer` handles one-time migration from the old `cobbleworkers` name to `cobblecrew` (copies both the AutoConfig JSON5 and the per-job config directory).

---

## 7. The Caching System

### Block Target Cache (`CobbleCrewCacheManager`)

- `ConcurrentHashMap<CacheKey, PastureCache>`
- `PastureCache`: one `MutableSet<BlockPos>` per `BlockCategory` (pre-initialized for all categories)
- `CacheKey`: sealed interface with `PastureKey(pos: BlockPos)` — extensible but currently only one variant
- Operations: `addTarget`, `getTargets`, `removeTarget`, `removeTargetGlobal`, `replaceAllCategoryTargets`
- Also caches structure locations for scout jobs (15min TTL)

### Deferred Block Scanner (`DeferredBlockScanner`, pasture mode)

- Iterates `BlockPos.stream(searchArea)` across the search volume
- Processes `blocksScannedPerTick` (default 50) blocks per tick
- Each block is classified against `BlockCategoryValidators` (a map of `BlockCategory → (Block, BlockState) → Boolean`)
- `requiresExposedFace` categories (underground blocks like ORE, STONE) require at least one non-opaque adjacent face
- Results staged per-scan, atomically committed on completion
- Scan cooldown prevents immediate rescan (default 8s)
- `neededCategories` is computed once from registered workers' `targetCategory` + `additionalScanCategories`, plus CONTAINER

### Eager Scan (party mode, in `PartyWorkerManager`)

- Synchronous: scans entire small volume (6×3 default) in one tick
- Only triggers every `scanIntervalTicks` AND only if player moved ≥3 blocks
- Uses same `classifyBlock()` and `replaceAllCategoryTargets()` as deferred scanner
- Old scan origin's cache is removed before creating new one

---

## 8. Navigation & Target Claiming

### Unified Claim System (`CobbleCrewNavigationUtils`, 312 lines)

Each Pokémon holds **at most one claim** (block, player, or mob). Three target types:

```kotlin
sealed interface Target {
    data class Block(val pos: BlockPos) : Target
    data class Player(val playerId: UUID) : Target
    data class Mob(val entityId: Int) : Target
}
```

Two maps maintain O(1) lookups:
- `pokemonToClaim: Map<UUID, Claim>` (forward: who's claiming what)
- `targetToPokemon: Map<Target, UUID>` (reverse: is this target claimed)

Key invariants:
- Claiming a new target **auto-releases** the previous claim
- Claims timeout after `timeoutTicks` (default 200 = 10s, mob claims = 600 = 30s)
- Expired claims are cleaned up every 20 ticks

### Blacklist / Grace System

- Recently expired block claims enter a **grace period** (configurable, default 3s) where they won't be re-claimed
- **Escalating blacklist**: Positions with repeated claim failures get longer blackouts:
  - 1 failure → `blacklistShortSeconds` (default 5s)
  - 2 failures → `blacklistMediumSeconds` (default 30s)
  - 3+ failures → `blacklistLongSeconds` (default 120s)

### Unreachable Cache

- `PathfindingBudget.tryPathfind()` caps synchronous A* pathfinds to 12 per server tick
- Failed pathfinds are cached in `unreachableCache` for 200 ticks (10s) per Pokémon-position pair
- `findReachableTarget()` tries up to 3 candidates, marking unreachable ones

### Navigation

- Pathfinding throttled to every 5 ticks per Pokémon (`PATHFIND_INTERVAL_TICKS`)
- Uses Cobblemon entity's `navigation.startMovingTo()` + `lookControl.lookAt()`

---

## 9. Party vs Pasture: Key Differences

| Aspect | Pasture | Party |
|--------|---------|-------|
| **Origin** | Fixed `BlockPos` (pasture block) | Player's position (updated per scan) |
| **Scan** | Deferred (50 blocks/tick, spread over many ticks) | Eager (full volume in 1 tick, every 40 ticks) |
| **Scan radius** | 8h × 5v (configurable) | 6h × 3v (configurable, smaller) |
| **Item deposit** | Find container → navigate → deposit | Deliver directly to player inventory |
| **Target finding** | Pathfind validation (budget-capped) | Skip pathfind validation (small radius) |
| **Lifecycle** | Mixin tick (5-tick interval) | Server tick event (every tick) |
| **Battle handling** | N/A (pasture Pokémon aren't in battles) | Force-recall all workers, clean state |
| **Distance leash** | N/A (tethered to pasture) | maxWorkDistance + teleportIfTooFar |
| **Context type** | `JobContext.Pasture` | `JobContext.Party` |

---

## 10. File Size Summary (Lines of Code)

### Top 10 Largest Files

| File | Lines |
|------|-------|
| `commands/CobbleCrewCommand.kt` | 946 |
| `jobs/registry/GatheringJobs.kt` | 460 |
| `utilities/CobbleCrewInventoryUtils.kt` | 406 |
| `jobs/WorkerDispatcher.kt` | 384 |
| `jobs/registry/ProductionJobs.kt` | 346 |
| `jobs/registry/ComboJobs.kt` | 353 |
| `jobs/PartyWorkerManager.kt` | 325 |
| `utilities/CobbleCrewNavigationUtils.kt` | 312 |
| `jobs/registry/EnvironmentalJobs.kt` | 287 |
| `jobs/registry/SupportJobs.kt` | 282 |

### By Directory

| Directory | Files | Total Lines |
|-----------|-------|-------------|
| `jobs/registry/` | 9 | 2,336 |
| `utilities/` | 14 | 1,477 |
| `jobs/` (non-registry, non-dsl) | 8 | 1,513 |
| `jobs/dsl/` | 8 | 545 |
| `config/` | 5 | 323 |
| `commands/` | 1 | 946 |
| `api/` | 1 | 232 |
| `cache/` | 3 | 151 |
| `enums/` | 4 | 138 |
| Other (top-level, interfaces, integration, network) | 7 | 235 |

---

## 11. Architectural Strengths

1. **Clean DSL pattern**: Adding a new gathering/production/defense job is 5-15 lines of declarative code. The DSL + base class hierarchy absorbs all boilerplate.

2. **Unified claim system**: One claim per Pokémon, auto-release on re-claim. Prevents multi-targeting and target leaks. Block/player/mob targets share the same infrastructure.

3. **Escalating blacklist**: Failed targets get progressively longer blackouts (5s → 30s → 120s), preventing pathfinding thrash on unreachable blocks.

4. **PathfindingBudget**: Hard cap of 12 A* pathfinds per server tick prevents lag spikes with many Pokémon.

5. **Two-layer config**: AutoConfig for global settings + per-job JSON for fine-grained tuning. Schema versioning auto-migrates stale configs.

6. **Profile caching**: Eligibility is computed once per Pokémon and cached. Only rebuilt if moves change. Eliminates O(N×M) per-tick eligibility checks.

7. **Deferred scanning**: Pasture scans spread over many ticks (50 blocks/tick). Party scans are synchronous but small radius and infrequent.

8. **Party/pasture polymorphism via `JobContext`**: Base classes handle both modes transparently. Party-specific behavior (direct delivery, skip pathfinding) is handled via `when (context)` checks.

9. **Work speed aura**: Support Pokémon provide multiplicative cooldown reductions to nearby workers, with diminishing returns (capped at 40%).

10. **Comprehensive debug logging**: Per-category toggles, species filter, verbose mode. Can isolate one Pokémon's behavior without drowning in logs.

---

## 12. Code Smells & Architectural Issues

### 12.1 State Fragmentation (HIGH)

`WorkerDispatcher` alone has **14 mutable maps** for tracking state:
```
activeJobs, profiles, jobAssignedTick, idleLogTick, idleSinceTick,
returningHome, lastReturnNavTick, lastWanderTick, lastPickupAttemptTick,
idlePickupClaimTick, idleHeldItems, idleFailedDeposits
```

Each base class adds 2-4 more maps. `CobbleCrewNavigationUtils` has 7. Every `cleanup()` must manually clear all of them. This is error-prone — missing a map in cleanup means a slow memory leak.

**Suggestion**: Group per-Pokémon state into a single data class (e.g., `PokemonWorkerState`) keyed by UUID. One map instead of fourteen.

### 12.2 God-Object Tendencies (MEDIUM)

- `WorkerDispatcher` (384 lines): orchestration + idle behavior + ground pickup + return-to-origin + wander + animation. The idle pickup logic alone (~80 lines) is a self-contained mini-job.
- `CobbleCrewInventoryUtils` (406 lines): container finding + depositing + extracting + chest animations + player delivery + cleanup. Mixes concerns.
- `CobbleCrewCommand` (946 lines): All commands in one file.

### 12.3 Duplicated Deposit/Deliver Pattern (MEDIUM)

Every base class (`BaseHarvester`, `BaseProducer`, `BaseProcessor`, `BasePlacer`) has nearly identical "am I in party mode → deliverToPlayer, else → handleDepositing" logic. This is repeated 5+ times across the codebase:

```kotlin
if (context is JobContext.Party) {
    CobbleCrewInventoryUtils.deliverToPlayer(context.player, heldItems, pokemonEntity)
    heldItemsByPokemon.remove(pokemonId)
    failedDepositLocations.remove(pokemonId)
    return
}
CobbleCrewInventoryUtils.handleDepositing(...)
```

**Suggestion**: Extract a `depositOrDeliver(context, pokemonId, items, pokemonEntity, heldMap, failedMap)` helper.

### 12.4 EnvironmentalJob Not Using a Base Class (LOW)

`EnvironmentalJob` implements `Worker` directly with its own `targets` and `lastActionTime` maps, while all other DSL classes extend base classes. This means it has its own `tick()` implementation (~50 lines) that partially duplicates navigation/arrival logic from `BaseHarvester`.

### 12.5 Stale Cache Entries (LOW)

Block cache entries are only cleaned up when:
- A harvester removes a target after harvesting
- A full rescan replaces all targets
- A pasture is broken

Between rescans, a block that was destroyed by a player or natural causes remains in cache until a worker claims it, finds it's air, and removes it. This is handled gracefully (early return) but adds unnecessary claim/release cycles.

### 12.6 Global Singleton State (LOW)

All managers (`WorkerDispatcher`, `CobbleCrewCacheManager`, `CobbleCrewNavigationUtils`, `PartyWorkerManager`, etc.) are Kotlin `object` singletons with mutable maps. This works fine for a single-server mod but makes unit testing impossible without reflection or test hooks.

### 12.7 `typeGatedMoves` Parameter Goes Unused (LOW)

Every DSL class accepts a `typeGatedMoves: Map<String, String>` parameter, but it's never read by any logic — `dslEligible()` and `dslMatchPriority()` don't reference it. Appears to be a planned feature that was never wired up.

### 12.8 CacheKey Sealed Interface with One Variant (TRIVIAL)

`CacheKey` is a sealed interface with only `PastureKey`. The abstraction overhead (and many convenience overloads that wrap `BlockPos` → `PastureKey`) suggests a planned second variant (e.g., `PlayerKey`) that was abandoned when `Party` context reuses `PastureKey` with the scan origin.

### 12.9 Command File Size (LOW)

`CobbleCrewCommand.kt` at 946 lines is the single largest file. Could be split into subcommand files (status, config, debug, etc.).

---

## 13. Summary Statistics

| Metric | Count |
|--------|-------|
| Total Kotlin files | 64 |
| Total Kotlin LoC | ~8,541 |
| Java mixin files | 1 (123 lines) |
| Worker interface methods | 11 |
| Base classes | 6 |
| DSL classes | 8 (7 category-specific + DslEligibility) |
| Registry files | 9 |
| Registered jobs (approximate) | ~129 |
| Enums | 4 (BlockCategory: 48 values, WorkerPriority: 4, JobImportance: 5, WorkPhase: 17) |
| Utility objects | 14 |
| Config groups | 3 (general, party, debug) |
| Per-job config fields | 17 |
| UUID-keyed mutable maps (estimated) | ~40+ across all objects |
