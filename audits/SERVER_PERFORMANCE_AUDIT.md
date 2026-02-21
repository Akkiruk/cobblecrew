# Cobbleworkers Server Performance Audit

**Date:** 2026-02-21

---

## Summary

**Overall: Low-to-moderate overhead.** The mod is well-architected with deferred scanning, profile caching, and cooldown-gated jobs. The main cost scales linearly with active pastures × tethered Pokémon. A server with a handful of pastures will barely notice; dozens of pastures with full Pokémon rosters could show measurable tick time increase.

**Estimated overhead per pasture (4 Pokémon, steady state):**
- ~0.05–0.15ms per server tick under normal conditions
- Spikes possible during initial area scan or `BlockPos.iterateOutwards` calls

---

## Per-System Breakdown

### 1. Mixin Tick Driver (PokemonPastureBlockEntityMixin)

**Hook:** Injects at tail of `PokemonPastureBlockEntity.TICKER`, runs **every server tick** (20/sec) for every loaded pasture block.

**Per pasture per tick:**
- 1× `tickAreaScan()` call
- N× `tickPokemon()` (one per tethered, non-fainted, non-sleeping Pokémon)
- Iterates `getTetheredPokemon()`, calls `getPokemon()` + entity lookup, data tracker read

**Cost:** Very cheap per-call. The iteration over tethered Pokémon is O(n) where n ≤ 4 (Cobblemon pasture limit). Main concern is that this runs every tick, not throttled.

**Rating:** ✅ Low

---

### 2. Deferred Block Scanner (DeferredBlockScanner)

**What it does:** Scans all blocks in the pasture's search area, classifying them into ~50 BlockCategory caches.

**Search volume:**  
Default `searchRadius=8`, `searchHeight=5` → Box of (8×2+1)³-ish = **17 × 11 × 17 = 3,179 blocks** per pasture.

**Throttling:**
- Processes `blocksScannedPerTick` (default: 15) blocks per tick per pasture
- Full scan takes ~212 ticks (10.6 seconds) to complete
- 60-second cooldown between scan completions (`SCAN_COOLDOWN_TICKS = 1200`)
- Skips unloaded chunks

**Per block scanned:**
- Checks `world.isChunkLoaded()` (cheap)
- Iterates all ~50 BlockCategory validators — each does `world.getBlockState(pos)` + block comparison
- That's ~50 `getBlockState` calls per block scanned (most are simple HashMap lookups from chunk sections)

**Cost per tick:** 15 blocks × ~50 validators = ~750 `getBlockState` calls per pasture. Each `getBlockState` is ~10-30ns. Total: ~7.5–22.5μs per pasture per tick.

**Problem:** All 50 validators run for every block regardless of whether those categories are in use. No short-circuiting.

**Rating:** ✅ Low (well-throttled, but could be optimized by only running validators for categories that active jobs need)

---

### 3. PokemonProfile (Eligibility Caching)

**What it does:** On first tick, builds a profile mapping the Pokémon's moves/types/species to eligible jobs. Cached until config reload or pasture re-entry.

**Cost:** One-time O(J) where J = number of registered workers (~90+). Each `isEligible()` check is pure string set operations — very fast.

**Rating:** ✅ Negligible — computed once, cached

---

### 4. WorkerDispatcher.tickPokemon

**Per Pokémon per tick:**
1. Profile lookup (HashMap get — O(1))
2. `bestEligible()` — returns first non-empty priority tier (O(1))
3. Checks current job's `hasActiveState()` + `getTarget()` — O(1) map lookups
4. If no active state: calls `isAvailable()` on each eligible job, then `random()` to pick one
5. Calls the selected job's `tick()` once

**Cost:** Dominated by the actual job's `tick()` — the dispatcher itself is ~O(1).

**Rating:** ✅ Low

---

### 5. Navigation System (CobbleworkersNavigationUtils)

**Per-Pokémon costs:**
- `navigateTo()` — throttled to once per 20 ticks (`PATHFIND_INTERVAL_TICKS`). Calls Minecraft's built-in `navigation.startMovingTo()` which triggers vanilla A* pathfinding.
- `releaseExpiredClaims()` — runs once per 20 ticks, iterates all claims. O(C) where C = total active claims across all pastures.
- Claim/release — O(1) map operations

**Pathfinding is the most expensive single operation.** Vanilla pathfinding can take 0.1–1ms depending on terrain complexity. Throttled to once per second per Pokémon, which is reasonable.

**Rating:** ⚠️ Moderate — vanilla pathfinding is expensive but well-throttled. With 20+ active Pokémon navigating simultaneously, this could be the largest single contributor.

---

### 6. Job-Specific Tick Costs

#### 6a. Gathering Jobs (BaseHarvester)

**Pattern:** Find target from cache → navigate → arrive → harvest → hold items → find container → navigate → deposit

- `findClosestTarget()`: Iterates cached targets, filters by `isTargetReady()` + recently-expired check, sorts by distance. O(T) where T = cached targets for that category.
- `harvestWithLoot()`: Constructs `LootContextParameterSet`, calls `getDroppedStacks()`. Moderate cost (~50-100μs) but runs only once per harvest.
- Most time is spent navigating (already throttled).

**Rating:** ✅ Low — most work is cooldown-gated

#### 6b. Production Jobs (BaseProducer)

**Pattern:** Cooldown check → produce items → deposit

- Cooldown check is O(1)
- Production runs only when cooldown expires (60-300 seconds typically)
- Nearly zero tick cost while on cooldown

