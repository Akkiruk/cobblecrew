/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.enums

/**
 * Controls how long a Pokémon pauses at its target before starting work.
 * Gathering/combat jobs keep the full animation; production and support jobs skip it.
 */
enum class ArrivalStyle(val delayTicks: Long) {
    /** No delay — work begins the tick the Pokémon arrives. */
    INSTANT(0L),
    /** Short pause (0.5s) for visual feedback without feeling sluggish. */
    QUICK(10L),
    /** Full arrival animation (1.5s) — default for gathering/combat. */
    ANIMATED(30L),
}
