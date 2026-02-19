/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs

import accieo.cobbleworkers.config.CobbleworkersConfigHolder
import accieo.cobbleworkers.enums.JobType
import accieo.cobbleworkers.utilities.CobbleworkersCropUtils
import accieo.cobbleworkers.utilities.CobbleworkersTypeUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object CropHarvester : BaseHarvester() {
    private val config get() = CobbleworkersConfigHolder.config.cropHarvest

    override val jobType = JobType.CropHarvester
    override val arrivalParticle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER
    override val blockValidator: ((World, BlockPos) -> Boolean) = { world, pos ->
        world.getBlockState(pos).block in CobbleworkersCropUtils.validCropBlocks
    }

    override fun isEnabled() = config.cropHarvestersEnabled
    override fun isEligible(pokemonEntity: PokemonEntity) =
        CobbleworkersTypeUtils.isAllowedByType(config.typeHarvestsCrops, pokemonEntity)
            || CobbleworkersTypeUtils.isDesignatedBySpecies(pokemonEntity, config.cropHarvesters)

    override fun findClosestTarget(world: World, origin: BlockPos) =
        CobbleworkersCropUtils.findClosestCrop(world, origin)

    override fun harvest(world: World, targetPos: BlockPos, pokemonEntity: PokemonEntity) {
        CobbleworkersCropUtils.harvestCrop(world, targetPos, pokemonEntity, heldItemsByPokemon, config)
    }
}
