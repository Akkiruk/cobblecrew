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
import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import akkiruk.cobblecrew.utilities.DeferredBlockScanner
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

/**
 * Tracks party Pokémon that are sent out and eligible for jobs.
 * Ticked from platform-specific server tick hooks.
 */
object PartyWorkerManager {

    private val config get() = CobbleCrewConfigHolder.config.party

    data class PartyWorkerEntry(
        val pokemonId: UUID,
        val pokemonEntity: PokemonEntity,
        val owner: ServerPlayerEntity,
    )

    // pokemonId → entry
    private val activePartyWorkers = mutableMapOf<UUID, PartyWorkerEntry>()

    // playerUUID → context (persists across ticks so pinnedOrigin stays stable)
    private val playerContexts = mutableMapOf<UUID, JobContext.Party>()

    // Players currently in battle — skip their workers during tick
    private val playersInBattle = mutableSetOf<UUID>()

    fun init() {
        CobblemonEvents.POKEMON_SENT_POST.subscribe { event ->
            try {
                onPokemonSentOut(event)
            } catch (e: Exception) {
                CobbleCrew.LOGGER.error("[CobbleCrew] Error in POKEMON_SENT_POST handler", e)
            }
        }

        CobblemonEvents.POKEMON_RECALL_POST.subscribe { event ->
            try {
                val pokemonId = event.pokemon.uuid
                onPokemonRecalled(pokemonId)
            } catch (e: Exception) {
                CobbleCrew.LOGGER.error("[CobbleCrew] Error in POKEMON_RECALL_POST handler", e)
            }
        }

        CobblemonEvents.BATTLE_STARTED_PRE.subscribe { event ->
            try {
                onBattleStartPre(event)
            } catch (_: Exception) {}
        }

        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            onBattleEnd(event.battle)
        }

        CobblemonEvents.BATTLE_FLED.subscribe { event ->
            onBattleEnd(event.battle)
        }

