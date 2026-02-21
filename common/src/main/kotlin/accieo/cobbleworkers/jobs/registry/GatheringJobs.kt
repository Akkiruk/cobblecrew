/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.registry

import accieo.cobbleworkers.config.JobConfigManager
import accieo.cobbleworkers.enums.BlockCategory
import accieo.cobbleworkers.jobs.WorkerRegistry
import accieo.cobbleworkers.jobs.dsl.GatheringJob
import accieo.cobbleworkers.utilities.CobbleworkersCropUtils
import accieo.cobbleworkers.utilities.CobbleworkersTags
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.ApricornBlock
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.block.MintBlock
import com.cobblemon.mod.common.block.entity.BerryBlockEntity
import net.minecraft.block.*
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.state.property.Properties

/**
 * All gathering jobs — find blocks, walk to them, harvest drops, deposit in chests.
 */
object GatheringJobs {
    private val TUMBLESTONE_BLOCKS: Set<Block> = setOf(
        CobblemonBlocks.TUMBLESTONE_CLUSTER,
        CobblemonBlocks.BLACK_TUMBLESTONE_CLUSTER,
        CobblemonBlocks.SKY_TUMBLESTONE_CLUSTER,
    )
    private val TUMBLESTONE_REPLACEMENTS: Map<Block, Block> = mapOf(
        CobblemonBlocks.TUMBLESTONE_CLUSTER to CobblemonBlocks.SMALL_BUDDING_TUMBLESTONE,
        CobblemonBlocks.BLACK_TUMBLESTONE_CLUSTER to CobblemonBlocks.SMALL_BUDDING_BLACK_TUMBLESTONE,
        CobblemonBlocks.SKY_TUMBLESTONE_CLUSTER to CobblemonBlocks.SMALL_BUDDING_SKY_TUMBLESTONE,
    )

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
        particle = ParticleTypes.SOUL,
    )

    val BAMBOO_CHOPPER = GatheringJob(
        name = "bamboo_chopper",
        targetCategory = BlockCategory.BAMBOO,
        qualifyingMoves = setOf("falseswipe", "bulletpunch"),
        fallbackSpecies = listOf("Pancham", "Pangoro"),
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
        qualifyingMoves = setOf("needlearm", "pinmissile"),
        fallbackSpecies = listOf("Cacnea", "Cacturne", "Maractus"),
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
        fallbackSpecies = listOf("Greedent"),
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
        particle = ParticleTypes.COMPOSTER,
    )

    val COCOA_HARVESTER = GatheringJob(
        name = "cocoa_harvester",
        targetCategory = BlockCategory.COCOA,
        qualifyingMoves = setOf("knockoff", "covet"),
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
        qualifyingMoves = setOf("mudshot", "highhorsepower"),
        particle = ParticleTypes.CRIT,
    )

    val SAND_MINER = GatheringJob(
        name = "sand_miner",
        targetCategory = BlockCategory.SAND,
        qualifyingMoves = setOf("sandtomb", "scorchingsands"),
        particle = ParticleTypes.CRIT,
    )

    val CLAY_DIGGER = GatheringJob(
        name = "clay_digger",
        targetCategory = BlockCategory.CLAY,
        qualifyingMoves = setOf("mudslap", "shoreup"),
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
        qualifyingMoves = setOf("icepunch", "icefang"),
        particle = ParticleTypes.SNOWFLAKE,
        toolOverride = ItemStack(Items.DIAMOND_PICKAXE), // silk-touch equivalent for ice drops
    )

    // ── Nature & Cleanup ───────────────────────────────────────────────

    val MUSHROOM_FORAGER = GatheringJob(
        name = "mushroom_forager",
        targetCategory = BlockCategory.MUSHROOM,
        qualifyingMoves = setOf("stunspore", "poisonpowder"),
        fallbackSpecies = listOf("Foongus", "Amoonguss", "Shroomish", "Breloom"),
        particle = ParticleTypes.SPORE_BLOSSOM_AIR,
    )

    val FLOWER_PICKER = GatheringJob(
        name = "flower_picker",
        targetCategory = BlockCategory.FLOWER,
        qualifyingMoves = setOf("petalblizzard", "petaldance"),
        fallbackSpecies = listOf("Comfey"),
        particle = ParticleTypes.CHERRY_LEAVES,
    )

    val SNOW_SCRAPER = GatheringJob(
        name = "snow_scraper",
        targetCategory = BlockCategory.SNOW_BLOCK,
        qualifyingMoves = setOf("icywind", "avalanche"),
        particle = ParticleTypes.SNOWFLAKE,
    )

    val MOSS_SCRAPER = GatheringJob(
        name = "moss_scraper",
        targetCategory = BlockCategory.MOSS,
        qualifyingMoves = setOf("grassknot", "gigadrain"),
        particle = ParticleTypes.COMPOSTER,
    )

    val DECOMPOSER = GatheringJob(
        name = "decomposer",
        targetCategory = BlockCategory.LEAVES,
        qualifyingMoves = setOf("phantomforce", "nightshade"),
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
        qualifyingMoves = setOf("bulldoze", "stompingtantrum"),
        particle = ParticleTypes.CRIT,
    )

    // ── Cobblemon Growables (migrated from legacy) ─────────────────────

    val APRICORN_HARVESTER = GatheringJob(
        name = "apricorn_harvester",
        targetCategory = BlockCategory.APRICORN,
        qualifyingMoves = setOf("pluck", "bugbite"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        readyCheck = { world, pos ->
            world.getBlockState(pos).isIn(CobbleworkersTags.Blocks.APRICORNS)
                && world.getBlockState(pos).get(ApricornBlock.AGE) == ApricornBlock.MAX_AGE
        },
        afterHarvestAction = { world, pos, state ->
            if (state.isIn(CobbleworkersTags.Blocks.APRICORNS)) {
                world.setBlockState(pos, state.with(ApricornBlock.AGE, 0), Block.NOTIFY_ALL)
            }
        },
    )

    val BERRY_HARVESTER = GatheringJob(
        name = "berry_harvester",
        targetCategory = BlockCategory.BERRY,
        qualifyingMoves = setOf("pluck", "stuffcheeks"),
        fallbackSpecies = listOf("Tropius"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        readyCheck = { world, pos ->
            val state = world.getBlockState(pos)
            state.isIn(CobbleworkersTags.Blocks.BERRIES) && state.get(BerryBlock.AGE) == BerryBlock.FRUIT_AGE
        },
        harvestOverride = { world, pos, pokemon ->
            val state = world.getBlockState(pos)
            val be = world.getBlockEntity(pos) as? BerryBlockEntity
            val drops = be?.harvest(world, state, pos, null) ?: emptyList()
            world.setBlockState(pos, state.with(BerryBlock.AGE, BerryBlock.MATURE_AGE), Block.NOTIFY_ALL)
            @Suppress("UNCHECKED_CAST")
            drops as List<ItemStack>
        },
    )

    val MINT_HARVESTER = GatheringJob(
        name = "mint_harvester",
        targetCategory = BlockCategory.MINT,
        qualifyingMoves = setOf("aromatherapy", "sweetscent"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        readyCheck = { world, pos ->
            world.getBlockState(pos).isIn(CobbleworkersTags.Blocks.MINTS)
                && world.getBlockState(pos).get(MintBlock.AGE) == MintBlock.MATURE_AGE
        },
        afterHarvestAction = { world, pos, state ->
            if (state.isIn(CobbleworkersTags.Blocks.MINTS)) {
                world.setBlockState(pos, state.with(MintBlock.AGE, 0), Block.NOTIFY_ALL)
            }
        },
    )

    val AMETHYST_HARVESTER = GatheringJob(
        name = "amethyst_harvester",
        targetCategory = BlockCategory.AMETHYST,
        qualifyingMoves = setOf("rockblast", "powergem"),
        particle = ParticleTypes.END_ROD,
        tolerance = 1.5,
    )

    val TUMBLESTONE_HARVESTER = GatheringJob(
        name = "tumblestone_harvester",
        targetCategory = BlockCategory.TUMBLESTONE,
        qualifyingMoves = setOf("ironhead", "meteormash"),
        particle = ParticleTypes.COMPOSTER,
        tolerance = 1.5,
        afterHarvestAction = { world, pos, state ->
            val replant = JobConfigManager.get("tumblestone_harvester").replant ?: true
            if (replant && state.block in TUMBLESTONE_BLOCKS) {
                val replacement = TUMBLESTONE_REPLACEMENTS[state.block] ?: return@GatheringJob
                var rState = replacement.defaultState
                val facing = Properties.FACING
                if (rState.properties.contains(facing) && state.contains(facing)) {
                    rState = rState.with(facing, state.get(facing))
                }
                world.setBlockState(pos, rState)
            } else {
                world.setBlockState(pos, Blocks.AIR.defaultState)
            }
        },
    )

    val NETHERWART_HARVESTER = GatheringJob(
        name = "netherwart_harvester",
        targetCategory = BlockCategory.NETHERWART,
        qualifyingMoves = setOf("nightmare", "hex"),
        particle = ParticleTypes.SMOKE,
        readyCheck = { world, pos ->
            world.getBlockState(pos).get(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE
        },
        afterHarvestAction = { world, pos, state ->
            val replant = JobConfigManager.get("netherwart_harvester").replant ?: true
            val newState = if (replant) state.with(NetherWartBlock.AGE, 0) else Blocks.AIR.defaultState
            world.setBlockState(pos, newState, Block.NOTIFY_ALL)
        },
    )

    val CROP_HARVESTER = GatheringJob(
        name = "crop_harvester",
        targetCategory = BlockCategory.CROP_GRAIN,
        qualifyingMoves = setOf("harvest", "razorleaf"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        readyCheck = { world, pos ->
            CobbleworkersCropUtils.isMatureCrop(world, pos)
        },
        harvestOverride = { world, pos, pokemon ->
            CobbleworkersCropUtils.harvestCropDsl(world, pos, pokemon)
        },
    )

    val ROOT_HARVESTER = GatheringJob(
        name = "root_harvester",
        targetCategory = BlockCategory.CROP_ROOT,
        qualifyingMoves = setOf("dig", "strength"),
        particle = ParticleTypes.COMPOSTER,
        readyCheck = { world, pos ->
            val state = world.getBlockState(pos)
            val block = state.block
            block is CropBlock && block.getAge(state) == block.maxAge
        },
        harvestOverride = { world, pos, pokemon ->
            CobbleworkersCropUtils.harvestCropDsl(world, pos, pokemon)
        },
    )

    val HONEY_HARVESTER = GatheringJob(
        name = "honey_harvester",
        targetCategory = BlockCategory.HONEY,
        qualifyingMoves = setOf("bugbuzz", "attackorder"),
        fallbackSpecies = listOf("Combee", "Vespiquen"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        readyCheck = { world, pos ->
            val state = world.getBlockState(pos)
            state.block is BeehiveBlock && state.get(BeehiveBlock.HONEY_LEVEL) == BeehiveBlock.FULL_HONEY_LEVEL
        },
        harvestOverride = { world, pos, _ ->
            val state = world.getBlockState(pos)
            world.setBlockState(pos, state.with(BeehiveBlock.HONEY_LEVEL, 0), Block.NOTIFY_ALL)
            listOf(ItemStack(Items.HONEYCOMB, 3))
        },
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
            // Cobblemon Growables (migrated from legacy)
            APRICORN_HARVESTER,
            BERRY_HARVESTER,
            MINT_HARVESTER,
            AMETHYST_HARVESTER,
            TUMBLESTONE_HARVESTER,
            NETHERWART_HARVESTER,
            CROP_HARVESTER,
            ROOT_HARVESTER,
            HONEY_HARVESTER,
        )
    }
}
