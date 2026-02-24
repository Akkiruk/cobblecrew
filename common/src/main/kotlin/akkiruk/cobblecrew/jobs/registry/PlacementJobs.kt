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
        qualifyingMoves = setOf("flash", "willowisp"),
        particle = ParticleTypes.FLAME,
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

    private const val SAPLING_SPACING = 4 // min blocks between saplings so trees grow properly

    val TREE_PLANTER = PlacementJob(
        name = "tree_planter",
        qualifyingMoves = setOf("ingrain", "seedbomb"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        itemCheck = { stack -> (stack.item as? BlockItem)?.block is SaplingBlock },
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

    val CROP_SOWER = PlacementJob(
        name = "crop_sower",
        qualifyingMoves = setOf("bulletseed", "leechseed"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        itemCheck = { stack ->
            stack.item in setOf(
                Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.CARROT, Items.POTATO,
                Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.TORCHFLOWER_SEEDS,
                Items.PITCHER_POD,
            )
        },
        findTarget = { world, origin ->
            BlockPos.iterateOutwards(origin, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
                .firstOrNull { pos ->
                    world.getBlockState(pos).isAir
                        && world.getBlockState(pos.down()).block == Blocks.FARMLAND
                }?.toImmutable()
        },
        placeFn = { world, pos, item ->
            val crop = when (item.item) {
                Items.WHEAT_SEEDS -> Blocks.WHEAT
                Items.BEETROOT_SEEDS -> Blocks.BEETROOTS
                Items.CARROT -> Blocks.CARROTS
                Items.POTATO -> Blocks.POTATOES
                Items.MELON_SEEDS -> Blocks.MELON_STEM
                Items.PUMPKIN_SEEDS -> Blocks.PUMPKIN_STEM
                Items.TORCHFLOWER_SEEDS -> Blocks.TORCHFLOWER_CROP
                Items.PITCHER_POD -> Blocks.PITCHER_CROP
                else -> Blocks.WHEAT
            }
            world.setBlockState(pos, crop.defaultState)
        },
    )

    val BONEMEAL_APPLICATOR = PlacementJob(
        name = "bonemeal_applicator",
        qualifyingMoves = setOf("synthesis"),
        particle = ParticleTypes.COMPOSTER,
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

    fun register() {
        WorkerRegistry.registerAll(
            TORCH_LIGHTER, TREE_PLANTER, CROP_SOWER, BONEMEAL_APPLICATOR,
        )
    }
}
