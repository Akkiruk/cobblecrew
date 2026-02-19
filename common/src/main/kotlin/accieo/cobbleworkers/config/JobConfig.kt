/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.config

/**
 * Universal per-job config. Loaded from per-category JSON files
 * under config/cobbleworkers/. DSL definitions provide defaults;
 * anything non-null here overrides the DSL default.
 */
data class JobConfig(
    val enabled: Boolean = true,
    val cooldownSeconds: Int = 30,
    val qualifyingMoves: List<String> = emptyList(),
    val fallbackType: String = "",
    val fallbackSpecies: List<String> = emptyList(),
    // Job-specific overrides (null = use DSL default)
    val replant: Boolean? = null,
    val lootTables: List<String>? = null,
    val effectDurationSeconds: Int? = null,
    val effectAmplifier: Int? = null,
)
