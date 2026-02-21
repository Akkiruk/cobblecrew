# Cobbleworkers Comprehensive Code Audit

**Date:** 2026-02-21  
**Scope:** All job infrastructure, registry, DSL, utility, config, and cache files  
**Total files audited:** 40+

---

## CRITICAL Issues

### 1. ScoutWorker — Cooldown resets before completing work (jobs never finish)
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/registry/SupportJobs.kt`  
**Lines:** ~195–210  
**Severity:** CRITICAL

The cooldown timer is reset (`lastGenTime[ownerId] = now`) BEFORE the scout has finished navigating to or picking up the map item. On the very next tick, `now - last < cd` is true (only 1 tick has elapsed vs 600s cooldown), so `tick()` returns early. The scout gets exactly **1 tick** of processing per 600-second cooldown window.

**Impact:** Scout job is effectively broken. It claims a target on the first tick, then can't continue navigating/picking up for another 600 seconds. If it DOES manage to create a map and hold items, it can't deposit them until the next cooldown window opens.

**Fix:** Move `lastGenTime[ownerId] = now` to AFTER the map is successfully created and held (inside `handleMapPickup`, after `heldItems[pid] = ...`). The cooldown check should gate production, not every tick of the state machine:
```kotlin
override fun tick(...) {
    val held = heldItems[pid]
    if (!held.isNullOrEmpty()) {
        CobbleworkersInventoryUtils.handleDepositing(...)
        return
    }
    failedDeposits.remove(pid)
    val now = world.time
    val last = lastGenTime[ownerId] ?: 0L
    val cd = ...
    if (now - last < cd) return
    handleMapPickup(world, origin, pokemonEntity)
}
// Set lastGenTime inside handleMapPickup on successful map creation
```

---

### 2. AURA_MASTER — Only applies Speed, missing 3 other effects
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/registry/ComboJobs.kt`  
**Lines:** ~350–360  
**Severity:** CRITICAL

The comment says "calmmind + helpinghand → Speed + Strength + Haste + Resistance" but the job only defines `statusEffect = StatusEffects.SPEED`. The comment `// primary; secondary effects applied via override` exists, but there IS no override — it inherits `BaseSupport.applyEffect()` which only applies the single declared effect.

**Impact:** Combo requirement (two moves) for a job that delivers identical output to the standalone SPEED_BOOSTER.

**Fix:** Override `applyEffect` in the anonymous class:
```kotlin
override fun applyEffect(player: PlayerEntity) {
    super.applyEffect(player) // Speed
    for (effect in listOf(StatusEffects.STRENGTH, StatusEffects.HASTE, StatusEffects.RESISTANCE)) {
        if (!player.hasStatusEffect(effect)) {
            player.addStatusEffect(StatusEffectInstance(effect, effectDurationTicks, effectAmplifier))
        }
    }
}
```

---

### 3. FULL_RESTORE — Doesn't clear negative effects as documented
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/registry/ComboJobs.kt`  
**Lines:** ~330–340  
**Severity:** CRITICAL

The comment says "healbell + aromatherapy → Regeneration II + clears all negatives" but there is no override to clear negative status effects. It only applies Regeneration II.

**Impact:** Combo requirement for a job barely better than the standard HEALER.

**Fix:** Override `applyEffect`:
```kotlin
override fun applyEffect(player: PlayerEntity) {
    player.activeStatusEffects.keys
        .filter { !it.value().isBeneficial }
        .toList()
        .forEach { player.removeStatusEffect(it) }
    super.applyEffect(player)
}
```

---

## HIGH Issues

### 4. PAPER_MAKER — Consumes sugar cane without producing paper
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/registry/ProcessingJobs.kt`  
**Lines:** ~106–112  
**Severity:** HIGH

