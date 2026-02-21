/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.enums

/**
 * Block-centric categories for the deferred scanner.
 * Multiple jobs can share a category (e.g. Overworld Logger and Tree Feller both use LOG_OVERWORLD).
 * One validator per category in BlockCategoryValidators.
 */
enum class BlockCategory {
    CONTAINER,

    // Cobblemon growables
    APRICORN, BERRY, MINT, TUMBLESTONE,

    // Vanilla/modded growables
    AMETHYST, NETHERWART,
    CROP_GRAIN, CROP_ROOT, CROP_BEET,
    SWEET_BERRY, PUMPKIN_MELON, COCOA, CHORUS, CAVE_VINE, DRIPLEAF,

    // Wood / plant structures
    LOG_OVERWORLD, LOG_TROPICAL, LOG_NETHER, BAMBOO, SUGAR_CANE, CACTUS,
    VINE, HONEY,

    // Stone / earth
    STONE, IGNEOUS, DEEPSLATE, DIRT, SAND, CLAY,

    // Specialty
    ICE, SNOW_BLOCK, SCULK, CORAL, MOSS,
    MUSHROOM, FLOWER, LEAVES, VEGETATION,

    // Functional blocks
    FURNACE, BREWING_STAND, CAULDRON, COMPOSTER, SUSPICIOUS,

    // Fire
    FIRE,

    // Fluids / farmland
    WATER, LAVA, FARMLAND,

    // Growable (crops + saplings for Growth Accelerator)
    GROWABLE,

    // Aquatic (pending test)
    KELP, LILY_PAD, SEA_PICKLE,
}
