# CobbleCrew Consolidation Audit

_Generated 2026-02-28_

---

## 1. Manual `isEligible()` Implementations (Not Using `dslEligible()`)

Files already using DSL (no action needed):
- **GatheringJobs.kt** — all `GatheringJob` DSL
- **ProcessingJobs.kt** — all `ProcessingJob` DSL
- **PlacementJobs.kt** — all `PlacementJob` DSL
- **DefenseJobs.kt** — all `DefenseJob` DSL
- **EnvironmentalJobs.kt** — all `EnvironmentalJob` DSL
- **SupportJobs.kt** — HEALER through HUNGER_RESTORER use `SupportJob` DSL

### Files with remaining manual `isEligible()`:

| File | Worker | Line | Pattern | Could use `dslEligible()`? |
|------|--------|------|---------|----------------------------|
| ProductionJobs.kt | `FishingLooter` | L68 | moves-only (no species fallback) | Yes — `dslEligible(config, qualifyingMoves, emptyList(), moves, species)` |
| ProductionJobs.kt | `PickupLooter` | L150 | **ability-based** — `ability == requiredAbility` | **No** — unique pattern, `dslEligible` doesn't support ability checks. Needs DSL extension or stays manual. |
| ProductionJobs.kt | `DiveCollector` | L229 | moves-only (no species fallback) | Yes — identical to FishingLooter pattern |
| ProductionJobs.kt | `DigSiteExcavator` | L309 | moves-only (no species fallback) | Yes — identical to FishingLooter pattern |
| LogisticsJobs.kt | `Magnetizer` | L64 | moves + species fallback | Yes — exactly matches `dslEligible()` signature |
| LogisticsJobs.kt | `GroundItemCollector` | L148 | moves-only (no species fallback) | Yes — `dslEligible(config, qualifyingMoves, emptyList(), moves, species)` |
| SupportJobs.kt | `ScoutWorker` | L170 | moves-only (no species fallback) | Yes — identical to FishingLooter pattern |
| ComboJobs.kt | `SILK_TOUCH_EXTRACTOR` | L95 | **custom combo** — requires `"psychic"` + any of `gatheringMoves` | **No** — unique AND logic that doesn't match `dslEligible(isCombo=true)` because only one of the two sets must fully match. Stays manual. |

**Summary:** 6 of 8 manual `isEligible()` implementations are trivially replaceable with `dslEligible()`. Only PickupLooter (ability-based) and SILK_TOUCH_EXTRACTOR (custom combo logic) need to stay manual or need DSL extensions.

---

## 2. Duplicated Deposit/Delivery Logic

Every manual `Worker` implementation in the registry files duplicates the same deposit pattern. The canonical version lives in **BaseProducer.tick()** (L52–L67). The manual workers reinvent it identically.

### The Repeated Pattern

```kotlin
val held = heldItems[pid]
if (held.isNullOrEmpty()) {
    failedDeposits.remove(pid)
    // ... produce/find items ...
} else {
    if (context is JobContext.Party) {
        CobbleCrewInventoryUtils.deliverToPlayer(context.player, held, pokemonEntity)
        heldItems.remove(pid)
        failedDeposits.remove(pid)
        return
    }
    CobbleCrewInventoryUtils.handleDepositing(world, origin, pokemonEntity, held, failedDeposits, heldItems)
}
```

### Every Occurrence

| File | Worker | `tick()` Line | `failedDeposits` decl. | `cleanup()` line | Notes |
|------|--------|---------------|------------------------|------------------|-------|
| ProductionJobs.kt | `FishingLooter` | L76–L91 | L56 | L123–L127 | Also has `lastGenTime` + Water check before deposit block |
| ProductionJobs.kt | `PickupLooter` | L157–L171 | L139 | L200–L204 | Identical structure, no water check |
| ProductionJobs.kt | `DiveCollector` | L236–L252 | L217 | L280–L284 | Water check before deposit block |
| ProductionJobs.kt | `DigSiteExcavator` | L315–L330 | L297 | L384–L388 | Deposit-first variant (items checked before target finding) |
| LogisticsJobs.kt | `GroundItemCollector` | L155–L169 | L138 | L192–L196 | Deposit-first variant |
| SupportJobs.kt | `ScoutWorker` | L180–L193 | L155 | L285–L289 | Deposit-first variant |

