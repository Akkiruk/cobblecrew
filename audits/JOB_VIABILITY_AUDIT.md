# CobbleCrew Job Viability Audit
**Date:** 2026-02-26  
**Scope:** All 129 jobs across 8 categories  
**Focus:** In-game viability, missing implementations, balance, practical useability

---

## CRITICAL: Combo Jobs with Broken/Missing Mechanics

These combo jobs require multiple rare moves (COMBO priority = highest) but don't actually implement their advertised special behavior. A player finds a Pokémon with the right moves, gets excited — and it just breaks one stone block at a time like any basic gatherer.

### 1. VEIN_MINER — Does not vein mine
- **Moves:** earthquake + dig
- **Advertised:** "Breaks target + connected same-type blocks"
- **Actual:** Breaks one STONE block at a time. `topDownHarvest` is false, no `harvestOverride` to break connected blocks. Functionally identical to STONE_BREAKER but requires two moves instead of one.
- **Fix:** Override `harvest()` to BFS-expand from the target block and break all connected same-type blocks (capped at ~16-32), collecting all drops.

### 2. FORTUNE_MINER — No fortune effect
- **Moves:** powergem + dig
- **Advertised:** "Mining with doubled drops"
- **Actual:** Uses `harvestTool = ItemStack.EMPTY` (default). No doubled drops. Same output as STONE_BREAKER.
- **Fix:** Override `harvest()` to double the drops list, or set `toolOverride` to a Fortune III pickaxe.

### 3. SILK_TOUCH_EXTRACTOR — No silk touch effect
- **Moves:** psychic + any gathering move
- **Advertised:** "Silk-touch harvest"
- **Actual:** No silk touch tool set. `harvestTool` is EMPTY. Stone → cobblestone, not stone. Ice melts instead of dropping.
- **Fix:** Set `toolOverride` to a Silk Touch pickaxe built at init, or override `harvest()` to drop the block itself.

### 4. DEMOLISHER — Not universal
- **Moves:** cut + rocksmash
- **Advertised:** "Breaks ANY block type (universal gatherer)"
- **Actual:** `targetCategory = BlockCategory.STONE`. Only finds stone/cobblestone/mossy cobblestone. Not universal at all.
- **Fix:** Either target multiple categories via `additionalScanCategories`, or make it truly universal by scanning all registered block categories.

### 5. FOSSIL_HUNTER — No fossil loot
- **Moves:** dig + rocksmash
- **Advertised:** "Fossil-weighted loot"
- **Actual:** Uses standard stone loot drops (gets cobblestone). No custom loot table, no fossil items, no special weighting.
- **Fix:** Add a `harvestOverride` that rolls from a custom fossil/bone/clay drop table with chances for actual Cobblemon fossil items.

### 6. GEM_VEIN_FINDER — No gem weighting
- **Moves:** dig + powergem
- **Advertised:** "Gem-weighted loot"
- **Actual:** Same as FOSSIL_HUNTER — standard stone drops, no gem items.
- **Fix:** Add a `harvestOverride` that produces emeralds, diamonds, lapis, amethyst shards, etc. with weighted probability.

### 7. FULL_RESTORE (support combo) — Doesn't clear negatives
- **Moves:** healbell + aromatherapy
- **Advertised:** "Regeneration II + clears all negatives"
- **Actual:** Only applies Regeneration II (amplifier=1). The negative-clearing behavior is never implemented — no `applyEffect` override to call `player.clearStatusEffects()` or selectively remove harmful effects.
- **Fix:** Override `applyEffect()` to iterate the player's active effects and remove all negative ones before applying regen.

### 8. AURA_MASTER (support combo) — Only gives Speed
- **Moves:** calmmind + helpinghand
- **Advertised:** "Speed + Strength + Haste + Resistance"
- **Actual:** Only applies Speed I. The SupportJob DSL only supports one `statusEffect`. The other three buffs are never applied.
- **Fix:** Override `applyEffect()` to add all four effects manually.

