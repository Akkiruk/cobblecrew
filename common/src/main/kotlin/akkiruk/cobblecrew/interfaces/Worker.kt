/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.interfaces

import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.JobImportance
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.JobContext
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack

interface Worker {
    /** Human-readable job name (e.g. "apricorn_shaker"). Used as config key. */
    val name: String

    /** Block category this job consumes from the scanner cache. Null = not block-based. */
    val targetCategory: BlockCategory? get() = null

    /** Extra categories this job reads from the cache besides [targetCategory]. */
    val additionalScanCategories: Set<BlockCategory> get() = emptySet()

    /** Priority tier for eligibility sorting. */
    val priority: WorkerPriority get() = WorkerPriority.TYPE

    /** How urgently this job should run within its eligibility tier. */
    val importance: JobImportance get() = JobImportance.STANDARD

    /**
     * Static eligibility check — called once during profile build.
     * Pure data, no entity reference needed.
     */
    fun isEligible(
        moves: Set<String>,
        types: Set<String>,
        species: String,
        ability: String,
    ): Boolean = false

    /**
     * Returns the effective priority tier for a specific Pokémon based on HOW it matched.
     * DSL jobs override this to return MOVE/SPECIES/COMBO dynamically.
     * Returns null if the Pokémon doesn't qualify.
     */
    fun matchPriority(
        moves: Set<String>,
        types: Set<String>,
        species: String,
        ability: String,
    ): WorkerPriority? = if (isEligible(moves, types, species, ability)) priority else null

    /**
     * Dynamic availability check — are there targets right now?
     * Called during job selection, not every tick.
     */
    fun isAvailable(context: JobContext, pokemonId: java.util.UUID): Boolean = true

    /** Main logic loop, executed each tick. */
    fun tick(context: JobContext, pokemonEntity: PokemonEntity)

    /** True if this worker has active per-Pokémon state that requires continued ticking. */
    fun hasActiveState(pokemonId: java.util.UUID): Boolean = false

    /** Cleans up per-Pokémon state when a Pokémon leaves a pasture. */
    fun cleanup(pokemonId: java.util.UUID) {}

    /** Returns held items for a Pokémon, if any. Used for item recovery on recall. */
    fun getHeldItems(pokemonId: java.util.UUID): List<ItemStack>? = null
}