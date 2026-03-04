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
import akkiruk.cobblecrew.enums.JobImportance
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.BaseJob
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.jobs.Target
import akkiruk.cobblecrew.jobs.WorkResult
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.jobs.dsl.SupportJob
import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.utilities.WorkSpeedBoostManager
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
import net.minecraft.particle.ParticleEffect
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
        importance = JobImportance.CRITICAL,
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
        importance = JobImportance.LOW,
        particle = ParticleTypes.CLOUD,
        statusEffect = StatusEffects.JUMP_BOOST,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
    )

    val NIGHT_VISION_PROVIDER = SupportJob(
        name = "night_vision_provider",
        qualifyingMoves = setOf("laserfocus"),
        importance = JobImportance.LOW,
        particle = ParticleTypes.END_ROD,
        statusEffect = StatusEffects.NIGHT_VISION,
        defaultDurationSeconds = 60,
        effectAmplifier = 0,
    )

    val WATER_BREATHER = SupportJob(
        name = "water_breather",
        qualifyingMoves = setOf("brine"),
        importance = JobImportance.LOW,
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
    object ScoutWorker : BaseJob() {
        override val name = "scout"
        override val priority = WorkerPriority.MOVE
        override val targetCategory: BlockCategory? = null
        override val requiresTarget = true
        override val arrivalParticle: ParticleEffect = ParticleTypes.END_ROD
        override val workPhase: WorkPhase = WorkPhase.HARVESTING
        override val config get() = JobConfigManager.get(name)

        private val qualifyingMoves = setOf("fly")

        // Shared async structure lookup cache (per structure ID, not per Pokémon)
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

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslEligible(config, qualifyingMoves, emptyList(), moves, species)

        override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? {
            val searchArea = Box(context.origin).expand(8.0, 8.0, 8.0)
            val mapItem = context.world.getEntitiesByClass(ItemEntity::class.java, searchArea) { true }
                .filter { it.isOnGround && it.stack.item == Items.MAP }
                .minByOrNull { it.squaredDistanceTo(context.origin.x + 0.5, context.origin.y + 0.5, context.origin.z + 0.5) }
                ?: return null
            val pos = mapItem.blockPos
            if (ClaimManager.isTargetedByOther(pos, state.pokemonId)) return null
            return Target.Block(pos)
        }

        override fun validateTarget(state: PokemonWorkerState, context: JobContext): Boolean {
            val pos = state.targetPos ?: return false
            val searchArea = Box(pos).expand(2.0)
            return context.world.getEntitiesByClass(ItemEntity::class.java, searchArea) { true }
                .any { it.isOnGround && it.stack.item == Items.MAP }
        }

        override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
            val pos = state.targetPos ?: return WorkResult.Done()
            val searchArea = Box(pos).expand(2.0)
            val mapItem = context.world.getEntitiesByClass(ItemEntity::class.java, searchArea) { true }
                .firstOrNull { it.isOnGround && it.stack.item == Items.MAP } ?: return WorkResult.Done()

            val singleItem = mapItem.stack.split(1)
            if (mapItem.stack.isEmpty) mapItem.discard()
            val map = createStructureMap(context.world as ServerWorld, context.origin)

            val baseCd = (config.cooldownSeconds.takeIf { it > 0 } ?: 600) * 20L
            state.lastActionTime = WorkSpeedBoostManager.adjustCooldown(baseCd, context.origin, context.world.time)
            return WorkResult.Done(listOf(map ?: singleItem))
        }

        override fun getCooldownTicks(state: PokemonWorkerState): Long {
            val cd = state.lastActionTime
            if (cd > 0) { state.lastActionTime = 0L; return cd }
            return 0L
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
