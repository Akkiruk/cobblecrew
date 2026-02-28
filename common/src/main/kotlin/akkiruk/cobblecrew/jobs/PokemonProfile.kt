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
    private val _allEligible by lazy { comboEligible + moveEligible + speciesEligible + typeEligible }

    /**
     * Returns all eligible jobs flattened, ordered by priority tier
     * (COMBO first, then MOVE, SPECIES, TYPE). Cached on first access.
     */
    fun allEligible(): List<Worker> = _allEligible

    fun getByPriority(priority: WorkerPriority): List<Worker> = when (priority) {
        WorkerPriority.COMBO -> comboEligible
        WorkerPriority.MOVE -> moveEligible
        WorkerPriority.SPECIES -> speciesEligible
        WorkerPriority.TYPE -> typeEligible
    }

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
