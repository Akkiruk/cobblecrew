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
import net.minecraft.block.*
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Maps each BlockCategory to a block validator lambda.
 * The deferred scanner runs these to classify blocks by category.
 * New categories are added here as jobs are implemented in later phases.
 */
object BlockCategoryValidators {
    private val TUMBLESTONE_BLOCKS = setOf(
        CobblemonBlocks.TUMBLESTONE_CLUSTER,
        CobblemonBlocks.BLACK_TUMBLESTONE_CLUSTER,
        CobblemonBlocks.SKY_TUMBLESTONE_CLUSTER,
    )

    private val OVERWORLD_LOGS = setOf(Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG)
    private val TROPICAL_LOGS = setOf(Blocks.JUNGLE_LOG, Blocks.CHERRY_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG)
    private val NETHER_LOGS = setOf(Blocks.CRIMSON_STEM, Blocks.WARPED_STEM)
    private val STONE_BLOCKS = setOf(Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE)
    private val IGNEOUS_BLOCKS = setOf(Blocks.GRANITE, Blocks.ANDESITE, Blocks.DIORITE)
    private val DEEPSLATE_BLOCKS = setOf(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.TUFF, Blocks.CALCITE)
    private val DIRT_BLOCKS = setOf(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.ROOTED_DIRT)
    private val MUSHROOM_BLOCKS = setOf(Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM)
    private val VEGETATION_BLOCKS = setOf(
        Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN,
        Blocks.DEAD_BUSH,
    )

