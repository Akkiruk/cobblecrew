/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.config

import akkiruk.cobblecrew.CobbleCrew
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Loads and manages per-job config from JSON files under config/cobblecrew/.
 * Each category (gathering, production, etc.) has its own file.
 * Missing files are auto-generated with defaults from DSL job definitions.
 */
object JobConfigManager {
    private val configs = mutableMapOf<String, JobConfig>()
    private val configDir: Path = Paths.get("config", "cobblecrew")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val mapType = object : TypeToken<Map<String, JobConfig>>() {}.type

    /**
     * Schema version for job configs. Bump when qualifyingMoves or fallbackSpecies
     * defaults change in code, so stale disk configs get migrated automatically.
     */
    private const val CURRENT_SCHEMA_VERSION = 2

    /** Default configs registered by DSL jobs before load() is called. */
    private val defaults = mutableMapOf<String, MutableMap<String, JobConfig>>()

    /**
     * Registers default config for a job within a category.
     * Called by DSL builders during registration, before load().
     */
    fun registerDefault(category: String, jobName: String, config: JobConfig) {
        defaults.getOrPut(category) { mutableMapOf() }[jobName] = config.copy(schemaVersion = CURRENT_SCHEMA_VERSION)
    }

    /**
     * Loads all per-category JSON files. For each category with registered defaults,
     * creates the file if missing. Merges loaded configs with defaults.
     */
    fun load() {
        val dir = configDir.toFile()
        dir.mkdirs()

        // Generate/load each category that has registered defaults
        for ((category, categoryDefaults) in defaults) {
            val file = File(dir, "$category.json")
            loadCategoryFile(file, categoryDefaults)
        }

        // Also load any extra category files the admin may have created
        dir.listFiles { f -> f.extension == "json" }?.forEach { file ->
            val category = file.nameWithoutExtension
            if (category !in defaults) {
                loadCategoryFile(file, emptyMap())
            }
        }

        CobbleCrew.LOGGER.info("[CobbleCrew] Loaded ${configs.size} job configs from ${defaults.size} categories")
    }

    private fun loadCategoryFile(file: File, categoryDefaults: Map<String, JobConfig>) {
        if (!file.exists()) {
            if (categoryDefaults.isNotEmpty()) {
                file.writeText(gson.toJson(categoryDefaults))
                configs.putAll(categoryDefaults)
            }
            return
        }

        try {
            val loaded: Map<String, JobConfig> = gson.fromJson(file.reader(), mapType)
            // Merge: loaded values override defaults, then migrate stale schemas
            val merged = categoryDefaults.toMutableMap()
            merged.putAll(loaded)

            var dirty = merged.size > loaded.size
            for ((name, cfg) in merged.toMap()) {
                if (cfg.schemaVersion < CURRENT_SCHEMA_VERSION) {
                    val codeDef = categoryDefaults[name] ?: continue
                    merged[name] = cfg.copy(
                        qualifyingMoves = codeDef.qualifyingMoves,
                        fallbackSpecies = codeDef.fallbackSpecies,
                        fallbackType = codeDef.fallbackType,
                        schemaVersion = CURRENT_SCHEMA_VERSION,
                    )
                    dirty = true
                }
            }
            configs.putAll(merged)

            if (dirty) {
                file.writeText(gson.toJson(merged))
            }
        } catch (e: Exception) {
            CobbleCrew.LOGGER.error("[CobbleCrew] Failed to load config file ${file.name}: ${e.message}")
            configs.putAll(categoryDefaults)
        }
    }

    /** Get config for a job by name. Returns default if not found. */
    fun get(jobName: String): JobConfig = configs[jobName] ?: JobConfig()

    /** Check if a specific job is enabled. */
    fun isEnabled(jobName: String): Boolean = get(jobName).enabled

    /** All loaded job names. */
    fun allJobNames(): Set<String> = configs.keys.toSet()

    /** Set enabled state for a job and persist to disk. */
    fun setEnabled(jobName: String, enabled: Boolean): Boolean {
        val current = configs[jobName] ?: return false
        configs[jobName] = current.copy(enabled = enabled)
        saveCategoryContaining(jobName)
        return true
    }

    /** Reload all job configs from disk. */
    fun reload() {
        configs.clear()
        load()
    }

    /** All job names grouped by category. */
    fun allJobsByCategory(): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        for ((category, categoryDefaults) in defaults) {
            result[category] = categoryDefaults.keys.toMutableList()
        }
        return result
    }

    private fun saveCategoryContaining(jobName: String) {
        for ((category, categoryDefaults) in defaults) {
            if (jobName in categoryDefaults) {
                saveCategory(category)
                return
            }
        }
    }

    private fun saveCategory(category: String) {
        val dir = configDir.toFile()
        val file = java.io.File(dir, "$category.json")
        val categoryJobs = defaults[category]?.keys ?: return
        val toSave = categoryJobs.associateWith { configs[it] ?: JobConfig() }
        file.writeText(gson.toJson(toSave))
    }
}