`inputCheck` requires `it.count >= 3` so it only matches slots with 3+ cane. But `BaseProcessor` calls `extractFromContainer(... predicate, maxAmount=1)` — extracts only 1 item. `transformFn` receives count=1, computes `1 / 3 = 0` batches → returns empty list. The 1 sugar cane is consumed and nothing is produced.

**Impact:** Sugar cane is silently destroyed with no output. Job is completely non-functional.

**Fix:** Either override extract amount in ProcessingJob/BaseProcessor to pass `maxAmount` based on minimum batch, OR change the transform to work with any count and accumulate:
```kotlin
// Option A: Fix extractFromContainer call
val taken = CobbleworkersInventoryUtils.extractFromContainer(world, target, ::inputPredicate, maxAmount = 64)
```

---

### 5. COMPOSTER — Same extraction bug as PAPER_MAKER
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/registry/ProcessingJobs.kt`  
**Lines:** ~145–155  
**Severity:** HIGH

`transformFn` expects 7+ items (`input.count / 7`) but `extractFromContainer` extracts only 1. Items consumed for nothing.

**Fix:** Same as #4 — increase extraction amount.

---

### 6. BaseSupport — Missing cleanup & wrong release method
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/BaseSupport.kt`  
**Lines:** ~55, ~80 (no cleanup override)  
**Severity:** HIGH

Two issues:
1. When `nearbyPlayers.isEmpty()`, line ~55 calls `CobbleworkersNavigationUtils.releaseTarget(pokemonId, world)` — this releases **block** targets, not player targets. The job uses player targets via `claimTarget(pokemonId, player, world)`.
2. No `cleanup()` override exists, so `releasePlayerTarget(pokemonId)` is never called on Pokémon removal. Player target claims leak indefinitely.

**Impact:** After a support job runs, player target claims persist, preventing other Pokémon from targeting the same player. Only fixed by server restart.

**Fix:**
```kotlin
override fun cleanup(pokemonId: java.util.UUID) {
    CobbleworkersNavigationUtils.releasePlayerTarget(pokemonId)
}
```
And change the empty-players branch:
```kotlin
if (nearbyPlayers.isEmpty()) {
    CobbleworkersNavigationUtils.releasePlayerTarget(pokemonId)
    return
}
```

---

### 7. BasePlacer — Silent item deletion on failed return-to-container
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/BasePlacer.kt`  
**Lines:** ~72–80  
**Severity:** HIGH

When no placement target is found, the code tries to return the extracted item to the source container:
```kotlin
CobbleworkersInventoryUtils.insertStack(
    world.getBlockEntity(source) as? Inventory ?: run { ... },
    taken
)
heldItems.remove(pokemonId)
```
The return value of `insertStack` (remaining items that didn't fit) is discarded. If the container filled up since extraction, the item is silently deleted.

**Fix:** Check the return value and drop remaining items:
```kotlin
val remainder = CobbleworkersInventoryUtils.insertStack(inv, taken)
if (!remainder.isEmpty) {
    Block.dropStack(world, pokemonEntity.blockPos, remainder)
}
heldItems.remove(pokemonId)
```

---

### 8. handleDepositing — Incorrect full-container detection
**File:** `common/src/main/kotlin/accieo/cobbleworkers/utilities/CobbleworkersInventoryUtils.kt`  
**Lines:** ~213  
**Severity:** HIGH

```kotlin
if (remainingDrops.size == itemsToDeposit.size) {
    triedPositions.add(inventoryPos)
}
```
This compares the **count of stacks**, not actual items. If you have 2 stacks and the container accepts items from one stack but the other is partially inserted, `remainingDrops.size` (2) == `itemsToDeposit.size` (2) → container marked as failed even though it accepted items. Conversely, if one stack is fully consumed, `remainingDrops.size` (1) < `itemsToDeposit.size` (2) → container NOT marked as failed, even if the second stack was completely rejected.

**Fix:** Compare total remaining count vs total input count:
```kotlin
val totalInput = itemsToDeposit.sumOf { it.count }
val totalRemaining = remainingDrops.sumOf { it.count }
if (totalRemaining >= totalInput) {
    triedPositions.add(inventoryPos)
}
```

---

### 9. Combo GatheringJobs — Advertised behaviors not implemented
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/registry/ComboJobs.kt`  
**Severity:** HIGH

