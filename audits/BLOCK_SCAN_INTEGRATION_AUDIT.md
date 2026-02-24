# Block Scan Integration Audit

**Date:** 2026-02-23
**Scope:** All jobs in `jobs/registry/` vs `DeferredBlockScanner`, `CobbleCrewCacheManager`, `BlockCategory`, `BlockCategoryValidators`

---

## 1. Complete Job → targetCategory Map

### GatheringJobs.kt (38 jobs — all extend `GatheringJob` → `BaseHarvester`)

| Job | targetCategory | Cache Usage |
|-----|---------------|-------------|
| OVERWORLD_LOGGER | `LOG_OVERWORLD` | BaseHarvester (origin) |
| JUNGLE_CHOPPER | `LOG_TROPICAL` | BaseHarvester (origin) |
| FUNGI_HARVESTER | `LOG_NETHER` | BaseHarvester (origin) |
| BAMBOO_CHOPPER | `BAMBOO` | BaseHarvester (origin) |
| SUGAR_CANE_CUTTER | `SUGAR_CANE` | BaseHarvester (origin) |
| CACTUS_PRUNER | `CACTUS` | BaseHarvester (origin) |
| VINE_TRIMMER | `VINE` | BaseHarvester (origin) |
| BEET_PULLER | `CROP_BEET` | BaseHarvester (origin) |
| SWEET_BERRY_HARVESTER | `SWEET_BERRY` | BaseHarvester (origin) |
| PUMPKIN_MELON_HARVESTER | `PUMPKIN_MELON` | BaseHarvester (origin) |
| COCOA_HARVESTER | `COCOA` | BaseHarvester (origin) |
| CHORUS_FRUIT_HARVESTER | `CHORUS` | BaseHarvester (origin) |
| GLOWBERRY_PICKER | `CAVE_VINE` | BaseHarvester (origin) |
| DRIPLEAF_HARVESTER | `DRIPLEAF` | BaseHarvester (origin) |
| STONE_BREAKER | `STONE` | BaseHarvester (origin) |
| IGNEOUS_MINER | `IGNEOUS` | BaseHarvester (origin) |
| DEEPSLATE_EXCAVATOR | `DEEPSLATE` | BaseHarvester (origin) |
| EXCAVATOR | `DIRT` | BaseHarvester (origin) |
| SAND_MINER | `SAND` | BaseHarvester (origin) |
| CLAY_DIGGER | `CLAY` | BaseHarvester (origin) |
| SCULK_HARVESTER | `SCULK` | BaseHarvester (origin) |
| ICE_MINER | `ICE` | BaseHarvester (origin) |
| MUSHROOM_FORAGER | `MUSHROOM` | BaseHarvester (origin) |
| FLOWER_PICKER | `FLOWER` | BaseHarvester (origin) |
| SNOW_SCRAPER | `SNOW_BLOCK` | BaseHarvester (origin) |
| MOSS_SCRAPER | `MOSS` | BaseHarvester (origin) |
| DECOMPOSER | `LEAVES` | BaseHarvester (origin) |
| TERRAIN_FLATTENER | `VEGETATION` | BaseHarvester (origin) |
| APRICORN_HARVESTER | `APRICORN` | BaseHarvester (origin) |
| BERRY_HARVESTER | `BERRY` | BaseHarvester (origin) |
| MINT_HARVESTER | `MINT` | BaseHarvester (origin) |
| AMETHYST_HARVESTER | `AMETHYST` | BaseHarvester (origin) |
| TUMBLESTONE_HARVESTER | `TUMBLESTONE` | BaseHarvester (origin) |
| NETHERWART_HARVESTER | `NETHERWART` | BaseHarvester (origin) |
| CROP_HARVESTER | `CROP_GRAIN` | BaseHarvester (origin) |
| ROOT_HARVESTER | `CROP_ROOT` | BaseHarvester (origin) |
| HONEY_HARVESTER | `HONEY` | BaseHarvester (origin) |

