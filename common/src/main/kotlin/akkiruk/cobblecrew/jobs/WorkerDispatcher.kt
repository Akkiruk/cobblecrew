/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.utilities.CobbleCrewDebugLogger
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import akkiruk.cobblecrew.utilities.DeferredBlockScanner
import akkiruk.cobblecrew.utilities.WorkerAnimationUtils
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

object WorkerDispatcher {
    private val workers: List<Worker> get() = WorkerRegistry.workers

    private val activeJobs = mutableMapOf<UUID, Worker>()
    private val profiles = mutableMapOf<UUID, PokemonProfile>()
    private val jobAssignedTick = mutableMapOf<UUID, Long>()
    private val idleLogTick = mutableMapOf<UUID, Long>()
    private val idleSinceTick = mutableMapOf<UUID, Long>()
    private const val JOB_STICKINESS_TICKS = 100L // 5 seconds
    private const val IDLE_LOG_INTERVAL = 600L // log idle reason every 30s
    private const val IDLE_BORED_THRESHOLD = 600L // 30 seconds idle → bored animation

    fun tickAreaScan(context: JobContext) {
        DeferredBlockScanner.tickAreaScan(context)
    }

    /**
     * Builds or retrieves a cached PokemonProfile for the given entity.
     * Profile caches which jobs a Pokémon qualifies for — eliminates
     * per-tick shouldRun() calls across all workers.
     */
    private fun getOrBuildProfile(pokemonEntity: PokemonEntity): PokemonProfile {
        val pokemon = pokemonEntity.pokemon
        val pokemonId = pokemon.uuid

        val moves = pokemon.moveSet.getMoves().map { it.name.lowercase() }.toSet()

        // Invalidate cache if moves changed (e.g. player taught/forgot a move)
        profiles[pokemonId]?.let { cached ->
            if (cached.moves == moves) return cached
            CobbleCrewDebugLogger.log(CobbleCrewDebugLogger.Category.DISPATCH, "${pokemon.species.name}($pokemonId) moves changed, rebuilding profile")
            // Clean up active job — it may no longer be eligible
            activeJobs.remove(pokemonId)?.cleanup(pokemonId)
            jobAssignedTick.remove(pokemonId)
        }

        val types = pokemon.types.map { it.name.uppercase() }.toSet()
        val species = pokemon.species.name.lowercase()
        val ability = pokemon.ability.name.lowercase()

        return PokemonProfile.build(pokemonId, moves, types, species, ability, workers)
            .also {
                profiles[pokemonId] = it
                val eligible = it.allEligible()
                CobbleCrewDebugLogger.profileBuilt(species, pokemonId, moves, types, eligible.map { w -> w.name })
            }
    }

