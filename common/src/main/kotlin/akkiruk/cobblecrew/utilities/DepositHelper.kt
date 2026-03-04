/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.PokemonWorkerState
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.world.World

/**
 * Universal deposit/deliver handler. Replaces the 11 copies of
 * the deposit-or-deliver-to-player pattern spread across base classes.
 *
 * Reads items from [PokemonWorkerState.heldItems], manages
 * [PokemonWorkerState.failedDeposits], and provides overflow
 * protection for ALL item-producing jobs (not just harvesters).
 */
object DepositHelper {

    private const val OVERFLOW_TIMEOUT_TICKS = 6000L // 5 minutes

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

        // Delegate to InventoryUtils for the container-finding + chest animation logic
        CobbleCrewInventoryUtils.handleDepositingV2(
            world, context.origin, pokemonEntity, state
        )
        return state.heldItems.isNotEmpty()
    }

    private fun clearDeposit(state: PokemonWorkerState) {
        state.heldItems.clear()
        state.failedDeposits.clear()
        state.depositRetryTick = 0L
        state.depositArrivalTick = null
        state.heldSinceTick = 0L
        state.lastDepositWarning = 0L
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
