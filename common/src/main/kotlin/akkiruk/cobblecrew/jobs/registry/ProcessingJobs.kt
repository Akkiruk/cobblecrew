/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.jobs.dsl.ProcessingJob
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
        qualifyingMoves = setOf("temperflare"),
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

    val PAPER_MAKER = object : ProcessingJob(
        name = "paper_maker",
        qualifyingMoves = setOf("slash"),
        particle = ParticleTypes.CLOUD,
        inputCheck = { it.item == Items.SUGAR_CANE && it.count >= 3 },
        transformFn = { input ->
            val batches = input.count / 3
            if (batches > 0) listOf(ItemStack(Items.PAPER, batches * 3)) else emptyList()
        },
    ) {
        override val minExtractAmount: Int = 3
    }

    val COMPOSTER = object : ProcessingJob(
        name = "composter",
        qualifyingMoves = setOf("recycle"),
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
    ) {
        override val minExtractAmount: Int = 7
    }

    fun register() {
        WorkerRegistry.registerAll(
            ORE_SMELTER,
            PAPER_MAKER,
            COMPOSTER,
        )
    }
}
