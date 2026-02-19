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
import com.cobblemon.mod.common.block.ApricornBlock
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object ApricornHarvester : BaseHarvester() {
    private val APRICORNS_TAG = TagKey.of(RegistryKeys.BLOCK, Identifier.of("cobblemon", "apricorns"))
    private val config get() = CobbleworkersConfigHolder.config.apricorn

    override val jobType = JobType.ApricornHarvester
    override val arrivalParticle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER
    override val blockValidator: ((World, BlockPos) -> Boolean) = { world, pos ->
        world.getBlockState(pos).isIn(APRICORNS_TAG)
    }

    override val name = "apricorn_harvester"
    override val targetCategory = BlockCategory.APRICORN
    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
        isEnabled() && (config.typeHarvestsApricorns.name in types
            || config.apricornHarvesters.any { it.equals(species, ignoreCase = true) })

    override fun isEnabled() = config.apricornHarvestersEnabled
    override fun isEligible(pokemonEntity: PokemonEntity) =
        CobbleworkersTypeUtils.isAllowedByType(config.typeHarvestsApricorns, pokemonEntity)
            || CobbleworkersTypeUtils.isDesignatedBySpecies(pokemonEntity, config.apricornHarvesters)

    override fun isTargetReady(world: World, pos: BlockPos) =
        world.getBlockState(pos).get(ApricornBlock.AGE) == ApricornBlock.MAX_AGE

    override fun harvest(world: World, targetPos: BlockPos, pokemonEntity: PokemonEntity) {
        if (!world.getBlockState(targetPos).isIn(APRICORNS_TAG)) return
        harvestWithLoot(world, targetPos, pokemonEntity) { w, p, state ->
            w.setBlockState(p, state.with(ApricornBlock.AGE, 0), Block.NOTIFY_ALL)
        }
    }
}
