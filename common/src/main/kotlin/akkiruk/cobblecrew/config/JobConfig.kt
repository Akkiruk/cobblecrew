/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.config

/**
 * Universal per-job config. Loaded from per-category JSON files
 * under config/cobblecrew/. DSL definitions provide defaults;
 * anything non-null here overrides the DSL default.
 */
data class JobConfig(
    val enabled: Boolean = true,
    val cooldownSeconds: Int = 30,
    val qualifyingMoves: List<String> = emptyList(),
    val fallbackSpecies: List<String> = emptyList(),
    val schemaVersion: Int = 0,
    // Job-specific overrides (null = use DSL default)
    val replant: Boolean? = null,
    val lootTables: List<String>? = null,
    val effectDurationSeconds: Int? = null,
    val effectAmplifier: Int? = null,
    val radius: Int? = null,
    val burnTimeSeconds: Int? = null,
    val addedFuel: Int? = null,
    val treasureChance: Int? = null,
    val structureTags: List<String>? = null,
    val useAllStructures: Boolean? = null,
    val mapNameIsHidden: Boolean? = null,
    val requiredAbility: String? = null,
    val requiresWater: Boolean? = null,
)
