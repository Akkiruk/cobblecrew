/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.state.StateManager
import akkiruk.cobblecrew.utilities.CobbleCrewDebugLogger
import akkiruk.cobblecrew.utilities.HostileScanCache
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.world.World
import java.util.UUID

/**
 * Central dispatcher — assigns jobs to Pokémon, delegates idle behavior
 * to [IdleBehaviorHandler].
 *
 * All per-Pokémon state lives in [StateManager]. This object holds only
 * constants and stateless logic (profile building, job selection).
 */
object WorkerDispatcher {
    private val workers: List<Worker> get() = WorkerRegistry.workers

    private const val JOB_STICKINESS_TICKS = 100L
    private const val IDLE_LOG_INTERVAL = 600L
    private const val SWEEP_INTERVAL = 200L
    private var lastSweepTick = 0L

    /**
     * Periodic maintenance — call once per server tick from platform hooks.
     * Sweeps expired claims/blacklists every [SWEEP_INTERVAL] ticks.
     */
    fun tickMaintenance(serverTick: Long) {
        if (serverTick - lastSweepTick >= SWEEP_INTERVAL) {
            lastSweepTick = serverTick
            ClaimManager.sweepExpired(serverTick)
            HostileScanCache.sweepExpired(serverTick)
        }
    }

    private fun getOrBuildProfile(pokemonEntity: PokemonEntity, state: PokemonWorkerState): PokemonProfile {
        val pokemon = pokemonEntity.pokemon
        val pokemonId = pokemon.uuid

        val active = pokemon.moveSet.getMoves().map { it.name.lowercase() }
        val benched = pokemon.benchedMoves.map { it.moveTemplate.name.lowercase() }
        val moves = (active + benched).toSet()

        val cached = state.profile
        if (cached != null && state.lastMoveSet == moves) return cached

        if (cached != null) {
            CobbleCrewDebugLogger.log(CobbleCrewDebugLogger.Category.DISPATCH,
                "${pokemon.species.name}($pokemonId) moves changed, rebuilding profile")
            state.activeJob?.cleanup(pokemonId)
            state.activeJob = null
            state.jobAssignedTick = 0L
        }

        val types = pokemon.types.map { it.name.uppercase() }.toSet()
        val species = pokemon.species.name.lowercase()
        val ability = pokemon.ability.name.lowercase()

        return PokemonProfile.build(pokemonId, moves, types, species, ability, workers)
            .also {
                state.profile = it
                state.lastMoveSet = moves
                val eligible = it.allEligible()
                CobbleCrewDebugLogger.profileBuilt(species, pokemonId, moves, types, eligible.map { w -> w.name })
            }
    }