**Rating:** ✅ Negligible

#### 6c. Processing Jobs (BaseProcessor)

- `findInputContainer()`: Iterates all CONTAINER cache entries, filters for barrels, checks inventory contents. O(C × S) where C = containers, S = slots per container.
- Called during IDLE phase to find work. Once found, transitions to navigation (cheap).

**Rating:** ⚠️ Low-moderate — inventory scanning can be slightly expensive with many containers

#### 6d. Defense Jobs (BaseDefender)

- `findNearbyHostiles()`: `world.getEntitiesByClass(HostileEntity, searchBox)` — vanilla entity query in AABB. Cost depends on entity count in area.
- Only scans when no current target

**Rating:** ⚠️ Low-moderate — entity queries are ~O(E) and run each tick when idle

#### 6e. Support Jobs (BaseSupport)

- `findNearbyPlayers()`: `world.getEntitiesByClass(PlayerEntity, searchBox)` — typically very few players, extremely cheap.

**Rating:** ✅ Negligible

#### 6f. Environmental Jobs (FrostFormer, ObsidianForge, GrowthAccelerator)

**⚠️ BIGGEST CONCERN:** These three jobs use `BlockPos.iterateOutwards(origin, 8, 8, 8)` **every tick while idle** to find targets. This iterates **(17³ = 4,913 positions)** synchronously, calling `getBlockState()` on each.

This is NOT cached by the deferred scanner — these jobs do their own live scanning every tick.

- `FrostFormer.findFrostTarget()` — scans 4,913 blocks for water/ice
- `ObsidianForge.findLava()` — scans 4,913 blocks for lava  
- `GrowthAccelerator.findGrowable()` — scans 4,913 blocks for crops/saplings

Each of these costs ~50-150μs per call. If a Pokémon is eligible for one of these and has no target, this runs **every single tick**.

**Rating:** ❌ **HIGH** — bypasses the deferred scanner, does synchronous full-area scans every tick

---

### 7. Memory Usage

**Per pasture:**
- `PastureCache`: One `MutableSet<BlockPos>` per BlockCategory (~50 categories). Mostly empty sets. Small.
- Active scan iterator: Holds a `BlockPos.stream()` iterator during scanning (3,179 positions). Temporary.

**Per Pokémon:**
- `PokemonProfile`: 4 small lists of Worker references. ~200 bytes.
- Job state maps: UUID → state. ~100 bytes per active Pokémon per job.

**Global singletons:** Navigation claim maps, arrival tick maps. O(P) where P = total active Pokémon across all pastures.

**Rating:** ✅ Negligible memory footprint

---

### 8. Scout (Structure Locating)

- Uses `CompletableFuture.supplyAsync` for `chunkGenerator.locateStructure()` — runs on a background thread
- Results are cached with 15-minute TTL
- Properly async, doesn't block the main thread

**Rating:** ✅ Well-implemented async pattern

---

## Scaling Analysis

| Pastures | Pokémon | Est. tick cost (ms) | Notes |
|----------|---------|---------------------|-------|
| 1        | 4       | 0.05–0.15          | Barely measurable |
| 5        | 20      | 0.25–0.75          | Noticeable in profiler |
| 10       | 40      | 0.5–1.5            | ~2-3% of tick budget |
| 20       | 80      | 1.0–3.0            | Could cause occasional lag spikes |
| 50+      | 200+    | 2.5–7.5+           | May impact TPS |

*Assumes worst case where environmental jobs are active. Without FrostFormer/ObsidianForge/GrowthAccelerator, costs are ~40-60% lower.*

---

## Hotspots (Ranked by Impact)

### 1. 🔴 Environmental Job Live Scanning
`FrostFormer.findFrostTarget()`, `ObsidianForge.findLava()`, `GrowthAccelerator.findGrowable()` all use `BlockPos.iterateOutwards()` every tick when idle.

**Fix:** Use the deferred scanner cache like other jobs. FrostFormer could use a new `WATER` category, ObsidianForge a `LAVA` category, GrowthAccelerator already has `CROP_GRAIN`/`FARMLAND` categories available. BlockCategory already has `WATER` and `LAVA` entries but no validators are registered for them.

### 2. 🟡 BlockCategory Validator Exhaustive Check
Every scanned block runs through all ~50 validators. If only 5 categories are relevant to active jobs, 90% of validator checks are wasted.

**Fix:** Track which categories have active consumers and only run those validators. Low priority since the scanner is already well-throttled.

### 3. 🟡 BaseDefender Entity Query Per Tick
`findNearbyHostiles()` runs an AABB entity query every tick when no target is held. In mob-dense areas this could be noticeable.

**Fix:** Add a simple tick-interval throttle (e.g. check every 20 ticks instead of every tick).

### 4. 🟢 Navigation Claim Cleanup
`releaseExpiredClaims()` iterates all claims every 20 ticks. Currently uses `forEach` + collecting into a list then iterating again. Minor, but with 100+ claims could be tightened.

---

## Conclusion

The mod is architecturally sound for typical server loads (1-10 pastures). The deferred scanner, profile caching, cooldown system, and pathfinding throttle are all well-designed performance safeguards.

The main concern is the 3 environmental jobs (`FrostFormer`, `ObsidianForge`, `GrowthAccelerator`) that bypass the caching system entirely and do full-area block scans every tick. Migrating these to use the deferred scanner cache would eliminate the single largest performance hotspot.

For a server with 5-10 pastures and typical usage, expect **<1ms per tick** overhead — well within acceptable limits.
