/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.state

import akkiruk.cobblecrew.jobs.Target
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages target claims and navigation for all working Pokémon.
 * Claims live in [PokemonWorkerState]; this object maintains the
 * reverse index for O(1) "is this targeted?" checks plus the
 * per-Pokémon unreachable cache.
 */
object ClaimManager {

    // --- Reverse index: target → pokemonId ---
    private val targetToPokemon = ConcurrentHashMap<TargetKey, UUID>()

    // --- Per-Pokémon unreachable cache ---
    private val unreachable = ConcurrentHashMap<Pair<UUID, BlockPos>, Long>()

    private const val PATHFIND_THROTTLE_TICKS = 5L
    private const val UNREACHABLE_TTL_TICKS = 200L // 10s
    private const val DEFAULT_CLAIM_TIMEOUT = 200L // 10s
    private const val WORKER_WALK_SPEED = 0.23 // target blocks/tick — gentle waddle

    // -- Lightweight target key for reverse index --
    private sealed interface TargetKey {
        data class Block(val pos: BlockPos) : TargetKey
        data class Player(val playerId: UUID) : TargetKey
        data class Mob(val entityId: UUID) : TargetKey
    }

    private fun Target.toKey(): TargetKey = when (this) {
        is Target.Block -> TargetKey.Block(pos)
        is Target.Player -> TargetKey.Player(playerId)
        is Target.Mob -> TargetKey.Mob(entityId)
    }

    // ---- Claims ----

    /** Claim a target. Auto-releases any existing claim first. */
    fun claim(state: PokemonWorkerState, target: Target, world: World) {
        release(state)
        state.claim = NavigationClaim(target, world.time, DEFAULT_CLAIM_TIMEOUT)
        targetToPokemon[target.toKey()] = state.pokemonId
    }

    /** Release the current claim. */
    fun release(state: PokemonWorkerState) {
        val claim = state.claim ?: return
        targetToPokemon.remove(claim.target.toKey())
        state.claim = null
    }

    /** Refresh the claim timer (called when actively working at a target). */
    fun renewClaim(state: PokemonWorkerState, world: World) {
        val old = state.claim ?: return
        state.claim = old.copy(claimTick = world.time)
    }

    fun isTargetedByOther(pos: BlockPos, pokemonId: UUID): Boolean {
        val owner = targetToPokemon[TargetKey.Block(pos)] ?: return false
        return owner != pokemonId
    }

    fun isTargetedByOther(target: Target, pokemonId: UUID): Boolean {
        val owner = targetToPokemon[target.toKey()] ?: return false
        return owner != pokemonId
    }

    fun isTargeted(pos: BlockPos): Boolean = targetToPokemon.containsKey(TargetKey.Block(pos))

    fun isPlayerTargeted(playerId: UUID): Boolean =
        targetToPokemon.containsKey(TargetKey.Player(playerId))

    // ---- Unreachable cache ----

    fun isUnreachable(pokemonId: UUID, pos: BlockPos, currentTick: Long): Boolean {
        val expiry = unreachable[pokemonId to pos] ?: return false
        if (currentTick >= expiry) {
            unreachable.remove(pokemonId to pos)
            return false
        }
        return true
    }

    fun markUnreachable(pokemonId: UUID, pos: BlockPos, currentTick: Long) {
        unreachable[pokemonId to pos] = currentTick + UNREACHABLE_TTL_TICKS
    }

    // ---- Navigation ----

    /**
     * Throttled pathfinding to a block position.
     * Returns true if a new path was started.
     */
    fun navigateTo(entity: PokemonEntity, pos: BlockPos, state: PokemonWorkerState): Boolean {
        val now = entity.world.time
        if (now - state.lastPathfindTick < PATHFIND_THROTTLE_TICKS) return false
        state.lastPathfindTick = now
        entity.navigation.startMovingTo(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, workerSpeed(entity))
        return true
    }

    /** Throttled pathfinding to a player's position. */
    fun navigateToPlayer(entity: PokemonEntity, player: PlayerEntity, state: PokemonWorkerState): Boolean {
        val now = entity.world.time
        if (now - state.lastPathfindTick < PATHFIND_THROTTLE_TICKS) return false
        state.lastPathfindTick = now
        entity.navigation.startMovingTo(player.x, player.y, player.z, workerSpeed(entity))
        return true
    }

    fun isPokemonAtPosition(entity: PokemonEntity, pos: BlockPos, tolerance: Double): Boolean {
        val dx = entity.x - (pos.x + 0.5)
        val dz = entity.z - (pos.z + 0.5)
        return dx * dx + dz * dz <= tolerance * tolerance
    }

    fun isPokemonNearPlayer(entity: PokemonEntity, player: PlayerEntity, tolerance: Double = 3.0): Boolean {
        val dx = entity.x - player.x
        val dz = entity.z - player.z
        return dx * dx + dz * dz <= tolerance * tolerance
    }

    /** Compute a speed multiplier that normalizes all workers to a gentle waddle. */
    private fun workerSpeed(entity: PokemonEntity): Double {
        val base = entity.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED)
        if (base <= 0.0) return 1.0
        return (WORKER_WALK_SPEED / base).coerceAtMost(1.0)
    }

    // ---- Cleanup ----

    /** Full cleanup for a Pokémon leaving the system. */
    fun cleanupPokemon(pokemonId: UUID) {
        val state = StateManager.get(pokemonId)
        if (state != null) release(state)
        unreachable.keys.removeIf { it.first == pokemonId }
    }

    /** Periodic sweep — call from server tick every ~200 ticks. */
    fun sweepExpired(currentTick: Long) {
        unreachable.entries.removeIf { currentTick >= it.value }

        // Expire stale claims (orphaned by bugs)
        val staleKeys = mutableListOf<TargetKey>()
        for ((key, pokemonId) in targetToPokemon) {
            val state = StateManager.get(pokemonId)
            if (state == null) { staleKeys.add(key); continue }
            val claim = state.claim
            if (claim == null) { staleKeys.add(key); continue }
            if (currentTick - claim.claimTick > claim.timeoutTicks * 2) {
                staleKeys.add(key)
            }
        }
        staleKeys.forEach { targetToPokemon.remove(it) }
    }

    fun clearAll() {
        targetToPokemon.clear()
        unreachable.clear()
    }
}
