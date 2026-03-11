/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.dsl

import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.JobImportance
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.BaseJob
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.jobs.Target
import akkiruk.cobblecrew.jobs.WorkResult
import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.utilities.HostileScanCache
import akkiruk.cobblecrew.utilities.WorkerAnimationUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID

/**
 * DSL-style defense job. Scans for hostile mobs, navigates to them,
 * and applies an effect (damage, debuff, etc.).
 */
open class DefenseJob(
    override val name: String,
    override val category: String = "defense",
    val qualifyingMoves: Set<String> = emptySet(),
    val typeGatedMoves: Map<String, String> = emptyMap(),
    val fallbackSpecies: List<String> = emptyList(),
    override val priority: WorkerPriority = WorkerPriority.MOVE,
    override val importance: JobImportance = JobImportance.CRITICAL,
    val particle: ParticleEffect = ParticleTypes.CRIT,
    val phase: WorkPhase = WorkPhase.ATTACKING,
    val effectFn: (World, PokemonEntity, HostileEntity) -> Unit,
    val isCombo: Boolean = false,
    val partyEnabled: Boolean = false,
) : BaseJob() {

    private val generalConfig get() = CobbleCrewConfigHolder.config.general

    override val arrivalParticle: ParticleEffect = particle
    override val targetCategory: BlockCategory? = null
    override val arrivalTolerance: Double = 2.0
    override val workPhase: WorkPhase = phase
    override val producesItems: Boolean = false
    override val bypassesAvailabilityCache: Boolean = true

    override fun buildDefaultConfig() = JobConfig(
        enabled = true,
        qualifyingMoves = qualifyingMoves.toList(),
        fallbackSpecies = fallbackSpecies,
        partyEnabled = partyEnabled,
    )

    init { registerConfig() }

    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
        dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)

    override fun matchPriority(moves: Set<String>, types: Set<String>, species: String, ability: String) =
        dslMatchPriority(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)

    override fun isAvailable(context: JobContext, pokemonId: UUID): Boolean {
        if (!config.enabled) return false
        val box = searchBox(context.origin)
        return HostileScanCache.getHostileIds(context.world, context.origin, box).any { id ->
            val mob = HostileScanCache.resolve(context.world, id) ?: return@any false
            !ClaimManager.isTargetedByOther(Target.Mob(id, mob.blockPos), pokemonId)
        }
    }

    override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? {
        val box = searchBox(context.origin)
        val ids = HostileScanCache.getHostileIds(context.world, context.origin, box)
        if (ids.isEmpty()) return null

        val closest = ids
            .mapNotNull { id -> HostileScanCache.resolve(context.world, id)?.let { id to it } }
            .filter { (id, mob) -> !ClaimManager.isTargetedByOther(Target.Mob(id, mob.blockPos), state.pokemonId) }
            .minByOrNull { (_, mob) -> mob.blockPos.getSquaredDistance(context.origin) }
            ?: return null

        return Target.Mob(closest.first, closest.second.blockPos)
    }

    override fun validateTarget(state: PokemonWorkerState, context: JobContext): Boolean {
        val claim = state.claim ?: return false
        val mobTarget = claim.target as? Target.Mob ?: return false
        val mob = HostileScanCache.resolve(context.world, mobTarget.entityId) ?: return false
        if (!mob.isAlive || mob.isRemoved) return false
        state.targetPos = mob.blockPos
        return true
    }

    override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
        val claim = state.claim ?: return WorkResult.Done()
        val mobTarget = claim.target as? Target.Mob ?: return WorkResult.Done()
        val mob = HostileScanCache.resolve(context.world, mobTarget.entityId) ?: return WorkResult.Done()
        WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.HOSTILE_SPOTTED, context.world)
        effectFn(context.world, pokemonEntity, mob)
        return WorkResult.Done()
    }

    private fun searchBox(origin: net.minecraft.util.math.BlockPos): Box {
        val r = generalConfig.searchRadius.toDouble()
        val h = generalConfig.searchHeight.toDouble()
        return Box(origin).expand(r, h, r)
    }
}
