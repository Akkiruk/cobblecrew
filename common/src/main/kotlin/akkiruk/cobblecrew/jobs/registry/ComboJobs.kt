/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.jobs.dsl.GatheringJob
import akkiruk.cobblecrew.jobs.dsl.ProductionJob
import akkiruk.cobblecrew.jobs.dsl.ProcessingJob
import akkiruk.cobblecrew.jobs.dsl.SupportJob
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.entity.effect.StatusEffectCategory
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.enchantment.Enchantments
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Direction

/**
 * Combo jobs — require ALL listed moves. COMBO priority (highest).
 *
 * Uses the DSL classes with overridden isEligible to require ALL moves
 * instead of ANY. Each combo wraps a DSL job via anonymous subclass.
 */
object ComboJobs {

    // Helper: ALL-moves eligibility check for combos
    private fun comboEligible(
        requiredMoves: Set<String>,
        config: JobConfig,
        moves: Set<String>,
        types: Set<String>,
        species: String,
    ): Boolean {
        if (!config.enabled) return false
        val eff = config.qualifyingMoves.ifEmpty { requiredMoves }.map { it.lowercase() }.toSet()
        return eff.all { it in moves }
    }

    // Helper: PokemonEntity-based combo eligibility (for BaseHarvester's shouldRun)
    private fun comboEligibleEntity(
        requiredMoves: Set<String>,
        config: JobConfig,
        pokemonEntity: PokemonEntity,
    ): Boolean {
        val active = pokemonEntity.pokemon.moveSet.getMoves().map { it.name.lowercase() }
        val benchedMvs = pokemonEntity.pokemon.benchedMoves.map { it.moveTemplate.name.lowercase() }
        val moves = (active + benchedMvs).toSet()
        val types = pokemonEntity.pokemon.types.map { it.name.uppercase() }.toSet()
        val species = pokemonEntity.pokemon.species.translatedName.string.lowercase()
        return comboEligible(requiredMoves, config, moves, types, species)
    }

    // ── CM1: Demolisher ──────────────────────────────────────────────
    // cut + rocksmash → heavy-duty stone miner for Fighting/Normal types
    val DEMOLISHER = object : GatheringJob(
        name = "demolisher",
        category = "combo",
        targetCategory = BlockCategory.STONE, // targets stone as representative
        qualifyingMoves = setOf("cut", "rocksmash"),
        particle = ParticleTypes.EXPLOSION,
        priority = WorkerPriority.COMBO,
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── CM11: Fortune Miner ──────────────────────────────────────────
    // powergem + dig → mining with Fortune III loot table drops
    val FORTUNE_MINER = object : GatheringJob(
        name = "fortune_miner",
        category = "combo",
        targetCategory = BlockCategory.ORE,
        qualifyingMoves = setOf("powergem", "dig"),
        particle = ParticleTypes.ENCHANT,
        priority = WorkerPriority.COMBO,
        harvestOverride = { world, pos, _ ->
            val state = world.getBlockState(pos)
            val sw = world as ServerWorld
            val fortunePick = ItemStack(Items.DIAMOND_PICKAXE)
            val enchReg = sw.registryManager.get(RegistryKeys.ENCHANTMENT)
            enchReg.getEntry(Enchantments.FORTUNE).ifPresent { fortunePick.addEnchantment(it, 3) }
            val lootBuilder = LootContextParameterSet.Builder(sw)
                .add(LootContextParameters.ORIGIN, pos.toCenterPos())
                .add(LootContextParameters.BLOCK_STATE, state)
                .add(LootContextParameters.TOOL, fortunePick)
            val drops = state.getDroppedStacks(lootBuilder)
            world.setBlockState(pos, Blocks.AIR.defaultState)
            drops
        },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── CM12: Silk Touch Extractor ───────────────────────────────────
    // psychic + any gathering move → drops the block itself (silk touch)
    val SILK_TOUCH_EXTRACTOR = object : GatheringJob(
        name = "silk_touch_extractor",
        category = "combo",
        targetCategory = BlockCategory.STONE,
        qualifyingMoves = setOf("psychic"),
        particle = ParticleTypes.ENCHANT,
        priority = WorkerPriority.COMBO,
        harvestOverride = { world, pos, _ ->
            val state = world.getBlockState(pos)
            val item = state.block.asItem()
            world.setBlockState(pos, Blocks.AIR.defaultState)
            if (item != Items.AIR) listOf(ItemStack(item)) else emptyList()
        },
    ) {
        private val gatheringMoves = setOf("cut", "rocksmash", "dig", "icebeam")

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!JobConfigManager.get(name).enabled) return false
            return "psychic" in moves && gatheringMoves.any { it in moves }
        }
    }

