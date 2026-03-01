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
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import akkiruk.cobblecrew.utilities.WorkSpeedBoostManager
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.loot.LootTables
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

/**
 * Production jobs. Loot-table-based custom workers (fishing, pickup, dive, dig site).
 */
object ProductionJobs {

    // ── Loot-based Workers ───────────────────────────────────────────

    object FishingLooter : Worker {
        override val name = "fishing_looter"
        override val priority = WorkerPriority.MOVE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("dive")
        private val lastGenTime = mutableMapOf<UUID, Long>()
        private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
        private val failedDeposits = mutableMapOf<UUID, MutableSet<BlockPos>>()

        init {
            JobConfigManager.registerDefault("production", name, JobConfig(
                enabled = true,
                cooldownSeconds = 120,
                qualifyingMoves = qualifyingMoves.toList(),
                treasureChance = 10,
                requiresWater = true,
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
            dslEligible(config, qualifyingMoves, emptyList(), moves, species)

        override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
            val world = context.world
            val origin = context.origin
            val pid = pokemonEntity.pokemon.uuid
            if (config.requiresWater == true && !pokemonEntity.isTouchingWater) return
            val held = heldItems[pid]
            if (held.isNullOrEmpty()) {
                failedDeposits.remove(pid)
                produce(world, origin, pokemonEntity)
            } else {
                if (context is JobContext.Party) {
                    CobbleCrewInventoryUtils.deliverToPlayer(context.player, held, pokemonEntity)
                    heldItems.remove(pid)
                    failedDeposits.remove(pid)
                    return
                }
                CobbleCrewInventoryUtils.handleDepositing(world, origin, pokemonEntity, held, failedDeposits, heldItems)
            }
        }

        private fun produce(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val baseCd = (config.cooldownSeconds.takeIf { it > 0 } ?: 120) * 20L
            val cd = WorkSpeedBoostManager.adjustCooldown(baseCd, origin, now)
            if (now - last < cd) return

            val chance = (config.treasureChance?.takeIf { it > 0 } ?: 10).toDouble() / 100
            val useTreasure = world.random.nextFloat() < chance

            val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
                .add(LootContextParameters.ORIGIN, origin.toCenterPos())
                .add(LootContextParameters.TOOL, ItemStack(Items.FISHING_ROD))
                .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)
                .build(LootContextTypes.FISHING)

            val table = if (useTreasure)
                world.server.reloadableRegistries.getLootTable(LootTables.FISHING_TREASURE_GAMEPLAY)
            else
                world.server.reloadableRegistries.getLootTable(LootTables.FISHING_GAMEPLAY)

            val drops = table.generateLoot(lootParams)
            if (drops.isNotEmpty()) {
                lastGenTime[pid] = now
                heldItems[pid] = drops
            }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in heldItems
        override fun cleanup(pokemonId: UUID) {
            lastGenTime.remove(pokemonId)
            heldItems.remove(pokemonId)
            failedDeposits.remove(pokemonId)
        }
    }

    object PickupLooter : Worker {
        override val name = "pickup_looter"
        override val priority = WorkerPriority.MOVE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val lastGenTime = mutableMapOf<UUID, Long>()
        private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
        private val failedDeposits = mutableMapOf<UUID, MutableSet<BlockPos>>()

        init {
            JobConfigManager.registerDefault("production", name, JobConfig(
                enabled = true,
                cooldownSeconds = 120,
                requiredAbility = "pickup",
                lootTables = listOf("cobblemon:gameplay/pickup"),
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val req = config.requiredAbility?.lowercase() ?: "pickup"
            return ability.lowercase() == req
        }

        override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
            val world = context.world
            val origin = context.origin
            val pid = pokemonEntity.pokemon.uuid
            val held = heldItems[pid]
            if (held.isNullOrEmpty()) {
                failedDeposits.remove(pid)
                produce(world, origin, pokemonEntity)
            } else {
                if (context is JobContext.Party) {
                    CobbleCrewInventoryUtils.deliverToPlayer(context.player, held, pokemonEntity)
                    heldItems.remove(pid)
                    failedDeposits.remove(pid)
                    return
                }
                CobbleCrewInventoryUtils.handleDepositing(world, origin, pokemonEntity, held, failedDeposits, heldItems)
            }
        }

        private fun produce(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val baseCd = (config.cooldownSeconds.takeIf { it > 0 } ?: 120) * 20L
            val cd = WorkSpeedBoostManager.adjustCooldown(baseCd, origin, now)
            if (now - last < cd) return

            val tables = (config.lootTables ?: emptyList()).ifEmpty { listOf("cobblemon:gameplay/pickup") }
                .mapNotNull { Identifier.tryParse(it) }
            if (tables.isEmpty()) return

            val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
                .add(LootContextParameters.ORIGIN, origin.toCenterPos())
                .add(LootContextParameters.THIS_ENTITY, pokemonEntity)
                .build(LootContextTypes.CHEST)

            val key = RegistryKey.of(RegistryKeys.LOOT_TABLE, tables.random())
            val drops = world.server.reloadableRegistries.getLootTable(key).generateLoot(lootParams)
            if (drops.isNotEmpty()) {
                lastGenTime[pid] = now
                heldItems[pid] = drops
            }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in heldItems
        override fun cleanup(pokemonId: UUID) {
            lastGenTime.remove(pokemonId)
            heldItems.remove(pokemonId)
            failedDeposits.remove(pokemonId)
        }
    }

    object DiveCollector : Worker {
        override val name = "dive_collector"
        override val priority = WorkerPriority.MOVE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("waterfall")
        private val lastGenTime = mutableMapOf<UUID, Long>()
        private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
        private val failedDeposits = mutableMapOf<UUID, MutableSet<BlockPos>>()

        init {
            JobConfigManager.registerDefault("production", name, JobConfig(
                enabled = true,
                cooldownSeconds = 210,
                qualifyingMoves = qualifyingMoves.toList(),
                requiresWater = true,
                lootTables = listOf("cobblemon:gameplay/pickup"),
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
            dslEligible(config, qualifyingMoves, emptyList(), moves, species)

        override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
            val world = context.world
            val origin = context.origin
            val pid = pokemonEntity.pokemon.uuid
            if (config.requiresWater == true && !pokemonEntity.isTouchingWater) return
            val held = heldItems[pid]
            if (held.isNullOrEmpty()) {
                failedDeposits.remove(pid)
                produce(world, origin, pokemonEntity)
            } else {
                if (context is JobContext.Party) {
                    CobbleCrewInventoryUtils.deliverToPlayer(context.player, held, pokemonEntity)
                    heldItems.remove(pid)
                    failedDeposits.remove(pid)
                    return
                }
                CobbleCrewInventoryUtils.handleDepositing(world, origin, pokemonEntity, held, failedDeposits, heldItems)
            }
        }

        private fun produce(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val baseCd = (config.cooldownSeconds.takeIf { it > 0 } ?: 210) * 20L
            val cd = WorkSpeedBoostManager.adjustCooldown(baseCd, origin, now)
            if (now - last < cd) return

            val tables = (config.lootTables ?: emptyList()).ifEmpty { listOf("cobblemon:gameplay/pickup") }
                .mapNotNull { Identifier.tryParse(it) }
            if (tables.isEmpty()) return

            val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
                .add(LootContextParameters.ORIGIN, origin.toCenterPos())
                .add(LootContextParameters.THIS_ENTITY, pokemonEntity)
                .build(LootContextTypes.CHEST)

            val key = RegistryKey.of(RegistryKeys.LOOT_TABLE, tables.random())
            val drops = world.server.reloadableRegistries.getLootTable(key).generateLoot(lootParams)
            if (drops.isNotEmpty()) {
                lastGenTime[pid] = now
                heldItems[pid] = drops
            }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in heldItems
        override fun cleanup(pokemonId: UUID) {
            lastGenTime.remove(pokemonId)
            heldItems.remove(pokemonId)
            failedDeposits.remove(pokemonId)
        }
    }

    object DigSiteExcavator : Worker {
        override val name = "dig_site_excavator"
        override val priority = WorkerPriority.TYPE
        override val targetCategory = BlockCategory.SUSPICIOUS

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("dig")
        private val lastGenTime = mutableMapOf<UUID, Long>()
        private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
        private val failedDeposits = mutableMapOf<UUID, MutableSet<BlockPos>>()
        private val targets = mutableMapOf<UUID, BlockPos>()

        init {
            JobConfigManager.registerDefault("production", name, JobConfig(
                enabled = true,
                cooldownSeconds = 120,
                qualifyingMoves = qualifyingMoves.toList(),
                lootTables = listOf("cobblemon:gameplay/pickup"),
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
            dslEligible(config, qualifyingMoves, emptyList(), moves, species)

        override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
            val world = context.world
            val origin = context.origin
            val pid = pokemonEntity.pokemon.uuid
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
            val target = targets[pid]
            if (target == null) {
                val found = findDigSpot(world, origin) ?: return
                if (!CobbleCrewNavigationUtils.isTargeted(found, world)) {
                    CobbleCrewNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                    CobbleCrewNavigationUtils.navigateTo(pokemonEntity, found)
                }
                return
            }
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.COMPOSTER, 3.0, WorkPhase.HARVESTING)) {
                generateLoot(world, target, pokemonEntity)
                CobbleCrewNavigationUtils.releaseTarget(pid, world, blacklist = false)
                targets.remove(pid)
            }
        }

        private fun findDigSpot(world: World, origin: BlockPos): BlockPos? {
            val cached = CobbleCrewCacheManager.getTargets(origin, BlockCategory.SUSPICIOUS)
            return cached
                .filter { pos ->
                    val above = world.getBlockState(pos.up())
                    above.isAir && !CobbleCrewNavigationUtils.isRecentlyExpired(pos, world)
                }
                .minByOrNull { it.getSquaredDistance(origin) }
        }

        private fun generateLoot(world: World, pos: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val baseCd = (config.cooldownSeconds.takeIf { it > 0 } ?: 120) * 20L
            val cd = WorkSpeedBoostManager.adjustCooldown(baseCd, pos, now)
            if (now - last < cd) return

            val tables = (config.lootTables ?: emptyList()).ifEmpty { listOf("cobblemon:gameplay/pickup") }
                .mapNotNull { Identifier.tryParse(it) }
            if (tables.isEmpty()) return

            val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
                .add(LootContextParameters.ORIGIN, pos.toCenterPos())
                .add(LootContextParameters.THIS_ENTITY, pokemonEntity)
                .build(LootContextTypes.CHEST)

            val key = RegistryKey.of(RegistryKeys.LOOT_TABLE, tables.random())
            val drops = world.server.reloadableRegistries.getLootTable(key).generateLoot(lootParams)
            if (drops.isNotEmpty()) {
                lastGenTime[pid] = now
                heldItems[pid] = drops
            }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in heldItems || pokemonId in targets
        override fun cleanup(pokemonId: UUID) {
            lastGenTime.remove(pokemonId)
            heldItems.remove(pokemonId)
            failedDeposits.remove(pokemonId)
            targets.remove(pokemonId)
        }
    }

    fun register() {
        WorkerRegistry.registerAll(
            FishingLooter,
            PickupLooter,
            DiveCollector,
            DigSiteExcavator,
        )
    }
}