### ProductionJobs.kt (29 jobs)

| Job | Type | targetCategory | Cache Usage |
|-----|------|---------------|-------------|
| WOOL_PRODUCER | ProductionJob → BaseProducer | `null` | None (cooldown-based) |
| SILK_SPINNER | ProductionJob → BaseProducer | `null` | None |
| SLIME_SECRETOR | ProductionJob → BaseProducer | `null` | None |
| INK_SQUIRTER | ProductionJob → BaseProducer | `null` | None |
| BONE_SHEDDER | ProductionJob → BaseProducer | `null` | None |
| PEARL_CREATOR | ProductionJob → BaseProducer | `null` | None |
| FEATHER_MOLTER | ProductionJob → BaseProducer | `null` | None |
| SCALE_SHEDDER | ProductionJob → BaseProducer | `null` | None |
| FRUIT_BEARER | ProductionJob → BaseProducer | `null` | None |
| COIN_MINTER | ProductionJob → BaseProducer | `null` | None |
| GEM_CRAFTER | ProductionJob → BaseProducer | `null` | None |
| SPORE_RELEASER | ProductionJob → BaseProducer | `null` | None |
| POLLEN_PACKER | ProductionJob → BaseProducer | `null` | None |
| GIFT_GIVER | ProductionJob → BaseProducer | `null` | None |
| EGG_LAYER | ProductionJob → BaseProducer | `null` | None |
| MILK_PRODUCER | ProductionJob → BaseProducer | `null` | None |
| ELECTRIC_CHARGER | ProductionJob → BaseProducer | `null` | None |
| WAX_PRODUCER | ProductionJob → BaseProducer | `null` | None |
| POWDER_MAKER | ProductionJob → BaseProducer | `null` | None |
| ASH_COLLECTOR | ProductionJob → BaseProducer | `null` | None |
| STATIC_GENERATOR | ProductionJob → BaseProducer | `null` | None |
| SAP_TAPPER | ProductionJob → BaseProducer | `null` | None |
| TOXIN_DISTILLER | ProductionJob → BaseProducer | `null` | None |
| CRYSTAL_GROWER | ProductionJob → BaseProducer | `null` | None |
| TEAR_COLLECTOR | ProductionJob → BaseProducer | `null` | None |
| FishingLooter | Custom Worker | `null` | None (loot table) |
| PickupLooter | Custom Worker | `null` | None (loot table) |
| DiveCollector | Custom Worker | `null` | None (loot table) |
| DigSiteExcavator | Custom Worker | **`SUSPICIOUS`** | **Direct `getTargets(origin, ...)` call** |

### ProcessingJobs.kt (10 jobs — all extend `ProcessingJob` → `BaseProcessor`)

| Job | targetCategory | Cache Usage |
|-----|---------------|-------------|
| ORE_SMELTER | `null` | None (container-based) |
| FOOD_COOKER | `null` | None |
| GLASS_MAKER | `null` | None |
| BRICK_BAKER | `null` | None |
| CHARCOAL_BURNER | `null` | None |
| PAPER_MAKER | `null` | None |
| BONE_GRINDER | `null` | None |
| FLINT_KNAPPER | `null` | None |
| PIGMENT_PRESSER | `null` | None |
| COMPOSTER | `null` | None |

### PlacementJobs.kt (4 jobs — all extend `PlacementJob` → `BasePlacer`)

| Job | targetCategory | Cache Usage |
|-----|---------------|-------------|
| TORCH_LIGHTER | `null` | None (manual `BlockPos.iterateOutwards`) |
| TREE_PLANTER | `null` | None (manual `BlockPos.iterateOutwards`) |
| CROP_SOWER | `null` | None (manual `BlockPos.iterateOutwards`) |
| BONEMEAL_APPLICATOR | `null` | None (manual `BlockPos.iterateOutwards`) |

