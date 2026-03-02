/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.enums

/**
 * How urgently a job should be performed, independent of eligibility tier.
 * Within each [WorkerPriority] tier, higher-importance jobs are tried first.
 * Same-importance jobs are shuffled for fairness.
 *
 * Enum order = dispatch order (first entry = highest priority).
 */
enum class JobImportance {
    /** Immediate threats — defense, healing, fire dousing */
    CRITICAL,
    /** Time-sensitive resources — harvesting mature crops, collecting expiring items */
    HIGH,
    /** Core work cycle — processing, production, combat buffs */
    STANDARD,
    /** Maintenance tasks — planting, growth, environmental modification */
    LOW,
    /** Filler work — consolidation, cleanup */
    BACKGROUND,
}
