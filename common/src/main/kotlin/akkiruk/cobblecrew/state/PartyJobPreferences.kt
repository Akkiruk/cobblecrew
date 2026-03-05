/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.state

import akkiruk.cobblecrew.CobbleCrew
import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player party job preferences. Each player can block specific jobs
 * or entire categories from running on their party Pokémon.
 * Persisted to config/cobblecrew/party-preferences.json.
 *
 * Blocking is checked at job selection time — blocked jobs are silently
 * skipped, so the Pokémon picks the next eligible job instead.
 */
object PartyJobPreferences {

    private val configDir: Path = Paths.get("config", "cobblecrew")
    private val file: File get() = configDir.resolve("party-preferences.json").toFile()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val mapType = object : TypeToken<Map<String, PlayerPrefs>>() {}.type

    /** Per-player preference data. */
    data class PlayerPrefs(
        val blockedJobs: MutableSet<String> = mutableSetOf(),
        val blockedCategories: MutableSet<String> = mutableSetOf(),
        val optedOut: Boolean = false,
    )

    private val playerPrefs = ConcurrentHashMap<UUID, PlayerPrefs>()

    // ── Query API ────────────────────────────────────────────────────

    /**
     * Returns true if this job is blocked for the given player's party Pokémon.
     * Checks both player-level blocks AND global config-level blocks.
     */
    fun isBlocked(playerId: UUID, jobName: String, jobCategory: String): Boolean {
        val globalConfig = CobbleCrewConfigHolder.config.party
        if (jobName in globalConfig.blockedJobs) return true
        if (jobCategory in globalConfig.blockedCategories) return true

        val prefs = playerPrefs[playerId] ?: return false
        if (jobName in prefs.blockedJobs) return true
        if (jobCategory in prefs.blockedCategories) return true
        return false
    }

    fun isOptedOut(playerId: UUID): Boolean =
        playerPrefs[playerId]?.optedOut == true

    fun getPrefs(playerId: UUID): PlayerPrefs =
        playerPrefs.getOrPut(playerId) { PlayerPrefs() }

    // ── Mutation API ─────────────────────────────────────────────────

    fun blockJob(playerId: UUID, jobName: String) {
        getPrefs(playerId).blockedJobs.add(jobName.lowercase())
        save()
    }

    fun allowJob(playerId: UUID, jobName: String) {
        getPrefs(playerId).blockedJobs.remove(jobName.lowercase())
        save()
    }

    fun blockCategory(playerId: UUID, category: String) {
        getPrefs(playerId).blockedCategories.add(category.lowercase())
        save()
    }

    fun allowCategory(playerId: UUID, category: String) {
        getPrefs(playerId).blockedCategories.remove(category.lowercase())
        save()
    }

    fun setOptedOut(playerId: UUID, optedOut: Boolean) {
        val prefs = getPrefs(playerId)
        val updated = PlayerPrefs(prefs.blockedJobs, prefs.blockedCategories, optedOut)
        playerPrefs[playerId] = updated
        save()
    }

    fun resetPlayer(playerId: UUID) {
        playerPrefs.remove(playerId)
        save()
    }

    // ── Persistence ──────────────────────────────────────────────────

    fun load() {
        val f = file
        if (!f.exists()) {
            CobbleCrew.LOGGER.info("[CobbleCrew] No party preferences file, starting fresh")
            return
        }

        try {
            val raw: Map<String, PlayerPrefs> = gson.fromJson(f.reader(), mapType) ?: emptyMap()
            playerPrefs.clear()
            for ((key, prefs) in raw) {
                try {
                    playerPrefs[UUID.fromString(key)] = prefs
                } catch (_: IllegalArgumentException) {
                    CobbleCrew.LOGGER.warn("[CobbleCrew] Invalid UUID in party preferences: $key")
                }
            }
            CobbleCrew.LOGGER.info("[CobbleCrew] Loaded party preferences for ${playerPrefs.size} players")
        } catch (e: Exception) {
            CobbleCrew.LOGGER.error("[CobbleCrew] Failed to load party preferences", e)
        }
    }

    fun save() {
        try {
            configDir.toFile().mkdirs()
            val serializable = playerPrefs.entries.associate { (uuid, prefs) -> uuid.toString() to prefs }
            file.writeText(gson.toJson(serializable))
        } catch (e: Exception) {
            CobbleCrew.LOGGER.error("[CobbleCrew] Failed to save party preferences", e)
        }
    }
}