    fun tickPokemon(context: JobContext, pokemonEntity: PokemonEntity) {
        val world = context.world
        val pokemonId = pokemonEntity.pokemon.uuid
        val state = StateManager.getOrCreate(pokemonId)
        val profile = getOrBuildProfile(pokemonEntity, state)
        val eligible = profile.allEligible()

        if (eligible.isEmpty()) {
            state.activeJob = null
            WorkerVisualUtils.setExcited(pokemonEntity, false)
            val now = world.time
            if (now - state.idleLogTick >= IDLE_LOG_INTERVAL) {
                state.idleLogTick = now
                CobbleCrewDebugLogger.noEligibleJobs(pokemonEntity.pokemon.species.name, pokemonId)
            }
            IdleBehaviorHandler.handle(state, context, pokemonEntity, world)
            return
        }

        val current = state.activeJob
        val now = world.time
        state.idleSinceTick = 0L

        if (current != null && current in eligible) {
            val hasRealWork = current.hasActiveState(pokemonId) || state.claim != null
            val stickyAndAvailable = !hasRealWork
                && now - state.jobAssignedTick < JOB_STICKINESS_TICKS
                && current.isAvailable(context, pokemonId)
            if (hasRealWork || stickyAndAvailable) {
                val reason = when {
                    current.hasActiveState(pokemonId) -> "activeState"
                    state.claim != null -> "hasClaim"
                    else -> "stickiness"
                }
                CobbleCrewDebugLogger.jobSticking(pokemonEntity.pokemon.species.name, pokemonId, current.name, reason)
                WorkerVisualUtils.setExcited(pokemonEntity, true)
                current.tick(context, pokemonEntity)
                return
            }
        }

        val job = selectBestAvailableJob(profile, context, pokemonId)

        if (current != null && job != current) {
            current.cleanup(pokemonId)
            ClaimManager.release(state, world, blacklist = false)
            state.resetJobState()
        }

        if (job == null) {
            state.activeJob = null
            WorkerVisualUtils.setExcited(pokemonEntity, false)
            if (current != null) state.lastPickupAttemptTick = now
            IdleBehaviorHandler.handle(state, context, pokemonEntity, world)

            if (now - state.idleLogTick >= IDLE_LOG_INTERVAL) {
                state.idleLogTick = now
                val availInfo = eligible.map { w ->
                    val avail = w.isAvailable(context, pokemonId)
                    "${w.name}=$avail"
                }
                CobbleCrewDebugLogger.jobIdle(
                    pokemonEntity.pokemon.species.name, pokemonId,
                    eligible.size, availInfo
                )
            }
            return
        }

        state.activeJob = job
        state.jobAssignedTick = now
        state.idleSinceTick = 0L
        state.returningHome = false
        state.lastReturnNavTick = 0L
        // Release stale idle-pickup claims when transitioning from idle to job
        if (current == null) {
            ClaimManager.release(state, world, blacklist = false)
            if (state.heldItems.isNotEmpty()) {
                state.heldItems.forEach { stack -> Block.dropStack(world, pokemonEntity.blockPos, stack) }
                state.heldItems.clear()
            }
            state.failedDeposits.clear()
            state.idlePickupClaimTick = 0L
        }
        CobbleCrewDebugLogger.jobAssigned(pokemonEntity.pokemon.species.name, pokemonId, job.name)
        WorkerVisualUtils.setExcited(pokemonEntity, true)
        job.tick(context, pokemonEntity)
    }

    private fun selectBestAvailableJob(profile: PokemonProfile, context: JobContext, pokemonId: UUID): Worker? {
        for (priority in WorkerPriority.entries) {
            val candidates = profile.getByPriority(priority)
            if (candidates.isEmpty()) continue
            val sorted = candidates
                .groupBy { it.importance }
                .toSortedMap()
                .flatMap { (_, workers) -> workers.shuffled() }
            val available = sorted.firstOrNull { it.isAvailable(context, pokemonId) }
            if (available != null) return available
        }
        return null
    }

    fun cleanupPokemon(pokemonId: UUID, world: World) {
        val state = StateManager.get(pokemonId)
        val species = state?.profile?.species

        workers.forEach { it.cleanup(pokemonId) }
        ClaimManager.cleanupPokemon(pokemonId, world)
        StateManager.remove(pokemonId)

        CobbleCrewDebugLogger.pokemonCleanedUp(species, pokemonId)
    }

    fun invalidateProfiles() {
        resetAllAssignments()
        for (state in StateManager.all()) {
            state.profile = null
            state.lastMoveSet = emptySet()
        }
        CobbleCrewDebugLogger.profileInvalidated()
    }

    // -- Command accessors --

    fun getActiveJobsSnapshot(): Map<UUID, String> = StateManager.activeJobsSnapshot()

    fun hasActiveJob(pokemonId: UUID): Boolean = StateManager.get(pokemonId)?.activeJob != null

    fun getProfilesSnapshot(): Map<UUID, PokemonProfile> = StateManager.profilesSnapshot()

    fun getCachedProfileCount(): Int = StateManager.cachedProfileCount()

    fun getActiveWorkerCount(): Int = StateManager.activeWorkerCount()

    fun resetAssignment(pokemonId: UUID) {
        val state = StateManager.get(pokemonId) ?: return
        val job = state.activeJob ?: return
        job.cleanup(pokemonId)
        state.activeJob = null
        state.jobAssignedTick = 0L
        state.idleLogTick = 0L
    }

    fun resetAllAssignments() {
        for (state in StateManager.all()) {
            if (state.activeJob != null) {
                state.activeJob!!.cleanup(state.pokemonId)
                state.activeJob = null
                state.jobAssignedTick = 0L
                state.idleLogTick = 0L
            }
        }
    }
}