    // ── CM13: Vein Miner ─────────────────────────────────────────────
    // earthquake + dig → breaks target + connected same-type ore blocks (up to 16)
    val VEIN_MINER = object : GatheringJob(
        name = "vein_miner",
        category = "combo",
        targetCategory = BlockCategory.ORE,
        qualifyingMoves = setOf("earthquake", "dig"),
        particle = ParticleTypes.EXPLOSION,
        priority = WorkerPriority.COMBO,
        harvestOverride = { world, pos, _ ->
            val targetBlock = world.getBlockState(pos).block
            val visited = mutableSetOf(pos)
            val frontier = mutableListOf(pos)
            val allDrops = mutableListOf<ItemStack>()
            val pickaxe = ItemStack(Items.DIAMOND_PICKAXE)
            var broken = 0
            while (frontier.isNotEmpty() && broken < 16) {
                val current = frontier.removeFirst()
                if (world.getBlockState(current).block != targetBlock) continue
                val state = world.getBlockState(current)
                val lootBuilder = LootContextParameterSet.Builder(world as ServerWorld)
                    .add(LootContextParameters.ORIGIN, current.toCenterPos())
                    .add(LootContextParameters.BLOCK_STATE, state)
                    .add(LootContextParameters.TOOL, pickaxe)
                allDrops.addAll(state.getDroppedStacks(lootBuilder))
                world.setBlockState(current, Blocks.AIR.defaultState)
                broken++
                for (dir in Direction.entries) {
                    val neighbor = current.offset(dir)
                    if (neighbor !in visited) {
                        visited.add(neighbor)
                        if (world.getBlockState(neighbor).block == targetBlock) {
                            frontier.add(neighbor)
                        }
                    }
                }
            }
            allDrops
        },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── CM14: Tree Feller ────────────────────────────────────────────
    // cut + headbutt → breaks entire tree (all connected logs) at once
    val TREE_FELLER = object : GatheringJob(
        name = "tree_feller",
        category = "combo",
        targetCategory = BlockCategory.LOG_OVERWORLD,
        qualifyingMoves = setOf("cut", "headbutt"),
        particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
        priority = WorkerPriority.COMBO,
        harvestOverride = { world, pos, _ ->
            val targetBlock = world.getBlockState(pos).block
            val visited = mutableSetOf(pos)
            val frontier = mutableListOf(pos)
            val allDrops = mutableListOf<ItemStack>()
            val axe = ItemStack(Items.DIAMOND_AXE)
            var broken = 0
            while (frontier.isNotEmpty() && broken < 64) {
                val current = frontier.removeFirst()
                if (world.getBlockState(current).block != targetBlock) continue
                val state = world.getBlockState(current)
                val lootBuilder = LootContextParameterSet.Builder(world as ServerWorld)
                    .add(LootContextParameters.ORIGIN, current.toCenterPos())
                    .add(LootContextParameters.BLOCK_STATE, state)
                    .add(LootContextParameters.TOOL, axe)
                allDrops.addAll(state.getDroppedStacks(lootBuilder))
                world.setBlockState(current, Blocks.AIR.defaultState)
                broken++
                for (dir in Direction.entries) {
                    val neighbor = current.offset(dir)
                    if (neighbor !in visited) {
                        visited.add(neighbor)
                        if (world.getBlockState(neighbor).block == targetBlock) {
                            frontier.add(neighbor)
                        }
                    }
                }
            }
            allDrops
        },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── CM17: Fossil Hunter ──────────────────────────────────────────
    // dig + rocksmash → fossil/mineral-weighted loot from stone
    val FOSSIL_HUNTER = object : GatheringJob(
        name = "fossil_hunter",
        category = "combo",
        targetCategory = BlockCategory.STONE,
        qualifyingMoves = setOf("dig", "rocksmash"),
        particle = ParticleTypes.CRIT,
        priority = WorkerPriority.COMBO,
        harvestOverride = { world, pos, _ ->
            world.setBlockState(pos, Blocks.AIR.defaultState)
            val roll = Math.random()
            when {
                roll < 0.03 -> listOf(ItemStack(Items.DIAMOND))
                roll < 0.10 -> listOf(ItemStack(Items.GOLD_INGOT, 2))
                roll < 0.25 -> listOf(ItemStack(Items.IRON_INGOT, 2))
                roll < 0.45 -> listOf(ItemStack(Items.BONE, 3))
                roll < 0.65 -> listOf(ItemStack(Items.CLAY_BALL, 4))
                else -> listOf(ItemStack(Items.FLINT, 2))
            }
        },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── CM18: Gem Vein Finder ────────────────────────────────────────
    // powergem + ancientpower → gem-weighted loot from stone
    val GEM_VEIN_FINDER = object : GatheringJob(
        name = "gem_vein_finder",
        category = "combo",
        targetCategory = BlockCategory.STONE,
        qualifyingMoves = setOf("powergem", "ancientpower"),
        particle = ParticleTypes.ENCHANT,
        priority = WorkerPriority.COMBO,
        harvestOverride = { world, pos, _ ->
            world.setBlockState(pos, Blocks.AIR.defaultState)
            val roll = Math.random()
            when {
                roll < 0.05 -> listOf(ItemStack(Items.DIAMOND))
                roll < 0.15 -> listOf(ItemStack(Items.EMERALD, 2))
                roll < 0.35 -> listOf(ItemStack(Items.AMETHYST_SHARD, 3))
                roll < 0.55 -> listOf(ItemStack(Items.LAPIS_LAZULI, 4))
                else -> listOf(ItemStack(Items.QUARTZ, 3))
            }
        },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── CM3: String Crafter ──────────────────────────────────────────
    // cut + stringshot → arrows, leads, or fishing rods (cycles)
    val STRING_CRAFTER = object : ProductionJob(
        name = "string_crafter",
        category = "combo",
        qualifyingMoves = setOf("cut", "stringshot"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.HAPPY_VILLAGER,
        priority = WorkerPriority.COMBO,
        output = { _, _ ->
            when ((Math.random() * 3).toInt()) {
                0 -> listOf(ItemStack(Items.ARROW, 3))
                1 -> listOf(ItemStack(Items.LEAD))
                else -> listOf(ItemStack(Items.FISHING_ROD))
            }
        },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── CM6: Book Binder ─────────────────────────────────────────────
    val BOOK_BINDER = object : ProductionJob(
        name = "book_binder",
        category = "combo",
        qualifyingMoves = setOf("cut", "psychic"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.ENCHANT,
        priority = WorkerPriority.COMBO,
        output = { _, _ -> listOf(ItemStack(Items.BOOK)) },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── CM7: Candle Maker ────────────────────────────────────────────
    val CANDLE_MAKER = object : ProductionJob(
        name = "candle_maker",
        category = "combo",
        qualifyingMoves = setOf("willowisp", "stringshot"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.FLAME,
        priority = WorkerPriority.COMBO,
        output = { _, _ -> listOf(ItemStack(Items.CANDLE)) },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── CM8: Chain Forger ────────────────────────────────────────────
    val CHAIN_FORGER = object : ProductionJob(
        name = "chain_forger",
        category = "combo",
        qualifyingMoves = setOf("ironhead", "firespin"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.LAVA,
        priority = WorkerPriority.COMBO,
        output = { _, _ -> listOf(ItemStack(Items.CHAIN)) },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── CM9: Lantern Builder ─────────────────────────────────────────
    val LANTERN_BUILDER = object : ProductionJob(
        name = "lantern_builder",
        category = "combo",
        qualifyingMoves = setOf("flashcannon", "ironhead"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.END_ROD,
        priority = WorkerPriority.COMBO,
        output = { _, _ -> listOf(ItemStack(Items.LANTERN)) },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── CM10: Banner Creator ─────────────────────────────────────────
    val BANNER_CREATOR = object : ProductionJob(
        name = "banner_creator",
        category = "combo",
        qualifyingMoves = setOf("cut", "sketch"),
        defaultCooldownSeconds = 240,
        particle = ParticleTypes.HAPPY_VILLAGER,
        priority = WorkerPriority.COMBO,
        output = { _, _ ->
            val banners = listOf(
                Items.WHITE_BANNER, Items.ORANGE_BANNER, Items.MAGENTA_BANNER,
                Items.LIGHT_BLUE_BANNER, Items.YELLOW_BANNER, Items.LIME_BANNER,
                Items.PINK_BANNER, Items.GRAY_BANNER, Items.LIGHT_GRAY_BANNER,
                Items.CYAN_BANNER, Items.PURPLE_BANNER, Items.BLUE_BANNER,
                Items.BROWN_BANNER, Items.GREEN_BANNER, Items.RED_BANNER, Items.BLACK_BANNER,
            )
            listOf(ItemStack(banners[(Math.random() * banners.size).toInt()]))
        },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── CM15: Deep Sea Trawler ───────────────────────────────────────
    val DEEP_SEA_TRAWLER = object : ProductionJob(
        name = "deep_sea_trawler",
        category = "combo",
        qualifyingMoves = setOf("dive", "whirlpool"),
        defaultCooldownSeconds = 300,
        particle = ParticleTypes.BUBBLE,
        priority = WorkerPriority.COMBO,
        output = { _, _ ->
            val roll = Math.random()
            when {
                roll < 0.03 -> listOf(ItemStack(Items.HEART_OF_THE_SEA))
                roll < 0.25 -> listOf(ItemStack(Items.NAUTILUS_SHELL))
                roll < 0.50 -> listOf(ItemStack(Items.PRISMARINE_CRYSTALS, 2))
                else -> listOf(ItemStack(Items.PRISMARINE_SHARD, 3))
            }
        },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── CM16: Magma Diver ────────────────────────────────────────────
    val MAGMA_DIVER = object : ProductionJob(
        name = "magma_diver",
        category = "combo",
        qualifyingMoves = setOf("dive", "lavaplume"),
        defaultCooldownSeconds = 300,
        particle = ParticleTypes.LAVA,
        priority = WorkerPriority.COMBO,
        output = { _, _ ->
            val roll = Math.random()
            when {
                roll < 0.03 -> listOf(ItemStack(Items.BLAZE_ROD, 2))
                roll < 0.15 -> listOf(ItemStack(Items.MAGMA_CREAM, 3))
                roll < 0.35 -> listOf(ItemStack(Items.GOLD_NUGGET, 5))
                else -> listOf(ItemStack(Items.OBSIDIAN))
            }
        },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    private val RAW_MATERIALS = setOf(Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER)

    // ── C11: Blast Furnace (combo processing) ────────────────────────
    // overheat + ironhead → smelt raw ores into ingots at 2x rate
    val BLAST_FURNACE = object : ProcessingJob(
        name = "blast_furnace",
        category = "combo",
        qualifyingMoves = setOf("overheat", "ironhead"),
        particle = ParticleTypes.LAVA,
        priority = WorkerPriority.COMBO,
        inputCheck = { stack -> stack.item in RAW_MATERIALS },
        transformFn = { input ->
            val ingot = when (input.item) {
                Items.RAW_IRON -> Items.IRON_INGOT
                Items.RAW_GOLD -> Items.GOLD_INGOT
                Items.RAW_COPPER -> Items.COPPER_INGOT
                else -> null
            }
            if (ingot != null) listOf(ItemStack(ingot, input.count * 2)) else emptyList()
        },
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)
    }

    // ── F11: Full Restore (combo support) ────────────────────────────
    // healbell + aromatherapy → Regeneration II + clears all harmful effects
    val FULL_RESTORE = object : SupportJob(
        name = "full_restore",
        category = "combo",
        qualifyingMoves = setOf("healbell", "aromatherapy"),
        particle = ParticleTypes.HEART,
        statusEffect = StatusEffects.REGENERATION,
        defaultDurationSeconds = 30,
        effectAmplifier = 1,
        priority = WorkerPriority.COMBO,
        workBoostPercent = 10,
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)

        override fun applyEffect(player: PlayerEntity) {
            player.statusEffects
                .filter { it.effectType.value().category == StatusEffectCategory.HARMFUL }
                .map { it.effectType }
                .toList()
                .forEach { player.removeStatusEffect(it) }
            super.applyEffect(player)
        }
    }

    // ── F12: Aura Master (combo support) ─────────────────────────────
    // calmmind + helpinghand → Speed + Strength + Haste + Resistance
    val AURA_MASTER = object : SupportJob(
        name = "aura_master",
        category = "combo",
        qualifyingMoves = setOf("calmmind", "helpinghand"),
        particle = ParticleTypes.ENCHANT,
        statusEffect = StatusEffects.SPEED, // primary; all 4 applied via override
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
        priority = WorkerPriority.COMBO,
        workBoostPercent = 20,
    ) {
        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            comboEligible(qualifyingMoves, JobConfigManager.get(name), moves, types, species)

        override fun applyEffect(player: PlayerEntity) {
            listOf(StatusEffects.SPEED, StatusEffects.STRENGTH, StatusEffects.HASTE, StatusEffects.RESISTANCE)
                .forEach { effect ->
                    if (!player.hasStatusEffect(effect)) {
                        player.addStatusEffect(StatusEffectInstance(effect, effectDurationTicks, 0))
                    }
                }
        }
    }

    fun register() {
        WorkerRegistry.registerAll(
            // Gathering combos
            DEMOLISHER, FORTUNE_MINER, SILK_TOUCH_EXTRACTOR, VEIN_MINER,
            TREE_FELLER, FOSSIL_HUNTER, GEM_VEIN_FINDER,
            // Production combos
            STRING_CRAFTER, BOOK_BINDER, CANDLE_MAKER, CHAIN_FORGER,
            LANTERN_BUILDER, BANNER_CREATOR, DEEP_SEA_TRAWLER, MAGMA_DIVER,
            // Processing combo
            BLAST_FURNACE,
            // Support combos
            FULL_RESTORE, AURA_MASTER,
        )
    }
}