        CobbleCrew.LOGGER.info("[CobbleCrew] Party worker events registered")
    }

    private fun onPokemonSentOut(event: PokemonSentEvent.Post) {
        if (!config.enabled) return

        val pokemonEntity = event.pokemonEntity
        // Filter out battle sends — battleId is set for battle-spawned entities
        if (pokemonEntity.battleId != null) return

        val pokemon = pokemonEntity.pokemon
        val owner = pokemon.getOwnerPlayer() as? ServerPlayerEntity ?: return

        // Only register party Pokémon (not tethered to a pasture)
        if (pokemonEntity.tethering != null) return

        val entry = PartyWorkerEntry(
            pokemonId = pokemon.uuid,
            pokemonEntity = pokemonEntity,
            owner = owner,
        )

        activePartyWorkers[pokemon.uuid] = entry
        CobbleCrew.LOGGER.debug("[CobbleCrew] Party worker registered: {} ({})", 
            pokemon.species.name, pokemon.uuid)
    }

    private fun onPokemonRecalled(pokemonId: UUID) {
        val entry = activePartyWorkers[pokemonId] ?: return
        deliverHeldItemsOnRecall(entry)
        cleanupWorker(pokemonId, entry.owner.uuid)
        CobbleCrew.LOGGER.debug("[CobbleCrew] Party worker recalled: {}", pokemonId)
    }

    private fun onBattleStartPre(event: com.cobblemon.mod.common.api.events.battles.BattleStartedEvent.Pre) {
        val playerUUIDs = event.battle.actors
            .filterIsInstance<PlayerBattleActor>()
            .map { it.uuid }
            .toSet()

        for (playerUUID in playerUUIDs) {
            val pokemonIds = activePartyWorkers.values
                .filter { it.owner.uuid == playerUUID }
                .map { it.pokemonId }

            for (pokemonId in pokemonIds) {
                val entry = activePartyWorkers[pokemonId] ?: continue
                deliverHeldItemsOnRecall(entry)

                if (!entry.pokemonEntity.isRemoved) {
                    entry.pokemonEntity.discard()
                }

                cleanupWorker(pokemonId, playerUUID)
            }

            playersInBattle.add(playerUUID)
        }
    }

    private fun onBattleEnd(battle: com.cobblemon.mod.common.api.battles.model.PokemonBattle) {
        val playerUUIDs = battle.actors
            .filterIsInstance<PlayerBattleActor>()
            .map { it.uuid }

        playerUUIDs.forEach { playersInBattle.remove(it) }
    }

    fun tick(server: MinecraftServer) {
        if (!config.enabled) return
        if (activePartyWorkers.isEmpty()) return

        val playerGroups = activePartyWorkers.values.groupBy { it.owner.uuid }

        for ((playerId, entries) in playerGroups) {
            if (playerId in playersInBattle) continue

            val first = entries.firstOrNull() ?: continue
            if (first.owner.isRemoved) {
                entries.forEach { cleanupWorker(it.pokemonId, playerId) }
                continue
            }

            val context = playerContexts.getOrPut(playerId) {
                JobContext.Party(first.owner, first.owner.serverWorld)
            }

            // Drive shared area scan — once per player
            WorkerDispatcher.tickAreaScan(context)

            // Drive chest animations for party workers
            CobbleCrewInventoryUtils.tickAnimations(context.world)

            for (entry in entries.toList()) {
                // Safety checks
                if (entry.pokemonEntity.isRemoved) {
                    cleanupWorker(entry.pokemonId, playerId)
                    continue
                }
                if (entry.pokemonEntity.pokemon.isFainted()) {
                    cleanupWorker(entry.pokemonId, playerId)
                    continue
                }
                if (entry.pokemonEntity.battleId != null) {
                    cleanupWorker(entry.pokemonId, playerId)
                    continue
                }

                val poseType = entry.pokemonEntity.getCurrentPoseType()
                if (poseType == PoseType.SLEEP) continue

                // Catch-up teleport
                if (config.teleportIfTooFar) {
                    val distSq = entry.pokemonEntity.squaredDistanceTo(context.player)
                    val td = config.teleportDistance.toDouble()
                    if (distSq > td * td) {
                        entry.pokemonEntity.requestTeleport(
                            context.player.x, context.player.y, context.player.z
                        )
                        WorkerDispatcher.resetAssignment(entry.pokemonId)
                        context.pinnedOrigin = null
                    }
                }

                // Zone transition check (only when idle)
                if (shouldTransitionZone(entry.pokemonId, context, context.world)) {
                    transitionZone(context, playerId)
                }

                // maxWorkDistance enforcement: if Pokémon wandered too far from work origin, 
                // recall them back instead of letting them work further away
                val maxDist = config.maxWorkDistance.toDouble()
                val distFromOriginSq = entry.pokemonEntity.squaredDistanceTo(
                    context.origin.x + 0.5, context.origin.y + 0.5, context.origin.z + 0.5
                )
                if (distFromOriginSq > maxDist * maxDist) {
                    WorkerDispatcher.resetAssignment(entry.pokemonId)
                    CobbleCrewNavigationUtils.navigateTo(entry.pokemonEntity, context.origin)
                } else {
                    WorkerDispatcher.tickPokemon(context, entry.pokemonEntity)
                }
            }

            // Cleanup player context if no more workers
            if (activePartyWorkers.values.none { it.owner.uuid == playerId }) {
                val removed = playerContexts.remove(playerId)
                if (removed != null) {
                    CobbleCrewCacheManager.removeCache(removed.cacheKey)
                    DeferredBlockScanner.clearScan(removed.cacheKey)
                }
            }
        }
    }

    private fun shouldTransitionZone(
        pokemonId: UUID,
        context: JobContext.Party,
        world: net.minecraft.world.World,
    ): Boolean {
        if (WorkerDispatcher.hasActiveJob(pokemonId)) return false
        if (CobbleCrewNavigationUtils.getTarget(pokemonId, world) != null) return false

        // No pinned origin = scan hasn't completed yet — don't interrupt it
        val pinned = context.pinnedOrigin ?: return false
        val dist = config.zoneTransitionDistance.toDouble()
        return pinned.getSquaredDistance(context.player.blockPos) > dist * dist
    }

    private fun transitionZone(context: JobContext.Party, playerId: UUID) {
        val oldKey = context.cacheKey
        context.pinnedOrigin = null

        // Clear old cache and scanner state
        CobbleCrewCacheManager.removeCache(oldKey)
        DeferredBlockScanner.clearScan(oldKey)
    }

    private fun deliverHeldItemsOnRecall(entry: PartyWorkerEntry) {
        for (worker in WorkerRegistry.workers) {
            val heldItems = worker.getHeldItems(entry.pokemonId) ?: continue
            if (heldItems.isNotEmpty()) {
                CobbleCrewInventoryUtils.deliverToPlayer(entry.owner, heldItems, entry.pokemonEntity)
            }
        }
    }

    private fun cleanupWorker(pokemonId: UUID, playerId: UUID) {
        activePartyWorkers.remove(pokemonId)
        WorkerDispatcher.cleanupPokemon(pokemonId, 
            playerContexts[playerId]?.world 
            ?: return)
        CobbleCrewInventoryUtils.cleanupPokemon(pokemonId)
    }

    /** Full cleanup for a player (disconnect/death). */
    fun cleanupPlayer(playerId: UUID) {
        val pokemonIds = activePartyWorkers.values
            .filter { it.owner.uuid == playerId }
            .map { it.pokemonId }

        for (pokemonId in pokemonIds) {
            val entry = activePartyWorkers[pokemonId] ?: continue
            deliverHeldItemsOnRecall(entry)
            if (!entry.pokemonEntity.isRemoved) {
                entry.pokemonEntity.discard()
            }
            cleanupWorker(pokemonId, playerId)
        }

        val context = playerContexts.remove(playerId)
        if (context != null) {
            CobbleCrewCacheManager.removeCache(context.cacheKey)
            DeferredBlockScanner.clearScan(context.cacheKey)
        }
        playersInBattle.remove(playerId)
    }

    // -- Command accessors --

    fun getActivePartyWorkers(): Map<UUID, PartyWorkerEntry> = activePartyWorkers.toMap()

    fun getActivePartyWorkerCount(): Int = activePartyWorkers.size
}
