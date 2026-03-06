/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.commands

import akkiruk.cobblecrew.CobbleCrew
import akkiruk.cobblecrew.jobs.WorkerRegistry
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.concurrent.CompletableFuture

internal object MoveAnalysisCommand {

    fun run(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        source.sendFeedback({ info("[CobbleCrew] Analyzing all species learnsets — this may take a moment...") }, false)

        CompletableFuture.runAsync {
            try {
                val report = buildReport()
                val url = uploadToMclogs(report)
                source.server.execute {
                    if (url != null) {
                        val clickable = Text.literal(url).setStyle(
                            Style.EMPTY
                                .withColor(Formatting.AQUA)
                                .withUnderline(true)
                                .withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to open")))
                        )
                        source.sendFeedback({
                            success("Move analysis uploaded: ").append(clickable)
                        }, false)
                    } else {
                        source.sendFeedback({ error("Upload failed — report saved to server log instead.") }, false)
                        CobbleCrew.LOGGER.info(report)
                    }
                }
            } catch (e: Exception) {
                CobbleCrew.LOGGER.error("[CobbleCrew] Move analysis failed", e)
                source.server.execute {
                    source.sendFeedback({ error("Move analysis failed: ${e.message}") }, false)
                }
            }
        }
        return 1
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildReport(): String {
        val sb = StringBuilder()
        sb.appendLine("═══ CobbleCrew Move Analysis ═══")
        sb.appendLine()

        val jobMoves = mutableMapOf<String, Set<String>>()
        val moveToJobs = mutableMapOf<String, MutableList<String>>()
        for (worker in WorkerRegistry.workers) {
            val moves = try {
                val field = worker.javaClass.getDeclaredField("qualifyingMoves")
                field.isAccessible = true
                (field.get(worker) as? Set<*>)?.filterIsInstance<String>()?.map { it.lowercase() }?.toSet()
            } catch (_: Exception) { null }
            if (moves != null && moves.isNotEmpty()) {
                jobMoves[worker.name] = moves
                for (m in moves) {
                    moveToJobs.getOrPut(m) { mutableListOf() }.add(worker.name)
                }
            }
        }

        val moveSpecies = mutableMapOf<String, MutableSet<String>>()
        var speciesCount = 0

        try {
            val psClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies")
            val instance = psClass.kotlin.objectInstance ?: throw Exception("No PokemonSpecies instance")

            val getImpl = psClass.getMethod("getImplemented")
            val implemented = getImpl.invoke(instance) as? List<*> ?: emptyList<Any>()

            for (species in implemented) {
                if (species == null) continue
                speciesCount++
                val speciesName = try {
                    species.javaClass.getMethod("getName").invoke(species) as? String ?: "unknown"
                } catch (_: Exception) { "unknown" }

                val learnset = try {
                    species.javaClass.getMethod("getMoves").invoke(species) ?: continue
                } catch (_: Exception) { continue }

                val allTemplates = mutableSetOf<Any>()
                for (getter in listOf("getTmMoves", "getTutorMoves", "getEggMoves", "getEvolutionMoves", "getFormChangeMoves")) {
                    try {
                        val result = learnset.javaClass.getMethod(getter).invoke(learnset)
                        when (result) {
                            is Collection<*> -> result.filterNotNull().forEach { allTemplates.add(it) }
                        }
                    } catch (_: Exception) {}
                }
                try {
                    val levelMoves = learnset.javaClass.getMethod("getLevelUpMoves").invoke(learnset)
                    if (levelMoves is Map<*, *>) {
                        for ((_, templates) in levelMoves) {
                            if (templates is Collection<*>) {
                                templates.filterNotNull().forEach { allTemplates.add(it) }
                            }
                        }
                    }
                } catch (_: Exception) {}

                for (template in allTemplates) {
                    val moveName = try {
                        (template.javaClass.getMethod("getName").invoke(template) as? String)?.lowercase()
                    } catch (_: Exception) { null }
                    if (moveName != null) {
                        moveSpecies.getOrPut(moveName) { mutableSetOf() }.add(speciesName)
                    }
                }
            }
        } catch (e: Exception) {
            sb.appendLine("ERROR: Could not access Cobblemon species registry: ${e.message}")
            sb.appendLine()
            return sb.toString()
        }

        val sorted = moveSpecies.entries.sortedByDescending { it.value.size }

        sb.appendLine("Species analyzed: $speciesCount")
        sb.appendLine("Total learnable moves: ${sorted.size}")
        sb.appendLine("Total CobbleCrew jobs: ${jobMoves.size}")
        sb.appendLine()

        val conflicts = moveToJobs.filter { it.value.size > 1 }
        if (conflicts.isNotEmpty()) {
            sb.appendLine("=== MOVE CONFLICTS (used by multiple jobs) ===")
            for ((move, jobs) in conflicts.entries.sortedBy { it.key }) {
                val count = moveSpecies[move]?.size ?: 0
                sb.appendLine("  $move ($count species) -> ${jobs.joinToString(", ")}")
            }
            sb.appendLine()
        }

        sb.appendLine("=== MOVES USED BY COBBLECREW JOBS ===")
        for ((move, jobs) in moveToJobs.entries.sortedBy { it.key }) {
            val count = moveSpecies[move]?.size ?: 0
            sb.appendLine("  $move ($count species) -> ${jobs.joinToString(", ")}")
        }
        sb.appendLine()

        sb.appendLine("=== ALL MOVES BY SPECIES COUNT ===")
        sb.appendLine("(* = used by a CobbleCrew job)")
        for ((move, species) in sorted) {
            val marker = if (move in moveToJobs) " *" else ""
            sb.appendLine("  $move: ${species.size}$marker")
        }

        return sb.toString()
    }
}
