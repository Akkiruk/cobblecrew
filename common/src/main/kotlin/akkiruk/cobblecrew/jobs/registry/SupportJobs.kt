/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.enums.JobImportance
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.jobs.dsl.SupportJob
import net.minecraft.entity.effect.StatusEffectCategory
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes

/**
 * Standard support jobs (F1–F8, F10). Apply positive status effects to nearby players.
 * F9 (Cleanser), F11 (Full Restore), F12 (Aura Master) require custom implementations.
 */
object SupportJobs {

    val HEALER = SupportJob(
        name = "healer",
        qualifyingMoves = setOf("healbell"),
        fallbackSpecies = listOf("Chansey", "Blissey", "Happiny", "Audino"),
        importance = JobImportance.CRITICAL,
        particle = ParticleTypes.HEART,
        statusEffect = StatusEffects.REGENERATION,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
        requiresDamage = true,
        workBoostPercent = 5,
        partyEnabled = true,
    )

    val SPEED_BOOSTER = SupportJob(
        name = "speed_booster",
        qualifyingMoves = setOf("tailwind"),
        particle = ParticleTypes.CLOUD,
        statusEffect = StatusEffects.SPEED,
        defaultDurationSeconds = 30,
        effectAmplifier = 1,
        workBoostPercent = 15,
        partyEnabled = true,
    )

    val STRENGTH_BOOSTER = SupportJob(
        name = "strength_booster",
        qualifyingMoves = setOf("charge"),
        particle = ParticleTypes.CRIT,
        statusEffect = StatusEffects.STRENGTH,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
        workBoostPercent = 5,
        partyEnabled = true,
    )

    val RESISTANCE_PROVIDER = SupportJob(
        name = "resistance_provider",
        qualifyingMoves = setOf("defensecurl"),
        particle = ParticleTypes.ENCHANT,
        statusEffect = StatusEffects.RESISTANCE,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
        workBoostPercent = 5,
        partyEnabled = true,
    )

    val HASTE_PROVIDER = SupportJob(
        name = "haste_provider",
        qualifyingMoves = setOf("honeclaws"),
        particle = ParticleTypes.ENCHANT,
        statusEffect = StatusEffects.HASTE,
        defaultDurationSeconds = 30,
        effectAmplifier = 1,
        workBoostPercent = 15,
        partyEnabled = true,
    )

    val JUMP_BOOSTER = SupportJob(
        name = "jump_booster",
        qualifyingMoves = setOf("bounce"),
        importance = JobImportance.LOW,
        particle = ParticleTypes.CLOUD,
        statusEffect = StatusEffects.JUMP_BOOST,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
        partyEnabled = true,
    )

    val NIGHT_VISION_PROVIDER = SupportJob(
        name = "night_vision_provider",
        qualifyingMoves = setOf("futuresight"),
        importance = JobImportance.LOW,
        particle = ParticleTypes.END_ROD,
        statusEffect = StatusEffects.NIGHT_VISION,
        defaultDurationSeconds = 60,
        effectAmplifier = 0,
        partyEnabled = true,
    )

    val WATER_BREATHER = SupportJob(
        name = "water_breather",
        qualifyingMoves = setOf("aquaring"),
        importance = JobImportance.LOW,
        particle = ParticleTypes.BUBBLE,
        statusEffect = StatusEffects.WATER_BREATHING,
        defaultDurationSeconds = 60,
        effectAmplifier = 0,
        partyEnabled = true,
    )

    val HUNGER_RESTORER = SupportJob(
        name = "hunger_restorer",
        qualifyingMoves = setOf("swallow"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        statusEffect = StatusEffects.SATURATION,
        defaultDurationSeconds = 10,
        effectAmplifier = 1,
        workBoostPercent = 5,
        partyEnabled = true,
    )

    // ── Full Restore (moved from combo, now single-move support) ────
    val FULL_RESTORE = object : SupportJob(
        name = "full_restore",
        category = "support",
        qualifyingMoves = setOf("lifedew"),
        importance = JobImportance.CRITICAL,
        particle = ParticleTypes.HEART,
        statusEffect = StatusEffects.REGENERATION,
        defaultDurationSeconds = 30,
        effectAmplifier = 1,
        priority = WorkerPriority.MOVE,
        workBoostPercent = 10,
        partyEnabled = true,
    ) {
        override fun applyEffect(player: PlayerEntity) {
            player.statusEffects
                .filter { it.effectType.value().category == StatusEffectCategory.HARMFUL }
                .map { it.effectType }
                .toList()
                .forEach { player.removeStatusEffect(it) }
            super.applyEffect(player)
        }
    }

    fun register() {
        WorkerRegistry.registerAll(
            HEALER, SPEED_BOOSTER, STRENGTH_BOOSTER, RESISTANCE_PROVIDER,
            HASTE_PROVIDER, JUMP_BOOSTER, NIGHT_VISION_PROVIDER,
            WATER_BREATHER, HUNGER_RESTORER, FULL_RESTORE,
        )
    }
}
