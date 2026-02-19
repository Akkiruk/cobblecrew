/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.interfaces

import accieo.cobbleworkers.enums.BlockCategory
import accieo.cobbleworkers.enums.JobType
import accieo.cobbleworkers.enums.WorkerPriority
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

interface Worker {
    /** Human-readable job name (e.g. "apricorn_shaker"). Used as config key. */
    val name: String get() = jobType.name.lowercase()

    /** Block category this job consumes from the scanner cache. Null = not block-based. */
    val targetCategory: BlockCategory? get() = null

    /** Priority tier for eligibility sorting. */
    val priority: WorkerPriority get() = WorkerPriority.TYPE

    // --- Legacy fields (kept for migration, will be removed in Phase 2) ---
    @Deprecated("Use targetCategory + BlockCategoryValidators instead")
    val jobType: JobType get() = JobType.Generic

    @Deprecated("Use BlockCategoryValidators instead")
    val blockValidator: ((World, BlockPos) -> Boolean)? get() = null

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
     * Dynamic availability check — are there targets right now?
     * Called during job selection, not every tick.
     */
    fun isAvailable(world: World, origin: BlockPos, pokemonId: java.util.UUID): Boolean = true

    /**
     * Legacy eligibility — delegates to isEligible() by default.
     * Existing jobs override this directly until migrated.
     */
    fun shouldRun(pokemonEntity: PokemonEntity): Boolean {
        val pokemon = pokemonEntity.pokemon
        val moves = pokemon.moveSet.getMoves().map { it.name.lowercase() }.toSet()
        val types = pokemon.types.map { it.name.uppercase() }.toSet()
        val species = pokemon.species.translatedName.string.lowercase()
        val ability = pokemon.ability.name.lowercase()
        return isEligible(moves, types, species, ability)
    }

    /** Main logic loop, executed each tick. */
    fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity)

    /** True if this worker has active per-Pokémon state that requires continued ticking. */
    fun hasActiveState(pokemonId: java.util.UUID): Boolean = false

    /** Cleans up per-Pokémon state when a Pokémon leaves a pasture. */
    fun cleanup(pokemonId: java.util.UUID) {}
}