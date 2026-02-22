/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.cache

import akkiruk.cobblecrew.enums.BlockCategory
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.gen.structure.Structure
import java.util.concurrent.ConcurrentHashMap

object CobbleCrewCacheManager {
    private val pastureCaches: MutableMap<BlockPos, PastureCache> = ConcurrentHashMap()
    private var structuresCache: Set<Identifier>? = null

    private val structureLocationCache: MutableMap<Identifier, com.mojang.datafixers.util.Pair<BlockPos, RegistryEntry<Structure>>> =
        ConcurrentHashMap()

    private val structureLocationExpiry: MutableMap<Identifier, Long> = ConcurrentHashMap()
    private const val CACHE_TTL = 20L * 60L * 15L

    fun addTarget(pastureOrigin: BlockPos, category: BlockCategory, pos: BlockPos) {
        val cache = pastureCaches.getOrPut(pastureOrigin) { PastureCache() }
        cache.targetsByCategory[category]?.add(pos)
    }

    fun getTargets(pastureOrigin: BlockPos, category: BlockCategory): Set<BlockPos> {
        return pastureCaches[pastureOrigin]?.targetsByCategory?.get(category) ?: emptySet()
    }

    fun removeTarget(pastureOrigin: BlockPos, category: BlockCategory, pos: BlockPos) {
        pastureCaches[pastureOrigin]?.targetsByCategory?.get(category)?.remove(pos)
    }

    /** Remove a position from ALL pasture caches (for overlapping pasture ranges). */
    fun removeTargetGlobal(category: BlockCategory, pos: BlockPos) {
        pastureCaches.values.forEach { cache ->
            cache.targetsByCategory[category]?.remove(pos)
        }
    }

    fun removeAllCategoryTargets(pastureOrigin: BlockPos) {
        pastureCaches[pastureOrigin]?.targetsByCategory?.values?.forEach { it.clear() }
    }

    /** Atomically replace all category targets for a pasture with new scan results. */
    fun replaceAllCategoryTargets(pastureOrigin: BlockPos, newTargets: Map<BlockCategory, Set<BlockPos>>) {
        val cache = pastureCaches.getOrPut(pastureOrigin) { PastureCache() }
        for ((category, set) in cache.targetsByCategory) {
            set.clear()
            newTargets[category]?.let { set.addAll(it) }
        }
    }

    fun removePasture(pastureOrigin: BlockPos) {
        pastureCaches.remove(pastureOrigin)
    }

    fun getStructures(world: ServerWorld, useAll: Boolean, tags: List<String>): Set<Identifier> {
        structuresCache?.let { return it }

        val registryManager = world.server.registryManager
        val structureRegistry = registryManager.get(RegistryKeys.STRUCTURE)

        val structures = if (useAll) {
            structureRegistry.keys.map { it.value }.toSet()
        } else {
            tags.mapNotNull { Identifier.tryParse(it) }.toSet()
        }

        structuresCache = structures
        return structures
    }

    fun getCachedStructure(id: Identifier, now: Long): com.mojang.datafixers.util.Pair<BlockPos, RegistryEntry<Structure>>? {
        val expiry = structureLocationExpiry[id] ?: return null
        if (now > expiry) {
            structureLocationCache.remove(id)
            structureLocationExpiry.remove(id)
            return null
        }
        return structureLocationCache[id]
    }

    fun cacheStructure(id: Identifier, result: com.mojang.datafixers.util.Pair<BlockPos, RegistryEntry<Structure>>, now: Long) {
        structureLocationCache[id] = result
        structureLocationExpiry[id] = now + CACHE_TTL
    }
}