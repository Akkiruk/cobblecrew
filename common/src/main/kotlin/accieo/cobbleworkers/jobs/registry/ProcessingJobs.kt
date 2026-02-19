/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.registry

import accieo.cobbleworkers.jobs.WorkerRegistry
import accieo.cobbleworkers.jobs.dsl.ProcessingJob
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes

/**
 * Processing jobs (C1–C10). Transform items from barrels, deposit results in chests.
 * C11 (Blast Furnace) is a combo job — registered in ComboJobs.
 */
object ProcessingJobs {

    val ORE_SMELTER = ProcessingJob(
        name = "ore_smelter",
        qualifyingMoves = setOf("flamethrower", "fireblast", "magmastorm"),
        fallbackSpecies = listOf("Magcargo", "Coalossal", "Torkoal"),
        particle = ParticleTypes.FLAME,
        inputCheck = { stack ->
            stack.item in setOf(Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER)
        },
        transformFn = { input ->
            when (input.item) {
                Items.RAW_IRON -> listOf(ItemStack(Items.IRON_INGOT, input.count))
                Items.RAW_GOLD -> listOf(ItemStack(Items.GOLD_INGOT, input.count))
                Items.RAW_COPPER -> listOf(ItemStack(Items.COPPER_INGOT, input.count))
                else -> emptyList()
            }
        },
    )

    val FOOD_COOKER = ProcessingJob(
        name = "food_cooker",
        qualifyingMoves = setOf("ember", "firepunch", "flamecharge"),
        fallbackType = "FIRE",
        particle = ParticleTypes.FLAME,
        inputCheck = { stack ->
            stack.item in setOf(
                Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN,
                Items.COD, Items.SALMON, Items.RABBIT, Items.POTATO, Items.KELP,
            )
        },
        transformFn = { input ->
            val cooked = when (input.item) {
                Items.BEEF -> Items.COOKED_BEEF
                Items.PORKCHOP -> Items.COOKED_PORKCHOP
                Items.MUTTON -> Items.COOKED_MUTTON
                Items.CHICKEN -> Items.COOKED_CHICKEN
                Items.COD -> Items.COOKED_COD
                Items.SALMON -> Items.COOKED_SALMON
                Items.RABBIT -> Items.COOKED_RABBIT
                Items.POTATO -> Items.BAKED_POTATO
                Items.KELP -> Items.DRIED_KELP
                else -> null
            }
            if (cooked != null) listOf(ItemStack(cooked, input.count)) else emptyList()
        },
    )

    val GLASS_MAKER = ProcessingJob(
        name = "glass_maker",
        qualifyingMoves = setOf("heatwave", "overheat", "mysticalfire"),
        particle = ParticleTypes.FLAME,
        inputCheck = { it.item == Items.SAND || it.item == Items.RED_SAND },
        transformFn = { input -> listOf(ItemStack(Items.GLASS, input.count)) },
    )

    val BRICK_BAKER = ProcessingJob(
        name = "brick_baker",
        qualifyingMoves = setOf("firespin", "lavaplume"),
        fallbackSpecies = listOf("Numel", "Torkoal", "Slugma", "Rolycoly"),
        particle = ParticleTypes.FLAME,
        inputCheck = { it.item == Items.CLAY_BALL },
        transformFn = { input -> listOf(ItemStack(Items.BRICK, input.count)) },
    )

    val CHARCOAL_BURNER = ProcessingJob(
        name = "charcoal_burner",
        qualifyingMoves = setOf("blastburn", "burnup"),
        fallbackType = "FIRE",
        particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
        inputCheck = { stack ->
            stack.item in setOf(
                Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG,
                Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.CHERRY_LOG, Items.MANGROVE_LOG,
            )
        },
        transformFn = { input -> listOf(ItemStack(Items.CHARCOAL, input.count)) },
    )

    val PAPER_MAKER = ProcessingJob(
        name = "paper_maker",
        qualifyingMoves = setOf("slash", "guillotine"),
        particle = ParticleTypes.CLOUD,
        inputCheck = { it.item == Items.SUGAR_CANE && it.count >= 3 },
        transformFn = { input ->
            val batches = input.count / 3
            if (batches > 0) listOf(ItemStack(Items.PAPER, batches * 3)) else emptyList()
        },
    )

