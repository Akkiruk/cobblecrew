/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.api

import accieo.cobbleworkers.config.JobConfigManager
import accieo.cobbleworkers.jobs.WorkerRegistry

/**
 * Public API for Cobbleworkers. Other mods can depend on this at compile time
 * to query job eligibility rules directly.
 */
object CobbleworkersApi {

    data class JobRule(
        val id: String,
        val displayName: String,
        val description: String,
        val enabled: Boolean,
        val requiredType: String?,
        val designatedSpecies: List<String>,
        val requiredMoves: List<String>,
        val requiredAbility: String?,
        val fallbackSpecies: List<String>,
        val priority: String,
    )

    /**
     * Returns the current list of all job eligibility rules from the WorkerRegistry.
     */
    fun getJobRules(): List<JobRule> {
        val seen = mutableSetOf<String>()
        return WorkerRegistry.workers.mapNotNull { worker ->
            if (!seen.add(worker.name)) return@mapNotNull null

            val config = JobConfigManager.get(worker.name)
            if (config.qualifyingMoves.isEmpty()
                && config.fallbackType.isEmpty()
                && config.fallbackSpecies.isEmpty()
            ) return@mapNotNull null

            JobRule(
                id = worker.name,
                displayName = formatName(worker.name),
                description = "",
                enabled = config.enabled,
                requiredType = config.fallbackType.takeIf { it.isNotEmpty() },
                designatedSpecies = emptyList(),
                requiredMoves = config.qualifyingMoves,
                requiredAbility = null,
                fallbackSpecies = config.fallbackSpecies,
                priority = worker.priority.name,
            )
        }
    }

    private fun formatName(snakeCase: String): String =
        snakeCase.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    /**
     * Generates a full diagnostic report of all registered jobs and their eligibility data.
     */
    fun generateDiagnosticReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Cobbleworkers Diagnostic Report ===")
        sb.appendLine("Generated: ${java.time.Instant.now()}")
        sb.appendLine()

        sb.appendLine("--- Worker Registry ---")
        sb.appendLine("Total registered workers: ${WorkerRegistry.workers.size}")
        sb.appendLine()

        for (worker in WorkerRegistry.workers) {
            val config = JobConfigManager.get(worker.name)
            sb.appendLine("  [${worker.name}]")
            sb.appendLine("    Priority: ${worker.priority.name}")
            sb.appendLine("    Enabled: ${config.enabled}")
            sb.appendLine("    Moves: ${config.qualifyingMoves.ifEmpty { listOf("(none)") }.joinToString(", ")}")
            sb.appendLine("    Fallback Type: ${config.fallbackType.ifEmpty { "(none)" }}")
            sb.appendLine("    Fallback Species: ${config.fallbackSpecies.ifEmpty { listOf("(none)") }.joinToString(", ")}")
            sb.appendLine()
        }

        sb.appendLine("--- API Rules ---")
        val rules = getJobRules()
        sb.appendLine("Total API rules: ${rules.size}")
        sb.appendLine()

        for (rule in rules) {
            sb.appendLine("  [${rule.id}] ${rule.displayName}")
            sb.appendLine("    Enabled: ${rule.enabled}")
            sb.appendLine("    Priority: ${rule.priority}")
            sb.appendLine("    Required Type: ${rule.requiredType ?: "(none)"}")
            sb.appendLine("    Required Moves: ${rule.requiredMoves.ifEmpty { listOf("(none)") }.joinToString(", ")}")
            sb.appendLine("    Required Ability: ${rule.requiredAbility ?: "(none)"}")
            sb.appendLine("    Fallback Species: ${rule.fallbackSpecies.ifEmpty { listOf("(none)") }.joinToString(", ")}")
            sb.appendLine()
        }

        sb.appendLine("--- JobConfigManager ---")
        val allNames = JobConfigManager.allJobNames()
        sb.appendLine("Total config entries: ${allNames.size}")
        sb.appendLine("Job names: ${allNames.sorted().joinToString(", ")}")

        return sb.toString()
    }
}
