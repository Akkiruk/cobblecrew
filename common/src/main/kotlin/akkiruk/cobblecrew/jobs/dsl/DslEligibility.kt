/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.dsl

import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.enums.WorkerPriority

/**
 * Shared eligibility check used by all DSL job classes.
 *
 * When [isCombo] is false (default), the Pokémon needs ANY qualifying move.
 * When [isCombo] is true, the Pokémon needs ALL qualifying moves (combo jobs).
 */
fun dslEligible(
    config: JobConfig,
    defaultMoves: Set<String>,
    defaultSpecies: List<String>,
    moves: Set<String>,
    species: String,
    isCombo: Boolean = false,
): Boolean {
    if (!config.enabled) return false
    val effectiveMoves = config.qualifyingMoves.ifEmpty { defaultMoves }.map { it.lowercase() }.toSet()
    val moveMatch = if (isCombo) effectiveMoves.all { it in moves } else moves.any { it in effectiveMoves }
    if (moveMatch) return true
    if (isCombo) return false // combos don't fall back to species
    val sp = config.fallbackSpecies.ifEmpty { defaultSpecies }
    return sp.any { it.equals(species, ignoreCase = true) }
}

/**
 * Returns the dynamic priority tier based on HOW the Pokémon matched.
 * COMBO if all combo moves present, MOVE if any qualifying move, SPECIES if fallback species.
 * Returns null if the Pokémon doesn't qualify at all.
 */
fun dslMatchPriority(
    config: JobConfig,
    defaultMoves: Set<String>,
    defaultSpecies: List<String>,
    moves: Set<String>,
    species: String,
    isCombo: Boolean = false,
): WorkerPriority? {
    if (!config.enabled) return null
    val effectiveMoves = config.qualifyingMoves.ifEmpty { defaultMoves }.map { it.lowercase() }.toSet()
    val moveMatch = if (isCombo) effectiveMoves.all { it in moves } else moves.any { it in effectiveMoves }
    if (moveMatch) return if (isCombo) WorkerPriority.COMBO else WorkerPriority.MOVE
    if (isCombo) return null
    val sp = config.fallbackSpecies.ifEmpty { defaultSpecies }
    if (sp.any { it.equals(species, ignoreCase = true) }) return WorkerPriority.SPECIES
    return null
}
