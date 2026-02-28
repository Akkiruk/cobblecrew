/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import net.minecraft.item.ItemStack
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World

/**
 * BFS flood-fill harvest: breaks connected same-block blocks outward from [pos],
 * collecting loot-table drops with the given [tool]. Returns all drops.
 */
fun floodFillHarvest(world: World, pos: BlockPos, maxBlocks: Int, tool: ItemStack): List<ItemStack> {
    val targetBlock = world.getBlockState(pos).block
    val visited = mutableSetOf(pos)
    val frontier = ArrayDeque<BlockPos>()
    frontier.add(pos)
    val allDrops = mutableListOf<ItemStack>()
    var broken = 0

    while (frontier.isNotEmpty() && broken < maxBlocks) {
        val current = frontier.removeFirst()
        if (world.getBlockState(current).block != targetBlock) continue
        val state = world.getBlockState(current)
        val lootBuilder = LootContextParameterSet.Builder(world as ServerWorld)
            .add(LootContextParameters.ORIGIN, current.toCenterPos())
            .add(LootContextParameters.BLOCK_STATE, state)
            .add(LootContextParameters.TOOL, tool)
        allDrops.addAll(state.getDroppedStacks(lootBuilder))
        world.breakBlock(current, false)
        broken++
        for (dir in Direction.entries) {
            val neighbor = current.offset(dir)
            if (neighbor !in visited) {
                visited.add(neighbor)
                if (world.getBlockState(neighbor).block == targetBlock) {
                    frontier.add(neighbor)
                }
            }
        }
    }
    return allDrops
}