| Job | Advertised | Reality |
|-----|-----------|---------|
| DEMOLISHER | "breaks ANY block type" | Only targets `BlockCategory.STONE` |
| FORTUNE_MINER | doubled drops | Standard loot, no fortune enchant |
| SILK_TOUCH_EXTRACTOR | silk-touch harvest | No silk-touch tool override |
| VEIN_MINER | break target + connected blocks | Breaks single block |
| TREE_FELLER | entire tree (all connected logs) | Breaks single log |
| FOSSIL_HUNTER | fossil-weighted loot | Standard stone drops |
| GEM_VEIN_FINDER | gem-weighted loot | Standard stone drops |

These all use the base `GatheringJob.harvest()` which either calls `harvestWithLoot` (normal loot context) or a custom `harvestOverride` (none provided). No special enchantments, multi-block breaking, or custom loot tables are implemented.

**Impact:** Players who breed Pokémon specifically for combo move requirements get identical results to non-combo jobs. Defeats the purpose of the combo tier.

**Fix:** Each combo needs custom `harvestOverride` or `toolOverride` to implement the advertised behavior. Example for SILK_TOUCH_EXTRACTOR:
```kotlin
toolOverride = ItemStack(Items.DIAMOND_PICKAXE).apply {
    addEnchantment(Enchantments.SILK_TOUCH, 1)
}
```

---

## MEDIUM Issues

### 10. navigateToPlayer has no pathfind throttling
**File:** `common/src/main/kotlin/accieo/cobbleworkers/utilities/CobbleworkersNavigationUtils.kt`  
**Lines:** ~67–74  
**Severity:** MEDIUM

`navigateTo(BlockPos)` throttles pathfinding to every 5 ticks, but `navigateToPlayer(PlayerEntity)` recalculates pathfinding every tick. With many support jobs tracking players, this wastes CPU.

**Fix:** Add the same throttle check to `navigateToPlayer`.

---

### 11. handlePlayerArrival has no grace period
**File:** `common/src/main/kotlin/accieo/cobbleworkers/utilities/WorkerVisualUtils.kt`  
**Lines:** ~88–107  
**Severity:** MEDIUM

`handleArrival` (block targets) has a 10-tick grace period to avoid timer resets when the Pokémon is briefly bumped away. `handlePlayerArrival` (player targets) immediately resets when the player walks slightly out of range. Since players constantly move, the 30-tick work delay may never complete.

**Impact:** Support jobs (healer, speed booster, etc.) may struggle to actually apply effects to moving players.

**Fix:** Add the same grace period logic from `handleArrival`.

---

### 12. GroundItemCollector picks first item, not closest
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/registry/LogisticsJobs.kt`  
**Lines:** ~195  
**Severity:** MEDIUM

```kotlin
val item = world.getEntitiesByClass(ItemEntity::class.java, searchArea) { true }
    .firstOrNull { it.isOnGround } ?: return
```
Uses `firstOrNull` instead of `minByOrNull { it.squaredDistanceTo(pokemonEntity.pos) }`. The Pokémon may navigate to a far-away item when a closer one exists.

**Fix:** Use `minByOrNull` like other target-finding methods.

---

### 13. PokemonProfile not invalidated on moveset change
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/WorkerDispatcher.kt`  
**Lines:** ~36–46  
**Severity:** MEDIUM

Profiles are cached by UUID and only invalidated on config reload (`invalidateProfiles()`). If a Pokémon learns a new move, levels up, or evolves while in the pasture, the profile won't update. The Pokémon would need to leave and re-enter the pasture.