    fun tickPokemon(context: JobContext, pokemonEntity: PokemonEntity) {
        val world = context.world
        val pokemonId = pokemonEntity.pokemon.uuid
        val profile = getOrBuildProfile(pokemonEntity)
        val eligible = profile.allEligible()

        if (eligible.isEmpty()) {
            activeJobs.remove(pokemonId)
            WorkerVisualUtils.setExcited(pokemonEntity, false)
            val now = world.time
            val lastLog = idleLogTick[pokemonId] ?: 0L
            if (now - lastLog >= IDLE_LOG_INTERVAL) {
                idleLogTick[pokemonId] = now
                CobbleCrewDebugLogger.noEligibleJobs(pokemonEntity.pokemon.species.name, pokemonId)
            }
            handleIdleAnimation(pokemonEntity, world, pokemonId)
            returnToOrigin(pokemonEntity, context)
            return
        }

        val current = activeJobs[pokemonId]
        val now = world.time
        idleSinceTick.remove(pokemonId) // Working, not idle

        // Stick with current job while it has work or stickiness hasn't expired
        if (current != null && current in eligible) {
            val assignedAt = jobAssignedTick[pokemonId] ?: 0L
            if (current.hasActiveState(pokemonId)
                || CobbleCrewNavigationUtils.getTarget(pokemonId, world) != null
                || CobbleCrewNavigationUtils.getPlayerTarget(pokemonId, world) != null
                || now - assignedAt < JOB_STICKINESS_TICKS
            ) {
                val reason = when {
                    current.hasActiveState(pokemonId) -> "activeState"
                    CobbleCrewNavigationUtils.getTarget(pokemonId, world) != null -> "hasTarget"
                    CobbleCrewNavigationUtils.getPlayerTarget(pokemonId, world) != null -> "hasPlayerTarget"
                    else -> "stickiness"
                }
                CobbleCrewDebugLogger.jobSticking(pokemonEntity.pokemon.species.name, pokemonId, current.name, reason)
                WorkerVisualUtils.setExcited(pokemonEntity, true)
                current.tick(context, pokemonEntity)
                return
            }
        }

        // Shuffle and cycle: try each eligible job until one is actually available
        val shuffled = eligible.shuffled()
        val job = shuffled.firstOrNull { it.isAvailable(context, pokemonId) }

        if (job == null) {
            // Nothing available right now — idle at origin
            activeJobs.remove(pokemonId)
            WorkerVisualUtils.setExcited(pokemonEntity, false)
            handleIdleAnimation(pokemonEntity, world, pokemonId)
            returnToOrigin(pokemonEntity, context)

            val lastLog = idleLogTick[pokemonId] ?: 0L
            if (now - lastLog >= IDLE_LOG_INTERVAL) {
                idleLogTick[pokemonId] = now
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

        activeJobs[pokemonId] = job
        jobAssignedTick[pokemonId] = now
        idleSinceTick.remove(pokemonId)
        CobbleCrewDebugLogger.jobAssigned(pokemonEntity.pokemon.species.name, pokemonId, job.name)
        WorkerVisualUtils.setExcited(pokemonEntity, true)
        job.tick(context, pokemonEntity)
    }

    private fun handleIdleAnimation(pokemonEntity: PokemonEntity, world: World, pokemonId: UUID) {
        val now = world.time
        val idleSince = idleSinceTick.getOrPut(pokemonId) { now }
        val phase = if (now - idleSince >= IDLE_BORED_THRESHOLD) WorkPhase.IDLE_BORED else WorkPhase.IDLE_AT_ORIGIN
        WorkerAnimationUtils.playWorkAnimation(pokemonEntity, phase, world)
    }

    private fun returnToOrigin(pokemonEntity: PokemonEntity, context: JobContext) {
        when (context) {
            is JobContext.Pasture -> {
                if (!CobbleCrewNavigationUtils.isPokemonAtPosition(pokemonEntity, context.origin, 3.0)) {
                    CobbleCrewNavigationUtils.navigateTo(pokemonEntity, context.origin)
                }
            }
            is JobContext.Party -> {
                if (!CobbleCrewNavigationUtils.isPokemonAtPosition(pokemonEntity, context.player.blockPos, 5.0)) {
                    CobbleCrewNavigationUtils.navigateTo(pokemonEntity, context.player.blockPos)
                }
            }
        }
    }

    fun cleanupPokemon(pokemonId: UUID, world: World) {
        val species = profiles[pokemonId]?.species
        workers.forEach { it.cleanup(pokemonId) }
        activeJobs.remove(pokemonId)
        profiles.remove(pokemonId)
        jobAssignedTick.remove(pokemonId)
        idleLogTick.remove(pokemonId)
        idleSinceTick.remove(pokemonId)
        WorkerVisualUtils.cleanup(pokemonId)
        CobbleCrewNavigationUtils.cleanupPokemon(pokemonId, world)
        CobbleCrewDebugLogger.pokemonCleanedUp(species, pokemonId)
    }

    /** Invalidate all cached profiles (e.g. on config reload). */
    fun invalidateProfiles() {
        profiles.clear()
        CobbleCrewDebugLogger.profileInvalidated()
    }

    // -- Command accessors --

    fun getActiveJobsSnapshot(): Map<UUID, String> = activeJobs.mapValues { it.value.name }

    fun hasActiveJob(pokemonId: UUID): Boolean = pokemonId in activeJobs

    fun getProfilesSnapshot(): Map<UUID, PokemonProfile> = profiles.toMap()

    fun getCachedProfileCount(): Int = profiles.size

    fun getActiveWorkerCount(): Int = activeJobs.size

    fun resetAssignment(pokemonId: UUID) {
        val job = activeJobs.remove(pokemonId) ?: return
        job.cleanup(pokemonId)
        jobAssignedTick.remove(pokemonId)
        idleLogTick.remove(pokemonId)
        profiles.remove(pokemonId)
    }

    fun resetAllAssignments() {
        val ids = activeJobs.keys.toList()
        ids.forEach { resetAssignment(it) }
    }
}