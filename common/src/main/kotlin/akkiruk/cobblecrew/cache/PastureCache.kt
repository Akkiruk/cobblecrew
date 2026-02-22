/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.cache

import akkiruk.cobblecrew.enums.BlockCategory
import net.minecraft.util.math.BlockPos

data class PastureCache(
    val targetsByCategory: MutableMap<BlockCategory, MutableSet<BlockPos>> =
        BlockCategory.entries.associateWith { mutableSetOf<BlockPos>() }.toMutableMap(),
)