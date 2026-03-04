/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.enums

/**
 * Lifecycle phase for a working Pokémon's current job cycle.
 * Managed by [BaseJob]'s state machine — never set manually by DSL jobs.
 */
enum class JobPhase {
    /** No active work — ready for job selection. */
    IDLE,
    /** Walking toward the target block/entity. */
    NAVIGATING,
    /** At target, playing work animation (30-tick delay). */
    ARRIVING,
    /** Executing the job action (harvest, produce, attack, etc.). */
    WORKING,
    /** Carrying items to a container or player. */
    DEPOSITING,
}
