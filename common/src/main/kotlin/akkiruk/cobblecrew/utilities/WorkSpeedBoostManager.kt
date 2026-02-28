/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import net.minecraft.util.math.BlockPos
import java.util.UUID

/**
 * Tracks work speed aura boosts from support Pokémon. When a support worker
 * is active near other workers (same pasture or party), it can reduce their
 * production cooldowns.
 *
 * Boosts are per-origin (pasture block pos or party scan origin), auto-expire
 * after a short TTL, and stack diminishingly up to a 40% max reduction.
 */
object WorkSpeedBoostManager {
    private data class Boost(val pokemonId: UUID, val multiplier: Double, val expiryTick: Long)

    private val boosts = mutableMapOf<BlockPos, MutableList<Boost>>()

    private const val BOOST_TTL_TICKS = 30L
    private const val MIN_MULTIPLIER = 0.60 // 40% max reduction

    /**
     * Register a work speed boost from a support Pokémon.
     * @param origin The pasture or party origin this boost applies to
     * @param pokemonId The support Pokémon providing the boost
     * @param boostPercent Speed increase percentage (e.g. 15 = 15% faster cooldowns)
     * @param worldTime Current world time in ticks
     */
    fun registerBoost(origin: BlockPos, pokemonId: UUID, boostPercent: Int, worldTime: Long) {
        if (boostPercent <= 0) return
        val multiplier = 1.0 - (boostPercent.coerceIn(1, 50) / 100.0)
        val expiry = worldTime + BOOST_TTL_TICKS
        val key = origin.toImmutable()
        val list = boosts.getOrPut(key) { mutableListOf() }
        list.removeAll { it.pokemonId == pokemonId }
        list.add(Boost(pokemonId, multiplier, expiry))
    }

    /**
     * Get the combined speed multiplier for an origin. Returns 1.0 (no boost)
     * if no active support Pokémon are nearby. Lower = faster.
     */
    fun getSpeedMultiplier(origin: BlockPos, worldTime: Long): Double {
        val list = boosts[origin] ?: return 1.0
        pruneExpired(list, origin, worldTime)
        if (list.isEmpty()) return 1.0
        return list.fold(1.0) { acc, b -> acc * b.multiplier }.coerceAtLeast(MIN_MULTIPLIER)
    }

    /** Remove expired boosts from a list, cleaning up the map entry if empty. */
    private fun pruneExpired(list: MutableList<Boost>, origin: BlockPos, worldTime: Long) {
        list.removeAll { it.expiryTick < worldTime }
        if (list.isEmpty()) boosts.remove(origin)
    }

    /**
     * Adjust a cooldown duration based on active support boosts at this origin.
     * @return Adjusted cooldown ticks (always at least 20 ticks / 1 second)
     */
    fun adjustCooldown(baseTicks: Long, origin: BlockPos, worldTime: Long): Long {
        val multiplier = getSpeedMultiplier(origin, worldTime)
        return (baseTicks * multiplier).toLong().coerceAtLeast(20L)
    }

    /** Get active boost count for an origin (for debug display). */
    fun getActiveBoostCount(origin: BlockPos, worldTime: Long): Int {
        val list = boosts[origin] ?: return 0
        pruneExpired(list, origin, worldTime)
        return list.size
    }

    /** Get the current multiplier as a readable percentage string (for debug). */
    fun getBoostDescription(origin: BlockPos, worldTime: Long): String {
        val mult = getSpeedMultiplier(origin, worldTime)
        if (mult >= 1.0) return "none"
        val pct = ((1.0 - mult) * 100).toInt()
        val count = getActiveBoostCount(origin, worldTime)
        return "${pct}% faster ($count aura${if (count != 1) "s" else ""})"
    }

    fun cleanup() {
        boosts.clear()
    }
}
