/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.registry

import accieo.cobbleworkers.enums.BlockCategory
import accieo.cobbleworkers.jobs.WorkerRegistry
import accieo.cobbleworkers.jobs.dsl.GatheringJob
import net.minecraft.block.*
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes

/**
 * New DSL-based gathering jobs (A1–A37).
 * Legacy gathering jobs (Berry, Apricorn, Mint, Netherwart, Tumblestone,
 * Amethyst, Crop, Honey) remain in LegacyJobs until migrated.
 */
object GatheringJobs {

    // ── Wood ────────────────────────────────────────────────────────────

    val OVERWORLD_LOGGER = GatheringJob(
        name = "overworld_logger",
        targetCategory = BlockCategory.LOG_OVERWORLD,
        qualifyingMoves = setOf("cut", "furycutter"),
        particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
    )

    val JUNGLE_CHOPPER = GatheringJob(
        name = "jungle_chopper",
        targetCategory = BlockCategory.LOG_TROPICAL,
        qualifyingMoves = setOf("xscissor", "nightslash", "sacredsword"),
        particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
    )

    val FUNGI_HARVESTER = GatheringJob(
        name = "fungi_harvester",
        targetCategory = BlockCategory.LOG_NETHER,
        qualifyingMoves = setOf("shadowclaw", "poltergeist"),
        fallbackType = "GHOST",
        particle = ParticleTypes.SOUL,
    )

    val BAMBOO_CHOPPER = GatheringJob(
        name = "bamboo_chopper",
        targetCategory = BlockCategory.BAMBOO,
        qualifyingMoves = setOf("falseswipe", "bulletpunch"),
        fallbackSpecies = listOf("Pangoro", "Pancham"),
        particle = ParticleTypes.COMPOSTER,
        readyCheck = { world, pos ->
            // Only harvest if the block above is not bamboo (top segment only)
            world.getBlockState(pos.up()).block != Blocks.BAMBOO
        },
    )

    val SUGAR_CANE_CUTTER = GatheringJob(
        name = "sugar_cane_cutter",
        targetCategory = BlockCategory.SUGAR_CANE,
        qualifyingMoves = setOf("razorleaf", "magicalleaf"),
        particle = ParticleTypes.COMPOSTER,
        readyCheck = { world, pos ->
            world.getBlockState(pos.up()).block != Blocks.SUGAR_CANE
        },
    )

    val CACTUS_PRUNER = GatheringJob(
        name = "cactus_pruner",
        targetCategory = BlockCategory.CACTUS,
        qualifyingMoves = setOf("needlearm", "pinmissile", "spikecannon"),
        fallbackSpecies = listOf("Cacturne", "Cacnea", "Maractus"),
        particle = ParticleTypes.CRIT,
        readyCheck = { world, pos ->
            world.getBlockState(pos.up()).block != Blocks.CACTUS
        },
    )

    val VINE_TRIMMER = GatheringJob(
        name = "vine_trimmer",
        targetCategory = BlockCategory.VINE,
        qualifyingMoves = setOf("vinewhip", "powerwhip"),
        fallbackSpecies = listOf("Tangela", "Tangrowth"),
        particle = ParticleTypes.COMPOSTER,
    )

    // ── Crops & Plants ─────────────────────────────────────────────────
    // A8 Grain Reaper, A9 Root Digger, A11 Berry, A12 Apricorn, A13 Mint, A14 Netherwart
    // are handled by LegacyJobs (CropHarvester, BerryHarvester, ApricornHarvester, etc.)

    val BEET_PULLER = GatheringJob(
        name = "beet_puller",
        targetCategory = BlockCategory.CROP_BEET,
        qualifyingMoves = setOf("strength", "rocksmash"),
        particle = ParticleTypes.COMPOSTER,
        readyCheck = { world, pos ->
            val state = world.getBlockState(pos)
            val block = state.block
            block is CropBlock && block.getAge(state) == block.maxAge
        },
        afterHarvestAction = { world, pos, _ ->
            world.setBlockState(pos, Blocks.AIR.defaultState)
        },
    )

