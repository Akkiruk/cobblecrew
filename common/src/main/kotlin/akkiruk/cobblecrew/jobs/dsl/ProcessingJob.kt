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
import akkiruk.cobblecrew.enums.JobImportance
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.BaseJob
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.jobs.Target
import akkiruk.cobblecrew.jobs.WorkResult
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.WorkerAnimationUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes

/**
 * DSL-style processing job. Finds a barrel with matching input items,
 * navigates there, extracts + transforms, then deposits the results.
 */
open class ProcessingJob(
    override val name: String,
    override val category: String = "processing",
    val qualifyingMoves: Set<String> = emptySet(),
    val typeGatedMoves: Map<String, String> = emptyMap(),
    val fallbackSpecies: List<String> = emptyList(),
    override val priority: WorkerPriority = WorkerPriority.MOVE,
    override val importance: JobImportance = JobImportance.STANDARD,
    val particle: ParticleEffect = ParticleTypes.SMOKE,
    val inputCheck: (ItemStack) -> Boolean,
    val transformFn: (ItemStack) -> List<ItemStack>,
    val isCombo: Boolean = false,
) : BaseJob() {

    override val arrivalParticle: ParticleEffect = particle
    override val targetCategory: BlockCategory? = null
    override val workPhase: WorkPhase = WorkPhase.PROCESSING
    open val minExtractAmount: Int = 1

    override fun buildDefaultConfig() = JobConfig(
        enabled = true,
        qualifyingMoves = qualifyingMoves.toList(),
        fallbackSpecies = fallbackSpecies,
    )

    init { registerConfig() }

    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
        dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)

    override fun matchPriority(moves: Set<String>, types: Set<String>, species: String, ability: String) =
        dslMatchPriority(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)

    override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? {
        val barrelPos = CobbleCrewInventoryUtils.findInputContainer(
            context.world, context.origin, inputCheck
        ) ?: return null
        return Target.Block(barrelPos)
    }

    override fun validateTarget(state: PokemonWorkerState, context: JobContext): Boolean {
        val pos = state.targetPos ?: return false
        val inv = context.world.getBlockEntity(pos) as? Inventory ?: return false
        return (0 until inv.size()).any { inputCheck(inv.getStack(it)) }
    }

    override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
        val pos = state.targetPos ?: return WorkResult.Done()
        val extracted = CobbleCrewInventoryUtils.extractFromContainer(
            context.world, pos, inputCheck, minExtractAmount
        )
        if (extracted.isEmpty) return WorkResult.Done()
        WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.PROCESSING, context.world)
        return WorkResult.Done(transformFn(extracted))
    }
}
