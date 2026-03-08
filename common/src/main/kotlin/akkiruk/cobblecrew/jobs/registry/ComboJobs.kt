/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.jobs.dsl.GatheringJob
import akkiruk.cobblecrew.jobs.dsl.SupportJob
import akkiruk.cobblecrew.utilities.floodFillHarvest
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes

/**
 * Combo jobs — require ALL listed moves. COMBO priority (highest).
 * Uses `isCombo = true` on DSL classes for ALL-move eligibility.
 */
object ComboJobs {

    // ── CM13: Vein Miner ─────────────────────────────────────────────
    val VEIN_MINER = GatheringJob(
        name = "vein_miner",
        category = "combo",
        targetCategory = BlockCategory.ORE,
        qualifyingMoves = setOf("earthquake", "brickbreak"),
        particle = ParticleTypes.EXPLOSION,
        priority = WorkerPriority.COMBO,
        isCombo = true,
        partyEnabled = true,
        harvestOverride = { world, pos, _ -> floodFillHarvest(world, pos, 16, ItemStack(Items.DIAMOND_PICKAXE)) },
    )

    // ── F12: Aura Master (custom applyEffect) ───────────────────────
    val AURA_MASTER = object : SupportJob(
        name = "aura_master",
        category = "combo",
        qualifyingMoves = setOf("workup", "psychup"),
        particle = ParticleTypes.ENCHANT,
        statusEffect = StatusEffects.SPEED,
        defaultDurationSeconds = 30,
        effectAmplifier = 0,
        priority = WorkerPriority.COMBO,
        workBoostPercent = 20,
        isCombo = true,
        partyEnabled = true,
    ) {
        override fun applyEffect(player: PlayerEntity) {
            listOf(StatusEffects.SPEED, StatusEffects.STRENGTH, StatusEffects.HASTE, StatusEffects.RESISTANCE)
                .forEach { effect ->
                    if (!player.hasStatusEffect(effect)) {
                        player.addStatusEffect(StatusEffectInstance(effect, effectDurationTicks, 0))
                    }
                }
        }
    }

    fun register() {
        WorkerRegistry.registerAll(
            VEIN_MINER,
            AURA_MASTER,
        )
    }
}
