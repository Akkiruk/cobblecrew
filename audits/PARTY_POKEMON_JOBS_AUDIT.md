# Party Pokémon Jobs — Implementation Audit

**Date:** 2026-02-22  
**Audited against:** `cobbleworkers/audits/PARTY_POKEMON_JOBS_PLAN.md`

---

## Phase 1: Core Abstraction Layer

| # | Item | Status | Details |
|---|------|--------|---------|
| 1 | `JobContext.kt` exists | ✅ DONE | Sealed interface with `Pasture` and `Party` data classes. `Party` has `pinnedOrigin`, `player`, dynamic `origin` getter. |
| 2 | `CacheKey.kt` exists | ✅ DONE | Sealed interface: `PastureKey(pos)` and `PlayerKey(playerId)`. |
| 3 | `Worker.kt` uses `tick(context: JobContext, ...)` | ✅ DONE | Line 45: `fun tick(context: JobContext, pokemonEntity: PokemonEntity)` |
| 4 | All 6 base classes converted | ✅ DONE | `BaseHarvester.kt` (L61), `BaseProducer.kt` (L45), `BaseProcessor.kt` (L55), `BasePlacer.kt` (L61), `BaseDefender.kt` (L52), `BaseSupport.kt` (L65) — all `override fun tick(context: JobContext, ...)` |
| 5 | DSL classes exist | ✅ DONE | All 6 DSL files exist: `GatheringJob.kt`, `ProductionJob.kt`, `ProcessingJob.kt`, `PlacementJob.kt`, `DefenseJob.kt`, `SupportJob.kt`. **However**: they do NOT reference `JobContext` directly — they build job definitions consumed by the base classes, so this is expected. |
| 6 | All 9 registry files use new signature | ✅ DONE | `EnvironmentalJobs.kt` (11 overrides), `LogisticsJobs.kt` (2 overrides), `ProductionJobs.kt` (4 overrides), `SupportJobs.kt` (1 override) all use `tick(context: JobContext, ...)`. GatheringJobs, ProcessingJobs, PlacementJobs, DefenseJobs, ComboJobs — these use inherited base class `tick()` so no override needed. **Zero occurrences of old `tick(world: World, origin: BlockPos` pattern remain.** |
| 7 | `WorkerDispatcher.kt` accepts `JobContext` | ✅ DONE | `tickAreaScan(context: JobContext)` (L31), `tickPokemon(context: JobContext, ...)` (L58), `returnToOrigin(entity, context: JobContext)` (L128). |
| 8 | `CobbleCrewCacheManager.kt` generalized to `CacheKey` | ❌ NOT DONE | Still keyed by `BlockPos`. Internal map is `MutableMap<BlockPos, PastureCache>`. All public methods (`addTarget`, `getTargets`, `removeTarget`, `removePasture`, etc.) take `pastureOrigin: BlockPos`. No reference to `CacheKey` anywhere. |
| 9 | `DeferredBlockScanner.kt` generalized to `CacheKey` | ❌ NOT DONE | `lastScanCompletion` is `MutableMap<BlockPos, Long>` (L34). `activeScans` is `MutableMap<BlockPos, ScanJob>` (L33). `isScanActive` takes `pastureOrigin: BlockPos` (L114). `tickPastureAreaScan` takes `pastureOrigin: BlockPos` (L56). No `CacheKey` or `JobContext` references. |
| 10a | `handleDepositing` signature | ❌ NOT DONE | Still takes `(world: World, origin: BlockPos, ...)` — not `(context: JobContext, ...)`. |
| 10b | `deliverToPlayer` exists | ✅ DONE | `CobbleCrewInventoryUtils.deliverToPlayer(player, items, pokemonEntity)` at L434. |
| 10c | `cleanupPokemon` exists | ✅ DONE | `CobbleCrewInventoryUtils.cleanupPokemon(pokemonId)` at L448. |
| 11 | Mixin files exist | ✅ DONE | Both `PokemonPastureBlockEntityMixin.java` and `FarmlandBlockMixin.java` exist in `common/src/main/java/akkiruk/cobblecrew/mixin/`. |

### Phase 1 Summary: **9/13 items complete**. CacheManager, DeferredBlockScanner, and handleDepositing not generalized to CacheKey/JobContext.

---

## Phase 2: Party Worker Manager

