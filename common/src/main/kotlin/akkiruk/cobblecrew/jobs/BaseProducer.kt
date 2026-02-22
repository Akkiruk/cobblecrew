/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.utilities.CobbleCrewDebugLogger
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

/**
 * Base class for production-style workers that self-generate items on a cooldown,
 * then navigate to a container to deposit them. No target block needed.
 *
 * Pattern: cooldown → produce items → hold → navigate to container → deposit.
 *
 * Subclasses define what items are produced and the cooldown.
 */
abstract class BaseProducer : Worker {
    protected val lastProductionTime = mutableMapOf<UUID, Long>()
    protected val heldItemsByPokemon = mutableMapOf<UUID, List<ItemStack>>()
    protected val failedDepositLocations = mutableMapOf<UUID, MutableSet<BlockPos>>()

    open val productionParticle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER

    /** Cooldown in ticks between productions. */
    abstract val cooldownTicks: Long

    /** Produce items for this Pokémon. Return empty list if nothing produced. */
    abstract fun produce(world: World, origin: BlockPos, pokemonEntity: PokemonEntity): List<ItemStack>

    override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
        val world = context.world
        val pokemonId = pokemonEntity.pokemon.uuid
        val heldItems = heldItemsByPokemon[pokemonId]

        if (heldItems.isNullOrEmpty()) {
            failedDepositLocations.remove(pokemonId)
            handleProduction(context, pokemonEntity)
        } else {
            if (context is JobContext.Party) {
                CobbleCrewInventoryUtils.deliverToPlayer(context.player, heldItems, pokemonEntity)
                heldItemsByPokemon.remove(pokemonId)
                failedDepositLocations.remove(pokemonId)
                return
            }
            CobbleCrewInventoryUtils.handleDepositing(
                context.world, context.origin, pokemonEntity, heldItems,
                failedDepositLocations, heldItemsByPokemon
            )
        }
    }

    protected open fun handleProduction(context: JobContext, pokemonEntity: PokemonEntity) {
        val world = context.world
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time
        val lastTime = lastProductionTime[pokemonId] ?: 0L

        if (now - lastTime < cooldownTicks) {
            CobbleCrewDebugLogger.productionOnCooldown(pokemonEntity, name, cooldownTicks - (now - lastTime))
            return
        }

        val items = produce(world, context.origin, pokemonEntity)
        if (items.isNotEmpty()) {
            lastProductionTime[pokemonId] = now
            heldItemsByPokemon[pokemonId] = items
            CobbleCrewDebugLogger.productionProduced(pokemonEntity, name, items)

            // Visual feedback
            WorkerVisualUtils.spawnParticles(world, pokemonEntity.blockPos, productionParticle, 5)
        }
    }

    override fun hasActiveState(pokemonId: UUID): Boolean = pokemonId in heldItemsByPokemon

    override fun cleanup(pokemonId: UUID) {
        lastProductionTime.remove(pokemonId)
        heldItemsByPokemon.remove(pokemonId)
        failedDepositLocations.remove(pokemonId)
    }

    override fun getHeldItems(pokemonId: UUID): List<ItemStack>? = heldItemsByPokemon[pokemonId]
}
