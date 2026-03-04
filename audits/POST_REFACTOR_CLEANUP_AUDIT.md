# CobbleCrew Post-Refactor Cleanup Audit

**Generated after Phases 0–7 refactor**  
**Scope:** Every `.kt` source file under `common/src/main/kotlin/akkiruk/cobblecrew/`

---

## Legend

| Symbol | Meaning |
|--------|---------|
| 🔴 | **Dead code** — delete entirely |
| 🟠 | **Stale remnant** — leftover from pre-refactor, partially dead |
| 🟡 | **Consolidation opportunity** — works but should be merged/moved |
| 🟢 | **Clean** — no action needed |
| ⚪ | **Minor nit** — optional cleanup |

---

## 1. DEAD CODE (delete entirely)

### 🔴 `utilities/CobbleCrewMoveUtils.kt` (~55 lines)
- Uses reflection to access Cobblemon's `Moves` registry, but `validateMoves()` never validates individual worker moves.
- Comment in file acknowledges: "full validation requires workers to expose their move sets."
- **Zero callers.** `CobbleCrewMoveUtils.` appears nowhere outside its own file.
- **Action:** Delete file.

### 🔴 `utilities/CobbleCrewTags.kt` (~55 lines)
- Defines `Blocks.APRICORNS`, `BERRIES`, `MINTS`, `DIRT`, `SMALL_FLOWERS`, etc.
- `BlockCategoryValidators.kt` uses **hardcoded block sets** instead of these tags.
- **Zero callers.** `CobbleCrewTags` appears nowhere outside its own file.
- **Action:** Delete file. If tags are ever reintroduced, rebuild from scratch to match validators.

### 🔴 `utilities/PathfindingBudget.kt` (~40 lines)
- Defines `tryPathfind()` throttle.
- **Zero callers.** `PathfindingBudget` appears nowhere outside its own file.
- **Action:** Delete file.

### 🔴 `utilities/CobbleCrewNavigationUtils.kt` — mob targeting methods (lines ~135-138)
```kotlin
fun claimMobTarget(pokemonId: UUID, entityId: Int, world: World) {}
fun releaseMobTarget(pokemonId: UUID) {}
fun getMobTarget(pokemonId: UUID): Int? = null
fun isMobTargeted(entityId: Int): Boolean = false
```
- All no-ops / return null/false. Mob targeting now goes through `ClaimManager` + `Target.Mob`.
- **Zero callers.**
- **Action:** Delete these 4 methods.

### 🔴 `utilities/CobbleCrewDebugLogger.kt` — mob target debug methods (lines ~151-157)
```kotlin
fun mobTargetClaimed(species: String?, pokemonId: UUID, entityId: Int)
fun mobTargetReleased(pokemonId: UUID)
```
- Paired with the dead NavigationUtils mob methods above.
- **Zero callers.** No code calls `mobTargetClaimed` or `mobTargetReleased`.
- **Action:** Delete these 2 methods.

### 🔴 `utilities/CobbleCrewInventoryUtils.kt` — V1 `handleDepositing()` + old UUID maps
- **`handleDepositing()`** (lines ~276-387): Takes external `MutableMap<UUID, ...>` parameters — classic pre-refactor signature.
- **Zero callers.** Only `handleDepositingV2()` is used (called via `DepositHelper`).
- **3 orphan maps** only used by V1:
  - `depositArrival: mutableMapOf<UUID, Long>()` (line 50)
  - `depositRetryTimers: mutableMapOf<UUID, Long>()` (line 55)
  - `lastDepositWarning: mutableMapOf<UUID, Long>()` (line 59)
- These duplicate `PokemonWorkerState.depositArrivalTick` / `lastDepositWarning` which V2 uses.
- `cleanupPokemon()` still cleans these old maps — that section can be trimmed.
- **Action:** Delete `handleDepositing()`, the 3 maps, and their cleanup calls.

### 🔴 `state/PokemonWorkerState.kt` — defense cache fields (lines 68-69)
```kotlin
var cachedHostileIds: List<UUID> = emptyList()
var lastHostileScan: Long = 0L
```
- **Zero readers/writers** outside the declaration. `DefenseJob` uses `Target.Mob` from `ClaimManager`, not these fields.
- **Action:** Delete both fields.

