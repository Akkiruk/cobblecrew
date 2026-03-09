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
import akkiruk.cobblecrew.listeners.BlockChangeNotifier
import akkiruk.cobblecrew.utilities.ContainerAnimations
import akkiruk.cobblecrew.utilities.CobbleCrewDebugLogger
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

    // Margin inside the tether boundary at which we preemptively teleport back.
    // Cobblemon recalls if the entity leaves the box entirely — we snap it back
    // before that can happen. 5 blocks of margin is enough to account for
    // movement between ticks + pathfinding overshoot.
    private const val TETHER_SAFETY_MARGIN = 5.0

    // Track previously-tethered UUIDs per pasture position for recall detection
    private val previousTethered = ConcurrentHashMap<BlockPos, MutableSet<UUID>>()

    /**
     * Called every pasture ticker tick (via mixin). Runs the deferred area scan
     * and dispatches jobs to tethered Pokémon.
     */
    fun tickPasture(world: World, pos: BlockPos, pasture: PokemonPastureBlockEntity) {
        if (world.isClient) return

        val context = JobContext.Pasture(pos, world)

        // Register this pasture for live block-change notifications
        BlockChangeNotifier.registerPasture(pos)

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

        ContainerAnimations.tickAnimations(world)

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

            // Preemptive tether safety — teleport back before Cobblemon's harsh recall.
            // Cobblemon's checkPastureTether() tries randomTeleport and if that fails,
            // it fully recalls the Pokémon from the pasture. We beat it to the punch.
            if (isNearTetherBoundary(pokemonEntity, pos)) {
                snapToPasture(pokemonEntity, pos)
                continue // skip this tick — let it re-orient next cycle
            }

            try {
                WorkerDispatcher.tickPokemon(context, pokemonEntity)
            } catch (e: Exception) {
                CobbleCrew.LOGGER.error("[CobbleCrew] Error in tickPokemon: ${e.message}")
            }
        }
    }

    /**
     * Check if a tethered Pokémon is dangerously close to the roam boundary.
     * Uses the Tethering's minRoamPos/maxRoamPos to detect when the entity
     * is within [TETHER_SAFETY_MARGIN] blocks of the edge.
     */
    private fun isNearTetherBoundary(entity: PokemonEntity, pasturePos: BlockPos): Boolean {
        val tethering = entity.tethering ?: return false
        val min = tethering.minRoamPos
        val max = tethering.maxRoamPos
        val margin = TETHER_SAFETY_MARGIN

        return entity.x <= min.x + margin || entity.x >= max.x + 1 - margin ||
               entity.y <= min.y + margin || entity.y >= max.y + 1 - margin ||
               entity.z <= min.z + margin || entity.z >= max.z + 1 - margin
    }

    /**
     * Teleport the Pokémon back to the pasture origin and reset its job.
     * Uses requestTeleport which unconditionally sets position (unlike
     * Cobblemon's randomTeleport which can fail and trigger a full recall).
     */
    private fun snapToPasture(entity: PokemonEntity, pasturePos: BlockPos) {
        entity.navigation.stop()
        entity.requestTeleport(
            pasturePos.x + 0.5,
            pasturePos.y.toDouble() + 1.0,
            pasturePos.z + 0.5,
        )
        val pokemonId = entity.pokemon.uuid
        WorkerDispatcher.resetAssignment(pokemonId)
        CobbleCrewDebugLogger.log(
            CobbleCrewDebugLogger.Category.NAVIGATION,
            entity.pokemon.species.name, pokemonId,
            "Snapped back to pasture — was near tether boundary"
        )
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
        BlockChangeNotifier.unregisterPasture(pasture.pos)
        previousTethered.remove(pasture.pos)
    }

    /** Clear all tracked pasture state (server shutdown). */
    fun clearAll() {
        previousTethered.clear()
        BlockChangeNotifier.clearAll()
    }
}
