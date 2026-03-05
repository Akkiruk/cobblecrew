/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import akkiruk.cobblecrew.jobs.Target
import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.StateManager
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID

/**
 * Backward-compatible facade over [ClaimManager] and [StateManager].
 * All state is delegated — this object maintains no internal maps.
 *
 * New code should use [ClaimManager] directly. This facade exists for
 * hand-coded jobs and other legacy callers that haven't been migrated yet.
 */
object CobbleCrewNavigationUtils {
    private const val PATHFIND_INTERVAL_TICKS = 5L

    // --- Position checks (pure geometry, no state) ---

    fun isPokemonAtPosition(pokemonEntity: PokemonEntity, targetPos: BlockPos, offset: Double = 1.0): Boolean {
        val interactionHitbox = Box(targetPos).expand(offset)
        return pokemonEntity.boundingBox.intersects(interactionHitbox)
    }

    fun isPokemonNearPlayer(pokemonEntity: PokemonEntity, player: PlayerEntity, offset: Double = 1.0): Boolean {
        val interactionHitbox = player.boundingBox.expand(offset)
        return pokemonEntity.boundingBox.intersects(interactionHitbox)
    }

    // --- Navigation (throttled pathfinding) ---

    fun navigateTo(pokemonEntity: PokemonEntity, targetPos: BlockPos, speed: Double = 0.5) {
        val state = StateManager.getOrCreate(pokemonEntity.pokemon.uuid)
        val now = pokemonEntity.world.time
        if (now - state.lastPathfindTick < PATHFIND_INTERVAL_TICKS) return
        state.lastPathfindTick = now

        pokemonEntity.lookControl.lookAt(
            targetPos.x + 0.5, targetPos.y + 0.5, targetPos.z + 0.5
        )
        pokemonEntity.navigation.startMovingTo(
            targetPos.x + 0.5, targetPos.y.toDouble(), targetPos.z + 0.5, speed
        )
    }

    fun navigateToPlayer(pokemonEntity: PokemonEntity, player: PlayerEntity, speed: Double = 0.5) {
        val state = StateManager.getOrCreate(pokemonEntity.pokemon.uuid)
        val now = pokemonEntity.world.time
        if (now - state.lastPathfindTick < PATHFIND_INTERVAL_TICKS) return
        state.lastPathfindTick = now

        pokemonEntity.lookControl.lookAt(player.x, player.eyeY, player.z)
        pokemonEntity.navigation.startMovingTo(player.x, player.y, player.z, speed)
    }

    fun canPathfindTo(pokemonEntity: PokemonEntity, targetPos: BlockPos): Boolean {
        val path = pokemonEntity.navigation.findPathTo(targetPos, 1)
        return path != null && path.reachesTarget()
    }

    // --- Block claims ---

    fun claimTarget(pokemonId: UUID, target: BlockPos, world: World, timeoutTicks: Long = 200L) {
        val state = StateManager.getOrCreate(pokemonId)
        ClaimManager.claim(state, Target.Block(target.toImmutable()), world)
    }

    fun releaseTarget(pokemonId: UUID, world: World, blacklist: Boolean = true) {
        val state = StateManager.get(pokemonId) ?: return
        ClaimManager.release(state, world, blacklist)
    }

    fun renewClaim(pokemonId: UUID, world: World) {
        val state = StateManager.get(pokemonId) ?: return
        ClaimManager.renewClaim(state, world)
    }

    fun getTarget(pokemonId: UUID, world: World): BlockPos? {
        val state = StateManager.get(pokemonId) ?: return null
        return (state.claim?.target as? Target.Block)?.pos
    }

    fun isTargeted(pos: BlockPos, world: World): Boolean =
        ClaimManager.isTargeted(pos)

    fun isTargetedByOther(pos: BlockPos, world: World, excludePokemonId: UUID): Boolean =
        ClaimManager.isTargetedByOther(pos, excludePokemonId)

    // --- Player claims ---

    fun claimTarget(pokemonId: UUID, player: PlayerEntity, world: World) {
        val state = StateManager.getOrCreate(pokemonId)
        ClaimManager.claim(state, Target.Player(player.uuid, player.blockPos), world)
    }

    fun releasePlayerTarget(pokemonId: UUID) {
        val state = StateManager.get(pokemonId) ?: return
        ClaimManager.releaseWithoutWorld(state)
    }

    fun getPlayerTarget(pokemonId: UUID, world: World): UUID? {
        val state = StateManager.get(pokemonId) ?: return null
        return (state.claim?.target as? Target.Player)?.playerId
    }

    fun isPlayerTargeted(player: PlayerEntity, world: World): Boolean =
        ClaimManager.isPlayerTargeted(player.uuid)

    // --- Blacklist / unreachable ---

    fun isRecentlyExpired(pos: BlockPos, world: World): Boolean =
        ClaimManager.isBlacklisted(pos, world.time)

    fun isUnreachable(pokemonId: UUID, pos: BlockPos, currentTick: Long): Boolean =
        ClaimManager.isUnreachable(pokemonId, pos, currentTick)

    fun markUnreachable(pokemonId: UUID, pos: BlockPos, currentTick: Long) =
        ClaimManager.markUnreachable(pokemonId, pos, currentTick)

    // --- Cleanup ---

    fun cleanupPokemon(pokemonId: UUID, world: World) {
        ClaimManager.cleanupPokemon(pokemonId, world)
    }
}