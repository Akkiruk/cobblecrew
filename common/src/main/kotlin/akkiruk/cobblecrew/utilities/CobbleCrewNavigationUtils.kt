/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import java.util.PriorityQueue
import java.util.UUID

/**
 * Pokémon navigation and target claim management.
 *
 * Internally uses a unified claim system: each Pokémon holds at most ONE claim
 * (block, player, or mob). Claiming a new target auto-releases the previous one.
 * Backward-compatible API wrappers are provided for each target type.
 */
object CobbleCrewNavigationUtils {
    private val generalConfig get() = CobbleCrewConfigHolder.config.general

    // --- Unified claim system ---

    sealed interface Target {
        data class Block(val pos: BlockPos) : Target
        data class Player(val playerId: UUID) : Target
        data class Mob(val entityId: Int) : Target
    }

    private data class Claim(
        val pokemonId: UUID,
        val target: Target,
        val claimTick: Long,
        val timeoutTicks: Long,
    )

    // Forward: pokemonId → claim. One claim per Pokémon, enforced.
    private val pokemonToClaim = mutableMapOf<UUID, Claim>()
    // Reverse: target → pokemonId. For O(1) "is this targeted?" checks.
    private val targetToPokemon = mutableMapOf<Target, UUID>()

    private data class ExpiredTarget(val pos: BlockPos, val expiryTick: Long)
    private val recentlyExpiredTargets = mutableMapOf<BlockPos, ExpiredTarget>()
    private val expiredQueue = PriorityQueue<ExpiredTarget>(compareBy { it.expiryTick })
    private val lastPathfindTick = mutableMapOf<UUID, Long>()
    private var lastCleanUpTick = 0L

    private const val DEFAULT_CLAIM_TIMEOUT = 100L
    private val EXPIRED_TARGET_TIMEOUT_TICKS: Long get() = generalConfig.targetGracePeriodSeconds.toLong() * 20L
    private const val PATHFIND_INTERVAL_TICKS = 5L

    // Escalating blacklist: per-position fail count (O(1) lookup)
    private val blockFailCounts = mutableMapOf<BlockPos, Int>()
    private val BLACKLIST_SHORT: Long get() = generalConfig.blacklistShortSeconds.toLong() * 20L
    private val BLACKLIST_MEDIUM: Long get() = generalConfig.blacklistMediumSeconds.toLong() * 20L
    private val BLACKLIST_LONG: Long get() = generalConfig.blacklistLongSeconds.toLong() * 20L

    // Pathfind validation cache: remembers unreachable targets temporarily
    private val unreachableCache = mutableMapOf<Pair<UUID, BlockPos>, Long>()
    private const val UNREACHABLE_CACHE_TTL = 200L

    /**
     * Checks if the Pokémon's bounding box intersects with the target block area.
     */
    fun isPokemonAtPosition(pokemonEntity: PokemonEntity, targetPos: BlockPos, offset: Double = 1.0): Boolean {
        val interactionHitbox = Box(targetPos).expand(offset)
        return pokemonEntity.boundingBox.intersects(interactionHitbox)
    }

    /**
     * Checks if the Pokémon is near enough to the player.
     */
    fun isPokemonNearPlayer(pokemonEntity: PokemonEntity, player: PlayerEntity, offset: Double = 1.0): Boolean {
        val interactionHitbox = player.boundingBox.expand(offset)
        return pokemonEntity.boundingBox.intersects(interactionHitbox)
    }

    /**
     * Commands the Pokémon entity to move towards the target destination.
     */
    fun navigateTo(pokemonEntity: PokemonEntity, targetPos: BlockPos, speed: Double = 0.5) {
        val world = pokemonEntity.world
        val now = world.time
        val id = pokemonEntity.pokemon.uuid
        val last = lastPathfindTick[id] ?: 0L

        if (now - last < PATHFIND_INTERVAL_TICKS) return
        lastPathfindTick[id] = now

        pokemonEntity.lookControl.lookAt(
            targetPos.x + 0.5,
            targetPos.y + 0.5,
            targetPos.z + 0.5
        )

        pokemonEntity.navigation.startMovingTo(
            targetPos.x + 0.5,
            targetPos.y.toDouble(),
            targetPos.z + 0.5,
            speed
        )
    }