---

## 2. STALE REMNANTS (partially dead / facade code)

### 🟠 `utilities/CobbleCrewNavigationUtils.kt` (~130 lines total)
- **Explicitly documented** as "backward-compatible facade over ClaimManager." Comment says: "New code should use ClaimManager directly."
- Still actively called by:
  - `WorkerDispatcher` — `navigateTo()`, `isPokemonAtPosition()`, `cleanupPokemon()` (idle behavior: wander, pickup, return-to-origin)
  - `PartyWorkerManager` — `navigateTo()` (teleport-to-player fallback)
  - `CobbleCrewInventoryUtils` V1 — `navigateTo()`, `isPokemonAtPosition()` (inside dead handleDepositing)
  - `CobbleCrewCauldronUtils` — `isRecentlyExpired()`
  - `EnvironmentalJobs` — `isTargeted()`, `isRecentlyExpired()` (FrostFormer, ObsidianForge, GrowthAccelerator, all cauldron fillers, FurnaceFueler, BrewingStandFueler, FireDouser, CropIrrigator, BeePollinator)
  - `WorkerVisualUtils` — `isPokemonAtPosition()`, `isPokemonNearPlayer()`
- **After deleting V1 handleDepositing**, only these remain:
  - `navigateTo()` — thin wrapper around Cobblemon's goal system
  - `isPokemonAtPosition()` — distance check
  - `isPokemonNearPlayer()` — distance check for SupportJob
  - `isTargeted()` — delegates to `ClaimManager.isClaimed()`
  - `isRecentlyExpired()` — delegates to `ClaimManager.isRecentlyExpired()`
  - `cleanupPokemon()` — delegates to `ClaimManager.releaseAll()`
- **Action (two options):**
  1. **Inline & delete** — move `navigateTo`/`isPokemonAtPosition`/`isPokemonNearPlayer` into a small `MovementUtils` and migrate callers of `isTargeted`/`isRecentlyExpired`/`cleanupPokemon` to `ClaimManager` directly. Then delete this file.
  2. **Rename to `MovementUtils`** — strip all ClaimManager facades, keep only the 3 movement helpers. Update callers.

### 🟠 `utilities/CobbleCrewInventoryUtils.kt` — file size
- Currently **554 lines**. After deleting V1 code + 3 maps + their cleanup: drops to ~400 lines.
- Still contains: `findNearestContainer()`, `tryInsertAll()`, `handleDepositingV2()`, `findInputContainer()`, `extractInputItems()`, plus compatibility sets for mod containers.
- **Action:** After V1 removal, consider splitting: container discovery vs. deposit logic.

### 🟠 `utilities/WorkerAnimationUtils.kt` — `lastAnimationTick` map (line 30)
```kotlin
private val lastAnimationTick = mutableMapOf<UUID, Long>()
```
- `PokemonWorkerState` already has `lastAnimationTick: Long` (line 60). 
- The utility's map is used for animation cooldown throttling, while the state field exists but may be unused.
- **Action:** Migrate to read/write `PokemonWorkerState.lastAnimationTick` via `StateManager`, delete the local map. Update `cleanup()` to stop removing from local map.

---

## 3. CONSOLIDATION OPPORTUNITIES

### 🟡 `jobs/registry/ProductionJobs.kt` — `LootProducer` abstract class (lines 48-79)
- `LootProducer` is an abstract base class that `FishingLooter`, `PickupLooter`, `DiveCollector` extend.
- It's defined **inside the registry file** rather than in `jobs/dsl/`.
- **Action:** Extract `LootProducer` to `jobs/dsl/LootProducerJob.kt` alongside the other DSL base classes for consistency.

