/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

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
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID

/**
 * Logistics jobs (H1–H2). Container-internal operations.
 */
object LogisticsJobs {

    // ── H1: Magnetizer ───────────────────────────────────────────────
    // Consolidates 9 nuggets → 1 ingot within containers
    object Magnetizer : Worker {
        override val name = "magnetizer"
        override val priority = WorkerPriority.MOVE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("magnetrise")
        private val fallbackSpecies = listOf("Magnemite", "Magneton", "Magnezone")
        private val targets = mutableMapOf<UUID, BlockPos>()

        private val NUGGET_TO_INGOT = mapOf(
            Items.IRON_NUGGET to Items.IRON_INGOT,
            Items.GOLD_NUGGET to Items.GOLD_INGOT,
        )

        init {
            JobConfigManager.registerDefault("logistics", name, JobConfig(
                enabled = true,
                qualifyingMoves = qualifyingMoves.toList(),
                fallbackSpecies = fallbackSpecies,
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
            dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species)

        override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
            val world = context.world
            val origin = context.origin
            val pid = pokemonEntity.pokemon.uuid
            val target = targets[pid]
            if (target == null) {
                val found = CobbleCrewInventoryUtils.findInputContainer(
                    world, origin, predicate = { stack ->
                        NUGGET_TO_INGOT.containsKey(stack.item) && stack.count >= 9
                    }
                ) ?: return
                if (!CobbleCrewNavigationUtils.isTargeted(found, world)) {
                    CobbleCrewNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                    CobbleCrewNavigationUtils.navigateTo(pokemonEntity, found)
                }
                return
            }
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.ELECTRIC_SPARK, 3.0, WorkPhase.PROCESSING)) {
                consolidateNuggets(world, target)
                CobbleCrewNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
            }
        }

        private fun consolidateNuggets(world: World, pos: BlockPos) {
            val be = world.getBlockEntity(pos) ?: return
            val inv = be as? Inventory ?: return
            for ((nugget, ingot) in NUGGET_TO_INGOT) {
                var totalNuggets = 0
                val slots = mutableListOf<Int>()
                for (i in 0 until inv.size()) {
                    val stack = inv.getStack(i)
                    if (stack.item == nugget) {
                        totalNuggets += stack.count
                        slots.add(i)
                    }
                }
                val ingots = totalNuggets / 9
                val remainder = totalNuggets % 9
                if (ingots > 0) {
                    slots.forEach { inv.setStack(it, ItemStack.EMPTY) }
                    if (remainder > 0) {
                        CobbleCrewInventoryUtils.insertStack(inv, ItemStack(nugget, remainder))
                    }
                    CobbleCrewInventoryUtils.insertStack(inv, ItemStack(ingot, ingots))
                    inv.markDirty()
                }
            }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in targets
        override fun cleanup(pokemonId: UUID) { targets.remove(pokemonId) }
    }

    // ── H2: Ground Item Collector ────────────────────────────────────
    // Picks up items from the ground and deposits in containers
    object GroundItemCollector : Worker {
        override val name = "ground_item_collector"
        override val priority = WorkerPriority.TYPE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("telekinesis")
        private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
        private val failedDeposits = mutableMapOf<UUID, MutableSet<BlockPos>>()

        init {
            JobConfigManager.registerDefault("logistics", name, JobConfig(
                enabled = true,
                qualifyingMoves = qualifyingMoves.toList(),
                radius = 8,
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
            val r = config.radius?.takeIf { it > 0 } ?: 8
            val searchArea = Box(origin).expand(r.toDouble(), r.toDouble(), r.toDouble())
            val item = world.getEntitiesByClass(ItemEntity::class.java, searchArea) { true }
                .firstOrNull { it.isOnGround } ?: return

            val currentTarget = CobbleCrewNavigationUtils.getTarget(pid, world)
            val itemPos = item.blockPos
            if (currentTarget == null) {
                if (!CobbleCrewNavigationUtils.isTargeted(itemPos, world)) {
                    CobbleCrewNavigationUtils.claimTarget(pid, itemPos, world)
                    CobbleCrewNavigationUtils.navigateTo(pokemonEntity, itemPos)
                }
                return
            }
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, currentTarget)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, currentTarget, world, ParticleTypes.ENCHANT, 3.0, WorkPhase.HARVESTING)) {
                val stack = item.stack.copy()
                item.discard()
                heldItems[pid] = listOf(stack)
                CobbleCrewNavigationUtils.releaseTarget(pid, world)
            }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in heldItems
        override fun cleanup(pokemonId: UUID) {
            heldItems.remove(pokemonId)
            failedDeposits.remove(pokemonId)
        }
    }

    fun register() {
        WorkerRegistry.registerAll(Magnetizer, GroundItemCollector)
    }
}
