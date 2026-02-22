/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.enums

/**
 * Priority tiers for job eligibility.
 * Higher priority = more specific match = preferred assignment.
 */
enum class WorkerPriority {
    /** Pokémon has ALL required moves for a combo job */
    COMBO,
    /** Pokémon has a qualifying move for the job */
    MOVE,
    /** Pokémon's species is in the job's designated species list */
    SPECIES,
    /** Pokémon's type matches the job's fallback type */
    TYPE,
}
