/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

/**
 * Base class for placement workers that take items from containers and place them
 * as blocks in the world.
 *
 * Pattern: find container with item → extract → find valid placement → navigate → place.
 */
abstract class BasePlacer : Worker {
    private enum class Phase { IDLE, FETCHING, NAVIGATING_PLACEMENT }

    private val phases = mutableMapOf<UUID, Phase>()
    private val sourceTargets = mutableMapOf<UUID, BlockPos>()
    private val placementTargets = mutableMapOf<UUID, BlockPos>()
    private val heldItems = mutableMapOf<UUID, ItemStack>()

    private val generalConfig get() = CobbleCrewConfigHolder.config.general
    protected val searchRadius get() = generalConfig.searchRadius
    protected val searchHeight get() = generalConfig.searchHeight

    open val placeParticle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER

    /** Predicate for items this placer consumes from containers. */
    abstract fun itemPredicate(stack: ItemStack): Boolean

    /** Find a valid placement position within range. Return null if none. */
    abstract fun findPlacementTarget(world: World, origin: BlockPos): BlockPos?

    /** Place the held item at the target position. */
    abstract fun placeBlock(world: World, pos: BlockPos, item: ItemStack)

    override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
        return findPlacementTarget(world, origin) != null
            && CobbleCrewInventoryUtils.findInputContainer(world, origin, ::itemPredicate) != null
    }

    override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid
        when (phases.getOrDefault(pokemonId, Phase.IDLE)) {
            Phase.IDLE -> {
                val source = CobbleCrewInventoryUtils.findInputContainer(
                    world, origin, ::itemPredicate
                ) ?: return
                sourceTargets[pokemonId] = source
                phases[pokemonId] = Phase.FETCHING
            }
            Phase.FETCHING -> {
                val source = sourceTargets[pokemonId] ?: run {
                    phases[pokemonId] = Phase.IDLE; return
                }
                CobbleCrewNavigationUtils.navigateTo(pokemonEntity, source)
                if (CobbleCrewNavigationUtils.isPokemonAtPosition(pokemonEntity, source, 2.0)) {
                    val taken = CobbleCrewInventoryUtils.extractFromContainer(
                        world, source, ::itemPredicate
                    )
                    if (taken.isEmpty) {
                        phases[pokemonId] = Phase.IDLE; return
                    }
                    heldItems[pokemonId] = taken
                    val target = findPlacementTarget(world, origin)
                    if (target == null) {
                        // No placement spot — drop item back
                        CobbleCrewInventoryUtils.insertStack(
                            world.getBlockEntity(source) as? net.minecraft.inventory.Inventory ?: run {
                                phases[pokemonId] = Phase.IDLE; return
                            }, taken
                        )
                        heldItems.remove(pokemonId)
                        phases[pokemonId] = Phase.IDLE
                        return
                    }
                    placementTargets[pokemonId] = target
                    phases[pokemonId] = Phase.NAVIGATING_PLACEMENT
                }
            }
            Phase.NAVIGATING_PLACEMENT -> {
                val target = placementTargets[pokemonId] ?: run {
                    phases[pokemonId] = Phase.IDLE; return
                }
                CobbleCrewNavigationUtils.navigateTo(pokemonEntity, target)
                if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, placeParticle, 1.5)) {
                    val item = heldItems.remove(pokemonId) ?: run {
                        phases[pokemonId] = Phase.IDLE; return
                    }
                    placeBlock(world, target, item)
                    phases[pokemonId] = Phase.IDLE
                }
            }
        }
    }

    override fun hasActiveState(pokemonId: UUID): Boolean =
        pokemonId in heldItems || phases.getOrDefault(pokemonId, Phase.IDLE) != Phase.IDLE

    override fun cleanup(pokemonId: UUID) {
        phases.remove(pokemonId)
        sourceTargets.remove(pokemonId)
        placementTargets.remove(pokemonId)
        heldItems.remove(pokemonId)
    }
}