### DefenseJobs.kt (7 jobs — all extend `DefenseJob` → `BaseDefender`)

| Job | targetCategory | Cache Usage |
|-----|---------------|-------------|
| GUARD | `null` | None (entity-based) |
| SENTRY | `null` | None |
| REPELLER | `null` | None |
| FEARMONGER | `null` | None |
| FIRE_TRAP | `null` | None |
| POISON_TRAP | `null` | None |
| ICE_TRAP | `null` | None |

### SupportJobs.kt (10 jobs)

| Job | Type | targetCategory | Cache Usage |
|-----|------|---------------|-------------|
| HEALER | SupportJob → BaseSupport | `null` | None (player-based) |
| SPEED_BOOSTER | SupportJob → BaseSupport | `null` | None |
| STRENGTH_BOOSTER | SupportJob → BaseSupport | `null` | None |
| RESISTANCE_PROVIDER | SupportJob → BaseSupport | `null` | None |
| HASTE_PROVIDER | SupportJob → BaseSupport | `null` | None |
| JUMP_BOOSTER | SupportJob → BaseSupport | `null` | None |
| NIGHT_VISION_PROVIDER | SupportJob → BaseSupport | `null` | None |
| WATER_BREATHER | SupportJob → BaseSupport | `null` | None |
| HUNGER_RESTORER | SupportJob → BaseSupport | `null` | None |
| ScoutWorker | Custom Worker | `null` | None (structure/entity-based) |

### EnvironmentalJobs.kt (11 jobs — all custom Worker impls)

| Job | targetCategory | Cache Usage |
|-----|---------------|-------------|
| FrostFormer | **`WATER`** | **Direct `getTargets(origin, ...)` — WATER + ICE** |
| ObsidianForge | **`LAVA`** | **Direct `getTargets(origin, ...)`** |
| GrowthAccelerator | **`GROWABLE`** | **Direct `getTargets(origin, ...)`** |
| LavaCauldronFiller | **`CAULDRON`** | Via `CobbleCrewCauldronUtils.findClosestCauldron(origin)` |
| WaterCauldronFiller | **`CAULDRON`** | Via `CobbleCrewCauldronUtils.findClosestCauldron(origin)` |
| SnowCauldronFiller | **`CAULDRON`** | Via `CobbleCrewCauldronUtils.findClosestCauldron(origin)` |
| FurnaceFueler | **`FURNACE`** | **Direct `getTargets(origin, ...)`** |
| BrewingStandFueler | **`BREWING_STAND`** | **Direct `getTargets(origin, ...)`** |
| FireDouser | **`FIRE`** | **Direct `getTargets(origin, ...)`** |
| CropIrrigatorWorker | **`FARMLAND`** | **Direct `getTargets(origin, ...)`** |
| BeePollinator | **`HONEY`** | **Direct `getTargets(origin, ...)`** |

### ComboJobs.kt (18 jobs)

| Job | Base Class | targetCategory | Cache Usage |
|-----|-----------|---------------|-------------|
| DEMOLISHER | GatheringJob → BaseHarvester | `STONE` | BaseHarvester (origin) |
| FORTUNE_MINER | GatheringJob → BaseHarvester | `STONE` | BaseHarvester (origin) |
| SILK_TOUCH_EXTRACTOR | GatheringJob → BaseHarvester | `STONE` | BaseHarvester (origin) |
| VEIN_MINER | GatheringJob → BaseHarvester | `STONE` | BaseHarvester (origin) |
| TREE_FELLER | GatheringJob → BaseHarvester | `LOG_OVERWORLD` | BaseHarvester (origin) |
| FOSSIL_HUNTER | GatheringJob → BaseHarvester | `STONE` | BaseHarvester (origin) |
| GEM_VEIN_FINDER | GatheringJob → BaseHarvester | `STONE` | BaseHarvester (origin) |
| STRING_CRAFTER | ProductionJob → BaseProducer | `null` | None |
| BOOK_BINDER | ProductionJob → BaseProducer | `null` | None |
| CANDLE_MAKER | ProductionJob → BaseProducer | `null` | None |
| CHAIN_FORGER | ProductionJob → BaseProducer | `null` | None |
| LANTERN_BUILDER | ProductionJob → BaseProducer | `null` | None |
| BANNER_CREATOR | ProductionJob → BaseProducer | `null` | None |
| DEEP_SEA_TRAWLER | ProductionJob → BaseProducer | `null` | None |
| MAGMA_DIVER | ProductionJob → BaseProducer | `null` | None |
| BLAST_FURNACE | ProcessingJob → BaseProcessor | `null` | None |
| FULL_RESTORE | SupportJob → BaseSupport | `null` | None |
| AURA_MASTER | SupportJob → BaseSupport | `null` | None |

