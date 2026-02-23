/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.config

import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry

@Config(name = "cobblecrew")
class CobbleCrewConfig : ConfigData {

    @ConfigEntry.Gui.CollapsibleObject
    var general = GeneralGroup()

    @ConfigEntry.Gui.CollapsibleObject
    var party = PartyGroup()

    @ConfigEntry.Gui.CollapsibleObject
    var debug = DebugGroup()

    class GeneralGroup {
        @ConfigEntry.BoundedDiscrete(min = 10, max = 100)
        var blocksScannedPerTick = 50
        @ConfigEntry.BoundedDiscrete(min = 1, max = 10)
        var searchRadius = 8
        @ConfigEntry.BoundedDiscrete(min = 1, max = 8)
        var searchHeight = 5
    }

    class PartyGroup {
        /** Enable party Pokémon jobs globally. */
        var enabled = true

        /** Ticks between party area scans (lower = more responsive, higher = less CPU). */
        @ConfigEntry.BoundedDiscrete(min = 20, max = 200)
        var scanIntervalTicks = 40

        /** Horizontal radius for party area scans (blocks). */
        @ConfigEntry.BoundedDiscrete(min = 3, max = 12)
        var searchRadius = 6

        /** Vertical radius for party area scans (blocks). */
        @ConfigEntry.BoundedDiscrete(min = 2, max = 8)
        var searchHeight = 3

        /** Max distance a party worker can travel from the player. */
        @ConfigEntry.BoundedDiscrete(min = 8, max = 64)
        var maxWorkDistance = 32

        /** Auto-teleport party workers to player if they fall behind. */
        var teleportIfTooFar = true

        /** Distance at which auto-teleport triggers. */
        @ConfigEntry.BoundedDiscrete(min = 32, max = 128)
        var teleportDistance = 64
    }

    class DebugGroup {
        /** Master toggle — must be true for any debug logging to appear. */
        var enabled = false

        /** Extra detail: full UUIDs, verbose position data, cooldown ticks. */
        var verbose = false

        /**
         * Filter logs to a specific Pokémon species name (e.g. "charizard")
         * or UUID prefix (e.g. "3fa85f64"). Empty = log all.
         */
        var pokemonFilter = ""

        // Per-category toggles (all default true — gated by master toggle)
        var logProfile = true
        var logDispatch = true
        var logNavigation = true
        var logHarvest = true
        var logDeposit = true
        var logProduction = true
        var logProcessing = true
        var logPlacement = true
        var logDefense = true
        var logSupport = true
        var logScanner = true
        var logCleanup = true
    }
}