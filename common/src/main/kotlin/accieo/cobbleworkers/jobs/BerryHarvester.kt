/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs

import accieo.cobbleworkers.config.CobbleworkersConfigHolder
import accieo.cobbleworkers.enums.BlockCategory
import accieo.cobbleworkers.enums.JobType
import accieo.cobbleworkers.utilities.CobbleworkersTypeUtils
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.block.entity.BerryBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object BerryHarvester : BaseHarvester() {
    private val BERRIES_TAG = TagKey.of(RegistryKeys.BLOCK, Identifier.of("cobblemon", "berries"))
    private val config get() = CobbleworkersConfigHolder.config.berries

    override val jobType = JobType.BerryHarvester
    override val arrivalParticle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER
    override val blockValidator: ((World, BlockPos) -> Boolean) = { world, pos ->
        world.getBlockState(pos).isIn(BERRIES_TAG)
    }

    override val name = "berry_harvester"
    override val targetCategory = BlockCategory.BERRY
    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
        isEnabled() && (config.typeHarvestsBerries.name in types
            || config.berryHarvesters.any { it.equals(species, ignoreCase = true) })

    override fun isEnabled() = config.berryHarvestersEnabled
    override fun isEligible(pokemonEntity: PokemonEntity) =
        CobbleworkersTypeUtils.isAllowedByType(config.typeHarvestsBerries, pokemonEntity)
            || CobbleworkersTypeUtils.isDesignatedBySpecies(pokemonEntity, config.berryHarvesters)

    override fun isTargetReady(world: World, pos: BlockPos) =
        world.getBlockState(pos).get(BerryBlock.AGE) == BerryBlock.FRUIT_AGE

    override fun harvest(world: World, targetPos: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val berryState = world.getBlockState(targetPos)
        val berryBlockEntity = world.getBlockEntity(targetPos) as? BerryBlockEntity ?: return
        if (!berryState.isIn(BERRIES_TAG)) return

        val drops = berryBlockEntity.harvest(world, berryState, targetPos, null)
        if (drops.isNotEmpty()) {
            heldItemsByPokemon[pokemonId] = drops as List<ItemStack>
        }

        world.setBlockState(targetPos, berryState.with(BerryBlock.AGE, BerryBlock.MATURE_AGE), Block.NOTIFY_ALL)
    }
}
