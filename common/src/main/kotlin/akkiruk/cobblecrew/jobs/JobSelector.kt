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
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.utilities.CobbleCrewDebugLogger
import java.util.UUID

/**
 * Job selection algorithm — picks the best available job for a Pokémon.
 *
 * Selection order:
 * 1. Priority tier: COMBO > MOVE > SPECIES > TYPE
 * 2. Within tier: importance (CRITICAL > HIGH > STANDARD > LOW > BACKGROUND)
 * 3. Within importance: random shuffle for fairness
 *
 * Also handles job stickiness — keeping a Pokémon on its current job
 * for [JOB_STICKINESS_TICKS] to prevent rapid flickering.
 */
object JobSelector {
    private const val JOB_STICKINESS_TICKS = 100L

    /**
     * Select the best available job from the Pokémon's eligible pool.
     * Returns null if no job is available right now.
     */
    fun selectBest(profile: PokemonProfile, context: JobContext, pokemonId: UUID): Worker? {
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

    /**
     * Check if the current job should stick — either because it has real
     * in-progress work, or because it was recently assigned and is still available.
     */
    fun shouldStick(
        current: Worker,
        state: PokemonWorkerState,
        context: JobContext,
        pokemonId: UUID,
        now: Long,
    ): Boolean {
        val hasRealWork = current.hasActiveState(pokemonId) || state.claim != null
        if (hasRealWork) return true
        return now - state.jobAssignedTick < JOB_STICKINESS_TICKS
            && current.isAvailable(context, pokemonId)
    }

    /** Debug reason string for why a job stuck. */
    fun stickReason(current: Worker, state: PokemonWorkerState, pokemonId: UUID): String = when {
        current.hasActiveState(pokemonId) -> "activeState"
        state.claim != null -> "hasClaim"
        else -> "stickiness"
    }
}
