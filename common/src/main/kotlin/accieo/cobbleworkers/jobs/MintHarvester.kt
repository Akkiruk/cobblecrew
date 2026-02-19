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
import accieo.cobbleworkers.utilities.CobbleworkersTypeUtils
import com.cobblemon.mod.common.block.MintBlock
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object MintHarvester : BaseHarvester() {
    private val MINTS_TAG = TagKey.of(RegistryKeys.BLOCK, Identifier.of("cobblemon", "mints"))
    private val config get() = CobbleworkersConfigHolder.config.mints

    override val jobType = JobType.MintHarvester
    override val arrivalParticle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER
    override val blockValidator: ((World, BlockPos) -> Boolean) = { world, pos ->
        world.getBlockState(pos).isIn(MINTS_TAG)
    }

    override fun isEnabled() = config.mintHarvestersEnabled
    override fun isEligible(pokemonEntity: PokemonEntity) =
        CobbleworkersTypeUtils.isAllowedByType(config.typeHarvestsMints, pokemonEntity)
            || CobbleworkersTypeUtils.isDesignatedBySpecies(pokemonEntity, config.mintHarvesters)

    override fun isTargetReady(world: World, pos: BlockPos) =
        world.getBlockState(pos).get(MintBlock.AGE) == MintBlock.MATURE_AGE

    override fun harvest(world: World, targetPos: BlockPos, pokemonEntity: PokemonEntity) {
        if (!world.getBlockState(targetPos).isIn(MINTS_TAG)) return
        harvestWithLoot(world, targetPos, pokemonEntity) { w, p, state ->
            w.setBlockState(p, state.with(MintBlock.AGE, 0), Block.NOTIFY_ALL)
        }
    }
}
