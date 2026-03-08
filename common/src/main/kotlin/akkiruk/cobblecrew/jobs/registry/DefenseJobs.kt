/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.jobs.dsl.DefenseJob
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
        qualifyingMoves = setOf("furyattack"),
        particle = ParticleTypes.CRIT,
        partyEnabled = true,
        effectFn = { world, pokemon, target ->
            (world as? ServerWorld)?.let { sw ->
                target.damage(sw.damageSources.mobAttack(pokemon), 4.0f)
            }
        },
    )

    val REPELLER = DefenseJob(
        name = "repeller",
        qualifyingMoves = setOf("psychicnoise"),
        particle = ParticleTypes.POOF,
        partyEnabled = true,
        phase = WorkPhase.DEBUFFING,
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
        qualifyingMoves = setOf("poltergeist"),
        particle = ParticleTypes.WITCH,
        partyEnabled = true,
        phase = WorkPhase.DEBUFFING,
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
        qualifyingMoves = setOf("mysticalfire"),
        particle = ParticleTypes.FLAME,
        partyEnabled = true,
        effectFn = { _, _, target ->
            target.setOnFireFor(5f)
        },
    )

    fun register() {
        WorkerRegistry.registerAll(
            GUARD, REPELLER, FEARMONGER, FIRE_TRAP,
        )
    }
}