---

## 2. Cache Key Consistency Analysis

### How the system works
- `JobContext.Pasture.cacheKey` → `CacheKey.PastureKey(origin)` where `origin` is the fixed pasture BlockPos
- `JobContext.Party.cacheKey` → `CacheKey.PastureKey(origin)` where `origin` = `scanOrigin` (updated each eager scan)
- `CobbleCrewCacheManager.getTargets(BlockPos, category)` wraps to `getTargets(CacheKey.PastureKey(pos), category)`
- The deferred scanner stores results via `replaceAllCategoryTargets(context.cacheKey, staged)` using `CacheKey.PastureKey(origin)`

**Both the `context.origin` and `context.cacheKey` ultimately map to the same `PastureKey(origin)` for both pasture and party contexts.** So calling `getTargets(context.origin, ...)` is equivalent to `getTargets(context.cacheKey, ...)`.

### Jobs using `context.origin` directly (via BlockPos overload)

All jobs that call `CobbleCrewCacheManager.getTargets(origin, ...)` extract `origin` from `context.origin`. This is **consistent** because the convenience method `getTargets(BlockPos, ...)` wraps to `getTargets(PastureKey(pos), ...)`, and `context.cacheKey` is `PastureKey(context.origin)`. No mismatch.

### Jobs using `context.cacheKey` directly

`BaseHarvester.handleHarvesting()` uses `context.cacheKey` when calling `removeTarget(context.cacheKey, cat, currentTarget)` after harvest — **consistent** with how the scanner stores data.

`BaseHarvester.isAvailable()` and `findClosestTarget()` / `findReachableTarget()` use `context.origin` via the BlockPos overload — **consistent** as explained above.

### Verdict: **No cache key mismatches found.** All usages are consistent.

---

## 3. Jobs That Bypass the Scan System

### By design (correct — not block-targeted):
- All **ProductionJob/BaseProducer** jobs (25 DSL + FishingLooter, PickupLooter, DiveCollector) — `targetCategory = null`, produce items on cooldown or via loot tables
- All **ProcessingJob/BaseProcessor** jobs (10 + BLAST_FURNACE combo) — `targetCategory = null`, use `CobbleCrewInventoryUtils.findInputContainer()` for barrel scanning
- All **DefenseJob/BaseDefender** jobs (7) — `targetCategory = null`, scan for `HostileEntity` via `world.getEntitiesByClass()`
- All **SupportJob/BaseSupport** jobs (9 + FULL_RESTORE, AURA_MASTER combos) — `targetCategory = null`, scan for `PlayerEntity`
- **ScoutWorker** — `targetCategory = null`, scans for `ItemEntity` (maps) + structures
- **Magnetizer** — `targetCategory = null`, uses `CobbleCrewInventoryUtils.findInputContainer()`
- **GroundItemCollector** — `targetCategory = null`, scans for `ItemEntity`

