/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.CobbleCrew
import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.DeferredBlockScanner
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages worker dispatch for Pokémon tethered to Pasture Blocks.
 * Called from the PastureBlockEntityMixin thin shim — all logic lives here.
 */
object PastureWorkerManager {
    private const val POKEMON_TICK_INTERVAL = 5

    // Track previously-tethered UUIDs per pasture position for recall detection
    private val previousTethered = ConcurrentHashMap<BlockPos, MutableSet<UUID>>()

    /**
     * Called every pasture ticker tick (via mixin). Runs the deferred area scan
     * and dispatches jobs to tethered Pokémon.
     */
    fun tickPasture(world: World, pos: BlockPos, pasture: PokemonPastureBlockEntity) {
        if (world.isClient) return

        val context = JobContext.Pasture(pos, world)

        // Area scan runs every tick (deferred scanner internally throttles via cooldown)
        try {
            DeferredBlockScanner.tickAreaScan(context)
        } catch (e: Exception) {
            CobbleCrew.LOGGER.error("[CobbleCrew] Error in tickAreaScan", e)
            return
        }

        // Stagger Pokémon dispatch — no need to evaluate jobs every tick.
        // Per-pasture offset so pastures don't all tick the same frame.
        if ((world.time + (pos.hashCode() and 0x7FFFFFFF)) % POKEMON_TICK_INTERVAL != 0L) return

        CobbleCrewInventoryUtils.tickAnimations(world)

        val tethered = pasture.tetheredPokemon

        // Detect recalled Pokémon (present last tick, absent now)
        val currentIds = mutableSetOf<UUID>()
        for (t in tethered) {
            try { currentIds.add(t.pokemonId) } catch (_: Exception) {}
        }
        val prevIds = previousTethered.getOrPut(pos) { mutableSetOf() }
        for (prevId in prevIds) {
            if (prevId !in currentIds) {
                WorkerDispatcher.cleanupPokemon(prevId, world)
            }
        }
        prevIds.clear()
        prevIds.addAll(currentIds)

        // Tick each tethered Pokémon
        for (tethering in tethered) {
            val pokemon = try { tethering.getPokemon() } catch (e: Exception) {
                CobbleCrew.LOGGER.error("[CobbleCrew] Failed to get Pokémon from tethering: ${e.message}")
                continue
            }
            if (pokemon == null || pokemon.isFainted()) continue

            val pokemonEntity = pokemon.entity ?: continue
            val poseType = pokemonEntity.dataTracker.get(PokemonEntity.POSE_TYPE)
            if (poseType == PoseType.SLEEP) continue

            try {
                WorkerDispatcher.tickPokemon(context, pokemonEntity)
            } catch (e: Exception) {
                CobbleCrew.LOGGER.error("[CobbleCrew] Error in tickPokemon: ${e.message}")
            }
        }
    }

    /**
     * Called when a pasture block is broken (via mixin). Cleans up all
     * tethered Pokémon and removes the block cache.
     */
    fun onPastureBroken(pasture: PokemonPastureBlockEntity) {
        val world = pasture.world ?: return
        if (world.isClient) return

        for (tethering in pasture.tetheredPokemon) {
            try {
                WorkerDispatcher.cleanupPokemon(tethering.pokemonId, world)
            } catch (e: Exception) {
                CobbleCrew.LOGGER.error("[CobbleCrew] Failed to cleanup Pokémon on pasture break", e)
            }
        }
        CobbleCrewCacheManager.removeCache(pasture.pos)
        previousTethered.remove(pasture.pos)
    }

    /** Clear all tracked pasture state (server shutdown). */
    fun clearAll() {
        previousTethered.clear()
    }
}