### 🟡 `CobbleCrewNavigationUtils` callers → `ClaimManager`
All these call sites use NavigationUtils as a facade when `ClaimManager` methods exist:
| Caller | Method | Should become |
|--------|--------|---------------|
| `EnvironmentalJobs` (11 jobs) | `isTargeted(pos, world)` | `ClaimManager.isClaimed(pos)` |
| `EnvironmentalJobs`, `CauldronUtils` | `isRecentlyExpired(pos, world)` | `ClaimManager.isRecentlyExpired(pos)` |
| `WorkerDispatcher.cleanupAll()` | `cleanupPokemon(id, world)` | `ClaimManager.releaseAll(id)` |
- **Action:** Update all callers to use `ClaimManager` directly. This accounts for ~10 call sites in EnvironmentalJobs + 1 in CauldronUtils + 1 in WorkerDispatcher.

### 🟡 `cache/CobbleCrewCacheManager.kt` — backward-compat aliases
- `removePasture()`, `getPastureStatus()`, `getPastureCount()` are explicitly marked as backward-compat aliases.
- **Callers:** Only `CobbleCrewCommand.kt` (3 call sites).
- **Action:** Update command to use the canonical method names, delete the aliases.

---

## 4. FILE-BY-FILE STATUS

### Root
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `CobbleCrew.kt` | ~32 | 🟢 | Clean init object |

### `api/`
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `CobbleCrewApi.kt` | 249 | 🟢 | Clean. Large `descriptions` map is useful for `/cobblecrew jobs info`. |

### `cache/`
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `CacheKey.kt` | 17 | 🟢 | Sealed interface, only `PastureKey` implementation |
| `CobbleCrewCacheManager.kt` | ~150 | ⚪ | Remove 3 compat aliases after updating CobbleCrewCommand |
| `PastureCache.kt` | 17 | 🟢 | Simple data class |

### `commands/`
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `CobbleCrewCommand.kt` | 1075 | ⚪ | Large but well-structured. Uses compat aliases from CacheManager. `buildMoveReport()` uses heavy reflection — works but fragile. Consider if `/cobblecrew moves` is worth maintaining. |

### `config/`
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `CobbleCrewConfig.kt` | ~105 | 🟢 | Clean AutoConfig class |
| `CobbleCrewConfigHolder.kt` | ~13 | 🟢 | Singleton holder |
| `CobbleCrewConfigInitializer.kt` | ~60 | 🟢 | Clean, includes old→new name migration |
| `JobConfig.kt` | ~35 | 🟢 | Universal per-job config data class |
| `JobConfigManager.kt` | ~160 | 🟢 | Clean JSON config management |

### `enums/`
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `BlockCategory.kt` | ~55 | 🟢 | Used extensively by scanner + validators |
| `JobImportance.kt` | ~30 | 🟢 | Used by Worker interface + DSL jobs |
| `JobPhase.kt` | ~28 | 🟢 | Used by BaseJob state machine |
| `WorkerPriority.kt` | ~24 | 🟢 | Used by Worker interface + PokemonProfile |
| `WorkPhase.kt` | ~45 | 🟢 | Used by animation + visual utils |

### `integration/`
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `CobbleCrewIntegrationHandler.kt` | ~65 | 🟢 | Clean. `addSophisticatedStorage()` is stubbed (TODO) — fine to keep. |
| `FarmersDelightBlocks.kt` | ~30 | 🟢 | Clean constant set |

### `interfaces/`
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `Worker.kt` | ~60 | ⚪ | `cleanup()` is now a no-op in BaseJob (StateManager handles everything). Could remove from interface, but it's harmless and keeps the contract explicit. |
| `ModIntegrationHelper.kt` | ~13 | 🟢 | Used by Fabric + NeoForge platform-specific implementations |

### `jobs/`
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `BaseJob.kt` | 325 | 🟢 | Clean state machine |
| `JobContext.kt` | ~46 | 🟢 | Sealed interface |
| `PartyWorkerManager.kt` | 391 | 🟢 | Internal maps are appropriate (party-level, not per-pokemon) |
| `PokemonProfile.kt` | ~86 | 🟢 | Clean profile data |
| `Target.kt` | ~38 | 🟢 | Sealed interface |
| `WorkerDispatcher.kt` | ~280 | 🟡 | Uses NavigationUtils facade for idle behavior — should migrate to ClaimManager + MovementUtils |
| `WorkerRegistry.kt` | ~52 | 🟢 | Simple worker list |