    val BONE_GRINDER = ProcessingJob(
        name = "bone_grinder",
        qualifyingMoves = setOf("bonemerang", "boneclub"),
        fallbackSpecies = listOf("Cubone", "Marowak"),
        particle = ParticleTypes.CRIT,
        inputCheck = { it.item == Items.BONE },
        transformFn = { input -> listOf(ItemStack(Items.BONE_MEAL, input.count * 3)) },
    )

    val FLINT_KNAPPER = ProcessingJob(
        name = "flint_knapper",
        qualifyingMoves = setOf("karatechop", "crosschop"),
        fallbackSpecies = listOf("Golem", "Rhyperior"),
        particle = ParticleTypes.CRIT,
        inputCheck = { it.item == Items.GRAVEL },
        transformFn = { input -> listOf(ItemStack(Items.FLINT, input.count)) },
    )

    val PIGMENT_PRESSER = ProcessingJob(
        name = "pigment_presser",
        qualifyingMoves = setOf("hammerarm", "bodypress"),
        fallbackSpecies = listOf("Pangoro", "Machamp"),
        particle = ParticleTypes.COMPOSTER,
        inputCheck = { stack ->
            stack.item in setOf(
                Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM,
                Items.AZURE_BLUET, Items.RED_TULIP, Items.ORANGE_TULIP, Items.WHITE_TULIP,
                Items.PINK_TULIP, Items.OXEYE_DAISY, Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY,
                Items.WITHER_ROSE, Items.SUNFLOWER, Items.LILAC, Items.ROSE_BUSH, Items.PEONY,
                Items.TORCHFLOWER, Items.PITCHER_PLANT,
            )
        },
        transformFn = { input ->
            val dye = when (input.item) {
                Items.DANDELION -> Items.YELLOW_DYE
                Items.POPPY -> Items.RED_DYE
                Items.BLUE_ORCHID -> Items.LIGHT_BLUE_DYE
                Items.ALLIUM -> Items.MAGENTA_DYE
                Items.AZURE_BLUET -> Items.LIGHT_GRAY_DYE
                Items.RED_TULIP -> Items.RED_DYE
                Items.ORANGE_TULIP -> Items.ORANGE_DYE
                Items.WHITE_TULIP -> Items.LIGHT_GRAY_DYE
                Items.PINK_TULIP -> Items.PINK_DYE
                Items.OXEYE_DAISY -> Items.LIGHT_GRAY_DYE
                Items.CORNFLOWER -> Items.BLUE_DYE
                Items.LILY_OF_THE_VALLEY -> Items.WHITE_DYE
                Items.WITHER_ROSE -> Items.BLACK_DYE
                Items.SUNFLOWER -> Items.YELLOW_DYE
                Items.LILAC -> Items.MAGENTA_DYE
                Items.ROSE_BUSH -> Items.RED_DYE
                Items.PEONY -> Items.PINK_DYE
                Items.TORCHFLOWER -> Items.ORANGE_DYE
                Items.PITCHER_PLANT -> Items.CYAN_DYE
                else -> Items.WHITE_DYE
            }
            listOf(ItemStack(dye, input.count))
        },
    )

    val COMPOSTER = ProcessingJob(
        name = "composter",
        qualifyingMoves = setOf("stockpile", "acidspray"),
        fallbackSpecies = listOf("Trubbish", "Garbodor", "Grimer", "Muk"),
        particle = ParticleTypes.COMPOSTER,
        inputCheck = { stack ->
            stack.item in setOf(
                Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS,
                Items.SHORT_GRASS, Items.KELP, Items.SUGAR_CANE, Items.VINE, Items.FERN,
                Items.TALL_GRASS, Items.LARGE_FERN, Items.DEAD_BUSH,
            )
        },
        transformFn = { input ->
            val batchCount = input.count / 7
            if (batchCount > 0) listOf(ItemStack(Items.BONE_MEAL, batchCount)) else emptyList()
        },
    )

    fun register() {
        WorkerRegistry.registerAll(
            ORE_SMELTER,
            FOOD_COOKER,
            GLASS_MAKER,
            BRICK_BAKER,
            CHARCOAL_BURNER,
            PAPER_MAKER,
            BONE_GRINDER,
            FLINT_KNAPPER,
            PIGMENT_PRESSER,
            COMPOSTER,
        )
    }
}
