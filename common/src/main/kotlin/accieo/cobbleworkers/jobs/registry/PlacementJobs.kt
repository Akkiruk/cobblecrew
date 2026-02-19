/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.registry

import accieo.cobbleworkers.jobs.WorkerRegistry
import accieo.cobbleworkers.jobs.dsl.PlacementJob
import net.minecraft.block.Blocks
import net.minecraft.block.CropBlock
import net.minecraft.block.SaplingBlock
import net.minecraft.item.BoneMealItem
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.LightType

/**
 * Placement jobs (D1–D4). Take items from containers, place as blocks.
 */
object PlacementJobs {

    private const val SEARCH_RADIUS = 8

    val TORCH_LIGHTER = PlacementJob(
        name = "torch_lighter",
        qualifyingMoves = setOf("flash", "willowisp"),
        fallbackSpecies = listOf("Litwick", "Lampent", "Chandelure", "Ampharos", "Lanturn"),
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

    val TREE_PLANTER = PlacementJob(
        name = "tree_planter",
        qualifyingMoves = setOf("ingrain", "seedbomb", "seedflare"),
        fallbackSpecies = listOf("Trevenant", "Torterra", "Celebi"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        itemCheck = { stack ->
            stack.item in setOf(
                Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING,
                Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING,
                Items.CHERRY_SAPLING, Items.MANGROVE_PROPAGULE,
            )
        },
        findTarget = { world, origin ->
            BlockPos.iterateOutwards(origin, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
                .firstOrNull { pos ->
                    world.getBlockState(pos).isAir
                        && world.getBlockState(pos.down()).block in setOf(
                            Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.PODZOL,
                            Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.MUD,
                        )
                        && world.isSkyVisible(pos)
                }?.toImmutable()
        },
        placeFn = { world, pos, item ->
            val sapling = when (item.item) {
                Items.OAK_SAPLING -> Blocks.OAK_SAPLING
                Items.SPRUCE_SAPLING -> Blocks.SPRUCE_SAPLING
                Items.BIRCH_SAPLING -> Blocks.BIRCH_SAPLING
                Items.JUNGLE_SAPLING -> Blocks.JUNGLE_SAPLING
                Items.ACACIA_SAPLING -> Blocks.ACACIA_SAPLING
                Items.DARK_OAK_SAPLING -> Blocks.DARK_OAK_SAPLING
                Items.CHERRY_SAPLING -> Blocks.CHERRY_SAPLING
                Items.MANGROVE_PROPAGULE -> Blocks.MANGROVE_PROPAGULE
                else -> Blocks.OAK_SAPLING
            }
            world.setBlockState(pos, sapling.defaultState)
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
        qualifyingMoves = setOf("synthesis", "junglehealing"),
        fallbackType = "GRASS",
        particle = ParticleTypes.COMPOSTER,
        itemCheck = { it.item == Items.BONE_MEAL },
        findTarget = { world, origin ->
            BlockPos.iterateOutwards(origin, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
                .firstOrNull { pos ->
                    val block = world.getBlockState(pos).block
                    block is CropBlock || block is SaplingBlock
                }?.toImmutable()
        },
        placeFn = { world, pos, stack ->
            BoneMealItem.useOnFertilizable(stack.copy(), world, pos)
        },
    )

    fun register() {
        WorkerRegistry.registerAll(
            TORCH_LIGHTER, TREE_PLANTER, CROP_SOWER, BONEMEAL_APPLICATOR,
        )
    }
}
