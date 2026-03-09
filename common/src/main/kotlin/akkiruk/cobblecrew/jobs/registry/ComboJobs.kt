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
import akkiruk.cobblecrew.jobs.dsl.ProcessingJob
import akkiruk.cobblecrew.jobs.dsl.ProductionJob
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
            BLAST_SMELTER,
            MASTER_CRAFTER,
            DYNAMO_CORE,
            BIOFUEL_REACTOR,
            ENCHANTED_INK_MAKER,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  FACTORY COMBO JOBS — Elite Pokémon with multiple moves needed
// ═══════════════════════════════════════════════════════════════════

/**
 * Blast Smelter — supercharged smelting. Processes any raw ore at 2× output.
 * Requires both temperflare + earthquake (earthquake cracks ore, temperflare melts).
 */
private val BLAST_SMELTER = object : ProcessingJob(
    name = "blast_smelter",
    category = "combo",
    qualifyingMoves = setOf("temperflare", "earthquake"),
    particle = ParticleTypes.LAVA,
    priority = WorkerPriority.COMBO,
    isCombo = true,
    inputCheck = { stack ->
        stack.item in setOf(Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER)
    },
    transformFn = { input ->
        when (input.item) {
            Items.RAW_IRON -> listOf(ItemStack(Items.IRON_INGOT, input.count * 2))
            Items.RAW_GOLD -> listOf(ItemStack(Items.GOLD_INGOT, input.count * 2))
            Items.RAW_COPPER -> listOf(ItemStack(Items.COPPER_INGOT, input.count * 2))
            else -> emptyList()
        }
    },
) {
    override val minExtractAmount: Int = 1
}

/**
 * Master Crafter — one Pokémon, any basic recipe. Turns ingots into tools.
 * Requires irondefense + metalclaw (defense shapes, claw sharpens).
 */
private val MASTER_CRAFTER = object : ProcessingJob(
    name = "master_crafter",
    category = "combo",
    qualifyingMoves = setOf("irondefense", "metalclaw"),
    particle = ParticleTypes.CRIT,
    priority = WorkerPriority.COMBO,
    isCombo = true,
    inputCheck = { stack ->
        stack.item in setOf(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND)
    },
    transformFn = { input ->
        when (input.item) {
            Items.IRON_INGOT -> when {
                input.count >= 5 -> listOf(
                    ItemStack(Items.IRON_HELMET, 1),
                    ItemStack(Items.IRON_INGOT, input.count - 5),
                ).filter { !it.isEmpty }
                input.count >= 3 -> listOf(
                    ItemStack(Items.IRON_PICKAXE, 1),
                    ItemStack(Items.IRON_INGOT, input.count - 3),
                ).filter { !it.isEmpty }
                else -> listOf(ItemStack(Items.IRON_NUGGET, input.count * 9))
            }
            Items.GOLD_INGOT -> when {
                input.count >= 4 -> listOf(
                    ItemStack(Items.CLOCK, 1),
                    ItemStack(Items.GOLD_INGOT, input.count - 4),
                ).filter { !it.isEmpty }
                else -> listOf(ItemStack(Items.GOLD_NUGGET, input.count * 9))
            }
            Items.DIAMOND -> when {
                input.count >= 3 -> listOf(
                    ItemStack(Items.DIAMOND_PICKAXE, 1),
                    ItemStack(Items.DIAMOND, input.count - 3),
                ).filter { !it.isEmpty }
                input.count >= 2 -> listOf(
                    ItemStack(Items.DIAMOND_SWORD, 1),
                    ItemStack(Items.DIAMOND, input.count - 2),
                ).filter { !it.isEmpty }
                else -> listOf(ItemStack(Items.DIAMOND, input.count))
            }
            else -> emptyList()
        }
    },
) {
    override val minExtractAmount: Int = 2
}

/**
 * Dynamo Core — Electric + Fire = power generator.
 * Produces redstone blocks from nothing (combines static + heat energy).
 */
private val DYNAMO_CORE = ProductionJob(
    name = "dynamo_core",
    category = "combo",
    qualifyingMoves = setOf("discharge", "flamethrower"),
    defaultCooldownSeconds = 200,
    priority = WorkerPriority.COMBO,
    particle = ParticleTypes.ELECTRIC_SPARK,
    isCombo = true,
    output = { _, _ -> listOf(ItemStack(Items.REDSTONE_BLOCK, 1)) },
)

/**
 * Biofuel Reactor — Grass + Fire = sustainable charcoal factory.
 * Produces charcoal + bone meal from photosynthesis + combustion.
 */
private val BIOFUEL_REACTOR = ProductionJob(
    name = "biofuel_reactor",
    category = "combo",
    qualifyingMoves = setOf("synthesis", "flameburst"),
    defaultCooldownSeconds = 120,
    priority = WorkerPriority.COMBO,
    particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
    isCombo = true,
    output = { _, _ ->
        listOf(
            ItemStack(Items.CHARCOAL, 4),
            ItemStack(Items.BONE_MEAL, 2),
        )
    },
)

/**
 * Enchanted Ink Maker — Psychic + Dark = mystical ink for enchanting.
 * Produces experience bottles (a scarce crafting enabler).
 */
private val ENCHANTED_INK_MAKER = ProductionJob(
    name = "enchanted_ink_maker",
    category = "combo",
    qualifyingMoves = setOf("psychic", "darkpulse"),
    defaultCooldownSeconds = 300,
    priority = WorkerPriority.COMBO,
    particle = ParticleTypes.ENCHANT,
    isCombo = true,
    output = { _, _ -> listOf(ItemStack(Items.EXPERIENCE_BOTTLE, 1)) },
)
