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
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * DSL-style environmental job. Navigates to a block, performs an in-world action,
 * then releases. No item harvesting/depositing — pure block modification.
 *
 * Two patterns:
 * - **Instant**: find → navigate → arrive → act → release (FrostFormer, ObsidianForge, etc.)
 * - **Repeated**: same flow but [shouldContinue] keeps the Pokémon at the target (cauldron fill)
 */
open class EnvironmentalJob(
    override val name: String,
    override val category: String = "environmental",
    override val targetCategory: BlockCategory,
    override val additionalScanCategories: Set<BlockCategory> = emptySet(),
    val qualifyingMoves: Set<String> = emptySet(),
    val fallbackSpecies: List<String> = emptyList(),
    override val priority: WorkerPriority = WorkerPriority.MOVE,
    override val importance: JobImportance = JobImportance.LOW,
    val particle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER,
    val defaultCooldownSeconds: Int = 0,
    val defaultRadius: Int? = null,
    val defaultBurnTimeSeconds: Int? = null,
    val defaultAddedFuel: Int? = null,
    findTarget: (World, BlockPos) -> BlockPos?,
    val action: (World, BlockPos) -> Unit,
    val validate: ((World, BlockPos) -> Boolean)? = null,
    val shouldContinue: ((World, BlockPos) -> Boolean)? = null,
) : BaseJob() {

    // Captured to avoid name conflict with BaseJob.findTarget()
    private val targetFinder = findTarget

    override val arrivalParticle: ParticleEffect = particle
    override val workPhase: WorkPhase = WorkPhase.ENVIRONMENTAL
    override val producesItems: Boolean = false

    override fun buildDefaultConfig() = JobConfig(
        enabled = true,
        qualifyingMoves = qualifyingMoves.toList(),
        fallbackSpecies = fallbackSpecies,
        cooldownSeconds = if (defaultCooldownSeconds > 0) defaultCooldownSeconds else 30,
        radius = defaultRadius,
        burnTimeSeconds = defaultBurnTimeSeconds,
        addedFuel = defaultAddedFuel,
    )

    init { registerConfig() }

    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
        dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species)

    override fun matchPriority(moves: Set<String>, types: Set<String>, species: String, ability: String) =
        dslMatchPriority(config, qualifyingMoves, fallbackSpecies, moves, species)

    override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? {
        val pos = targetFinder(context.world, context.origin) ?: return null
        return Target.Block(pos)
    }

    override fun validateTarget(state: PokemonWorkerState, context: JobContext): Boolean {
        val pos = state.targetPos ?: return false
        return validate?.invoke(context.world, pos)
            ?: !context.world.getBlockState(pos).isAir
    }

    override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
        val pos = state.targetPos ?: return WorkResult.Done()
        action(context.world, pos)

        if (shouldContinue?.invoke(context.world, pos) == true) {
            return WorkResult.Repeat
        }
        return WorkResult.Done()
    }

    override fun getCooldownTicks(state: PokemonWorkerState): Long {
        if (defaultCooldownSeconds <= 0) return 0L
        return (config.cooldownSeconds.takeIf { it > 0 } ?: defaultCooldownSeconds) * 20L
    }
}
