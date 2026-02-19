/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs

import accieo.cobbleworkers.enums.WorkerPriority
import accieo.cobbleworkers.interfaces.Worker
import java.util.UUID

/**
 * Computed once per Pokémon on pasture entry. Caches which jobs
 * the Pokémon qualifies for, partitioned by priority tier.
 * Rebuilt on config reload or pasture re-entry.
 */
data class PokemonProfile(
    val pokemonId: UUID,
    val moves: Set<String>,
    val types: Set<String>,
    val species: String,
    val ability: String,
    val comboEligible: List<Worker>,
    val moveEligible: List<Worker>,
    val speciesEligible: List<Worker>,
    val typeEligible: List<Worker>,
) {
    /** Returns the highest-priority non-empty eligibility tier. */
    fun bestEligible(): List<Worker> =
        comboEligible.ifEmpty { moveEligible.ifEmpty { speciesEligible.ifEmpty { typeEligible } } }

    companion object {
        fun build(
            pokemonId: UUID,
            moves: Set<String>,
            types: Set<String>,
            species: String,
            ability: String,
            allWorkers: List<Worker>,
        ): PokemonProfile {
            val combo = mutableListOf<Worker>()
            val move = mutableListOf<Worker>()
            val speciesList = mutableListOf<Worker>()
            val type = mutableListOf<Worker>()

            for (worker in allWorkers) {
                if (!worker.isEligible(moves, types, species, ability)) continue
                when (worker.priority) {
                    WorkerPriority.COMBO -> combo.add(worker)
                    WorkerPriority.MOVE -> move.add(worker)
                    WorkerPriority.SPECIES -> speciesList.add(worker)
                    WorkerPriority.TYPE -> type.add(worker)
                }
            }

            return PokemonProfile(
                pokemonId = pokemonId,
                moves = moves,
                types = types,
                species = species,
                ability = ability,
                comboEligible = combo,
                moveEligible = move,
                speciesEligible = speciesList,
                typeEligible = type,
            )
        }
    }
}
