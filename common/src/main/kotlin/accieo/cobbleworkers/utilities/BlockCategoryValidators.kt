/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.utilities

import accieo.cobbleworkers.enums.BlockCategory
import com.cobblemon.mod.common.CobblemonBlocks
import net.minecraft.block.AbstractFurnaceBlock
import net.minecraft.block.BeehiveBlock
import net.minecraft.block.Blocks
import net.minecraft.block.BrewingStandBlock
import net.minecraft.block.NetherWartBlock
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Maps each BlockCategory to a block validator lambda.
 * The deferred scanner runs these to classify blocks by category.
 * New categories are added here as jobs are implemented in later phases.
 */
object BlockCategoryValidators {
    private val APRICORNS_TAG = TagKey.of(RegistryKeys.BLOCK, Identifier.of("cobblemon", "apricorns"))
    private val BERRIES_TAG = TagKey.of(RegistryKeys.BLOCK, Identifier.of("cobblemon", "berries"))
    private val MINTS_TAG = TagKey.of(RegistryKeys.BLOCK, Identifier.of("cobblemon", "mints"))

    private val TUMBLESTONE_BLOCKS = setOf(
        CobblemonBlocks.TUMBLESTONE_CLUSTER,
        CobblemonBlocks.BLACK_TUMBLESTONE_CLUSTER,
        CobblemonBlocks.SKY_TUMBLESTONE_CLUSTER,
    )

    val validators: Map<BlockCategory, (World, BlockPos) -> Boolean> = mapOf(
        // Cobblemon growables
        BlockCategory.APRICORN to { world, pos -> world.getBlockState(pos).isIn(APRICORNS_TAG) },
        BlockCategory.BERRY to { world, pos -> world.getBlockState(pos).isIn(BERRIES_TAG) },
        BlockCategory.MINT to { world, pos -> world.getBlockState(pos).isIn(MINTS_TAG) },
        BlockCategory.TUMBLESTONE to { world, pos -> world.getBlockState(pos).block in TUMBLESTONE_BLOCKS },
        BlockCategory.AMETHYST to { world, pos -> world.getBlockState(pos).block == Blocks.AMETHYST_CLUSTER },

        // Vanilla growables
        BlockCategory.NETHERWART to { world, pos -> world.getBlockState(pos).block is NetherWartBlock },
        BlockCategory.CROP_GRAIN to { world, pos -> world.getBlockState(pos).block in CobbleworkersCropUtils.validCropBlocks },

        // Honey
        BlockCategory.HONEY to { world, pos -> world.getBlockState(pos).block is BeehiveBlock },

        // Farmland (irrigation)
        BlockCategory.FARMLAND to { world, pos -> world.getBlockState(pos).block == Blocks.FARMLAND },

        // Functional blocks
        BlockCategory.FURNACE to { world, pos -> world.getBlockState(pos).block is AbstractFurnaceBlock },
        BlockCategory.BREWING_STAND to { world, pos ->
            world.getBlockState(pos).block is BrewingStandBlock
        },
        BlockCategory.CAULDRON to { world, pos -> world.getBlockState(pos).isOf(Blocks.CAULDRON) },
        BlockCategory.SUSPICIOUS to { world, pos ->
            val block = world.getBlockState(pos).block
            block == Blocks.DIRT || block == Blocks.GRAVEL || block == Blocks.MUD
                || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT
        },

        // Fire (extinguisher)
        BlockCategory.CONTAINER to { world, pos ->
            CobbleworkersInventoryUtils.blockValidator(world, pos)
        },
    )
}