    val SWEET_BERRY_HARVESTER = GatheringJob(
        name = "sweet_berry_harvester",
        targetCategory = BlockCategory.SWEET_BERRY,
        qualifyingMoves = setOf("stuffcheeks"),
        fallbackSpecies = listOf("Teddiursa", "Greedent"),
        particle = ParticleTypes.COMPOSTER,
        readyCheck = { world, pos ->
            val state = world.getBlockState(pos)
            state.block is SweetBerryBushBlock && state.get(SweetBerryBushBlock.AGE) >= 3
        },
        afterHarvestAction = { world, pos, _ ->
            // Reset to age 1 (keeps the bush alive)
            world.setBlockState(pos, Blocks.SWEET_BERRY_BUSH.defaultState.with(SweetBerryBushBlock.AGE, 1))
        },
    )

    val PUMPKIN_MELON_HARVESTER = GatheringJob(
        name = "pumpkin_melon_harvester",
        targetCategory = BlockCategory.PUMPKIN_MELON,
        qualifyingMoves = setOf("stomp", "slam"),
        fallbackType = "GRASS",
        particle = ParticleTypes.COMPOSTER,
    )

    val COCOA_HARVESTER = GatheringJob(
        name = "cocoa_harvester",
        targetCategory = BlockCategory.COCOA,
        qualifyingMoves = setOf("knockoff", "covet"),
        fallbackSpecies = listOf("Tropius"),
        particle = ParticleTypes.COMPOSTER,
        readyCheck = { world, pos ->
            val state = world.getBlockState(pos)
            state.block == Blocks.COCOA && state.get(CocoaBlock.AGE) >= 2
        },
    )

    val CHORUS_FRUIT_HARVESTER = GatheringJob(
        name = "chorus_fruit_harvester",
        targetCategory = BlockCategory.CHORUS,
        qualifyingMoves = setOf("teleport", "psychocut"),
        particle = ParticleTypes.PORTAL,
    )

    val GLOWBERRY_PICKER = GatheringJob(
        name = "glowberry_picker",
        targetCategory = BlockCategory.CAVE_VINE,
        qualifyingMoves = setOf("pluck", "thief"),
        particle = ParticleTypes.GLOW,
        readyCheck = { world, pos ->
            val state = world.getBlockState(pos)
            state.block is CaveVines && CaveVines.hasBerries(state)
        },
        harvestOverride = { world, pos, _ ->
            val state = world.getBlockState(pos)
            CaveVines.pickBerries(null, state, world, pos)
            listOf(ItemStack(Items.GLOW_BERRIES))
        },
    )

    val DRIPLEAF_HARVESTER = GatheringJob(
        name = "dripleaf_harvester",
        targetCategory = BlockCategory.DRIPLEAF,
        qualifyingMoves = setOf("leafblade", "leafage"),
        particle = ParticleTypes.COMPOSTER,
    )

    // ── Stone & Earth ──────────────────────────────────────────────────

    val STONE_BREAKER = GatheringJob(
        name = "stone_breaker",
        targetCategory = BlockCategory.STONE,
        qualifyingMoves = setOf("brickbreak", "smackdown"),
        fallbackType = "ROCK",
        particle = ParticleTypes.CRIT,
    )

    val IGNEOUS_MINER = GatheringJob(
        name = "igneous_miner",
        targetCategory = BlockCategory.IGNEOUS,
        qualifyingMoves = setOf("drillrun", "headsmash"),
        particle = ParticleTypes.CRIT,
    )

    val DEEPSLATE_EXCAVATOR = GatheringJob(
        name = "deepslate_excavator",
        targetCategory = BlockCategory.DEEPSLATE,
        qualifyingMoves = setOf("earthpower", "earthquake"),
        particle = ParticleTypes.CRIT,
    )

    val EXCAVATOR = GatheringJob(
        name = "excavator",
        targetCategory = BlockCategory.DIRT,
        qualifyingMoves = setOf("mudshot", "highhorsepower", "magnitude"),
        fallbackType = "GROUND",
        particle = ParticleTypes.CRIT,
    )

    val SAND_MINER = GatheringJob(
        name = "sand_miner",
        targetCategory = BlockCategory.SAND,
        qualifyingMoves = setOf("sandstorm", "sandattack", "sandtomb", "scorchingsands"),
        fallbackSpecies = listOf("Sandshrew", "Sandslash", "Palossand", "Sandygast"),
        particle = ParticleTypes.CRIT,
    )

    val CLAY_DIGGER = GatheringJob(
        name = "clay_digger",
        targetCategory = BlockCategory.CLAY,
        qualifyingMoves = setOf("mudslap", "mudbomb", "shoreup"),
        particle = ParticleTypes.SPLASH,
    )