| # | Item | Status | Details |
|---|------|--------|---------|
| 1 | `PartyWorkerManager.kt` exists | ✅ DONE | Full object with `init()`, `tick(server)`, `PartyWorkerEntry` data class, event handlers (`onPokemonSentOut`, `onPokemonRecalled`, `onBattleStartPre`, `onBattleEnd`), zone transition logic, cleanup methods, held-item delivery on recall. |
| 2 | `CobbleCrew.kt` calls `PartyWorkerManager.init()` | ✅ DONE | Line 30: `PartyWorkerManager.init()` |
| 3 | Fabric entrypoint: server tick hook | ✅ DONE | `CobbleCrewFabric.kt` L65: `PartyWorkerManager.tick(server)` + L70: `PartyWorkerManager.cleanupPlayer(...)` on disconnect. |
| 4 | NeoForge entrypoint: server tick hook | ✅ DONE | `CobbleCrewNeoForge.kt` L55: `PartyWorkerManager.tick(it)` + L60: `PartyWorkerManager.cleanupPlayer(...)` on disconnect. |

### Phase 2 Summary: **4/4 items complete.**

---

## Phase 3: Party-Specific Behavior

| # | Item | Status | Details |
|---|------|--------|---------|
| 1 | `returnToOrigin` handles Party context | ✅ DONE | Line 128-141: `when (context)` branches for `Pasture` (navigate to origin) and `Party` (navigate to `context.player.blockPos`). |
| 2 | BaseHarvester party delivery | ✅ DONE | Lines 71-73: `if (context is JobContext.Party) { deliverToPlayer(context.player, heldItems, pokemonEntity) }` |
| 3 | BaseProducer party delivery | ✅ DONE | Lines 54-55: same pattern. |
| 4 | BaseProcessor party delivery | ✅ DONE | Lines 103-104: same pattern. |
| 5 | Reachability filtering (exposed-face, pathfind) | ❌ NOT DONE | No references to `exposedFace`, `reachability`, `pathfindValid`, or `canReach` in the codebase. |
| 6 | Max leash distance / work range enforcement | ⚠️ PARTIAL | `maxWorkDistance` config exists (default 32) but **is not referenced in WorkerDispatcher or any job code**. Only `teleportDistance` and `zoneTransitionDistance` are actively enforced in `PartyWorkerManager`. The config field exists but isn't wired up to constrain actual work target selection. |

### Phase 3 Summary: **4/6 items complete.** Reachability not implemented. maxWorkDistance config defined but not enforced.

---

## Phase 4: Configuration & Commands

| # | Item | Status | Details |
|---|------|--------|---------|
| 1 | `CobbleCrewConfig.kt` PartyGroup | ✅ DONE | Full `PartyGroup` class: `enabled`, `maxWorkDistance` (8-64, default 32), `zoneTransitionDistance` (24-96, default 48), `teleportIfTooFar` (default true), `teleportDistance` (32-128, default 64). |
| 2 | Command extensions for party workers | ❌ NOT DONE | `CobbleCrewCommand.kt` exists but has no references to `party` or `Party`. No subcommands to list/manage party workers. |

### Phase 4 Summary: **1/2 items complete.**

---

## Phase 5: Opt-in / Opt-out Mechanism

| # | Item | Status | Details |
|---|------|--------|---------|
| 1 | Per-player or per-Pokémon opt-in/opt-out | ❌ NOT DONE | No references to `optIn`, `optOut`, `partyOptIn`, or `partyToggle` anywhere. The only toggle is the global `party.enabled` config. |

### Phase 5 Summary: **0/1 items complete.**

---

## Overall Scorecard

| Phase | Done | Total | Pct |
|-------|------|-------|-----|
| Phase 1: Core Abstraction | 9 | 13 | 69% |
| Phase 2: Party Worker Manager | 4 | 4 | 100% |
| Phase 3: Party Behavior | 4 | 6 | 67% |
| Phase 4: Config & Commands | 1 | 2 | 50% |
| Phase 5: Opt-in/Opt-out | 0 | 1 | 0% |
| **Total** | **18** | **26** | **69%** |

---

## Key Gaps (Priority Order)

1. **CacheManager + DeferredBlockScanner still BlockPos-keyed** — Party workers currently work because `JobContext.Party.origin` returns a `BlockPos` (the player's position or pinned origin), so the existing `BlockPos`-keyed caches technically function. But this means two party workers for different players at the same position could collide, and it doesn't cleanly separate per-player vs per-pasture caches. The `CacheKey` sealed interface was created but never wired in.

2. **`handleDepositing` not context-aware** — Takes raw `(world, origin)` instead of `JobContext`. Party workers bypass this via `deliverToPlayer` in the base classes, so depositing works for party workers, but the function itself wasn't refactored.

3. **`maxWorkDistance` config not enforced** — Defined in config but never used to constrain target selection. Workers could theoretically pick targets arbitrarily far from the player.

4. **No reachability filtering** — No exposed-face checks or pathfinding validation for work targets.

5. **No party-specific commands** — Can't list or manage party workers via `/cobblecrew`.

6. **No per-player opt-in/opt-out** — Only global toggle via config.