    /**
     * Commands the Pokémon entity to move towards the player's current position.
     * Throttled to avoid pathfinding every tick.
     */
    fun navigateToPlayer(pokemonEntity: PokemonEntity, player: PlayerEntity, speed: Double = 0.5) {
        val id = pokemonEntity.pokemon.uuid
        val now = pokemonEntity.world.time
        val last = lastPathfindTick[id] ?: 0L
        if (now - last < PATHFIND_INTERVAL_TICKS) return
        lastPathfindTick[id] = now

        pokemonEntity.lookControl.lookAt(player.x, player.eyeY, player.z)
        pokemonEntity.navigation.startMovingTo(player.x, player.y, player.z, speed)
    }

    /**
     * Assigns a target block to a Pokémon. Releases any existing claim first.
     * @param timeoutTicks per-job timeout (default 5s). Slow jobs can pass longer values.
     */
    fun claimTarget(pokemonId: UUID, target: BlockPos, world: World, timeoutTicks: Long = DEFAULT_CLAIM_TIMEOUT) {
        releaseExpiredClaims(world)
        releaseInternal(pokemonId, world)

        val immutableTarget = target.toImmutable()
        val t = Target.Block(immutableTarget)
        pokemonToClaim[pokemonId] = Claim(pokemonId, t, world.time, timeoutTicks)
        targetToPokemon[t] = pokemonId
        CobbleCrewDebugLogger.targetClaimed(null, pokemonId, immutableTarget)
    }

    /**
     * Assigns a player target to a Pokémon. Releases any existing claim first.
     */
    fun claimTarget(pokemonId: UUID, player: PlayerEntity, world: World) {
        releaseExpiredClaims(world)
        releaseInternal(pokemonId, world)

        val t = Target.Player(player.uuid)
        pokemonToClaim[pokemonId] = Claim(pokemonId, t, world.time, DEFAULT_CLAIM_TIMEOUT)
        targetToPokemon[t] = pokemonId
        CobbleCrewDebugLogger.playerTargetClaimed(null, pokemonId, player.uuid)
    }

    /**
     * Refreshes the claim timer so it won't expire while the Pokémon is actively working.
     */
    fun renewClaim(pokemonId: UUID, world: World) {
        val claim = pokemonToClaim[pokemonId] ?: return
        pokemonToClaim[pokemonId] = claim.copy(claimTick = world.time)
    }

    /**
     * Releases any claim held by this Pokémon.
     * Block claims are tracked in the recently-expired set for blacklisting.
     */
    fun releaseTarget(pokemonId: UUID, world: World) = releaseInternal(pokemonId, world)

    /**
     * Releases the player target for a given Pokémon.
     */
    fun releasePlayerTarget(pokemonId: UUID) = releaseInternal(pokemonId, null)

    private fun releaseInternal(pokemonId: UUID, world: World?) {
        val claim = pokemonToClaim.remove(pokemonId) ?: return
        targetToPokemon.remove(claim.target)

        // Track recently expired block targets for blacklist
        if (claim.target is Target.Block && world != null) {
            val pos = claim.target.pos
            val expired = ExpiredTarget(pos, world.time)
            recentlyExpiredTargets[pos] = expired
            expiredQueue.add(expired)
            CobbleCrewDebugLogger.targetReleased(null, pokemonId, pos)
        } else if (claim.target is Target.Player) {
            CobbleCrewDebugLogger.playerTargetReleased(pokemonId)
        } else if (claim.target is Target.Mob) {
            CobbleCrewDebugLogger.mobTargetReleased(pokemonId)
        }
    }

    /**
     * Gets the current block target for a Pokémon, or null.
     */
    fun getTarget(pokemonId: UUID, world: World): BlockPos? {
        releaseExpiredClaims(world)
        return (pokemonToClaim[pokemonId]?.target as? Target.Block)?.pos
    }

    /**
     * Gets the current player target UUID for a Pokémon, or null.
     */
    fun getPlayerTarget(pokemonId: UUID, world: World): UUID? {
        releaseExpiredClaims(world)
        return (pokemonToClaim[pokemonId]?.target as? Target.Player)?.playerId
    }

    /**
     * Checks if a specific block is claimed by any Pokémon.
     */
    fun isTargeted(pos: BlockPos, world: World): Boolean {
        releaseExpiredClaims(world)
        return targetToPokemon.containsKey(Target.Block(pos))
    }

    /**
     * Checks if a player is targeted by any Pokémon.
     */
    fun isPlayerTargeted(player: PlayerEntity, world: World): Boolean {
        releaseExpiredClaims(world)
        return targetToPokemon.containsKey(Target.Player(player.uuid))
    }

