/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.jobs.dsl.dslEligible
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.jobs.dsl.SupportJob
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import akkiruk.cobblecrew.utilities.WorkSpeedBoostManager
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.MapColorComponent
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.FilledMapItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.map.MapDecorationTypes
import net.minecraft.item.map.MapState
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.entry.RegistryEntryList
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import net.minecraft.world.gen.structure.Structure
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Standard support jobs (F1–F8, F10). Apply positive status effects to nearby players.
 * F9 (Cleanser), F11 (Full Restore), F12 (Aura Master) require custom implementations.
 */
object SupportJobs {

    val HEALER = SupportJob(
        name = "healer",
        qualifyingMoves = setOf("drainingkiss"),
        fallbackSpecies = listOf("Chansey", "Blissey", "Happiny", "Audino"),
        particle = ParticleTypes.HEART,
        statusEffect = StatusEffects.REGENERATION,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
        requiresDamage = true,
        workBoostPercent = 5,
    )

    val SPEED_BOOSTER = SupportJob(
        name = "speed_booster",
        qualifyingMoves = setOf("tailwind"),
        particle = ParticleTypes.CLOUD,
        statusEffect = StatusEffects.SPEED,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
        workBoostPercent = 15,
    )

    val STRENGTH_BOOSTER = SupportJob(
        name = "strength_booster",
        qualifyingMoves = setOf("swordsdance"),
        particle = ParticleTypes.CRIT,
        statusEffect = StatusEffects.STRENGTH,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
        workBoostPercent = 5,
    )

    val RESISTANCE_PROVIDER = SupportJob(
        name = "resistance_provider",
        qualifyingMoves = setOf("irondefense"),
        particle = ParticleTypes.ENCHANT,
        statusEffect = StatusEffects.RESISTANCE,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
        workBoostPercent = 5,
    )

    val HASTE_PROVIDER = SupportJob(
        name = "haste_provider",
        qualifyingMoves = setOf("honeclaws"),
        particle = ParticleTypes.ENCHANT,
        statusEffect = StatusEffects.HASTE,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
        workBoostPercent = 15,
    )

    val JUMP_BOOSTER = SupportJob(
        name = "jump_booster",
        qualifyingMoves = setOf("bounce"),
        particle = ParticleTypes.CLOUD,
        statusEffect = StatusEffects.JUMP_BOOST,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
    )

    val NIGHT_VISION_PROVIDER = SupportJob(
        name = "night_vision_provider",
        qualifyingMoves = setOf("laserfocus"),
        particle = ParticleTypes.END_ROD,
        statusEffect = StatusEffects.NIGHT_VISION,
        defaultDurationSeconds = 60,
        effectAmplifier = 0,
    )

    val WATER_BREATHER = SupportJob(
        name = "water_breather",
        qualifyingMoves = setOf("brine"),
        particle = ParticleTypes.BUBBLE,
        statusEffect = StatusEffects.WATER_BREATHING,
        defaultDurationSeconds = 60,
        effectAmplifier = 0,
    )

