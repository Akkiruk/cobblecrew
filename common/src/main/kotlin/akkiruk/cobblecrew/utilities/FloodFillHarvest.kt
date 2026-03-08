/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import net.minecraft.block.LeavesBlock
import net.minecraft.item.ItemStack
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.registry.tag.BlockTags
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

/**
 * Harvests an entire tree from any log in the structure.
 *
 * BFS through all connected blocks matching the `minecraft:logs` tag,
 * breaks them all, and collects loot-table drops. Returns all drops
 * plus the set of all block positions that were broken (for cache purging).
 *
 * When [includeLeaves] is true, also breaks connected leaf blocks adjacent
 * to any log in the tree, collecting their loot (saplings, sticks, apples).
 */
fun treeHarvest(
    world: World,
    startPos: BlockPos,
    maxLogs: Int = 128,
    tool: ItemStack = ItemStack.EMPTY,
    includeLeaves: Boolean = false,
): TreeHarvestResult {
    val sw = world as ServerWorld
    val visited = mutableSetOf(startPos)
    val frontier = ArrayDeque<BlockPos>()
    frontier.add(startPos)
    val allDrops = mutableListOf<ItemStack>()
    val brokenPositions = mutableSetOf<BlockPos>()
    val leafPositions = mutableSetOf<BlockPos>()
    var logsBroken = 0

    // Phase 1: BFS through connected logs, also seed leaf positions
    // Check all 26 neighbors (face + edge + corner) for leaf seeding since
    // vanilla tree generators place leaves diagonally from logs.
    while (frontier.isNotEmpty() && logsBroken < maxLogs) {
        val current = frontier.removeFirst()
        val state = world.getBlockState(current)
        if (!state.isIn(BlockTags.LOGS)) continue

        val lootBuilder = LootContextParameterSet.Builder(sw)
            .add(LootContextParameters.ORIGIN, current.toCenterPos())
            .add(LootContextParameters.BLOCK_STATE, state)
            .add(LootContextParameters.TOOL, tool)
        allDrops.addAll(state.getDroppedStacks(lootBuilder))
        world.breakBlock(current, false)
        brokenPositions.add(current)
        logsBroken++

        // Face neighbors for log connectivity (with leaf-peek: look through up to 2 leaf blocks for logs)
        for (dir in Direction.entries) {
            val neighbor = current.offset(dir)
            if (neighbor in visited) continue
            val neighborState = world.getBlockState(neighbor)
            if (neighborState.isIn(BlockTags.LOGS)) {
                visited.add(neighbor)
                frontier.add(neighbor)
            } else if (neighborState.block is LeavesBlock) {
                if (includeLeaves) leafPositions.add(neighbor)
                // Peek through up to 2 leaf blocks to find hidden logs
                var peekPos = neighbor
                for (hop in 1..2) {
                    peekPos = peekPos.offset(dir)
                    if (peekPos in visited) break
                    val peekState = world.getBlockState(peekPos)
                    if (peekState.isIn(BlockTags.LOGS)) {
                        visited.add(peekPos)
                        frontier.add(peekPos)
                        break
                    } else if (peekState.block !is LeavesBlock) {
                        break
                    }
                }
            }
        }

        // Diagonal neighbors for leaf seeding only (leaves often diagonal to logs)
        if (includeLeaves) {
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                if (dx == 0 && dy == 0 && dz == 0) continue
                // Skip pure face offsets — already handled above
                if ((dx == 0 && dz == 0) || (dx == 0 && dy == 0) || (dy == 0 && dz == 0)) continue
                val neighbor = current.add(dx, dy, dz)
                if (neighbor in visited || neighbor in leafPositions) continue
                if (world.getBlockState(neighbor).block is LeavesBlock) {
                    leafPositions.add(neighbor)
                }
            }
        }
    }

    // Phase 2: break collected leaves (only if includeLeaves)
    if (includeLeaves && leafPositions.isNotEmpty()) {
        // BFS outward from seed positions to get the full canopy
        val leafFrontier = ArrayDeque(leafPositions)
        val leafVisited = mutableSetOf<BlockPos>().also { it.addAll(leafPositions); it.addAll(visited) }
        var leavesBroken = 0
        val maxLeaves = 1024

        while (leafFrontier.isNotEmpty() && leavesBroken < maxLeaves) {
            val current = leafFrontier.removeFirst()
            val state = world.getBlockState(current)
            if (state.block !is LeavesBlock) continue

            val lootBuilder = LootContextParameterSet.Builder(sw)
                .add(LootContextParameters.ORIGIN, current.toCenterPos())
                .add(LootContextParameters.BLOCK_STATE, state)
                .add(LootContextParameters.TOOL, tool)
            allDrops.addAll(state.getDroppedStacks(lootBuilder))
            world.breakBlock(current, false)
            brokenPositions.add(current)
            leavesBroken++

            for (dir in Direction.entries) {
                val neighbor = current.offset(dir)
                if (neighbor in leafVisited) continue
                leafVisited.add(neighbor)
                if (world.getBlockState(neighbor).block is LeavesBlock) {
                    leafFrontier.add(neighbor)
                }
            }
        }
    }

    return TreeHarvestResult(allDrops, brokenPositions)
}

data class TreeHarvestResult(
    val drops: List<ItemStack>,
    val brokenPositions: Set<BlockPos>,
)
