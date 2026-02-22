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
    private val caches: MutableMap<CacheKey, PastureCache> = ConcurrentHashMap()
    private var structuresCache: Set<Identifier>? = null

    private val structureLocationCache: MutableMap<Identifier, com.mojang.datafixers.util.Pair<BlockPos, RegistryEntry<Structure>>> =
        ConcurrentHashMap()

    private val structureLocationExpiry: MutableMap<Identifier, Long> = ConcurrentHashMap()
    private const val CACHE_TTL = 20L * 60L * 15L

    fun addTarget(key: CacheKey, category: BlockCategory, pos: BlockPos) {
        val cache = caches.getOrPut(key) { PastureCache() }
        cache.targetsByCategory[category]?.add(pos)
    }

    /** Convenience: wrap BlockPos to PastureKey. */
    fun addTarget(origin: BlockPos, category: BlockCategory, pos: BlockPos) =
        addTarget(CacheKey.PastureKey(origin), category, pos)

    fun getTargets(key: CacheKey, category: BlockCategory): Set<BlockPos> {
        return caches[key]?.targetsByCategory?.get(category) ?: emptySet()
    }

    /** Convenience: wrap BlockPos to PastureKey. */
    fun getTargets(origin: BlockPos, category: BlockCategory): Set<BlockPos> =
        getTargets(CacheKey.PastureKey(origin), category)

    fun removeTarget(key: CacheKey, category: BlockCategory, pos: BlockPos) {
        caches[key]?.targetsByCategory?.get(category)?.remove(pos)
    }

    /** Convenience: wrap BlockPos to PastureKey. */
    fun removeTarget(origin: BlockPos, category: BlockCategory, pos: BlockPos) =
        removeTarget(CacheKey.PastureKey(origin), category, pos)

    /** Remove a position from ALL caches (for overlapping ranges). */
    fun removeTargetGlobal(category: BlockCategory, pos: BlockPos) {
        caches.values.forEach { cache ->
            cache.targetsByCategory[category]?.remove(pos)
        }
    }

    fun removeAllCategoryTargets(key: CacheKey) {
        caches[key]?.targetsByCategory?.values?.forEach { it.clear() }
    }

    /** Atomically replace all category targets with new scan results. */
    fun replaceAllCategoryTargets(key: CacheKey, newTargets: Map<BlockCategory, Set<BlockPos>>) {
        val cache = caches.getOrPut(key) { PastureCache() }
        for ((category, set) in cache.targetsByCategory) {
            set.clear()
            newTargets[category]?.let { set.addAll(it) }
        }
    }

    fun removeCache(key: CacheKey) {
        caches.remove(key)
    }

    /** Backward-compat: remove by BlockPos (wraps to PastureKey). */
    fun removePasture(pastureOrigin: BlockPos) {
        caches.remove(CacheKey.PastureKey(pastureOrigin))
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

    /** Snapshot of all tracked caches and their target counts per category. */
    fun getCacheStatus(): Map<CacheKey, Map<String, Int>> {
        return caches.mapValues { (_, cache) ->
            cache.targetsByCategory
                .filter { it.value.isNotEmpty() }
                .mapKeys { it.key.name }
                .mapValues { it.value.size }
        }
    }

    /** Backward-compat alias. */
    fun getPastureStatus(): Map<BlockPos, Map<String, Int>> {
        return caches.entries
            .filter { it.key is CacheKey.PastureKey }
            .associate { (it.key as CacheKey.PastureKey).pos to it.value }
            .mapValues { (_, cache) ->
                cache.targetsByCategory
                    .filter { it.value.isNotEmpty() }
                    .mapKeys { it.key.name }
                    .mapValues { it.value.size }
            }
    }

    /** Total tracked cache count. */
    fun getCacheCount(): Int = caches.size

    /** Backward-compat alias. */
    fun getPastureCount(): Int = caches.count { it.key is CacheKey.PastureKey }

    /** Clear all caches. */
    fun clearAll() {
        caches.clear()
        structuresCache = null
        structureLocationCache.clear()
        structureLocationExpiry.clear()
    }
}