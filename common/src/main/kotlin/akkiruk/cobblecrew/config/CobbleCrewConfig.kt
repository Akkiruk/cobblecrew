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

    class GeneralGroup {
        @ConfigEntry.BoundedDiscrete(min = 10, max = 30)
        var blocksScannedPerTick = 15
        @ConfigEntry.BoundedDiscrete(min = 1, max = 10)
        var searchRadius = 8
        @ConfigEntry.BoundedDiscrete(min = 1, max = 8)
        var searchHeight = 5
    }
}