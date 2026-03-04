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
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * DSL-style gathering job. Most gathering jobs are 5-8 lines:
 * ```
 * val LOGGER = GatheringJob(
 *     name = "logger",
 *     targetCategory = BlockCategory.LOG,
 *     qualifyingMoves = setOf("cut", "furycutter"),
 *     particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
 * )
 * ```
 */
open class GatheringJob(
    override val name: String,
    override val category: String = "gathering",
    override val targetCategory: BlockCategory,
    val qualifyingMoves: Set<String> = emptySet(),
    val typeGatedMoves: Map<String, String> = emptyMap(),
    val fallbackSpecies: List<String> = emptyList(),
    val particle: ParticleEffect = ParticleTypes.CAMPFIRE_COSY_SMOKE,
    override val priority: WorkerPriority = WorkerPriority.TYPE,
    override val importance: JobImportance = JobImportance.HIGH,
    val harvestOverride: ((World, BlockPos, PokemonEntity) -> List<ItemStack>)? = null,
    val toolOverride: ItemStack = ItemStack.EMPTY,
    val readyCheck: ((World, BlockPos) -> Boolean)? = null,
    val afterHarvestAction: ((World, BlockPos, BlockState) -> Unit)? = null,
    val tolerance: Double = 3.0,
    val isCombo: Boolean = false,
) : BaseJob() {

    override val arrivalParticle: ParticleEffect = particle
    override val arrivalTolerance: Double = tolerance
    override val workPhase: WorkPhase = WorkPhase.HARVESTING

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

    override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? =
        findCachedBlockTarget(state, context, targetCategory, readyCheck = readyCheck)

    override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
        val pos = state.targetPos ?: return WorkResult.Done()
        val items = if (harvestOverride != null) {
            harvestOverride.invoke(context.world, pos, pokemonEntity)
        } else {
            harvestWithLoot(context.world, pos, pokemonEntity, toolOverride) { w, p, s ->
                afterHarvestAction?.invoke(w, p, s)
                    ?: w.setBlockState(p, net.minecraft.block.Blocks.AIR.defaultState)
            }
        }
        return WorkResult.Done(items)
    }
}
