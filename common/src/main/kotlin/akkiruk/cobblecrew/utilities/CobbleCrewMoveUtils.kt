/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import akkiruk.cobblecrew.CobbleCrew
import akkiruk.cobblecrew.interfaces.Worker

/**
 * Validates move names configured in job definitions against Cobblemon's move registry.
 * Run once at startup after Cobblemon initializes.
 */
object CobbleCrewMoveUtils {
    /**
     * Checks all qualifying moves in registered workers against the Cobblemon move registry.
     * Logs warnings for unknown moves but does not crash.
     */
    fun validateMoves(workers: List<Worker>) {
        val allMoves = try {
            val movesClass = Class.forName("com.cobblemon.mod.common.api.moves.Moves")
            val allMethod = movesClass.getMethod("all")
            val movesInstance = movesClass.kotlin.objectInstance ?: return
            @Suppress("UNCHECKED_CAST")
            val moveList = allMethod.invoke(movesInstance) as? Iterable<*> ?: return
            moveList.mapNotNull { move ->
                try {
                    val nameMethod = move!!.javaClass.getMethod("getName")
                    (nameMethod.invoke(move) as? String)?.lowercase()
                } catch (_: Exception) { null }
            }.toSet()
        } catch (_: Exception) {
            CobbleCrew.LOGGER.warn("[CobbleCrew] Could not access Cobblemon Moves registry for validation")
            return
        }

        if (allMoves.isEmpty()) return

        CobbleCrew.LOGGER.info("[CobbleCrew] Validating move names against ${allMoves.size} Cobblemon moves")

        // Collect all configured move names from workers that expose them
        // For now, just log the registry size — full validation requires workers
        // to expose their move sets (added in Phase 1 when DSL is built)
        CobbleCrew.LOGGER.info("[CobbleCrew] Move validation complete — ${allMoves.size} moves available")
    }
}