---

## HIGH: Balance & Design Issues

### 9. DEEP_SEA_TRAWLER — Trident factory with no water check
- **Moves:** dive + whirlpool, 300s cooldown
- **Output:** 1% Trident, 4% Heart of the Sea, 25% Nautilus Shell, 70% Prismarine Shards
- **Issue:** Unlike FishingLooter and DiveCollector, there's no `requiresWater` check. Works on dry land. Tridents are rare combat weapons dropped by Drowned — 1% per 5 minutes with multiple Pokémon running this is a trident farm. Heart of the Sea is normally single-use per buried treasure.
- **Fix:** Add `requiresWater`. Consider lowering trident chance to 0.1% or removing it. Consider removing Heart of the Sea entirely.

### 10. MAGMA_DIVER — Netherite scrap from overworld
- **Moves:** dive + lavaplume, 300s cooldown
- **Output:** 0.5% Netherite Scrap, 15% Magma Cream, 84.5% Obsidian
- **Issue:** Netherite scrap bypasses ancient debris mining in the Nether, which is intended to be the hardest material to acquire. No dimensional or location check.
- **Fix:** Consider removing netherite scrap, or restrict to Nether dimension only (`world.registryKey == World.NETHER`). Alternatively lower to 0.05%.

### 11. BEET_PULLER — Destroys beets without replanting
- **Move:** strength
- **Issue:** `afterHarvestAction` sets the block to AIR. No replant. Unlike CROP_HARVESTER and ROOT_HARVESTER which both call `harvestCropDsl()` and reset age to 0, beet fields are permanently cleared.
- **Irony:** `CobbleCrewCropUtils.harvestCropDsl()` already handles beet replanting (`Blocks.BEETROOTS -> blockState.with(BeetrootsBlock.AGE, 0)`), so the infrastructure exists.
- **Fix:** Use `harvestOverride` calling `CobbleCrewCropUtils.harvestCropDsl()` like ROOT_HARVESTER, instead of a custom `afterHarvestAction`.

### 12. ELECTRIC_CHARGER — Thematically wrong output
- **Move:** charge, 120s cooldown
- **Output:** 2 glowstone dust
- **Issue:** Electric Pokémon producing glowstone dust (a Nether material) doesn't make sense. STATIC_GENERATOR (discharge → redstone) already fills the electric→redstone niche correctly. Glowstone dust has no electrical connection.
- **Fix:** Change output to something electrically thematic: redstone (overlap with STATIC_GENERATOR), copper ingot, or lightning rod. Or rename to "Light Emitter" and keep glowstone dust.

### 13. PEARL_CREATOR — Misleading name
- **Move:** shellsmash, Clamperl, 180s cooldown
- **Output:** 2 prismarine shards
- **Issue:** Named "Pearl Creator" but produces prismarine shards. Clamperl is literally the Pearl Pokémon. Players will expect ender pearls, or at least prismarine crystals.
- **Fix:** Rename to "Shard Shedder", or change output to ender pearls (1 per cycle, longer cooldown) for a more thematic and useful reward.

### 14. HUNGER_RESTORER — Effect too weak to notice
- **Move:** swallow, 30s cooldown (from base DSL)
- **Output:** Saturation I for 5 seconds
- **Issue:** Saturation I for 5 seconds restores approximately 1 food point and 2 saturation points. In practice, players won't notice this effect. The 200-tick min cooldown on BaseSupport means the worker is idle most of the time applying a barely-visible buff.
- **Fix:** Increase to Saturation II for 10 seconds, or add a second effect like Regeneration I for 10 seconds.

### 15. MILK_PRODUCER — Creates buckets from nothing
- **Move:** milkdrink, Miltank, 120s cooldown
- **Output:** 1 milk bucket
- **Issue:** Buckets require 3 iron ingots to craft. This produces free iron-equivalent items. In early game this is immense value; in late game buckets are throwaway.
- **Verdict:** Acceptable for a species-locked job (Miltank only). No change needed, just noting.

