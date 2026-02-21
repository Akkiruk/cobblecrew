/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs

import accieo.cobbleworkers.interfaces.Worker
import accieo.cobbleworkers.utilities.CobbleworkersNavigationUtils
import accieo.cobbleworkers.utilities.DeferredBlockScanner
import accieo.cobbleworkers.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

object WorkerDispatcher {
    private val workers: List<Worker> get() = WorkerRegistry.workers

    private val activeJobs = mutableMapOf<UUID, Worker>()
    private val profiles = mutableMapOf<UUID, PokemonProfile>()
    private val jobAssignedTick = mutableMapOf<UUID, Long>()
    private const val JOB_STICKINESS_TICKS = 100L // 5 seconds

    fun tickAreaScan(world: World, pastureOrigin: BlockPos) {
        DeferredBlockScanner.tickPastureAreaScan(world, pastureOrigin)
    }

    /**
     * Builds or retrieves a cached PokemonProfile for the given entity.
     * Profile caches which jobs a Pokémon qualifies for — eliminates
     * per-tick shouldRun() calls across all workers.
     */
    private fun getOrBuildProfile(pokemonEntity: PokemonEntity): PokemonProfile {
        val pokemon = pokemonEntity.pokemon
        val pokemonId = pokemon.uuid
        profiles[pokemonId]?.let { return it }

        val moves = pokemon.moveSet.getMoves().map { it.name.lowercase() }.toSet()
        val types = pokemon.types.map { it.name.uppercase() }.toSet()
        val species = pokemon.species.translatedName.string.lowercase()
        val ability = pokemon.ability.name.lowercase()

        return PokemonProfile.build(pokemonId, moves, types, species, ability, workers)
            .also { profiles[pokemonId] = it }
    }

    fun tickPokemon(world: World, pastureOrigin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val profile = getOrBuildProfile(pokemonEntity)
        val eligible = profile.allEligible()

        if (eligible.isEmpty()) {
            activeJobs.remove(pokemonId)
            WorkerVisualUtils.setExcited(pokemonEntity, false)
            returnToPasture(pokemonEntity, pastureOrigin)
            return
        }

        val current = activeJobs[pokemonId]
        val now = world.time

        // Stick with current job while it has work or stickiness hasn't expired
        if (current != null && current in eligible) {
            val assignedAt = jobAssignedTick[pokemonId] ?: 0L
            if (current.hasActiveState(pokemonId)
                || CobbleworkersNavigationUtils.getTarget(pokemonId, world) != null
                || CobbleworkersNavigationUtils.getPlayerTarget(pokemonId, world) != null
                || now - assignedAt < JOB_STICKINESS_TICKS
            ) {
                WorkerVisualUtils.setExcited(pokemonEntity, true)
                current.tick(world, pastureOrigin, pokemonEntity)
                return
            }
        }

        // Shuffle and cycle: try each eligible job until one is actually available
        val shuffled = eligible.shuffled()
        val job = shuffled.firstOrNull { it.isAvailable(world, pastureOrigin, pokemonId) }

        if (job == null) {
            // Nothing available right now — idle at pasture
            activeJobs.remove(pokemonId)
            WorkerVisualUtils.setExcited(pokemonEntity, false)
            returnToPasture(pokemonEntity, pastureOrigin)
            return
        }

        activeJobs[pokemonId] = job
        jobAssignedTick[pokemonId] = now
        WorkerVisualUtils.setExcited(pokemonEntity, true)
        job.tick(world, pastureOrigin, pokemonEntity)
    }

    private fun returnToPasture(pokemonEntity: PokemonEntity, pastureOrigin: BlockPos) {
        if (!CobbleworkersNavigationUtils.isPokemonAtPosition(pokemonEntity, pastureOrigin, 3.0)) {
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, pastureOrigin)
        }
    }

    fun cleanupPokemon(pokemonId: UUID, world: World) {
        workers.forEach { it.cleanup(pokemonId) }
        activeJobs.remove(pokemonId)
        profiles.remove(pokemonId)
        jobAssignedTick.remove(pokemonId)
        WorkerVisualUtils.cleanup(pokemonId)
        CobbleworkersNavigationUtils.cleanupPokemon(pokemonId, world)
    }

    /** Invalidate all cached profiles (e.g. on config reload). */
    fun invalidateProfiles() {
        profiles.clear()
    }
}