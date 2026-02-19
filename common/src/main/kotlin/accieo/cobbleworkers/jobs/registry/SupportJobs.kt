/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.registry

import accieo.cobbleworkers.jobs.WorkerRegistry
import accieo.cobbleworkers.jobs.dsl.SupportJob
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.particle.ParticleTypes

/**
 * Standard support jobs (F1–F8, F10). Apply positive status effects to nearby players.
 * F9 (Cleanser), F11 (Full Restore), F12 (Aura Master) require custom implementations.
 */
object SupportJobs {

    val HEALER = SupportJob(
        name = "healer",
        qualifyingMoves = setOf("wish", "recover", "moonlight", "healpulse", "lifedew"),
        particle = ParticleTypes.HEART,
        statusEffect = StatusEffects.REGENERATION,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
    )

    val SPEED_BOOSTER = SupportJob(
        name = "speed_booster",
        qualifyingMoves = setOf("tailwind", "agility", "extremespeed"),
        particle = ParticleTypes.CLOUD,
        statusEffect = StatusEffects.SPEED,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
    )

    val STRENGTH_BOOSTER = SupportJob(
        name = "strength_booster",
        qualifyingMoves = setOf("howl", "swordsdance", "coaching"),
        typeGatedMoves = mapOf("helpinghand" to "FIGHTING"),
        particle = ParticleTypes.CRIT,
        statusEffect = StatusEffects.STRENGTH,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
    )

    val RESISTANCE_PROVIDER = SupportJob(
        name = "resistance_provider",
        qualifyingMoves = setOf("irondefense", "barrier", "shelter"),
        particle = ParticleTypes.ENCHANT,
        statusEffect = StatusEffects.RESISTANCE,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
    )

    val HASTE_PROVIDER = SupportJob(
        name = "haste_provider",
        qualifyingMoves = setOf("nastyplot", "calmmind", "focusenergy", "workup"),
        particle = ParticleTypes.ENCHANT,
        statusEffect = StatusEffects.HASTE,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
    )

    val JUMP_BOOSTER = SupportJob(
        name = "jump_booster",
        qualifyingMoves = setOf("bounce", "highjumpkick", "jumpkick"),
        particle = ParticleTypes.CLOUD,
        statusEffect = StatusEffects.JUMP_BOOST,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
    )

    val NIGHT_VISION_PROVIDER = SupportJob(
        name = "night_vision_provider",
        qualifyingMoves = setOf("miracleeye", "mindreader"),
        fallbackSpecies = listOf("Noctowl", "Umbreon", "Luxray"),
        particle = ParticleTypes.END_ROD,
        statusEffect = StatusEffects.NIGHT_VISION,
        defaultDurationSeconds = 60,
        effectAmplifier = 0,
    )

    val WATER_BREATHER = SupportJob(
        name = "water_breather",
        qualifyingMoves = setOf("aquaring", "raindance"),
        particle = ParticleTypes.BUBBLE,
        statusEffect = StatusEffects.WATER_BREATHING,
        defaultDurationSeconds = 60,
        effectAmplifier = 0,
    )

    val HUNGER_RESTORER = SupportJob(
        name = "hunger_restorer",
        qualifyingMoves = setOf("swallow", "slackoff"),
        fallbackSpecies = listOf("Snorlax", "Munchlax", "Miltank"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        statusEffect = StatusEffects.SATURATION,
        defaultDurationSeconds = 5,
        effectAmplifier = 0,
    )

    fun register() {
        WorkerRegistry.registerAll(
            HEALER, SPEED_BOOSTER, STRENGTH_BOOSTER, RESISTANCE_PROVIDER,
            HASTE_PROVIDER, JUMP_BOOSTER, NIGHT_VISION_PROVIDER,
            WATER_BREATHER, HUNGER_RESTORER,
        )
    }
}
