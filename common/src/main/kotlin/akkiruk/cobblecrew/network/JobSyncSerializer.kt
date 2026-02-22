/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.network

import akkiruk.cobblecrew.api.CobbleCrewApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Serializes job rules into a compact JSON byte array for network transmission.
 */
object JobSyncSerializer {

    private val gson = Gson()

    fun serialize(rules: List<CobbleCrewApi.JobRule>): ByteArray {
        return gson.toJson(rules).toByteArray(Charsets.UTF_8)
    }

    fun deserialize(data: ByteArray): List<CobbleCrewApi.JobRule> {
        val json = String(data, Charsets.UTF_8)
        val type = object : TypeToken<List<CobbleCrewApi.JobRule>>() {}.type
        return gson.fromJson(json, type)
    }
}
