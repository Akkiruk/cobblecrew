/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.state

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for all per-Pokémon worker state.
 * Cleanup is one call: `remove(pokemonId)` — zero leak potential.
 */
object StateManager {
    private val states = ConcurrentHashMap<UUID, PokemonWorkerState>()

    fun getOrCreate(pokemonId: UUID): PokemonWorkerState =
        states.getOrPut(pokemonId) { PokemonWorkerState(pokemonId) }

    fun get(pokemonId: UUID): PokemonWorkerState? = states[pokemonId]

    fun remove(pokemonId: UUID): PokemonWorkerState? = states.remove(pokemonId)

    fun all(): Collection<PokemonWorkerState> = states.values

    fun count(): Int = states.size

    /** Snapshot of pokemonId → active job name (for commands). */
    fun activeJobsSnapshot(): Map<UUID, String> =
        states.entries
            .filter { it.value.activeJob != null }
            .associate { it.key to it.value.activeJob!!.name }

    /** Snapshot of pokemonId → profile (for commands). */
    fun profilesSnapshot(): Map<UUID, akkiruk.cobblecrew.jobs.PokemonProfile> =
        states.entries
            .mapNotNull { (id, s) -> s.profile?.let { id to it } }
            .toMap()

    fun activeWorkerCount(): Int = states.values.count { it.activeJob != null }

    fun cachedProfileCount(): Int = states.values.count { it.profile != null }

    fun clearAll() = states.clear()
}