**Fix:** Listen to Cobblemon's `PokemonUpdated` / `EvolutionComplete` events and invalidate the specific profile:
```kotlin
fun invalidateProfile(pokemonId: UUID) { profiles.remove(pokemonId) }
```

---

### 14. BaseProducer — No overflow protection timeout
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/BaseProducer.kt`  
**Lines:** ~42–51  
**Severity:** MEDIUM

Unlike `BaseHarvester` which has a 30-second overflow timeout (drops items if stuck), `BaseProducer` relies solely on `handleDepositing` to eventually drop items. If `isScanActive` returns true persistently (scan loops), items could be held indefinitely without being dropped.

**Fix:** Add the same `OVERFLOW_TIMEOUT_TICKS` pattern from BaseHarvester.

---

### 15. BaseProcessor — No overflow protection timeout  
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/BaseProcessor.kt`  
**Severity:** MEDIUM

Same as #14 — no explicit overflow timeout.

---

### 16. BasePlacer — No stuck-in-navigation timeout
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/BasePlacer.kt`  
**Severity:** MEDIUM

If a Pokémon gets stuck navigating in `NAVIGATING_PLACEMENT` phase (pathing blocked, target unreachable), it holds items forever. Unlike BaseHarvester, there's no timeout to drop items.

---

### 17. Cauldron fillers don't check if cauldron is already the target fluid
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/registry/EnvironmentalJobs.kt`  
**Lines:** ~300–400  
**Severity:** MEDIUM

`findClosestCauldron` only finds EMPTY cauldrons (`isOf(Blocks.CAULDRON)`). But `addFluid` doesn't check the current state before replacing. If another worker fills the cauldron between claim and arrival, `addFluid` would overwrite the existing fluid with a different one (e.g., water overwrites lava). The `isOf(Blocks.CAULDRON)` check at claim time prevents this, but if two Pokémon both claim the same empty cauldron (possible since the `isTargeted` check is not atomic), one could overwrite the other's work.

**Fix:** Add a `isOf(Blocks.CAULDRON)` check inside the arrival handler before calling `addFluid`.

---

## LOW Issues

### 18. Redundant `world.getBlockState()` calls in BlockCategoryValidators
**File:** `common/src/main/kotlin/accieo/cobbleworkers/utilities/BlockCategoryValidators.kt`  
**Severity:** LOW

Many validators call `world.getBlockState(pos)` multiple times in the same lambda (e.g., ICE checks `world.getBlockState(pos).block == Blocks.ICE || world.getBlockState(pos).block == Blocks.PACKED_ICE`). Should cache the state reference:
```kotlin
BlockCategory.ICE to { world, pos ->
    val block = world.getBlockState(pos).block
    block == Blocks.ICE || block == Blocks.PACKED_ICE
}
```

---

### 19. SUSPICIOUS category is misleading
**File:** `common/src/main/kotlin/accieo/cobbleworkers/utilities/BlockCategoryValidators.kt`  
**Lines:** ~114–117  
**Severity:** LOW

The SUSPICIOUS validator matches regular `DIRT`, `GRAVEL`, `MUD`, etc. — not vanilla suspicious sand/suspicious gravel. The DigSiteExcavator uses this to generate loot from regular soil. Technically this is by design, but the category name suggests it should target actual suspicious blocks.

---

### 20. ScoutWorker pendingLookups never cleaned on pasture removal
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/registry/SupportJobs.kt`  
**Lines:** ~265  
**Severity:** LOW

`pendingLookups` is a static map that holds `CompletableFuture` references. If a scout leaves the pasture while a structure lookup is pending, the future completes but is never consumed. Minor memory leak.

---

### 21. BerryHarvester unchecked cast
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/registry/GatheringJobs.kt`  
**Lines:** ~370  
**Severity:** LOW

```kotlin
@Suppress("UNCHECKED_CAST")
drops as List<ItemStack>
```
The return type of `BerryBlockEntity.harvest()` may change in future Cobblemon versions. If it returns something other than `List<ItemStack>`, this would throw a ClassCastException at runtime.

