/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.jobs.dsl.PlacementJob
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.CropBlock
import net.minecraft.block.SaplingBlock
import net.minecraft.block.StemBlock
import net.minecraft.item.BlockItem
import net.minecraft.item.BoneMealItem
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.world.LightType
import net.minecraft.world.World

/**
 * Placement jobs (D1–D4). Take items from containers, place as blocks.
 */
object PlacementJobs {

    private const val SEARCH_RADIUS = 8

    val TORCH_LIGHTER = PlacementJob(
        name = "torch_lighter",
        qualifyingMoves = setOf("nightslash"),
        particle = ParticleTypes.FLAME,
        partyEnabled = true,
        itemCheck = { it.item == Items.TORCH },
        findTarget = { world, origin ->
            BlockPos.iterateOutwards(origin, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
                .firstOrNull { pos ->
                    world.getLightLevel(LightType.BLOCK, pos) <= 7
                        && world.getBlockState(pos).isAir
                        && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())
                }?.toImmutable()
        },
        placeFn = { world, pos, _ ->
            world.setBlockState(pos, Blocks.TORCH.defaultState)
        },
    )

    private val DIRT_BLOCKS = setOf(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.ROOTED_DIRT, Blocks.MYCELIUM, Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS)

    /** Saplings that MUST be 2x2 to grow (excluded from normal planter). */
    private val REQUIRES_TWO_BY_TWO = setOf(Blocks.DARK_OAK_SAPLING)

    /** All saplings that CAN grow as 2x2 mega trees. */
    private val MEGA_TREE_SAPLINGS = setOf(Blocks.DARK_OAK_SAPLING, Blocks.SPRUCE_SAPLING, Blocks.JUNGLE_SAPLING)

    private const val SAPLING_SPACING = 3 // min blocks between saplings so trees grow properly
    private const val LARGE_TREE_SPACING = 5 // 2x2 trees have wider canopies

    val TREE_PLANTER = PlacementJob(
        name = "tree_planter",
        qualifyingMoves = setOf("leafstorm"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        itemCheck = { stack ->
            val block = (stack.item as? BlockItem)?.block
            block is SaplingBlock && block !in REQUIRES_TWO_BY_TWO
        },
        findTarget = { world, origin ->
            BlockPos.iterateOutwards(origin, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
                .firstOrNull { pos ->
                    world.getBlockState(pos).isAir
                        && world.getBlockState(pos.down()).block in DIRT_BLOCKS
                        && world.isSkyVisible(pos)
                        && hasNoNearbySaplingOrLog(world, pos, SAPLING_SPACING)
                }?.toImmutable()
        },
        placeFn = { world, pos, item ->
            // Look up the sapling block by registry ID (item name matches block name for all saplings)
            val itemId = Registries.ITEM.getId(item.item)
            val block = Registries.BLOCK.get(itemId)
            val state = if (block is SaplingBlock) block.defaultState else Blocks.OAK_SAPLING.defaultState
            world.setBlockState(pos, state)
        },
    )

    val LARGE_TREE_PLANTER = PlacementJob(
        name = "large_tree_planter",
        qualifyingMoves = setOf("leafstorm"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        extractAmount = 4,
        itemCheck = { stack ->
            val block = (stack.item as? BlockItem)?.block
            block in MEGA_TREE_SAPLINGS && stack.count >= 4
        },
        findTarget = { world, origin ->
            BlockPos.iterateOutwards(origin, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
                .firstOrNull { pos ->
                    isTwoByTwoValid(world, pos)
                        && hasNoNearbySaplingOrLog(world, pos, LARGE_TREE_SPACING)
                }?.toImmutable()
        },
        placeFn = { world, pos, item ->
            val itemId = Registries.ITEM.getId(item.item)
            val block = Registries.BLOCK.get(itemId)
            val saplingState = if (block is SaplingBlock) block.defaultState else Blocks.DARK_OAK_SAPLING.defaultState
            world.setBlockState(pos, saplingState)
            world.setBlockState(pos.east(), saplingState)
            world.setBlockState(pos.south(), saplingState)
            world.setBlockState(pos.east().south(), saplingState)
        },
    )

    val CROP_SOWER = PlacementJob(
        name = "crop_sower",
        qualifyingMoves = setOf("bulletseed"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        partyEnabled = true,
        itemCheck = { stack ->
            val block = Block.getBlockFromItem(stack.item)
            block is CropBlock || block is StemBlock
        },
        findTarget = { world, origin ->
            BlockPos.iterateOutwards(origin, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
                .firstOrNull { pos ->
                    world.getBlockState(pos).isAir
                        && world.getBlockState(pos.down()).block == Blocks.FARMLAND
                }?.toImmutable()
        },
        placeFn = { world, pos, item ->
            val crop = Block.getBlockFromItem(item.item)
            if (crop is CropBlock || crop is StemBlock) {
                world.setBlockState(pos, crop.defaultState)
            }
        },
    )

    val BONEMEAL_APPLICATOR = PlacementJob(
        name = "bonemeal_applicator",
        qualifyingMoves = setOf("synthesis"),
        particle = ParticleTypes.COMPOSTER,
        partyEnabled = true,
        itemCheck = { it.item == Items.BONE_MEAL },
        findTarget = { world, origin ->
            BlockPos.iterateOutwards(origin, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
                .firstOrNull { pos ->
                    val state = world.getBlockState(pos)
                    val block = state.block
                    when {
                        block is CropBlock -> block.getAge(state) < block.maxAge
                        block is SaplingBlock -> true
                        else -> false
                    }
                }?.toImmutable()
        },
        placeFn = { world, pos, stack ->
            BoneMealItem.useOnFertilizable(stack.copy(), world, pos)
        },
    )

    /** Checks that no sapling or log exists within [radius] blocks horizontally. */
    private fun hasNoNearbySaplingOrLog(world: World, pos: BlockPos, radius: Int): Boolean {
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                if (dx == 0 && dz == 0) continue
                for (dy in -1..3) {
                    val state = world.getBlockState(pos.add(dx, dy, dz))
                    if (state.block is SaplingBlock) return false
                    if (state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) return false
                }
            }
        }
        return true
    }

    /** Checks that all 4 positions in a 2x2 pattern are valid for sapling placement. */
    private fun isTwoByTwoValid(world: World, nwCorner: BlockPos): Boolean {
        return listOf(
            nwCorner, nwCorner.east(), nwCorner.south(), nwCorner.east().south()
        ).all { pos ->
            world.getBlockState(pos).isAir
                && world.getBlockState(pos.down()).block in DIRT_BLOCKS
                && world.isSkyVisible(pos)
        }
    }

    fun register() {
        WorkerRegistry.registerAll(
            TORCH_LIGHTER, TREE_PLANTER, LARGE_TREE_PLANTER, CROP_SOWER, BONEMEAL_APPLICATOR,
        )
    }
}