    /**
     * Releases claims that have exceeded their per-job timeout.
     */
    private fun releaseExpiredClaims(world: World) {
        val now = world.time

        if (now - lastCleanUpTick < 20) return
        lastCleanUpTick = now

        val expired = mutableListOf<UUID>()

        for ((pokemonId, claim) in pokemonToClaim) {
            if (claim.timeoutTicks == Long.MAX_VALUE) continue // unlimited (mob claims)
            if (now - claim.claimTick > claim.timeoutTicks) {
                expired.add(pokemonId)
                // Track block claim failures for escalating blacklist
                if (claim.target is Target.Block) {
                    val pos = claim.target.pos
                    blockFailCounts.merge(pos, 1) { a, b -> a + b }
                    CobbleCrewDebugLogger.targetExpired(pokemonId, pos)
                }
            }
        }

        expired.forEach { releaseInternal(it, world) }

        // Clean expired target queue
        while (expiredQueue.isNotEmpty() && now - expiredQueue.peek().expiryTick > EXPIRED_TARGET_TIMEOUT_TICKS) {
            val removed = expiredQueue.poll().pos
            recentlyExpiredTargets.remove(removed)
            blockFailCounts.remove(removed)
        }

        // Periodic sweep of unreachableCache (prevents unbounded growth)
        unreachableCache.values.removeAll { expiry -> now >= expiry }
    }

    /**
     * Checks if a block is in the recently expired targets.
     * Uses escalating blacklist: repeated failures extend the blackout.
     * O(1) lookup via per-position fail counts.
     */
    fun isRecentlyExpired(pos: BlockPos, world: World): Boolean {
        releaseExpiredClaims(world)
        val expired = recentlyExpiredTargets[pos] ?: return false
        val now = world.time
        val fails = blockFailCounts.getOrDefault(pos, 0)
        val blacklistDuration = when {
            fails <= 1 -> BLACKLIST_SHORT
            fails == 2 -> BLACKLIST_MEDIUM
            else -> BLACKLIST_LONG
        }
        return now - expired.expiryTick < blacklistDuration
    }

    /**
     * Checks if a target was recently found unreachable by pathfinding validation.
     */
    fun isUnreachable(pokemonId: UUID, pos: BlockPos, currentTick: Long): Boolean {
        val expiry = unreachableCache[pokemonId to pos] ?: return false
        if (currentTick >= expiry) {
            unreachableCache.remove(pokemonId to pos)
            return false
        }
        return true
    }

    /**
     * Marks a block as unreachable for a specific Pokémon for [UNREACHABLE_CACHE_TTL] ticks.
     */
    fun markUnreachable(pokemonId: UUID, pos: BlockPos, currentTick: Long) {
        unreachableCache[pokemonId to pos] = currentTick + UNREACHABLE_CACHE_TTL
    }

    /**
     * Validates pathfinding to a target. Returns true if a path exists and can reach the target.
     */
    fun canPathfindTo(pokemonEntity: PokemonEntity, targetPos: BlockPos): Boolean {
        val path = pokemonEntity.navigation.findPathTo(targetPos, 1)
        return path != null && path.reachesTarget()
    }

    /**
     * Removes all state associated with a Pokémon.
     */
    fun cleanupPokemon(pokemonId: UUID, world: World) {
        releaseInternal(pokemonId, world)
        lastPathfindTick.remove(pokemonId)
        unreachableCache.keys.removeAll { it.first == pokemonId }
    }

    // --- Mob targeting (delegates to unified claim system) ---

    fun claimMobTarget(pokemonId: UUID, entityId: Int) {
        releaseInternal(pokemonId, null)
        val target = Target.Mob(entityId)
        pokemonToClaim[pokemonId] = Claim(pokemonId, target, 0L, Long.MAX_VALUE)
        targetToPokemon[target] = pokemonId
        CobbleCrewDebugLogger.mobTargetClaimed(null, pokemonId, entityId)
    }

    fun releaseMobTarget(pokemonId: UUID) {
        val claim = pokemonToClaim[pokemonId] ?: return
        if (claim.target is Target.Mob) {
            releaseInternal(pokemonId, null)
            CobbleCrewDebugLogger.mobTargetReleased(pokemonId)
        }
    }

    fun getMobTarget(pokemonId: UUID): Int? =
        (pokemonToClaim[pokemonId]?.target as? Target.Mob)?.entityId

    fun isMobTargeted(entityId: Int): Boolean =
        targetToPokemon.containsKey(Target.Mob(entityId))
}