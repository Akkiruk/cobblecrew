/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs

import accieo.cobbleworkers.enums.JobType
import accieo.cobbleworkers.interfaces.Worker
import accieo.cobbleworkers.utilities.CobbleworkersNavigationUtils
import accieo.cobbleworkers.utilities.DeferredBlockScanner
import accieo.cobbleworkers.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

object WorkerDispatcher {
    /**
     * Worker registry — all registered workers from WorkerRegistry.
     */
    private val workers: List<Worker> get() = WorkerRegistry.workers

    /**
     * Gathers all block validators from registered workers.
     */
    @Suppress("DEPRECATION")
    private val jobValidators: Map<JobType, (World, BlockPos) -> Boolean>
        get() = workers
            .mapNotNull { worker -> worker.blockValidator?.let { worker.jobType to it } }
            .toMap()

    /**
     * Ticks the deferred block scanning for a single pasture.
     * Called ONCE per pasture per tick.
     */
    fun tickAreaScan(world: World, pastureOrigin: BlockPos) {
        DeferredBlockScanner.tickPastureAreaScan(
            world,
            pastureOrigin,
            jobValidators
        )
    }

    /**
     * Tracks which worker each Pokémon is currently assigned to.
     * Ensures one job at a time per Pokémon.
     */
    private val activeJobs = mutableMapOf<UUID, Worker>()

    /**
     * Ticks the action logic for a specific Pokémon.
     * Assigns one job at a time — the Pokémon completes its current task before switching.
     */
    fun tickPokemon(world: World, pastureOrigin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val eligible = workers.filter { it.shouldRun(pokemonEntity) }

        if (eligible.isEmpty()) {
            activeJobs.remove(pokemonId)
            WorkerVisualUtils.setExcited(pokemonEntity, false)
            returnToPasture(pokemonEntity, pastureOrigin)
            return
        }

        val current = activeJobs[pokemonId]
        val job = if (current != null && current in eligible) {
            current
        } else {
            eligible.random().also { activeJobs[pokemonId] = it }
        }

        WorkerVisualUtils.setExcited(pokemonEntity, true)
        job.tick(world, pastureOrigin, pokemonEntity)

        // Allow job rotation when the current task cycle is done
        if (CobbleworkersNavigationUtils.getTarget(pokemonId, world) == null &&
            CobbleworkersNavigationUtils.getPlayerTarget(pokemonId, world) == null &&
            !job.hasActiveState(pokemonId)) {
            activeJobs.remove(pokemonId)
        }
    }

    private fun returnToPasture(pokemonEntity: PokemonEntity, pastureOrigin: BlockPos) {
        if (!CobbleworkersNavigationUtils.isPokemonAtPosition(pokemonEntity, pastureOrigin, 3.0)) {
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, pastureOrigin)
        }
    }

    /**
     * Cleans up all per-Pokémon state when a Pokémon leaves a pasture.
     * Call this when a Pokémon is removed/recalled from a pasture.
     */
    fun cleanupPokemon(pokemonId: UUID, world: World) {
        workers.forEach { it.cleanup(pokemonId) }
        activeJobs.remove(pokemonId)
        WorkerVisualUtils.cleanup(pokemonId)
        CobbleworkersNavigationUtils.cleanupPokemon(pokemonId, world)
    }
}