---

## MEDIUM: Functional Issues

### 16. COCOA_HARVESTER — Destroys pods permanently
- **Move:** knockoff
- **Issue:** No `afterHarvestAction` set, so default behavior sets the block to AIR. Cocoa pods are wall-mounted and won't regrow without replanting cocoa beans on jungle logs. Fields are depleted over time.
- **Fix:** Add `afterHarvestAction` that resets cocoa age to 0: `world.setBlockState(pos, state.with(CocoaBlock.AGE, 0))`.

### 17. FishingLooter vs DiveCollector overlap
- **Both require:** dive move
- **FishingLooter:** 120s, fishing loot table
- **DiveCollector:** 210s, pickup loot table  
- **Issue:** A Pokémon with `dive` qualifies for both. The dispatcher randomly picks one. Players have no control. Neither job's name communicates what loot table it uses.
- **Fix:** Give DiveCollector a different qualifying move (e.g., `surf`, `whirlpool`), or merge them with a config toggle for loot table type.

### 18. ORE_SMELTER vs FURNACE_FUELER move overlap
- **Both require:** flamethrower
- **Issue:** A Pokémon with flamethrower qualifies for both ore smelting (processing from barrels) and furnace fueling (environmental). The dispatcher will randomly alternate between them, reducing efficiency of both.
- **Fix:** Change one job's move. FURNACE_FUELER could use `ember` or `fireblast` instead.

### 19. HEALER vs CROP_IRRIGATOR move overlap
- **HEALER** uses lifedew (support)
- **CROP_IRRIGATOR** uses aquaring/lifedew (environmental)
- **WATER_BREATHER** uses aquaring (support)
- **Issue:** A Pokémon with `lifedew` could get assigned to either healing players or irrigating crops. With `aquaring` it could give water breathing or irrigate. The player likely wants one, not random alternation.
- **Fix:** Give CROP_IRRIGATOR a unique move like `muddywater` or `watergun`.

### 20. BEET_PULLER vs ROOT_HARVESTER move overlap
- **Both require:** strength
- **Issue:** A Pokémon with `strength` qualifies for both. ROOT_HARVESTER replants; BEET_PULLER doesn't. If assigned to BEET_PULLER, beets get destroyed. This is already noted in issue #11, but the move overlap makes it actively worse.
- **Fix:** Merge BEET_PULLER into ROOT_HARVESTER's target category, or give BEET_PULLER a unique move.

### 21. DigSiteExcavator SUSPICIOUS category overlaps DIRT
- **SUSPICIOUS targets:** dirt, gravel, mud, coarse dirt, rooted dirt
- **DIRT targets:** dirt, coarse dirt, gravel, rooted dirt
- **Issue:** The block scanner will classify dirt blocks as BOTH SUSPICIOUS and DIRT. A Pokémon with `dig` qualifies for both DigSiteExcavator (production, uses pickup loot table) and EXCAVATOR (gathering, mines dirt). It could randomly pick up dirt blocks or generate loot from them.
- **Fix:** Make SUSPICIOUS validate for actual suspicious sand/gravel blocks only (`Blocks.SUSPICIOUS_SAND`, `Blocks.SUSPICIOUS_GRAVEL`), or add a flag to exclude blocks already matching another category.

---

## LOW: Minor / Cosmetic Issues

### 22. BANNER_CREATOR accessibility
- **Moves:** cut + sketch  
- Sketch is Smeargle-exclusive. Very few players will have a Smeargle with both Cut and Sketch. Low practical usage.
- **Suggestion:** Add fallback species for Smeargle, or change `sketch` to something more accessible like `tailorswipe` or a drawing-related move.

### 23. CACTUS_PRUNER accessibility
- **Move:** needlearm (rare), fallback Cacnea/Cacturne/Maractus
- **Verdict:** Fine — fallback species cover the rarity.

