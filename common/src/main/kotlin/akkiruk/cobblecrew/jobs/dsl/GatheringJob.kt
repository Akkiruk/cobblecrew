/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.dsl

import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.BaseHarvester
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World

/**
 * DSL-style gathering job. Most gathering jobs are 5-8 lines:
 * ```
 * val OVERWORLD_LOGGER = GatheringJob(
 *     name = "overworld_logger",
 *     category = "gathering",
 *     targetCategory = BlockCategory.LOG_OVERWORLD,
 *     qualifyingMoves = setOf("cut", "furycutter"),
 *     particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
 * )
 * ```
 */
open class GatheringJob(
    override val name: String,
    val category: String = "gathering",
    override val targetCategory: BlockCategory,
    val qualifyingMoves: Set<String> = emptySet(),
    val typeGatedMoves: Map<String, String> = emptyMap(),
    val fallbackType: String = "",
    val fallbackSpecies: List<String> = emptyList(),
    val particle: ParticleEffect = ParticleTypes.CAMPFIRE_COSY_SMOKE,
    override val priority: WorkerPriority = WorkerPriority.TYPE,
    val harvestOverride: ((World, BlockPos, PokemonEntity) -> List<ItemStack>)? = null,
    val toolOverride: ItemStack = ItemStack.EMPTY,
    val readyCheck: ((World, BlockPos) -> Boolean)? = null,
    val afterHarvestAction: ((World, BlockPos, BlockState) -> Unit)? = null,
    val tolerance: Double = 3.0,
    val topDownHarvest: Boolean = false,
    val isCombo: Boolean = false,
) : BaseHarvester() {

    private val config get() = JobConfigManager.get(name)

    override val arrivalParticle: ParticleEffect = particle
    override val arrivalTolerance: Double = tolerance
    override val harvestTool: ItemStack = toolOverride

    init {
        JobConfigManager.registerDefault(category, name, JobConfig(
            enabled = true,
            qualifyingMoves = qualifyingMoves.toList(),
            fallbackType = fallbackType,
            fallbackSpecies = fallbackSpecies,
        ))
    }

    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
        dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)

    override fun isTargetReady(world: World, pos: BlockPos): Boolean {
        return readyCheck?.invoke(world, pos) ?: true
    }

    override fun harvest(world: World, targetPos: BlockPos, pokemonEntity: PokemonEntity) {
        if (harvestOverride != null) {
            val drops = harvestOverride.invoke(world, targetPos, pokemonEntity)
            if (drops.isNotEmpty()) {
                heldItemsByPokemon[pokemonEntity.pokemon.uuid] = drops
            }
        } else {
            harvestWithLoot(world, targetPos, pokemonEntity) { w, pos, state ->
                afterHarvestAction?.invoke(w, pos, state)
                    ?: w.setBlockState(pos, net.minecraft.block.Blocks.AIR.defaultState)
            }
        }
    }

    /**
     * For top-down harvesting: BFS from [targetPos] to find all connected blocks of the
     * same type, then return the one farthest away (by BFS depth). Removing the farthest
     * node keeps the tree connected — no floating pieces even with arches or branches.
     */
    override fun resolveHarvestPos(world: World, targetPos: BlockPos): BlockPos {
        if (!topDownHarvest) return targetPos

        val targetBlock = world.getBlockState(targetPos).block
        val visited = mutableSetOf(targetPos)
        val queue = ArrayDeque<BlockPos>()
        queue.add(targetPos)
        var farthest = targetPos

        while (queue.isNotEmpty() && visited.size < MAX_CONNECTED_SCAN) {
            val current = queue.removeFirst()
            // Last node dequeued in BFS = farthest from root
            farthest = current
            for (dir in Direction.entries) {
                val neighbor = current.offset(dir)
                if (neighbor in visited) continue
                if (world.getBlockState(neighbor).block == targetBlock) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
        return farthest
    }

    companion object {
        private const val MAX_CONNECTED_SCAN = 128
    }
}