    val HUNGER_RESTORER = SupportJob(
        name = "hunger_restorer",
        qualifyingMoves = setOf("yawn"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        statusEffect = StatusEffects.SATURATION,
        defaultDurationSeconds = 10,
        effectAmplifier = 1,
        workBoostPercent = 5,
    )

    // ── Scout (migrated from legacy) ─────────────────────────────────
    // Picks up blank Maps, converts to structure-locating filled maps
    object ScoutWorker : Worker {
        override val name = "scout"
        override val priority = WorkerPriority.MOVE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("fly")
        private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
        private val failedDeposits = mutableMapOf<UUID, MutableSet<BlockPos>>()
        private val lastGenTime = mutableMapOf<UUID, Long>()
        private val pendingLookups = mutableMapOf<Identifier, CompletableFuture<com.mojang.datafixers.util.Pair<BlockPos, RegistryEntry<Structure>>?>>()

        init {
            JobConfigManager.registerDefault("support", name, JobConfig(
                enabled = true,
                cooldownSeconds = 600,
                qualifyingMoves = qualifyingMoves.toList(),
                structureTags = listOf("minecraft:village", "minecraft:shipwreck"),
                useAllStructures = false,
                mapNameIsHidden = false,
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
            dslEligible(config, qualifyingMoves, emptyList(), moves, species)

        override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
            val world = context.world
            val origin = context.origin
            val pid = pokemonEntity.pokemon.uuid
            val ownerId = pokemonEntity.ownerUuid ?: return

            val held = heldItems[pid]
            if (!held.isNullOrEmpty()) {
                if (context is JobContext.Party) {
                    CobbleCrewInventoryUtils.deliverToPlayer(context.player, held, pokemonEntity)
                    heldItems.remove(pid)
                    failedDeposits.remove(pid)
                    return
                }
                CobbleCrewInventoryUtils.handleDepositing(world, origin, pokemonEntity, held, failedDeposits, heldItems)
                return
            }
            failedDeposits.remove(pid)

            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val baseCd = (config.cooldownSeconds.takeIf { it > 0 } ?: 600) * 20L
            val cd = WorkSpeedBoostManager.adjustCooldown(baseCd, origin, now)
            if (now - last < cd) return

            handleMapPickup(world, origin, pokemonEntity)
        }

        private fun handleMapPickup(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val r = 8
            val searchArea = Box(origin).expand(r.toDouble(), r.toDouble(), r.toDouble())
            val mapItem = world.getEntitiesByClass(ItemEntity::class.java, searchArea) { true }
                .filter { it.isOnGround && it.stack.item == Items.MAP }
                .minByOrNull { it.squaredDistanceTo(origin.x + 0.5, origin.y + 0.5, origin.z + 0.5) }
                ?: return

            val itemPos = mapItem.blockPos
            val currentTarget = CobbleCrewNavigationUtils.getTarget(pid, world)
            if (currentTarget == null) {
                if (!CobbleCrewNavigationUtils.isTargeted(itemPos, world)) {
                    CobbleCrewNavigationUtils.claimTarget(pid, itemPos, world)
                    CobbleCrewNavigationUtils.navigateTo(pokemonEntity, itemPos)
                }
                return
            }
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, currentTarget)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, currentTarget, world, ParticleTypes.END_ROD, 3.0, WorkPhase.HARVESTING)) {
                if (mapItem.stack.item == Items.MAP) {
                    val singleItem = mapItem.stack.split(1)
                    if (mapItem.stack.isEmpty) mapItem.discard()
                    val map = createStructureMap(world as ServerWorld, origin)
                    heldItems[pid] = listOf(map ?: singleItem)
                    lastGenTime[pid] = world.time
                }
                CobbleCrewNavigationUtils.releaseTarget(pid, world, blacklist = false)
            }
        }

        private fun createStructureMap(world: ServerWorld, origin: BlockPos): ItemStack? {
            val now = world.time
            val useAll = config.useAllStructures ?: false
            val tags = (config.structureTags ?: emptyList()).ifEmpty { listOf("minecraft:village") }
            val structures = CobbleCrewCacheManager.getStructures(world, useAll, tags)
            if (structures.isEmpty()) return null

            val selectedId = structures.random()
            val cached = CobbleCrewCacheManager.getCachedStructure(selectedId, now)
            val searchResult = if (cached != null) {
                cached
            } else {
                val existing = pendingLookups[selectedId]
                if (existing == null) {
                    val reg = world.server.registryManager.get(RegistryKeys.STRUCTURE)
                    val entry = reg.getEntry(RegistryKey.of(RegistryKeys.STRUCTURE, selectedId)).orElse(null) ?: return null
                    val entryList = RegistryEntryList.of(entry)
                    val cg = world.chunkManager.chunkGenerator
                    val sp = origin.toImmutable()
                    val future = CompletableFuture<com.mojang.datafixers.util.Pair<BlockPos, RegistryEntry<Structure>>?>()
                    world.server.execute {
                        try {
                            future.complete(cg.locateStructure(world, entryList, sp, 100, false))
                        } catch (e: Exception) {
                            future.complete(null)
                        }
                    }
                    pendingLookups[selectedId] = future
                    return null
                } else if (!existing.isDone) {
                    return null
                } else {
                    pendingLookups.remove(selectedId)
                    val result = existing.join() ?: return null
                    CobbleCrewCacheManager.cacheStructure(selectedId, result, now)
                    result
                }
            }

            val structurePos = searchResult.first
            val structureEntry = searchResult.second
            val map = FilledMapItem.createMap(world, structurePos.x, structurePos.z, 2.toByte(), true, true)
            MapState.addDecorationsNbt(map, structurePos, "target", MapDecorationTypes.RED_X)

            val hidden = config.mapNameIsHidden ?: false
            val mapName = if (hidden) Text.of("Scout's map")
            else Text.of(cleanMapName(structureEntry.idAsString))
            map.set(DataComponentTypes.CUSTOM_NAME, mapName)
            map.set(DataComponentTypes.MAP_COLOR, MapColorComponent(0xCC84ED))
            return map
        }

        private fun cleanMapName(rawName: String): String =
            rawName.substringAfterLast(":").substringAfterLast("/")
                .replace("_", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in heldItems
        override fun cleanup(pokemonId: UUID) {
            heldItems.remove(pokemonId)
            failedDeposits.remove(pokemonId)
            lastGenTime.remove(pokemonId)
        }
    }

    fun register() {
        WorkerRegistry.registerAll(
            HEALER, SPEED_BOOSTER, STRENGTH_BOOSTER, RESISTANCE_PROVIDER,
            HASTE_PROVIDER, JUMP_BOOSTER, NIGHT_VISION_PROVIDER,
            WATER_BREATHER, HUNGER_RESTORER,
            ScoutWorker,
        )
    }
}
