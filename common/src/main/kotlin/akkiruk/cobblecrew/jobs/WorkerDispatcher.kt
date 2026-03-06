/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.StateManager
import akkiruk.cobblecrew.utilities.CobbleCrewDebugLogger
import akkiruk.cobblecrew.utilities.HostileScanCache
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.world.World
import java.util.UUID

/**
 * Central dispatcher — orchestrates job assignment per tick.
 * Profile building is delegated to [ProfileManager],
 * job selection to [JobSelector], idle behavior to [IdleBehaviorHandler].
 */
object WorkerDispatcher {

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

    fun tickPokemon(context: JobContext, pokemonEntity: PokemonEntity) {
        val world = context.world
        val pokemonId = pokemonEntity.pokemon.uuid
        val state = StateManager.getOrCreate(pokemonId)
        val profile = ProfileManager.getOrBuild(pokemonEntity, state)
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

        // Stick with current job if it has real work or is within stickiness window
        if (current != null && current in eligible && JobSelector.shouldStick(current, state, context, pokemonId, now)) {
            val reason = JobSelector.stickReason(current, state, pokemonId)
            CobbleCrewDebugLogger.jobSticking(pokemonEntity.pokemon.species.name, pokemonId, current.name, reason)
            WorkerVisualUtils.setExcited(pokemonEntity, true)
            current.tick(context, pokemonEntity)
            return
        }

        val job = JobSelector.selectBest(profile, context, pokemonId)

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
        state.idle.resetOnJobAssign()
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

    fun cleanupPokemon(pokemonId: UUID, world: World) {
        val state = StateManager.get(pokemonId)
        val species = state?.profile?.species

        ClaimManager.cleanupPokemon(pokemonId, world)
        StateManager.remove(pokemonId)

        CobbleCrewDebugLogger.pokemonCleanedUp(species, pokemonId)
    }

    fun invalidateProfiles() {
        resetAllAssignments()
        ProfileManager.invalidateAll()
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