### `jobs/dsl/`
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `DefenseJob.kt` | ~115 | 🟢 | Clean |
| `DslEligibility.kt` | ~60 | 🟢 | Shared helper functions |
| `EnvironmentalJob.kt` | ~110 | 🟢 | Clean |
| `GatheringJob.kt` | ~97 | 🟢 | Clean |
| `PlacementJob.kt` | ~115 | 🟢 | Clean |
| `ProcessingJob.kt` | ~96 | 🟢 | Clean |
| `ProductionJob.kt` | ~82 | 🟢 | Clean |
| `SupportJob.kt` | ~155 | 🟢 | Clean |

### `jobs/registry/`
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `ComboJobs.kt` | ~300 | 🟢 | 18 combo jobs, clean DSL |
| `DefenseJobs.kt` | ~100 | 🟢 | 7 defense jobs |
| `EnvironmentalJobs.kt` | ~240 | 🟡 | Uses NavigationUtils facade — migrate to ClaimManager |
| `GatheringJobs.kt` | 505 | 🟢 | 30+ gathering jobs, clean |
| `LogisticsJobs.kt` | ~165 | 🟢 | 2 logistics jobs |
| `PlacementJobs.kt` | ~150 | 🟢 | 4 placement jobs |
| `ProcessingJobs.kt` | ~175 | 🟢 | 10 processing jobs |
| `ProductionJobs.kt` | ~220 | 🟡 | `LootProducer` base class should move to `dsl/` |
| `SupportJobs.kt` | ~260 | 🟢 | ScoutWorker's `pendingLookups` map is appropriate (async structure search) |

### `network/`
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `JobSyncPayload.kt` | ~40 | 🟢 | Clean S2C payload |
| `JobSyncSerializer.kt` | ~30 | 🟢 | Clean JSON serializer |

### `state/`
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `ClaimManager.kt` | 212 | 🟢 | Clean, well-structured |
| `PokemonWorkerState.kt` | ~90 | 🔴 | Delete `cachedHostileIds` + `lastHostileScan` (dead). Verify `lastAnimationTick` is being used by state consumers. |
| `StateManager.kt` | ~50 | 🟢 | Clean ConcurrentHashMap wrapper |

### `utilities/`
| File | Lines | Status | Notes |
|------|-------|--------|-------|
| `BlockCategoryValidators.kt` | ~150 | 🟢 | Clean, uses hardcoded blocks (not tags) |
| `CobbleCrewCauldronUtils.kt` | ~50 | 🟡 | Uses NavigationUtils facade — migrate |
| `CobbleCrewCropUtils.kt` | ~165 | 🟢 | Clean |
| `CobbleCrewDebugLogger.kt` | 279 | 🔴 | Delete 2 dead mob target methods |
| `CobbleCrewInventoryUtils.kt` | 554 | 🔴 | Delete V1 `handleDepositing()` + 3 orphan maps + cleanup calls |
| `CobbleCrewMoveUtils.kt` | ~55 | 🔴 | **Delete entire file** |
| `CobbleCrewNavigationUtils.kt` | ~130 | 🟠 | Facade — inline movement helpers, delete rest |
| `CobbleCrewTags.kt` | ~55 | 🔴 | **Delete entire file** |
| `DeferredBlockScanner.kt` | ~170 | 🟢 | Clean |
| `DepositHelper.kt` | ~80 | 🟢 | Clean, uses V2 |
| `FloodFillHarvest.kt` | ~160 | 🟢 | Clean |
| `PathfindingBudget.kt` | ~40 | 🔴 | **Delete entire file** |
| `WorkerAnimationUtils.kt` | ~80 | 🟠 | Migrate `lastAnimationTick` map to PokemonWorkerState |
| `WorkerVisualUtils.kt` | ~130 | 🟡 | Uses NavigationUtils — migrate to direct calls |
| `WorkSpeedBoostManager.kt` | ~95 | 🟢 | Clean, per-origin maps are appropriate |

---

## 5. `mutableMapOf<UUID, ...>` INVENTORY

Post-cleanup, only these should remain:

