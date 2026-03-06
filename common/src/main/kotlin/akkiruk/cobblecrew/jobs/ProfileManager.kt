/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.state.StateManager
import akkiruk.cobblecrew.utilities.CobbleCrewDebugLogger
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity

/**
 * Builds and caches [PokemonProfile]s — the immutable snapshot of a
 * Pokémon's moves/types/species/ability used for job eligibility.
 *
 * Profiles are rebuilt only when a Pokémon's move set changes.
 * Call [invalidateAll] on config reload to force a full rebuild.
 */
object ProfileManager {
    private val workers: List<Worker> get() = WorkerRegistry.workers

    /**
     * Return the cached profile, or build a fresh one if moves changed.
     * Active job is cleaned up on rebuild so the dispatcher can re-select.
     */
    fun getOrBuild(pokemonEntity: PokemonEntity, state: PokemonWorkerState): PokemonProfile {
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

    /** Force all profiles to rebuild on next tick. Called on config reload. */
    fun invalidateAll() {
        for (state in StateManager.all()) {
            state.profile = null
            state.lastMoveSet = emptySet()
        }
        CobbleCrewDebugLogger.profileInvalidated()
    }
}
