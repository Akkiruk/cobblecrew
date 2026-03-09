/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.dsl

import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.ArrivalStyle
import akkiruk.cobblecrew.enums.JobImportance
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.BaseJob
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.jobs.Target
import akkiruk.cobblecrew.jobs.WorkResult
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * DSL-style placement job. Two-phase workflow:
 * 1. Navigate to container → extract matching item
 * 2. Navigate to placement target → place block
 *
 * Uses [secondaryTargetPos] in state to track whether we're in fetch or place phase.
 */
class PlacementJob(
    override val name: String,
    override val category: String = "placement",
    val qualifyingMoves: Set<String> = emptySet(),
    val typeGatedMoves: Map<String, String> = emptyMap(),
    val fallbackSpecies: List<String> = emptyList(),
    override val priority: WorkerPriority = WorkerPriority.MOVE,
    override val importance: JobImportance = JobImportance.LOW,
    val particle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER,
    val itemCheck: (ItemStack) -> Boolean,
    findTarget: (World, BlockPos) -> BlockPos?,
    val placeFn: (World, BlockPos, ItemStack) -> Unit,
    val partyEnabled: Boolean = false,
    val extractAmount: Int = 1,
) : BaseJob() {

    // Captured under a different name to avoid conflict with BaseJob.findTarget()
    private val placementFinder = findTarget

    override val arrivalParticle: ParticleEffect = particle
    override val targetCategory: BlockCategory? = null
    override val workPhase: WorkPhase = WorkPhase.PLACING
    override val producesItems: Boolean = false
    override val arrivalStyle: ArrivalStyle = ArrivalStyle.QUICK

    override fun buildDefaultConfig() = JobConfig(
        enabled = true,
        qualifyingMoves = qualifyingMoves.toList(),
        fallbackSpecies = fallbackSpecies,
        partyEnabled = partyEnabled,
    )

    init { registerConfig() }

    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
        dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species)

    override fun matchPriority(moves: Set<String>, types: Set<String>, species: String, ability: String) =
        dslMatchPriority(config, qualifyingMoves, fallbackSpecies, moves, species)

    override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? {
        // Must have both a placement location AND a source container with the item
        val placementPos = placementFinder(context.world, context.origin) ?: return null
        CobbleCrewInventoryUtils.findInputContainer(
            context.world, context.origin, itemCheck
        ) ?: return null
        // Navigate to the source container first
        val sourcePos = CobbleCrewInventoryUtils.findInputContainer(
            context.world, context.origin, itemCheck
        )!!
        state.secondaryTargetPos = placementPos
        return Target.Block(sourcePos)
    }

    override fun validateTarget(state: PokemonWorkerState, context: JobContext): Boolean {
        val pos = state.targetPos ?: return false
        // In placement phase, validate placement target
        if (state.heldItems.isNotEmpty()) return true
        // In fetch phase, validate the container still has items
        return !context.world.getBlockState(pos).isAir
    }

    override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
        val pos = state.targetPos ?: return WorkResult.Done()

        // Phase 2: at placement target — place the block
        if (state.heldItems.isNotEmpty() && state.secondaryTargetPos != null) {
            val item = state.heldItems.removeFirst()
            placeFn(context.world, pos, item)
            state.secondaryTargetPos = null
            return WorkResult.Done()
        }

        // Phase 1: at source container — extract item(s) and navigate to placement
        val extracted = CobbleCrewInventoryUtils.extractFromContainer(
            context.world, pos, itemCheck, extractAmount
        )
        if (extracted.isEmpty) return WorkResult.Done()

        val placementPos = state.secondaryTargetPos ?: placementFinder(context.world, context.origin)
        if (placementPos == null) {
            Block.dropStack(context.world, pokemonEntity.blockPos, extracted)
            state.secondaryTargetPos = null
            return WorkResult.Done()
        }

        state.heldItems.add(extracted)
        state.secondaryTargetPos = placementPos
        return WorkResult.MoveTo(placementPos)
    }
}