Each duplicates:
1. `private val heldItems = mutableMapOf<UUID, List<ItemStack>>()`
2. `private val failedDeposits = mutableMapOf<UUID, MutableSet<BlockPos>>()`
3. The `if (context is Party) deliverToPlayer else handleDepositing` branch
4. The `cleanup()` removing from all three maps

**BaseProducer already has this exact logic** but these 6 workers implement `Worker` directly instead of extending `BaseProducer`.

---

## 3. Other Significant Duplication

### A. Loot-Table Generation (3 near-identical `produce()` methods)

`PickupLooter.produce()` (L173–L194), `DiveCollector.produce()` (L255–L276), and `DigSiteExcavator.generateLoot()` (L358–L379) all share this identical core:

```kotlin
val tables = (config.lootTables ?: emptyList()).ifEmpty { listOf("cobblemon:gameplay/pickup") }
    .mapNotNull { Identifier.tryParse(it) }
if (tables.isEmpty()) return

val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
    .add(LootContextParameters.ORIGIN, origin.toCenterPos())
    .add(LootContextParameters.THIS_ENTITY, pokemonEntity)
    .build(LootContextTypes.CHEST)

val key = RegistryKey.of(RegistryKeys.LOOT_TABLE, tables.random())
val drops = world.server.reloadableRegistries.getLootTable(key).generateLoot(lootParams)
```

Only `FishingLooter.produce()` differs — it uses `LootContextTypes.FISHING` + adds `TOOL` parameter + has treasure chance branching.

**Opportunity:** Extract a shared `generateFromLootTables(world, pos, entity, tables, contextType)` utility.

### B. Navigate → Claim → Arrive → Release Pattern (3 occurrences in registry/)

| File | Worker | Lines | Particle | WorkPhase |
|------|--------|-------|----------|-----------|
| LogisticsJobs.kt | `Magnetizer` | L78–L94 | `ELECTRIC_SPARK` | `PROCESSING` |
| LogisticsJobs.kt | `GroundItemCollector` | L173–L190 | `ENCHANT` | `HARVESTING` |
| ProductionJobs.kt | `DigSiteExcavator` | L329–L345 | `COMPOSTER` | `HARVESTING` |
| SupportJobs.kt | `ScoutWorker` | L211–L232 | `END_ROD` | `HARVESTING` |

All four follow:
```kotlin
if (target == null) {
    val found = <find target>
    if (!Nav.isTargeted(found, world)) {
        Nav.claimTarget(pid, found, world)
        targets[pid] = found
        Nav.navigateTo(pokemonEntity, found)
    }
    return
}
Nav.navigateTo(pokemonEntity, target)
if (VisualUtils.handleArrival(pokemonEntity, target, world, <particle>, 3.0, <phase>)) {
    <do work>
    Nav.releaseTarget(pid, world)
    targets.remove(pid)
}
```

The DSL classes (GatheringJob, PlacementJob, etc.) already abstract this pattern. These 4 manual workers don't use it.

### C. Identical `cleanup()` Boilerplate

Every manual worker has a cleanup removing from 2–4 maps. This is identical to what BaseProducer does automatically. Six workers duplicate it.

### D. `hasActiveState()` Boilerplate

All 6 manual workers have `override fun hasActiveState(pokemonId: UUID) = pokemonId in heldItems` (or `in heldItems || in targets`). BaseProducer provides this automatically.

---

## Recommended Consolidation Priority

1. **High impact, low risk:** Convert `FishingLooter`, `DiveCollector`, `DigSiteExcavator` `isEligible()` → `dslEligible()` calls (3 one-line changes)
2. **High impact, medium risk:** Make `FishingLooter`, `PickupLooter`, `DiveCollector` extend `BaseProducer` instead of `Worker` — eliminates all deposit/cleanup duplication for those 3 workers
3. **Medium impact:** Extract shared loot-table-generation utility for `PickupLooter`, `DiveCollector`, `DigSiteExcavator`
4. **Lower priority:** `DigSiteExcavator`, `GroundItemCollector`, `ScoutWorker`, `Magnetizer` are harder to convert to DSL because they combine target-finding + deposit patterns in unique ways — would need a new DSL class or more flexible base class
