/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.registry

import accieo.cobbleworkers.jobs.WorkerRegistry
import accieo.cobbleworkers.jobs.dsl.DefenseJob
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import kotlin.math.sqrt

/**
 * Defense jobs (E1–E7). Interact with hostile mobs near the pasture.
 */
object DefenseJobs {

    val GUARD = DefenseJob(
        name = "guard",
        qualifyingMoves = setOf("bite", "crunch", "closecombat"),
        fallbackSpecies = listOf("Growlithe", "Arcanine", "Lucario", "Lycanroc"),
        particle = ParticleTypes.CRIT,
        effectFn = { world, pokemon, target ->
            (world as? ServerWorld)?.let { sw ->
                target.damage(sw.damageSources.mobAttack(pokemon), 4.0f)
            }
        },
    )

    val SENTRY = DefenseJob(
        name = "sentry",
        qualifyingMoves = setOf("detect", "foresight", "odorsleuth", "meanlook"),
        fallbackSpecies = listOf("Persian", "Noctowl", "Watchog"),
        particle = ParticleTypes.ELECTRIC_SPARK,
        effectFn = { world, _, target ->
            (world as? ServerWorld)?.spawnParticles(
                ParticleTypes.ELECTRIC_SPARK,
                target.x, target.boundingBox.maxY, target.z,
                15, 0.5, 0.5, 0.5, 0.1,
            )
        },
    )

    val REPELLER = DefenseJob(
        name = "repeller",
        qualifyingMoves = setOf("roar", "whirlwind", "dragontail", "circlethrow"),
        fallbackSpecies = listOf("Gyarados", "Salamence"),
        particle = ParticleTypes.POOF,
        effectFn = { _, pokemon, target ->
            val dx = target.x - pokemon.x
            val dz = target.z - pokemon.z
            val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1)
            target.addVelocity(dx / dist * 2.0, 0.5, dz / dist * 2.0)
            target.velocityModified = true
            target.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 200, 1))
        },
    )

    val FEARMONGER = DefenseJob(
        name = "fearmonger",
        qualifyingMoves = setOf("scaryface", "glare", "snarl", "screech"),
        fallbackSpecies = listOf("Gyarados", "Arcanine"),
        particle = ParticleTypes.WITCH,
        effectFn = { _, pokemon, target ->
            val dx = target.x - pokemon.x
            val dz = target.z - pokemon.z
            val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1)
            target.addVelocity(dx / dist * 4.0, 0.8, dz / dist * 4.0)
            target.velocityModified = true
            target.addStatusEffect(StatusEffectInstance(StatusEffects.WEAKNESS, 400, 2))
        },
    )

    val FIRE_TRAP = DefenseJob(
        name = "fire_trap",
        qualifyingMoves = setOf("flamewheel", "searingshot", "firelash"),
        fallbackType = "FIRE",
        particle = ParticleTypes.FLAME,
        effectFn = { _, _, target ->
            target.setOnFireFor(5f)
        },
    )

    val POISON_TRAP = DefenseJob(
        name = "poison_trap",
        qualifyingMoves = setOf("toxicspikes", "sludgewave", "venoshock"),
        fallbackType = "POISON",
        particle = ParticleTypes.ITEM_SLIME,
        effectFn = { _, _, target ->
            target.addStatusEffect(StatusEffectInstance(StatusEffects.POISON, 200, 1))
        },
    )

    val ICE_TRAP = DefenseJob(
        name = "ice_trap",
        qualifyingMoves = setOf("blizzard", "freezedry", "glaciate"),
        fallbackType = "ICE",
        particle = ParticleTypes.SNOWFLAKE,
        effectFn = { _, _, target ->
            target.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 300, 3))
            target.addStatusEffect(StatusEffectInstance(StatusEffects.MINING_FATIGUE, 300, 2))
        },
    )

    fun register() {
        WorkerRegistry.registerAll(
            GUARD, SENTRY, REPELLER, FEARMONGER,
            FIRE_TRAP, POISON_TRAP, ICE_TRAP,
        )
    }
}
