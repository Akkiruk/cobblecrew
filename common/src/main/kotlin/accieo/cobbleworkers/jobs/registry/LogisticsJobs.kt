/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.registry

import accieo.cobbleworkers.config.JobConfig
import accieo.cobbleworkers.config.JobConfigManager
import accieo.cobbleworkers.enums.BlockCategory
import accieo.cobbleworkers.enums.WorkerPriority
import accieo.cobbleworkers.interfaces.Worker
import accieo.cobbleworkers.jobs.WorkerRegistry
import accieo.cobbleworkers.utilities.CobbleworkersInventoryUtils
import accieo.cobbleworkers.utilities.CobbleworkersNavigationUtils
import accieo.cobbleworkers.utilities.WorkerVisualUtils
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
        private val qualifyingMoves = setOf("magnetrise", "flashcannon", "steelbeam", "magneticflux")
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

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            if (moves.any { it in eff }) return true
            val sp = config.fallbackSpecies.ifEmpty { fallbackSpecies }
            return sp.any { it.equals(species, ignoreCase = true) }
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val target = targets[pid]
            if (target == null) {
                val found = CobbleworkersInventoryUtils.findInputContainer(
                    world, origin, predicate = { stack ->
                        NUGGET_TO_INGOT.containsKey(stack.item) && stack.count >= 9
                    }
                ) ?: return
                if (!CobbleworkersNavigationUtils.isTargeted(found, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, found)
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.ELECTRIC_SPARK, 3.0)) {
                consolidateNuggets(world, target)
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
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
                        CobbleworkersInventoryUtils.insertStack(inv, ItemStack(nugget, remainder))
                    }
                    CobbleworkersInventoryUtils.insertStack(inv, ItemStack(ingot, ingots))
                    inv.markDirty()
                }
            }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in targets
        override fun cleanup(pokemonId: UUID) { targets.remove(pokemonId) }
    }

    // ── H2: Trash Disposal ───────────────────────────────────────────
    // Deletes configurable junk items from containers
    object TrashDisposal : Worker {
        override val name = "trash_disposal"
        override val priority = WorkerPriority.MOVE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("sludgebomb", "gunkshot")
        private val targets = mutableMapOf<UUID, BlockPos>()

        private val JUNK_ITEMS: Set<Item> = setOf(
            Items.ROTTEN_FLESH, Items.POISONOUS_POTATO,
            Items.DEAD_BUSH, Items.STRING,
        )

        init {
            JobConfigManager.registerDefault("logistics", name, JobConfig(
                enabled = true,
                qualifyingMoves = qualifyingMoves.toList(),
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            return moves.any { it in eff }
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val target = targets[pid]
            if (target == null) {
                val found = CobbleworkersInventoryUtils.findInputContainer(
                    world, origin, predicate = { stack -> stack.item in JUNK_ITEMS }
                ) ?: return
                if (!CobbleworkersNavigationUtils.isTargeted(found, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, found)
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.SMOKE, 3.0)) {
                removeJunk(world, target)
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
            }
        }

        private fun removeJunk(world: World, pos: BlockPos) {
            val be = world.getBlockEntity(pos) ?: return
            val inv = be as? Inventory ?: return
            for (i in 0 until inv.size()) {
                val stack = inv.getStack(i)
                if (stack.item in JUNK_ITEMS) {
                    inv.setStack(i, ItemStack.EMPTY)
                }
            }
            inv.markDirty()
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in targets
        override fun cleanup(pokemonId: UUID) { targets.remove(pokemonId) }
    }

    // ── H3: Ground Item Collector ────────────────────────────────────
    // Picks up items from the ground and deposits in containers
    object GroundItemCollector : Worker {
        override val name = "ground_item_collector"
        override val priority = WorkerPriority.TYPE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("psychic", "telekinesis", "confusion")
        private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
        private val failedDeposits = mutableMapOf<UUID, MutableSet<BlockPos>>()

        init {
            JobConfigManager.registerDefault("logistics", name, JobConfig(
                enabled = true,
                qualifyingMoves = qualifyingMoves.toList(),
                fallbackType = "PSYCHIC",
                radius = 8,
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            if (moves.any { it in eff }) return true
            val ft = config.fallbackType.ifEmpty { "PSYCHIC" }.uppercase()
            return ft in types
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val held = heldItems[pid]
            if (!held.isNullOrEmpty()) {
                CobbleworkersInventoryUtils.handleDepositing(world, origin, pokemonEntity, held, failedDeposits, heldItems)
                return
            }
            failedDeposits.remove(pid)
            val r = config.radius?.takeIf { it > 0 } ?: 8
            val searchArea = Box(origin).expand(r.toDouble(), r.toDouble(), r.toDouble())
            val item = world.getEntitiesByClass(ItemEntity::class.java, searchArea) { true }
                .firstOrNull { it.isOnGround } ?: return

            val currentTarget = CobbleworkersNavigationUtils.getTarget(pid, world)
            val itemPos = item.blockPos
            if (currentTarget == null) {
                if (!CobbleworkersNavigationUtils.isTargeted(itemPos, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, itemPos, world)
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, itemPos)
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, currentTarget)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, currentTarget, world, ParticleTypes.ENCHANT, 3.0)) {
                val stack = item.stack.copy()
                item.discard()
                heldItems[pid] = listOf(stack)
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
            }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in heldItems
        override fun cleanup(pokemonId: UUID) {
            heldItems.remove(pokemonId)
            failedDeposits.remove(pokemonId)
        }
    }

    fun register() {
        WorkerRegistry.registerAll(Magnetizer, TrashDisposal, GroundItemCollector)
    }
}
