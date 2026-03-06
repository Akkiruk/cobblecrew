/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.PokemonWorkerState
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Owns the full deposit lifecycle: finding containers, navigating,
 * opening/closing animations, inserting items, retry logic, and overflow.
 * All item-producing jobs route through [tick].
 */
object DepositHelper {

    private const val OVERFLOW_TIMEOUT_TICKS = 6000L // 5 minutes
    private const val DEPOSIT_DELAY = 15L // ticks before depositing after arrival
    private const val DEPOSIT_RETRY_COOLDOWN = 200L // 10 seconds before retrying all containers

    /**
     * Tick the deposit logic for a Pokémon carrying items.
     * Returns true while items remain (still depositing),
     * false when all items have been delivered/deposited/dropped.
     */
    fun tick(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): Boolean {
        if (state.heldItems.isEmpty()) return false

        // Party → deliver directly to player inventory
        if (context is JobContext.Party) {
            CobbleCrewInventoryUtils.deliverToPlayer(context.player, state.heldItems.toList(), pokemonEntity)
            clearDeposit(state)
            return false
        }

        // Overflow protection — drop items if stuck for 5+ minutes
        val world = context.world
        if (state.heldSinceTick > 0 && world.time - state.heldSinceTick > OVERFLOW_TIMEOUT_TICKS) {
            dropItems(world, pokemonEntity, state.heldItems)
            clearDeposit(state)
            return false
        }

        depositToContainer(world, context.origin, pokemonEntity, state)
        return state.heldItems.isNotEmpty()
    }

    // ---- Container deposit orchestration ----

    private fun depositToContainer(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        state: PokemonWorkerState,
    ) {
        val itemsToDeposit = state.heldItems.toList()
        val allContainers = CobbleCrewCacheManager.getTargets(origin, BlockCategory.CONTAINER)
        val inventoryPos = CobbleCrewInventoryUtils.findClosestInventory(world, origin, state.failedDeposits, itemsToDeposit)

        if (inventoryPos == null) {
            val now = world.time
            if (now - state.lastDepositWarning >= 200L) {
                CobbleCrewDebugLogger.depositNoContainers(pokemonEntity, allContainers.size, state.failedDeposits.size)
                state.lastDepositWarning = now
            }
            if (DeferredBlockScanner.isScanActive(origin)) return

            if (state.depositRetryTick == 0L) state.depositRetryTick = now
            if (now - state.depositRetryTick >= DEPOSIT_RETRY_COOLDOWN) {
                CobbleCrewDebugLogger.depositRetryReset(pokemonEntity)
                state.failedDeposits.clear()
                state.depositRetryTick = 0L
            }

            ClaimManager.navigateTo(pokemonEntity, origin, state)
            return
        }

        if (ClaimManager.isPokemonAtPosition(pokemonEntity, inventoryPos, 2.0)) {
            val now = world.time
            val arrived = state.depositArrivalTick

            if (arrived == null) {
                if (!CobbleCrewInventoryUtils.hasSpaceFor(world, inventoryPos, itemsToDeposit)) {
                    CobbleCrewDebugLogger.depositContainerFull(pokemonEntity, inventoryPos)
                    state.failedDeposits.add(inventoryPos)
                    return
                }
                state.depositArrivalTick = now
                pokemonEntity.navigation.stop()
                WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.DEPOSITING, world)
                pokemonEntity.lookControl.lookAt(inventoryPos.x + 0.5, inventoryPos.y + 0.5, inventoryPos.z + 0.5)
                ContainerAnimations.openContainer(world, inventoryPos)
                return
            }

            if (now - arrived < DEPOSIT_DELAY) {
                pokemonEntity.lookControl.lookAt(inventoryPos.x + 0.5, inventoryPos.y + 0.5, inventoryPos.z + 0.5)
                return
            }

            state.depositArrivalTick = null

            val inventory = world.getBlockEntity(inventoryPos) as? Inventory
            if (inventory == null) {
                CobbleCrewDebugLogger.log(
                    CobbleCrewDebugLogger.Category.DEPOSIT, pokemonEntity,
                    "no block entity at (${inventoryPos.x},${inventoryPos.y},${inventoryPos.z}), marking tried"
                )
                state.failedDeposits.add(inventoryPos)
                ContainerAnimations.scheduleCloseAt(inventoryPos, now + 5L)
                return
            }

            WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.DEPOSIT_SUCCESS, world)
            val remainingDrops = CobbleCrewInventoryUtils.insertStacks(inventory, itemsToDeposit)

            if (remainingDrops.size == itemsToDeposit.size) {
                state.failedDeposits.add(inventoryPos)
            }

            ContainerAnimations.scheduleClose(world, inventoryPos)

            state.heldItems.clear()
            if (remainingDrops.isNotEmpty()) {
                state.heldItems.addAll(remainingDrops)
            } else {
                CobbleCrewDebugLogger.depositSuccess(pokemonEntity, inventoryPos, itemsToDeposit)
                state.failedDeposits.clear()
                state.depositRetryTick = 0L
                state.lastDepositWarning = 0L
                pokemonEntity.navigation.stop()
            }
        } else {
            ClaimManager.navigateTo(pokemonEntity, inventoryPos, state)
        }
    }

    fun clearDeposit(state: PokemonWorkerState) {
        state.deposit.clear()
    }

    private fun dropItems(world: World, entity: PokemonEntity, items: MutableList<ItemStack>) {
        for (stack in items) {
            if (stack.isEmpty) continue
            val drop = ItemEntity(world, entity.x, entity.y, entity.z, stack.copy())
            drop.setPickupDelay(20)
            world.spawnEntity(drop)
        }
        items.clear()
    }
}