---

### 22. Math.random() usage instead of world.random
**File:** `common/src/main/kotlin/accieo/cobbleworkers/jobs/registry/ProductionJobs.kt`, `ComboJobs.kt`  
**Lines:** Various  
**Severity:** LOW

Several production jobs use `Math.random()` instead of `world.random`. This bypasses Minecraft's seeded random, making these jobs non-deterministic relative to the world seed. Not a bug, but inconsistent with MC conventions.

---

### 23. CobbleworkersMoveUtils — Validation is incomplete
**File:** `common/src/main/kotlin/accieo/cobbleworkers/utilities/CobbleworkersMoveUtils.kt`  
**Severity:** LOW

The validation only logs the registry size. It does not actually check if configured move names in jobs match the registry. The comment says "full validation requires workers to expose their move sets (added in Phase 1 when DSL is built)" — the DSL is now built, but validation was never completed.

---

## Patterns That Look Correct

| Component | Assessment |
|-----------|-----------|
| **WorkerDispatcher** lifecycle | Clean. Priority-based profile system is well-designed. Job stickiness prevents thrashing. |
| **PokemonProfile** tiering | COMBO → MOVE → SPECIES → TYPE priority is correctly implemented. Profile caching eliminates per-tick `isEligible` overhead. |
| **BaseHarvester** overflow protection | 30-second timeout to drop held items if depositing fails — good safety net. |
| **BaseHarvester** target validation | Checks for air blocks and recently-expired positions before navigating. |
| **DeferredBlockScanner** | Well-designed deferred scan with configurable blocks/tick, cooldown, chunk-load checks, and needed-category filtering. |
| **CobbleworkersCacheManager** | ConcurrentHashMap for thread safety (structure lookups are async). TTL on structure cache. |
| **CobbleworkersNavigationUtils** claim system | Block/player/mob targeting with auto-expiry on stale claims (100 ticks). Recently-expired target cooldown prevents instant re-targeting. |
| **CobbleworkersInventoryUtils** depositing | Full chest open/close animation with sound. Deposit delay for visual feedback. Multi-container fallback with "tried positions" tracking. |
| **WorkerVisualUtils** | Grace period on block arrivals. Work delay timer. Particle effects and cry behavior. |
| **JobConfigManager** | Per-category JSON files with merge-defaults pattern. Auto-generates missing config files. |
| **DSL isEligible pattern** | Consistent across all 6 DSL classes. Config overrides work properly. Type-gated moves are correctly handled. |
| **BaseDefender** hostile scan throttling | Caches hostile list for 20 ticks to avoid scanning every tick. |
| **Cauldron/Furnace/BrewingStand** mixin usage | Proper accessor mixins for injecting fuel/burn time. Value clamping on burn time. |
| **Crop replanting** | Comprehensive handling of vanilla, Cobblemon, and Farmer's Delight crops. |
| **GatheringJobs** readyCheck lambdas | Proper maturity checks for all crop types (ApricornBlock.MAX_AGE, BerryBlock.FRUIT_AGE, etc.). |
| **All cleanup() overrides** | Every custom Worker implementation cleans up its UUID maps. WorkerDispatcher calls cleanup on all workers during `cleanupPokemon`. |

---

## Summary by Severity

| Severity | Count | Key Issues |
|----------|-------|------------|
| **CRITICAL** | 3 | Scout cooldown bug, AURA_MASTER/FULL_RESTORE missing functionality |
| **HIGH** | 6 | PAPER_MAKER/COMPOSTER item consumption, BaseSupport leak, BasePlacer item deletion, deposit detection, combo jobs unimplemented |
| **MEDIUM** | 8 | Navigation throttling, grace periods, overflow protection, profile invalidation |
| **LOW** | 6 | Redundant calls, naming, memory leaks, code quality |
| **TOTAL** | **23** | |