### 24. POLLEN_PACKER vs WAX_PRODUCER overlap
- POLLEN_PACKER: pollenpuff, Ribombee → honey bottle
- WAX_PRODUCER: defendorder/healorder, Combee/Vespiquen → honeycomb
- BEE_POLLINATOR: pollenpuff, Combee/Vespiquen → increases hive honey level
- **Issue:** Combee/Vespiquen with `pollenpuff` would qualify for POLLEN_PACKER (production), WAX_PRODUCER (production), BEE_POLLINATOR (environmental), and potentially HONEY_HARVESTER (gathering). Four jobs for one species lineage.
- **Verdict:** Not a bug — dispatcher picks randomly and all are useful. Just dense overlap for bee-themed Pokémon.

### 25. GrowthAccelerator only random-ticks CropBlock
- **Issue:** Targets `BlockCategory.GROWABLE` which includes saplings, but the actual tick logic only random-ticks `CropBlock` instances: `if (state.block is CropBlock)`. Saplings are found but never accelerated.
- **Fix:** Add sapling ticking: `if (state.block is CropBlock || state.block is SaplingBlock)`.

### 26. FrostFormer ICE category doesn't include Blue Ice
- `BlockCategory.ICE` validates for `Blocks.ICE` and `Blocks.PACKED_ICE`
- FrostFormer chain: Water → Ice → Packed Ice → Blue Ice
- **Issue:** Once the Pokémon creates Blue Ice, it won't be in the ICE scan category anymore (Blue Ice isn't in the validator). This is actually correct behavior — Blue Ice is the terminal state. No fix needed.

---

## Summary Table

| # | Job | Severity | Issue | Fix Effort |
|---|-----|----------|-------|------------|
| 1 | VEIN_MINER | CRITICAL | Doesn't vein mine | Medium |
| 2 | FORTUNE_MINER | CRITICAL | No fortune/doubling | Low |
| 3 | SILK_TOUCH_EXTRACTOR | CRITICAL | No silk touch | Low |
| 4 | DEMOLISHER | CRITICAL | Not universal | Medium |
| 5 | FOSSIL_HUNTER | CRITICAL | No fossil loot | Medium |
| 6 | GEM_VEIN_FINDER | CRITICAL | No gem weighting | Medium |
| 7 | FULL_RESTORE | CRITICAL | No negative clearing | Low |
| 8 | AURA_MASTER | CRITICAL | Only 1/4 buffs applied | Low |
| 9 | DEEP_SEA_TRAWLER | HIGH | Overpowered, no water check | Low |
| 10 | MAGMA_DIVER | HIGH | Netherite from overworld | Low |
| 11 | BEET_PULLER | HIGH | No replant | Low |
| 12 | ELECTRIC_CHARGER | HIGH | Glowstone from electricity | Low |
| 13 | PEARL_CREATOR | HIGH | Name doesn't match output | Low |
| 14 | HUNGER_RESTORER | HIGH | Effect imperceptible | Low |
| 15 | MILK_PRODUCER | — | Noted, acceptable | — |
| 16 | COCOA_HARVESTER | MEDIUM | No replant/age reset | Low |
| 17 | FishingLooter/Dive | MEDIUM | Identical move requirement | Low |
| 18 | ORE_SMELTER/Fueler | MEDIUM | Flamethrower overlap | Low |
| 19 | HEALER/Irrigator | MEDIUM | Lifedew overlap | Low |
| 20 | BEET/ROOT overlap | MEDIUM | Strength → wrong job | Low |
| 21 | DigSite/DIRT overlap | MEDIUM | Same blocks, both dig | Medium |
| 22 | BANNER_CREATOR | LOW | Sketch too rare | Low |
| 23 | CACTUS_PRUNER | LOW | Fine with fallback | — |
| 24 | Bee Pokémon overlap | LOW | 4 jobs, fine | — |
| 25 | GrowthAccelerator | LOW | Ignores saplings | Low |

**Critical fixes needed: 8 combo jobs** that advertise powerful abilities but have no implementation behind them.