### Placement jobs — potential optimization opportunity:
- **TORCH_LIGHTER, TREE_PLANTER, CROP_SOWER, BONEMEAL_APPLICATOR** all use `BlockPos.iterateOutwards()` in their `findTarget` lambdas to manually scan for placement positions every tick. They do NOT use the cache.

  **This is by design** — placement jobs look for *empty air positions* meeting specific criteria (light level, adjacent block type), which is fundamentally different from the scanner which looks for *existing blocks* of a category. The scanner wouldn't help here because these jobs need to find *where blocks are absent*, not where they exist. **No issue.**

### DigSiteExcavator — uses cache correctly:
Despite being in ProductionJobs, `DigSiteExcavator` declares `targetCategory = BlockCategory.SUSPICIOUS` and calls `CobbleCrewCacheManager.getTargets(origin, BlockCategory.SUSPICIOUS)` directly. This is **correct** — it declares the category so the scanner will scan for it via `getNeededCategories()`, and it reads from the cache correctly.

---

## 4. BlockCategory Values — Cross-Reference

### Categories declared in `BlockCategory.kt` (54 entries):
```
CONTAINER, APRICORN, BERRY, MINT, TUMBLESTONE,
AMETHYST, NETHERWART, CROP_GRAIN, CROP_ROOT, CROP_BEET,
SWEET_BERRY, PUMPKIN_MELON, COCOA, CHORUS, CAVE_VINE, DRIPLEAF,
LOG_OVERWORLD, LOG_TROPICAL, LOG_NETHER, BAMBOO, SUGAR_CANE, CACTUS,
VINE, HONEY, STONE, IGNEOUS, DEEPSLATE, DIRT, SAND, CLAY,
ICE, SNOW_BLOCK, SCULK, CORAL, MOSS, MUSHROOM, FLOWER, LEAVES, VEGETATION,
FURNACE, BREWING_STAND, CAULDRON, COMPOSTER, SUSPICIOUS,
FIRE, WATER, LAVA, FARMLAND, GROWABLE,
KELP, LILY_PAD, SEA_PICKLE
```

### Categories declared in `BlockCategoryValidators.kt` (has validators for):
```
APRICORN, BERRY, MINT, TUMBLESTONE, AMETHYST,
NETHERWART, CROP_GRAIN, CROP_ROOT, CROP_BEET, SWEET_BERRY,
PUMPKIN_MELON, COCOA, CHORUS, CAVE_VINE, DRIPLEAF,
LOG_OVERWORLD, LOG_TROPICAL, LOG_NETHER, BAMBOO, SUGAR_CANE, CACTUS, VINE,
STONE, IGNEOUS, DEEPSLATE, DIRT, SAND, CLAY,
ICE, SCULK, SNOW_BLOCK,
MUSHROOM, FLOWER, MOSS, LEAVES, VEGETATION,
HONEY, FIRE, WATER, LAVA,
GROWABLE, FARMLAND,
FURNACE, BREWING_STAND, CAULDRON, SUSPICIOUS,
KELP, LILY_PAD, SEA_PICKLE,
CONTAINER
```

### Categories used by jobs (`targetCategory` values):
```
LOG_OVERWORLD, LOG_TROPICAL, LOG_NETHER, BAMBOO, SUGAR_CANE, CACTUS, VINE,
CROP_BEET, SWEET_BERRY, PUMPKIN_MELON, COCOA, CHORUS, CAVE_VINE, DRIPLEAF,
STONE, IGNEOUS, DEEPSLATE, DIRT, SAND, CLAY,
SCULK, ICE, SNOW_BLOCK, MUSHROOM, FLOWER, MOSS, LEAVES, VEGETATION,
APRICORN, BERRY, MINT, AMETHYST, TUMBLESTONE, NETHERWART,
CROP_GRAIN, CROP_ROOT, HONEY,
WATER, LAVA, GROWABLE,
CAULDRON, FURNACE, BREWING_STAND, FIRE, FARMLAND,
SUSPICIOUS
```

