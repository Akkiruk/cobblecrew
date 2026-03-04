/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import akkiruk.cobblecrew.enums.BlockCategory
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.ApricornBlock
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.block.MintBlock
import net.minecraft.block.*
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.registry.tag.BlockTags
import net.minecraft.state.property.Properties

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

    private val STONE_BLOCKS = setOf(Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE)
    private val IGNEOUS_BLOCKS = setOf(Blocks.GRANITE, Blocks.ANDESITE, Blocks.DIORITE)
    private val DEEPSLATE_BLOCKS = setOf(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.TUFF, Blocks.CALCITE)
    private val ORE_BLOCKS = setOf(
        Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE,
        Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE, Blocks.EMERALD_ORE, Blocks.COPPER_ORE,
        Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE,
    )
    private val DIRT_BLOCKS = setOf(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.ROOTED_DIRT)
    private val SAND_BLOCKS = setOf(Blocks.SAND, Blocks.RED_SAND)
    private val MUSHROOM_BLOCKS = setOf(Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM)
    private val VEGETATION_BLOCKS = setOf(
        Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN,
        Blocks.DEAD_BUSH,
    )

    private val SMALL_FLOWER_BLOCKS = setOf(
        Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM,
        Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP,
        Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY,
        Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.TORCHFLOWER,
        Blocks.WITHER_ROSE,
    )
    private val TALL_FLOWER_BLOCKS = setOf(
        Blocks.SUNFLOWER, Blocks.LILAC, Blocks.ROSE_BUSH,
        Blocks.PEONY, Blocks.PITCHER_PLANT,
    )

    val validators: Map<BlockCategory, (Block, BlockState) -> Boolean> = mapOf(
        // Cobblemon growables
        BlockCategory.APRICORN to { block, _ -> block is ApricornBlock },
        BlockCategory.BERRY to { block, _ -> block is BerryBlock },
        BlockCategory.MINT to { block, _ -> block is MintBlock },
        BlockCategory.TUMBLESTONE to { block, _ -> block in TUMBLESTONE_BLOCKS },
        BlockCategory.AMETHYST to { block, _ -> block == Blocks.AMETHYST_CLUSTER },

        // Vanilla growables
        BlockCategory.NETHERWART to { block, _ -> block is NetherWartBlock },
        BlockCategory.CROP_GRAIN to { block, state ->
            block in CobbleCrewCropUtils.validCropBlocks &&
                !(state.contains(Properties.DOUBLE_BLOCK_HALF) && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
        },
        BlockCategory.CROP_ROOT to { block, _ ->
            block == Blocks.CARROTS || block == Blocks.POTATOES || block == Blocks.BEETROOTS
        },

        BlockCategory.SWEET_BERRY to { block, _ -> block == Blocks.SWEET_BERRY_BUSH },
        BlockCategory.PUMPKIN_MELON to { block, _ -> block == Blocks.PUMPKIN || block == Blocks.MELON },
        BlockCategory.COCOA to { block, _ -> block == Blocks.COCOA },
        BlockCategory.CHORUS to { block, _ -> block == Blocks.CHORUS_PLANT || block == Blocks.CHORUS_FLOWER },
        BlockCategory.CAVE_VINE to { block, _ -> block is CaveVines },
        BlockCategory.DRIPLEAF to { block, _ -> block == Blocks.BIG_DRIPLEAF },

        // Wood / plant structures (tag-based: covers all vanilla + modded logs)
        BlockCategory.LOG to { _, state -> state.isIn(BlockTags.LOGS) },
        BlockCategory.BAMBOO to { block, _ -> block == Blocks.BAMBOO },
        BlockCategory.SUGAR_CANE to { block, _ -> block == Blocks.SUGAR_CANE },
        BlockCategory.CACTUS to { block, _ -> block == Blocks.CACTUS },
        BlockCategory.VINE to { block, _ -> block == Blocks.VINE },

        // Stone / earth
        BlockCategory.STONE to { block, _ -> block in STONE_BLOCKS },
        BlockCategory.IGNEOUS to { block, _ -> block in IGNEOUS_BLOCKS },
        BlockCategory.DEEPSLATE to { block, _ -> block in DEEPSLATE_BLOCKS },
        BlockCategory.DIRT to { block, _ -> block in DIRT_BLOCKS },
        BlockCategory.SAND to { block, _ -> block in SAND_BLOCKS },
        BlockCategory.CLAY to { block, _ -> block == Blocks.CLAY },
        BlockCategory.ORE to { block, _ -> block in ORE_BLOCKS },

        // Minerals & special
        BlockCategory.ICE to { block, _ -> block == Blocks.ICE || block == Blocks.PACKED_ICE },
        BlockCategory.SCULK to { block, _ -> block == Blocks.SCULK || block == Blocks.SCULK_VEIN },
        BlockCategory.SNOW_BLOCK to { block, _ -> block == Blocks.SNOW_BLOCK },

        // Nature
        BlockCategory.MUSHROOM to { block, _ -> block in MUSHROOM_BLOCKS },
        BlockCategory.FLOWER to { block, _ -> block in SMALL_FLOWER_BLOCKS || block in TALL_FLOWER_BLOCKS },
        BlockCategory.MOSS to { block, _ ->
            block == Blocks.MOSS_BLOCK || block == Blocks.MOSS_CARPET || block == Blocks.AZALEA || block == Blocks.FLOWERING_AZALEA
        },
        BlockCategory.LEAVES to { block, _ -> block is LeavesBlock },
        BlockCategory.VEGETATION to { block, _ -> block in VEGETATION_BLOCKS },

        // Honey
        BlockCategory.HONEY to { block, _ -> block is BeehiveBlock },

        // Fire
        BlockCategory.FIRE to { block, _ -> block == Blocks.FIRE || block == Blocks.SOUL_FIRE },

        // Fluids (source blocks only — flowing would be too noisy)
        BlockCategory.WATER to { _, state -> state.fluidState.isOf(net.minecraft.fluid.Fluids.WATER) },
        BlockCategory.LAVA to { _, state -> state.fluidState.isOf(net.minecraft.fluid.Fluids.LAVA) },

        // Growable (crops + saplings — Growth Accelerator)
        BlockCategory.GROWABLE to { block, state ->
            (block is CropBlock || block is SaplingBlock)
                && !(state.contains(Properties.DOUBLE_BLOCK_HALF) && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
        },

        // Farmland (irrigation)
        BlockCategory.FARMLAND to { block, _ -> block == Blocks.FARMLAND },

        // Functional blocks
        BlockCategory.FURNACE to { block, _ -> block is AbstractFurnaceBlock },
        BlockCategory.BREWING_STAND to { block, _ -> block is BrewingStandBlock },
        BlockCategory.CAULDRON to { _, state -> state.isOf(Blocks.CAULDRON) },
        BlockCategory.SUSPICIOUS to { block, _ -> block == Blocks.SUSPICIOUS_SAND || block == Blocks.SUSPICIOUS_GRAVEL },

        // Aquatic (pending)
        BlockCategory.KELP to { block, _ -> block == Blocks.KELP || block == Blocks.KELP_PLANT },
        BlockCategory.LILY_PAD to { block, _ -> block == Blocks.LILY_PAD },
        BlockCategory.SEA_PICKLE to { block, _ -> block == Blocks.SEA_PICKLE },

        // Container
        BlockCategory.CONTAINER to { block, _ -> CobbleCrewInventoryUtils.isValidInventoryBlock(block) },
    )
}
