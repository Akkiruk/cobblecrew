/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.dsl

import accieo.cobbleworkers.config.JobConfig
import accieo.cobbleworkers.config.JobConfigManager
import accieo.cobbleworkers.enums.BlockCategory
import accieo.cobbleworkers.enums.WorkerPriority
import accieo.cobbleworkers.jobs.BasePlacer
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * DSL-style placement job. Takes items from containers and places them as blocks.
 */
class PlacementJob(
    override val name: String,
    val category: String = "placement",
    val qualifyingMoves: Set<String> = emptySet(),
    val typeGatedMoves: Map<String, String> = emptyMap(),
    val fallbackType: String = "",
    val fallbackSpecies: List<String> = emptyList(),
    override val priority: WorkerPriority = WorkerPriority.MOVE,
    val particle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER,
    val itemCheck: (ItemStack) -> Boolean,
    val findTarget: (World, BlockPos) -> BlockPos?,
    val placeFn: (World, BlockPos, ItemStack) -> Unit,
) : BasePlacer() {

    private val config get() = JobConfigManager.get(name)

    override val targetCategory: BlockCategory? = null
    override val placeParticle: ParticleEffect = particle

    fun defaultConfig(): JobConfig = JobConfig(
        enabled = true,
        qualifyingMoves = qualifyingMoves.toList(),
        fallbackType = fallbackType,
        fallbackSpecies = fallbackSpecies,
    )

    init {
        JobConfigManager.registerDefault(category, name, defaultConfig())
    }

    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
        if (!config.enabled) return false

        val effectiveMoves = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
        val effectiveType = config.fallbackType.ifEmpty { fallbackType }.uppercase()
        val effectiveSpecies = config.fallbackSpecies.ifEmpty { fallbackSpecies }

        if (moves.any { it in effectiveMoves }) return true
        for ((move, requiredType) in typeGatedMoves) {
            if (move in moves && requiredType.uppercase() in types) return true
        }
        if (effectiveSpecies.any { it.equals(species, ignoreCase = true) }) return true
        if (effectiveType.isNotEmpty() && effectiveType in types) return true

        return false
    }

    override fun itemPredicate(stack: ItemStack): Boolean = itemCheck(stack)
    override fun findPlacementTarget(world: World, origin: BlockPos): BlockPos? = findTarget(world, origin)
    override fun placeBlock(world: World, pos: BlockPos, item: ItemStack) = placeFn(world, pos, item)
}