### Categories in `BlockCategory.kt` with NO validator in `BlockCategoryValidators.kt`:
| Category | Status |
|----------|--------|
| **`CORAL`** | **No validator. No job uses it.** Orphaned enum entry. |
| **`COMPOSTER`** | **No validator. No job uses it.** Orphaned enum entry. |

### Categories in `BlockCategory.kt` with a validator but NO job using them:
| Category | Status |
|----------|--------|
| **`KELP`** | Validator exists, no job. Comment says "pending test." |
| **`LILY_PAD`** | Validator exists, no job. Comment says "pending test." |
| **`SEA_PICKLE`** | Validator exists, no job. Comment says "pending test." |

### Categories used by jobs that ARE defined in `BlockCategory.kt` and have validators:
**All 44 job-used categories are valid.** No job references a category that doesn't exist.

---

## 5. Summary of Findings

### CLEAN (no issues):
1. **All 38 GatheringJobs** — properly declare `targetCategory`, inherit `BaseHarvester` which uses `getTargets(origin, ...)` consistently. Cache removal after harvest uses `context.cacheKey`. All correct.
2. **All 25 DSL ProductionJobs** — `targetCategory = null`, cooldown-based. No cache interaction needed.
3. **All 10 ProcessingJobs** — `targetCategory = null`, container-based. Correct.
4. **All 4 PlacementJobs** — `targetCategory = null`, manual air-position scanning. Correct by design.
5. **All 7 DefenseJobs** — `targetCategory = null`, entity-based. Correct.
6. **All 9 DSL SupportJobs + ScoutWorker** — `targetCategory = null`, player/entity-based. Correct.
7. **All 18 ComboJobs** — 7 gathering (inherit BaseHarvester correctly), 8 production, 1 processing, 2 support. All correct.
8. **3 custom ProductionJobs (FishingLooter, PickupLooter, DiveCollector)** — `targetCategory = null`, loot-table. Correct.
9. **DigSiteExcavator** — declares `SUSPICIOUS`, uses `getTargets(origin, ...)`. Correct.
10. **11 EnvironmentalJobs** — all declare a `targetCategory`, all use `getTargets(origin, ...)` or `CauldronUtils` (which also uses `getTargets(origin, ...)`). Correct.
11. **Magnetizer, GroundItemCollector** — `targetCategory = null`, non-block-based. Correct.

### WARNINGS (not bugs, but worth noting):

1. **`CORAL` and `COMPOSTER` BlockCategory entries have no validator and no job.** They're dead enum values. Consider removing them or adding validators/jobs for completeness.

2. **`KELP`, `LILY_PAD`, `SEA_PICKLE` have validators but no jobs.** The scanner will never scan for these because `getNeededCategories()` only includes categories from registered workers. The validators are inert — no wasted performance, but the code is dormant.

3. **FrostFormer declares `targetCategory = WATER` but also reads from `BlockCategory.ICE`.** This means `getNeededCategories()` only includes `WATER` from FrostFormer's declaration. However, `ICE` is already in `getNeededCategories()` because **ICE_MINER** (GatheringJobs) declares `targetCategory = ICE`. If ICE_MINER were ever disabled/removed, FrostFormer would silently lose access to ICE targets. This is a latent fragility but NOT a current bug.

4. **The `SUSPICIOUS` category validator in BlockCategoryValidators overlaps with `DIRT`.** Both match `Blocks.DIRT`, `Blocks.COARSE_DIRT`, `Blocks.ROOTED_DIRT`, and `Blocks.GRAVEL`. This means the same block position can appear in both `DIRT` and `SUSPICIOUS` target sets. This is intentional (different jobs consume the same blocks for different purposes), but it means `DigSiteExcavator` and `EXCAVATOR` can compete for the same blocks. Not a bug — just a design decision.

### VERDICT: **No cache key mismatches. No incorrect cache usage. No jobs bypass the scan system incorrectly. No undefined BlockCategory values used. The scan-to-job integration is clean.**