    val validators: Map<BlockCategory, (World, BlockPos) -> Boolean> = mapOf(
        // Cobblemon growables
        BlockCategory.APRICORN to { world, pos -> world.getBlockState(pos).isIn(CobbleworkersTags.Blocks.APRICORNS) },
        BlockCategory.BERRY to { world, pos -> world.getBlockState(pos).isIn(CobbleworkersTags.Blocks.BERRIES) },
        BlockCategory.MINT to { world, pos -> world.getBlockState(pos).isIn(CobbleworkersTags.Blocks.MINTS) },
        BlockCategory.TUMBLESTONE to { world, pos -> world.getBlockState(pos).block in TUMBLESTONE_BLOCKS },
        BlockCategory.AMETHYST to { world, pos -> world.getBlockState(pos).block == Blocks.AMETHYST_CLUSTER },

        // Vanilla growables
        BlockCategory.NETHERWART to { world, pos -> world.getBlockState(pos).block is NetherWartBlock },
        BlockCategory.CROP_GRAIN to { world, pos -> world.getBlockState(pos).block in CobbleworkersCropUtils.validCropBlocks },
        BlockCategory.CROP_ROOT to { world, pos ->
            val block = world.getBlockState(pos).block
            block == Blocks.CARROTS || block == Blocks.POTATOES
        },
        BlockCategory.CROP_BEET to { world, pos -> world.getBlockState(pos).block == Blocks.BEETROOTS },
        BlockCategory.SWEET_BERRY to { world, pos -> world.getBlockState(pos).block == Blocks.SWEET_BERRY_BUSH },
        BlockCategory.PUMPKIN_MELON to { world, pos ->
            val block = world.getBlockState(pos).block
            block == Blocks.PUMPKIN || block == Blocks.MELON
        },
        BlockCategory.COCOA to { world, pos -> world.getBlockState(pos).block == Blocks.COCOA },
        BlockCategory.CHORUS to { world, pos -> world.getBlockState(pos).block == Blocks.CHORUS_PLANT || world.getBlockState(pos).block == Blocks.CHORUS_FLOWER },
        BlockCategory.CAVE_VINE to { world, pos -> world.getBlockState(pos).block is CaveVines },
        BlockCategory.DRIPLEAF to { world, pos -> world.getBlockState(pos).block == Blocks.BIG_DRIPLEAF },

        // Wood / plant structures
        BlockCategory.LOG_OVERWORLD to { world, pos -> world.getBlockState(pos).block in OVERWORLD_LOGS },
        BlockCategory.LOG_TROPICAL to { world, pos -> world.getBlockState(pos).block in TROPICAL_LOGS },
        BlockCategory.LOG_NETHER to { world, pos -> world.getBlockState(pos).block in NETHER_LOGS },
        BlockCategory.BAMBOO to { world, pos -> world.getBlockState(pos).block == Blocks.BAMBOO },
        BlockCategory.SUGAR_CANE to { world, pos -> world.getBlockState(pos).block == Blocks.SUGAR_CANE },
        BlockCategory.CACTUS to { world, pos -> world.getBlockState(pos).block == Blocks.CACTUS },
        BlockCategory.VINE to { world, pos -> world.getBlockState(pos).block == Blocks.VINE },

        // Stone / earth
        BlockCategory.STONE to { world, pos -> world.getBlockState(pos).block in STONE_BLOCKS },
        BlockCategory.IGNEOUS to { world, pos -> world.getBlockState(pos).block in IGNEOUS_BLOCKS },
        BlockCategory.DEEPSLATE to { world, pos -> world.getBlockState(pos).block in DEEPSLATE_BLOCKS },
        BlockCategory.DIRT to { world, pos -> world.getBlockState(pos).block in DIRT_BLOCKS },
        BlockCategory.SAND to { world, pos -> world.getBlockState(pos).isIn(CobbleworkersTags.Blocks.SAND) },
        BlockCategory.CLAY to { world, pos -> world.getBlockState(pos).block == Blocks.CLAY },

        // Minerals & special
        BlockCategory.ICE to { world, pos -> world.getBlockState(pos).block == Blocks.ICE || world.getBlockState(pos).block == Blocks.PACKED_ICE },
        BlockCategory.SCULK to { world, pos ->
            val block = world.getBlockState(pos).block
            block == Blocks.SCULK || block == Blocks.SCULK_VEIN
        },
        BlockCategory.SNOW_BLOCK to { world, pos -> world.getBlockState(pos).block == Blocks.SNOW_BLOCK },

        // Nature
        BlockCategory.MUSHROOM to { world, pos -> world.getBlockState(pos).block in MUSHROOM_BLOCKS },
        BlockCategory.FLOWER to { world, pos ->
            val state = world.getBlockState(pos)
            state.isIn(CobbleworkersTags.Blocks.SMALL_FLOWERS) || state.isIn(CobbleworkersTags.Blocks.TALL_FLOWERS)
        },
        BlockCategory.MOSS to { world, pos ->
            val block = world.getBlockState(pos).block
            block == Blocks.MOSS_BLOCK || block == Blocks.MOSS_CARPET || block == Blocks.AZALEA || block == Blocks.FLOWERING_AZALEA
        },
        BlockCategory.LEAVES to { world, pos -> world.getBlockState(pos).isIn(CobbleworkersTags.Blocks.LEAVES) },
        BlockCategory.VEGETATION to { world, pos -> world.getBlockState(pos).block in VEGETATION_BLOCKS },

        // Honey
        BlockCategory.HONEY to { world, pos -> world.getBlockState(pos).block is BeehiveBlock },

        // Fire
        BlockCategory.FIRE to { world, pos -> world.getBlockState(pos).isIn(CobbleworkersTags.Blocks.FIRE) },

        // Fluids (source blocks only — flowing would be too noisy)
        BlockCategory.WATER to { world, pos ->
            world.getBlockState(pos).fluidState.isOf(net.minecraft.fluid.Fluids.WATER)
        },
        BlockCategory.LAVA to { world, pos ->
            world.getBlockState(pos).fluidState.isOf(net.minecraft.fluid.Fluids.LAVA)
        },

        // Growable (crops + saplings — Growth Accelerator)
        BlockCategory.GROWABLE to { world, pos ->
            val block = world.getBlockState(pos).block
            block is CropBlock || block is SaplingBlock
        },

        // Farmland (irrigation)
        BlockCategory.FARMLAND to { world, pos -> world.getBlockState(pos).block == Blocks.FARMLAND },

        // Functional blocks
        BlockCategory.FURNACE to { world, pos -> world.getBlockState(pos).block is AbstractFurnaceBlock },
        BlockCategory.BREWING_STAND to { world, pos -> world.getBlockState(pos).block is BrewingStandBlock },
        BlockCategory.CAULDRON to { world, pos -> world.getBlockState(pos).isOf(Blocks.CAULDRON) },
        BlockCategory.SUSPICIOUS to { world, pos ->
            val block = world.getBlockState(pos).block
            block == Blocks.DIRT || block == Blocks.GRAVEL || block == Blocks.MUD
                || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT
        },

        // Aquatic (pending)
        BlockCategory.KELP to { world, pos -> world.getBlockState(pos).block == Blocks.KELP || world.getBlockState(pos).block == Blocks.KELP_PLANT },
        BlockCategory.LILY_PAD to { world, pos -> world.getBlockState(pos).block == Blocks.LILY_PAD },
        BlockCategory.SEA_PICKLE to { world, pos -> world.getBlockState(pos).block == Blocks.SEA_PICKLE },

        // Container
        BlockCategory.CONTAINER to { world, pos ->
            CobbleworkersInventoryUtils.blockValidator(world, pos)
        },
    )
}