| Location | Map | Verdict |
|----------|-----|---------|
| `PartyWorkerManager` | `activePartyWorkers` | ✅ Appropriate (party-level tracking) |
| `PartyWorkerManager` | `playerContexts` | ✅ Appropriate |
| `PartyWorkerManager` | `lastScanTick` | ✅ Appropriate |
| `WorkerAnimationUtils` | `lastAnimationTick` | ❌ Migrate to PokemonWorkerState |
| `CobbleCrewInventoryUtils` | `depositArrival` | ❌ Delete (V1 only) |
| `CobbleCrewInventoryUtils` | `depositRetryTimers` | ❌ Delete (V1 only) |
| `CobbleCrewInventoryUtils` | `lastDepositWarning` | ❌ Delete (V1 only) |
| `SupportJobs.ScoutWorker` | `pendingLookups` | ✅ Appropriate (async structure search) |

---

## 6. SUMMARY & PRIORITY ORDER

### Phase 8A: Pure deletions (safe, no behavioral change)
1. Delete `CobbleCrewMoveUtils.kt`
2. Delete `CobbleCrewTags.kt`
3. Delete `PathfindingBudget.kt`
4. Delete `PokemonWorkerState.cachedHostileIds` + `lastHostileScan`
5. Delete `CobbleCrewNavigationUtils` mob targeting methods (4 methods)
6. Delete `CobbleCrewDebugLogger` mob target methods (2 methods)
7. Delete `CobbleCrewInventoryUtils.handleDepositing()` (V1) + 3 orphan maps + cleanup for those maps

### Phase 8B: Facade migration (behavioral equivalent, changes imports)
1. Migrate `EnvironmentalJobs` callers from `NavigationUtils.isTargeted()` → `ClaimManager.isClaimed()`
2. Migrate `EnvironmentalJobs` + `CauldronUtils` from `NavigationUtils.isRecentlyExpired()` → `ClaimManager.isRecentlyExpired()`
3. Migrate `WorkerDispatcher.cleanupAll()` from `NavigationUtils.cleanupPokemon()` → `ClaimManager.releaseAll()`
4. Migrate `WorkerAnimationUtils.lastAnimationTick` map → `PokemonWorkerState` field
5. Rename remaining `CobbleCrewNavigationUtils` to `MovementUtils` (keep only `navigateTo`, `isPokemonAtPosition`, `isPokemonNearPlayer`)

### Phase 8C: Structural moves (optional, improves organization)
1. Extract `LootProducer` from `ProductionJobs.kt` → `jobs/dsl/LootProducerJob.kt`
2. Update `CobbleCrewCommand` to use canonical CacheManager method names, delete compat aliases
3. Consider splitting `CobbleCrewInventoryUtils` (container discovery vs. deposit logic) — optional

### Estimated line reduction
- **Deleted files:** ~205 lines (MoveUtils 55 + Tags 55 + PathfindingBudget 40 + dead methods 55)
- **Deleted code in remaining files:** ~130 lines (V1 handleDepositing ~110 + maps/cleanup ~20)
- **Total:** ~335 lines removed, 3 files deleted, 0 new bugs

---

## 7. THINGS THAT LOOK SUSPICIOUS BUT ARE FINE

- **`Worker.cleanup()`** is a no-op in BaseJob — but the interface method is still called by `WorkerDispatcher` and `PartyWorkerManager` as a contract hook. Harmless to keep.
- **`Worker.getHeldItems()`** reads from StateManager — this is fine, it provides a public API for held items.
- **`CobbleCrewCommand.buildMoveReport()`** uses heavy reflection — fragile but only runs on-demand via `/cobblecrew moves`. Worth keeping for analysis.
- **`CobbleCrewIntegrationHandler.addSophisticatedStorage()`** is stubbed — fine, marked TODO.
- **`WorkSpeedBoostManager.boosts`** uses `mutableMapOf<BlockPos, ...>` — appropriate, tracks per-origin speed buffs.
- **`SupportJobs.ScoutWorker.pendingLookups`** — appropriate, caches async structure search results.
- **`FarmersDelightBlocks.MUSHROOMS`** set is defined but may not be used separately from `ALL` — harmless, kept for future granularity.
