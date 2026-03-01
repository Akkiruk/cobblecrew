/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.jobs.dsl.GatheringJob
import akkiruk.cobblecrew.jobs.dsl.ProductionJob
import akkiruk.cobblecrew.jobs.dsl.ProcessingJob
import akkiruk.cobblecrew.jobs.dsl.SupportJob
import akkiruk.cobblecrew.utilities.floodFillHarvest
import akkiruk.cobblecrew.utilities.treeHarvest
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

/**
 * Combo jobs — require ALL listed moves. COMBO priority (highest).
 * Uses `isCombo = true` on DSL classes for ALL-move eligibility.
 */
object ComboJobs {

    // ── CM1: Demolisher ──────────────────────────────────────────────
    val DEMOLISHER = GatheringJob(
        name = "demolisher",
        category = "combo",
        targetCategory = BlockCategory.STONE,
        qualifyingMoves = setOf("rocksmash", "smackdown"),
        particle = ParticleTypes.EXPLOSION,
        priority = WorkerPriority.COMBO,
        isCombo = true,
    )

    // ── CM11: Fortune Miner ──────────────────────────────────────────
    val FORTUNE_MINER = GatheringJob(
        name = "fortune_miner",
        category = "combo",
        targetCategory = BlockCategory.ORE,
        qualifyingMoves = setOf("ancientpower", "psychic"),
        particle = ParticleTypes.ENCHANT,
        priority = WorkerPriority.COMBO,
        isCombo = true,
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
            world.breakBlock(pos, false)
            drops
        },
    )

    // ── CM12: Silk Touch Extractor ───────────────────────────────────
    val SILK_TOUCH_EXTRACTOR = GatheringJob(
        name = "silk_touch_extractor",
        category = "combo",
        targetCategory = BlockCategory.STONE,
        qualifyingMoves = setOf("mindreader", "ironhead"),
        particle = ParticleTypes.ENCHANT,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        harvestOverride = { world, pos, _ ->
            val state = world.getBlockState(pos)
            val item = state.block.asItem()
            world.breakBlock(pos, false)
            if (item != Items.AIR) listOf(ItemStack(item)) else emptyList()
        },
    )

    // ── CM13: Vein Miner ─────────────────────────────────────────────
    val VEIN_MINER = GatheringJob(
        name = "vein_miner",
        category = "combo",
        targetCategory = BlockCategory.ORE,
        qualifyingMoves = setOf("earthquake", "crosschop"),
        particle = ParticleTypes.EXPLOSION,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        harvestOverride = { world, pos, _ -> floodFillHarvest(world, pos, 16, ItemStack(Items.DIAMOND_PICKAXE)) },
    )

    // ── CM14: Tree Feller ────────────────────────────────────────────
    // Fells entire tree + strips leaves for bonus loot (saplings, sticks, apples)
    val TREE_FELLER = GatheringJob(
        name = "tree_feller",
        category = "combo",
        targetCategory = BlockCategory.LOG,
        qualifyingMoves = setOf("headbutt", "powerwhip"),
        particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        harvestOverride = { world, pos, _ ->
            val result = treeHarvest(world, pos, maxLogs = 128, tool = ItemStack(Items.DIAMOND_AXE), includeLeaves = true)
            result.brokenPositions.forEach { broken ->
                CobbleCrewCacheManager.removeTargetGlobal(BlockCategory.LOG, broken)
            }
            result.drops
        },
    )

    // ── CM17: Fossil Hunter ──────────────────────────────────────────
    val FOSSIL_HUNTER = GatheringJob(
        name = "fossil_hunter",
        category = "combo",
        targetCategory = BlockCategory.STONE,
        qualifyingMoves = setOf("boneclub", "furycutter"),
        particle = ParticleTypes.CRIT,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        harvestOverride = { world, pos, _ ->
            world.breakBlock(pos, false)
            val roll = world.random.nextDouble()
            when {
                roll < 0.03 -> listOf(ItemStack(Items.DIAMOND))
                roll < 0.10 -> listOf(ItemStack(Items.GOLD_INGOT, 2))
                roll < 0.25 -> listOf(ItemStack(Items.IRON_INGOT, 2))
                roll < 0.45 -> listOf(ItemStack(Items.BONE, 3))
                roll < 0.65 -> listOf(ItemStack(Items.CLAY_BALL, 4))
                else -> listOf(ItemStack(Items.FLINT, 2))
            }
        },
    )

    // ── CM18: Gem Vein Finder ────────────────────────────────────────
    val GEM_VEIN_FINDER = GatheringJob(
        name = "gem_vein_finder",
        category = "combo",
        targetCategory = BlockCategory.STONE,
        qualifyingMoves = setOf("flashcannon", "foresight"),
        particle = ParticleTypes.ENCHANT,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        harvestOverride = { world, pos, _ ->
            world.breakBlock(pos, false)
            val roll = world.random.nextDouble()
            when {
                roll < 0.05 -> listOf(ItemStack(Items.DIAMOND))
                roll < 0.15 -> listOf(ItemStack(Items.EMERALD, 2))
                roll < 0.35 -> listOf(ItemStack(Items.AMETHYST_SHARD, 3))
                roll < 0.55 -> listOf(ItemStack(Items.LAPIS_LAZULI, 4))
                else -> listOf(ItemStack(Items.QUARTZ, 3))
            }
        },
    )

    // ── CM3: String Crafter ──────────────────────────────────────────
    val STRING_CRAFTER = ProductionJob(
        name = "string_crafter",
        category = "combo",
        qualifyingMoves = setOf("stringshot", "xscissor"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.HAPPY_VILLAGER,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        output = { world, _ ->
            when (world.random.nextInt(3)) {
                0 -> listOf(ItemStack(Items.ARROW, 3))
                1 -> listOf(ItemStack(Items.LEAD))
                else -> listOf(ItemStack(Items.FISHING_ROD))
            }
        },
    )

    // ── CM6: Book Binder ─────────────────────────────────────────────
    val BOOK_BINDER = ProductionJob(
        name = "book_binder",
        category = "combo",
        qualifyingMoves = setOf("sketch", "shadowclaw"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.ENCHANT,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        output = { _, _ -> listOf(ItemStack(Items.BOOK)) },
    )

    // ── CM7: Candle Maker ────────────────────────────────────────────
    val CANDLE_MAKER = ProductionJob(
        name = "candle_maker",
        category = "combo",
        qualifyingMoves = setOf("willowisp", "aromatherapy"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.FLAME,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        output = { _, _ -> listOf(ItemStack(Items.CANDLE)) },
    )

    // ── CM8: Chain Forger ────────────────────────────────────────────
    val CHAIN_FORGER = ProductionJob(
        name = "chain_forger",
        category = "combo",
        qualifyingMoves = setOf("metalclaw", "bodypress"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.LAVA,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        output = { _, _ -> listOf(ItemStack(Items.CHAIN)) },
    )

    // ── CM9: Lantern Builder ─────────────────────────────────────────
    val LANTERN_BUILDER = ProductionJob(
        name = "lantern_builder",
        category = "combo",
        qualifyingMoves = setOf("sunnyday", "irontail"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.END_ROD,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        output = { _, _ -> listOf(ItemStack(Items.LANTERN)) },
    )

    // ── CM10: Banner Creator ─────────────────────────────────────────
    val BANNER_CREATOR = ProductionJob(
        name = "banner_creator",
        category = "combo",
        qualifyingMoves = setOf("dazzlinggleam", "tailwhip"),
        defaultCooldownSeconds = 240,
        particle = ParticleTypes.HAPPY_VILLAGER,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        output = { world, _ ->
            val banners = listOf(
                Items.WHITE_BANNER, Items.ORANGE_BANNER, Items.MAGENTA_BANNER,
                Items.LIGHT_BLUE_BANNER, Items.YELLOW_BANNER, Items.LIME_BANNER,
                Items.PINK_BANNER, Items.GRAY_BANNER, Items.LIGHT_GRAY_BANNER,
                Items.CYAN_BANNER, Items.PURPLE_BANNER, Items.BLUE_BANNER,
                Items.BROWN_BANNER, Items.GREEN_BANNER, Items.RED_BANNER, Items.BLACK_BANNER,
            )
            listOf(ItemStack(banners[world.random.nextInt(banners.size)]))
        },
    )

    // ── CM15: Deep Sea Trawler ───────────────────────────────────────
    val DEEP_SEA_TRAWLER = ProductionJob(
        name = "deep_sea_trawler",
        category = "combo",
        qualifyingMoves = setOf("whirlpool", "raindance"),
        defaultCooldownSeconds = 300,
        particle = ParticleTypes.BUBBLE,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        output = { world, _ ->
            val roll = world.random.nextDouble()
            when {
                roll < 0.03 -> listOf(ItemStack(Items.HEART_OF_THE_SEA))
                roll < 0.25 -> listOf(ItemStack(Items.NAUTILUS_SHELL))
                roll < 0.50 -> listOf(ItemStack(Items.PRISMARINE_CRYSTALS, 2))
                else -> listOf(ItemStack(Items.PRISMARINE_SHARD, 3))
            }
        },
    )

    // ── CM16: Magma Diver ────────────────────────────────────────────
    val MAGMA_DIVER = ProductionJob(
        name = "magma_diver",
        category = "combo",
        qualifyingMoves = setOf("overheat", "darkpulse"),
        defaultCooldownSeconds = 300,
        particle = ParticleTypes.LAVA,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        output = { world, _ ->
            val roll = world.random.nextDouble()
            when {
                roll < 0.03 -> listOf(ItemStack(Items.BLAZE_ROD, 2))
                roll < 0.15 -> listOf(ItemStack(Items.MAGMA_CREAM, 3))
                roll < 0.35 -> listOf(ItemStack(Items.GOLD_NUGGET, 5))
                else -> listOf(ItemStack(Items.OBSIDIAN))
            }
        },
    )

    private val RAW_MATERIALS = setOf(Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER)

    // ── C11: Blast Furnace ───────────────────────────────────────────
    val BLAST_FURNACE = ProcessingJob(
        name = "blast_furnace",
        category = "combo",
        qualifyingMoves = setOf("scald", "firepunch"),
        particle = ParticleTypes.LAVA,
        priority = WorkerPriority.COMBO,
        isCombo = true,
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
    )

    // ── F11: Full Restore (custom applyEffect) ──────────────────────
    val FULL_RESTORE = object : SupportJob(
        name = "full_restore",
        category = "combo",
        qualifyingMoves = setOf("healbell", "lifedew"),
        particle = ParticleTypes.HEART,
        statusEffect = StatusEffects.REGENERATION,
        defaultDurationSeconds = 30,
        effectAmplifier = 1,
        priority = WorkerPriority.COMBO,
        workBoostPercent = 10,
        isCombo = true,
    ) {
        override fun applyEffect(player: PlayerEntity) {
            player.statusEffects
                .filter { it.effectType.value().category == StatusEffectCategory.HARMFUL }
                .map { it.effectType }
                .toList()
                .forEach { player.removeStatusEffect(it) }
            super.applyEffect(player)
        }
    }

    // ── F12: Aura Master (custom applyEffect) ───────────────────────
    val AURA_MASTER = object : SupportJob(
        name = "aura_master",
        category = "combo",
        qualifyingMoves = setOf("calmmind", "helpinghand"),
        particle = ParticleTypes.ENCHANT,
        statusEffect = StatusEffects.SPEED,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
        priority = WorkerPriority.COMBO,
        workBoostPercent = 20,
        isCombo = true,
    ) {
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
            DEMOLISHER, FORTUNE_MINER, SILK_TOUCH_EXTRACTOR, VEIN_MINER,
            TREE_FELLER, FOSSIL_HUNTER, GEM_VEIN_FINDER,
            STRING_CRAFTER, BOOK_BINDER, CANDLE_MAKER, CHAIN_FORGER,
            LANTERN_BUILDER, BANNER_CREATOR, DEEP_SEA_TRAWLER, MAGMA_DIVER,
            BLAST_FURNACE,
            FULL_RESTORE, AURA_MASTER,
        )
    }
}