    // ── Minerals & Crystals ────────────────────────────────────────────
    // A27 Tumblestone, A28 Amethyst are handled by LegacyJobs

    val SCULK_HARVESTER = GatheringJob(
        name = "sculk_harvester",
        targetCategory = BlockCategory.SCULK,
        qualifyingMoves = setOf("shadowball", "spite"),
        particle = ParticleTypes.SCULK_SOUL,
    )

    val ICE_MINER = GatheringJob(
        name = "ice_miner",
        targetCategory = BlockCategory.ICE,
        qualifyingMoves = setOf("icepunch", "icefang", "iceshard"),
        fallbackType = "ICE",
        particle = ParticleTypes.SNOWFLAKE,
        toolOverride = ItemStack(Items.DIAMOND_PICKAXE), // silk-touch equivalent for ice drops
    )

    // ── Nature & Cleanup ───────────────────────────────────────────────
    // A35 Honey Extractor is handled by LegacyJobs (HoneyCollector)

    val MUSHROOM_FORAGER = GatheringJob(
        name = "mushroom_forager",
        targetCategory = BlockCategory.MUSHROOM,
        qualifyingMoves = setOf("stunspore", "poisonpowder"),
        fallbackSpecies = listOf("Breloom", "Parasect", "Shiinotic"),
        particle = ParticleTypes.SPORE_BLOSSOM_AIR,
    )

    val FLOWER_PICKER = GatheringJob(
        name = "flower_picker",
        targetCategory = BlockCategory.FLOWER,
        qualifyingMoves = setOf("petalblizzard", "petaldance"),
        fallbackSpecies = listOf("Comfey", "Florges"),
        particle = ParticleTypes.CHERRY_LEAVES,
    )

    val SNOW_SCRAPER = GatheringJob(
        name = "snow_scraper",
        targetCategory = BlockCategory.SNOW_BLOCK,
        qualifyingMoves = setOf("icywind", "avalanche", "hail"),
        fallbackType = "ICE",
        particle = ParticleTypes.SNOWFLAKE,
    )

    val MOSS_SCRAPER = GatheringJob(
        name = "moss_scraper",
        targetCategory = BlockCategory.MOSS,
        qualifyingMoves = setOf("grassknot", "gigadrain", "absorb"),
        fallbackSpecies = listOf("Tangela", "Tangrowth"),
        particle = ParticleTypes.COMPOSTER,
    )

    val DECOMPOSER = GatheringJob(
        name = "decomposer",
        targetCategory = BlockCategory.LEAVES,
        qualifyingMoves = setOf("phantomforce", "nightshade"),
        fallbackType = "GHOST",
        particle = ParticleTypes.COMPOSTER,
        harvestOverride = { world, pos, _ ->
            // Leaves → bone meal
            world.setBlockState(pos, Blocks.AIR.defaultState)
            listOf(ItemStack(Items.BONE_MEAL, 1))
        },
    )

    val TERRAIN_FLATTENER = GatheringJob(
        name = "terrain_flattener",
        targetCategory = BlockCategory.VEGETATION,
        qualifyingMoves = setOf("bulldoze", "stompingtantrum", "mudsport"),
        fallbackType = "GROUND",
        particle = ParticleTypes.CRIT,
    )

    fun register() {
        WorkerRegistry.registerAll(
            // Wood
            OVERWORLD_LOGGER,
            JUNGLE_CHOPPER,
            FUNGI_HARVESTER,
            BAMBOO_CHOPPER,
            SUGAR_CANE_CUTTER,
            CACTUS_PRUNER,
            VINE_TRIMMER,
            // Crops & Plants
            BEET_PULLER,
            SWEET_BERRY_HARVESTER,
            PUMPKIN_MELON_HARVESTER,
            COCOA_HARVESTER,
            CHORUS_FRUIT_HARVESTER,
            GLOWBERRY_PICKER,
            DRIPLEAF_HARVESTER,
            // Stone & Earth
            STONE_BREAKER,
            IGNEOUS_MINER,
            DEEPSLATE_EXCAVATOR,
            EXCAVATOR,
            SAND_MINER,
            CLAY_DIGGER,
            // Minerals & Crystals
            SCULK_HARVESTER,
            ICE_MINER,
            // Nature & Cleanup
            MUSHROOM_FORAGER,
            FLOWER_PICKER,
            SNOW_SCRAPER,
            MOSS_SCRAPER,
            DECOMPOSER,
            TERRAIN_FLATTENER,
        )
    }
}
