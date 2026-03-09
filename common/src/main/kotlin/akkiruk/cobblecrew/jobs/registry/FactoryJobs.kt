/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.jobs.dsl.ProcessingJob
import akkiruk.cobblecrew.jobs.dsl.ProductionJob
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes

/**
 * Factory jobs — the Palworld-style production chain.
 *
 * Three tiers:
 * 1. **Material Producers**: Pokémon generate raw materials from nothing (ProductionJob)
 * 2. **Refiners**: Transform raw goods into refined materials (ProcessingJob, barrel → chest)
 * 3. **Assemblers**: Craft finished products from components (ProcessingJob, creative recipes)
 *
 * Chain example: silk_weaver → String → arrow_fletcher uses Flint → Arrows
 */
object FactoryJobs {

    // ═══════════════════════════════════════════════════════════════════
    //  TIER 1: MATERIAL PRODUCERS — Pokémon generate items from nothing
    // ═══════════════════════════════════════════════════════════════════

    /** Poison/Slime species secrete slimeballs. */
    val SLIME_PRODUCER = ProductionJob(
        name = "slime_producer",
        category = "factory",
        qualifyingMoves = setOf("gunkshot"),
        fallbackSpecies = listOf("Grimer", "Muk", "Goodra", "Sliggoo", "Goomy", "Ditto", "Gulpin", "Swalot"),
        defaultCooldownSeconds = 90,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.ITEM_SLIME,
        output = { _, _ -> listOf(ItemStack(Items.SLIME_BALL, 2)) },
    )

    /** Squid-likes produce ink sacs. */
    val INK_PRODUCER = ProductionJob(
        name = "ink_producer",
        category = "factory",
        qualifyingMoves = setOf("octazooka"),
        fallbackSpecies = listOf("Octillery", "Remoraid", "Malamar", "Inkay", "Tentacool", "Tentacruel"),
        defaultCooldownSeconds = 80,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.SQUID_INK,
        output = { _, _ -> listOf(ItemStack(Items.INK_SAC, 3)) },
    )

    /** Bug types spin silk into string. */
    val SILK_WEAVER = ProductionJob(
        name = "silk_weaver",
        category = "factory",
        qualifyingMoves = setOf("stringshot"),
        fallbackSpecies = listOf("Caterpie", "Spinarak", "Ariados", "Sewaddle", "Joltik", "Galvantula", "Dewpider", "Wurmple"),
        defaultCooldownSeconds = 60,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.END_ROD,
        output = { _, _ -> listOf(ItemStack(Items.STRING, 3)) },
    )

    /** Bird Pokémon shed feathers periodically. */
    val FEATHER_SHEDDER = ProductionJob(
        name = "feather_shedder",
        category = "factory",
        qualifyingMoves = setOf("featherdance"),
        fallbackSpecies = listOf("Pidgeot", "Pidgeotto", "Swellow", "Staraptor", "Unfezant", "Talonflame", "Rookidee", "Corvisquire", "Corviknight"),
        defaultCooldownSeconds = 70,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.CLOUD,
        output = { _, _ -> listOf(ItemStack(Items.FEATHER, 2)) },
    )

    /** Bird Pokémon lay eggs. */
    val EGG_LAYER = ProductionJob(
        name = "egg_layer",
        category = "factory",
        qualifyingMoves = setOf("softboiled"),
        fallbackSpecies = listOf("Chansey", "Blissey", "Happiny", "Togekiss", "Togetic", "Exeggcute"),
        defaultCooldownSeconds = 120,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.HEART,
        output = { _, _ -> listOf(ItemStack(Items.EGG, 1)) },
    )

    /** Explosion-capable Pokémon create gunpowder. */
    val GUNPOWDER_MAKER = ProductionJob(
        name = "gunpowder_maker",
        category = "factory",
        qualifyingMoves = setOf("selfdestruct"),
        fallbackSpecies = listOf("Koffing", "Weezing", "Voltorb", "Electrode"),
        defaultCooldownSeconds = 100,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.SMOKE,
        output = { _, _ -> listOf(ItemStack(Items.GUNPOWDER, 2)) },
    )

    /** Luminous Pokémon shed glowstone dust. */
    val GLOWDUST_PRODUCER = ProductionJob(
        name = "glowdust_producer",
        category = "factory",
        qualifyingMoves = setOf("dazzlinggleam"),
        fallbackSpecies = listOf("Volbeat", "Illumise", "Lanturn", "Chinchou", "Ampharos", "Mareep"),
        defaultCooldownSeconds = 100,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.GLOW,
        output = { _, _ -> listOf(ItemStack(Items.GLOWSTONE_DUST, 2)) },
    )

    /** Electric types generate redstone from static charge. */
    val STATIC_GENERATOR = ProductionJob(
        name = "static_generator",
        category = "factory",
        qualifyingMoves = setOf("discharge"),
        fallbackSpecies = listOf("Pikachu", "Raichu", "Magneton", "Magnezone", "Electabuzz", "Electivire", "Jolteon", "Luxray"),
        defaultCooldownSeconds = 80,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.ELECTRIC_SPARK,
        output = { _, _ -> listOf(ItemStack(Items.REDSTONE, 3)) },
    )

    /** Bee Pokémon produce honeycomb. */
    val WAX_PRODUCER = ProductionJob(
        name = "wax_producer",
        category = "factory",
        qualifyingMoves = setOf("defendorder"),
        fallbackSpecies = listOf("Combee", "Vespiquen"),
        defaultCooldownSeconds = 120,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.WAX_ON,
        output = { _, _ -> listOf(ItemStack(Items.HONEYCOMB, 2)) },
    )

    /** Strong Fire types distill blaze powder. */
    val EMBER_PRODUCER = ProductionJob(
        name = "ember_producer",
        category = "factory",
        qualifyingMoves = setOf("lavaplume"),
        fallbackSpecies = listOf("Magcargo", "Camerupt", "Torkoal", "Numel", "Heatran", "Coalossal"),
        defaultCooldownSeconds = 150,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.FLAME,
        output = { _, _ -> listOf(ItemStack(Items.BLAZE_POWDER, 1)) },
    )

    /** Ice types pack snowballs. */
    val SNOWBALL_MAKER = ProductionJob(
        name = "snowball_maker",
        category = "factory",
        qualifyingMoves = setOf("powdersnow"),
        fallbackSpecies = listOf("Snover", "Abomasnow", "Snom", "Frosmoth", "Vanillite", "Vanilluxe", "Cryogonal"),
        defaultCooldownSeconds = 50,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.SNOWFLAKE,
        output = { _, _ -> listOf(ItemStack(Items.SNOWBALL, 4)) },
    )

    /** Ghost types shed phantom membrane. */
    val PHANTOM_PRODUCER = ProductionJob(
        name = "phantom_producer",
        category = "factory",
        qualifyingMoves = setOf("shadowball"),
        fallbackSpecies = listOf("Gengar", "Dusknoir", "Chandelure", "Drifblim", "Mismagius", "Banette", "Spiritomb"),
        defaultCooldownSeconds = 180,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.SOUL,
        output = { _, _ -> listOf(ItemStack(Items.PHANTOM_MEMBRANE, 1)) },
    )

    /** Shell species produce prismarine shards. */
    val PEARL_PRODUCER = ProductionJob(
        name = "pearl_producer",
        category = "factory",
        qualifyingMoves = setOf("shellsmash"),
        fallbackSpecies = listOf("Cloyster", "Omastar", "Corsola", "Clamperl", "Shellder", "Binacle", "Barbaracle"),
        defaultCooldownSeconds = 120,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.BUBBLE,
        output = { _, _ -> listOf(ItemStack(Items.PRISMARINE_SHARD, 2)) },
    )

    /** Clay/Ground types produce clay balls. */
    val CLAY_SHAPER = ProductionJob(
        name = "clay_shaper",
        category = "factory",
        qualifyingMoves = setOf("mudshot"),
        fallbackSpecies = listOf("Mudbray", "Mudsdale", "Sandygast", "Palossand", "Whiscash", "Quagsire", "Gastrodon"),
        defaultCooldownSeconds = 70,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.CLOUD,
        output = { _, _ -> listOf(ItemStack(Items.CLAY_BALL, 4)) },
    )

    /** Dragon types generate dragon's breath. */
    val DRAGONS_BREATH_PRODUCER = ProductionJob(
        name = "dragons_breath_producer",
        category = "factory",
        qualifyingMoves = setOf("dragonbreath"),
        fallbackSpecies = listOf("Dragonite", "Salamence", "Garchomp", "Hydreigon", "Goodra", "Drampa", "Druddigon"),
        defaultCooldownSeconds = 200,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.DRAGON_BREATH,
        output = { _, _ -> listOf(ItemStack(Items.DRAGON_BREATH, 1)) },
    )

    /** Rock types shed flint fragments. */
    val FLINT_SHEDDER = ProductionJob(
        name = "flint_shedder",
        category = "factory",
        qualifyingMoves = setOf("rockblast"),
        fallbackSpecies = listOf("Geodude", "Graveler", "Golem", "Onix", "Steelix", "Rhyhorn", "Rhydon", "Nosepass"),
        defaultCooldownSeconds = 80,
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.CRIT,
        output = { _, _ -> listOf(ItemStack(Items.FLINT, 2)) },
    )

    // ═══════════════════════════════════════════════════════════════════
    //  TIER 2: REFINERS — Transform raw materials into refined goods
    // ═══════════════════════════════════════════════════════════════════

    /** Fire types melt sand into glass. */
    val GLASS_BLOWER = ProcessingJob(
        name = "glass_blower",
        category = "factory",
        qualifyingMoves = setOf("firespin"),
        particle = ParticleTypes.FLAME,
        inputCheck = { it.item == Items.SAND || it.item == Items.RED_SAND },
        transformFn = { input -> listOf(ItemStack(Items.GLASS, input.count)) },
    )

    /** Fire types fire clay into bricks. */
    val BRICK_MAKER = ProcessingJob(
        name = "brick_maker",
        category = "factory",
        qualifyingMoves = setOf("heatwave"),
        particle = ParticleTypes.FLAME,
        inputCheck = { it.item == Items.CLAY_BALL },
        transformFn = { input -> listOf(ItemStack(Items.BRICK, input.count)) },
    )

    /** Fire types char logs into charcoal. */
    val CHARCOAL_BURNER = ProcessingJob(
        name = "charcoal_burner",
        category = "factory",
        qualifyingMoves = setOf("ember"),
        particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
        inputCheck = { stack ->
            stack.item in setOf(
                Items.OAK_LOG, Items.BIRCH_LOG, Items.SPRUCE_LOG, Items.JUNGLE_LOG,
                Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.CHERRY_LOG, Items.MANGROVE_LOG,
            )
        },
        transformFn = { input -> listOf(ItemStack(Items.CHARCOAL, input.count)) },
    )

    /** Rock types chisel stone into stone bricks. */
    val STONE_CARVER = ProcessingJob(
        name = "stone_carver",
        category = "factory",
        qualifyingMoves = setOf("rocksmash"),
        particle = ParticleTypes.CRIT,
        inputCheck = { it.item == Items.STONE },
        transformFn = { input -> listOf(ItemStack(Items.STONE_BRICKS, input.count)) },
    )

    /** Fighting/Ground types crush bone into bone meal. */
    val BONE_GRINDER = ProcessingJob(
        name = "bone_grinder",
        category = "factory",
        qualifyingMoves = setOf("bonerush"),
        fallbackSpecies = listOf("Marowak", "Cubone", "Lucario"),
        particle = ParticleTypes.COMPOSTER,
        inputCheck = { it.item == Items.BONE },
        transformFn = { input -> listOf(ItemStack(Items.BONE_MEAL, input.count * 3)) },
    )

    /** Cutting types shear wool into string. */
    val WOOL_SHEARER = object : ProcessingJob(
        name = "wool_shearer",
        category = "factory",
        qualifyingMoves = setOf("furycutter"),
        particle = ParticleTypes.CLOUD,
        inputCheck = { stack ->
            stack.item in setOf(
                Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL,
                Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
                Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
                Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL,
            )
        },
        transformFn = { input -> listOf(ItemStack(Items.STRING, input.count * 4)) },
    ) {
        override val minExtractAmount: Int = 1
    }

    /** Fighting types tan rabbit hide into leather. */
    val LEATHER_TANNER = ProcessingJob(
        name = "leather_tanner",
        category = "factory",
        qualifyingMoves = setOf("scratch"),
        particle = ParticleTypes.CRIT,
        inputCheck = { it.item == Items.RABBIT_HIDE },
        transformFn = { input ->
            val batches = input.count / 4
            if (batches > 0) listOf(ItemStack(Items.LEATHER, batches)) else emptyList()
        },
    )

    /** Water types harden concrete powder into concrete. */
    val CONCRETE_HARDENER = ProcessingJob(
        name = "concrete_hardener",
        category = "factory",
        qualifyingMoves = setOf("aquajet"),
        particle = ParticleTypes.SPLASH,
        inputCheck = { stack ->
            stack.item in setOf(
                Items.WHITE_CONCRETE_POWDER, Items.ORANGE_CONCRETE_POWDER, Items.MAGENTA_CONCRETE_POWDER,
                Items.LIGHT_BLUE_CONCRETE_POWDER, Items.YELLOW_CONCRETE_POWDER, Items.LIME_CONCRETE_POWDER,
                Items.PINK_CONCRETE_POWDER, Items.GRAY_CONCRETE_POWDER, Items.LIGHT_GRAY_CONCRETE_POWDER,
                Items.CYAN_CONCRETE_POWDER, Items.PURPLE_CONCRETE_POWDER, Items.BLUE_CONCRETE_POWDER,
                Items.BROWN_CONCRETE_POWDER, Items.GREEN_CONCRETE_POWDER, Items.RED_CONCRETE_POWDER,
                Items.BLACK_CONCRETE_POWDER,
            )
        },
        transformFn = { input ->
            val concreteItem = when (input.item) {
                Items.WHITE_CONCRETE_POWDER -> Items.WHITE_CONCRETE
                Items.ORANGE_CONCRETE_POWDER -> Items.ORANGE_CONCRETE
                Items.MAGENTA_CONCRETE_POWDER -> Items.MAGENTA_CONCRETE
                Items.LIGHT_BLUE_CONCRETE_POWDER -> Items.LIGHT_BLUE_CONCRETE
                Items.YELLOW_CONCRETE_POWDER -> Items.YELLOW_CONCRETE
                Items.LIME_CONCRETE_POWDER -> Items.LIME_CONCRETE
                Items.PINK_CONCRETE_POWDER -> Items.PINK_CONCRETE
                Items.GRAY_CONCRETE_POWDER -> Items.GRAY_CONCRETE
                Items.LIGHT_GRAY_CONCRETE_POWDER -> Items.LIGHT_GRAY_CONCRETE
                Items.CYAN_CONCRETE_POWDER -> Items.CYAN_CONCRETE
                Items.PURPLE_CONCRETE_POWDER -> Items.PURPLE_CONCRETE
                Items.BLUE_CONCRETE_POWDER -> Items.BLUE_CONCRETE
                Items.BROWN_CONCRETE_POWDER -> Items.BROWN_CONCRETE
                Items.GREEN_CONCRETE_POWDER -> Items.GREEN_CONCRETE
                Items.RED_CONCRETE_POWDER -> Items.RED_CONCRETE
                Items.BLACK_CONCRETE_POWDER -> Items.BLACK_CONCRETE
                else -> return@ProcessingJob emptyList()
            }
            listOf(ItemStack(concreteItem, input.count))
        },
    )

    /** Rock types polish quartz into blocks. */
    val QUARTZ_POLISHER = object : ProcessingJob(
        name = "quartz_polisher",
        category = "factory",
        qualifyingMoves = setOf("rocktomb"),
        particle = ParticleTypes.CRIT,
        inputCheck = { it.item == Items.QUARTZ && it.count >= 4 },
        transformFn = { input ->
            val batches = input.count / 4
            if (batches > 0) listOf(ItemStack(Items.QUARTZ_BLOCK, batches)) else emptyList()
        },
    ) {
        override val minExtractAmount: Int = 4
    }

    /** Fire types fire netherrack into nether bricks. */
    val NETHER_BRICK_MAKER = ProcessingJob(
        name = "nether_brick_maker",
        category = "factory",
        qualifyingMoves = setOf("inferno"),
        particle = ParticleTypes.SOUL_FIRE_FLAME,
        inputCheck = { it.item == Items.NETHERRACK },
        transformFn = { input -> listOf(ItemStack(Items.NETHER_BRICK, input.count)) },
    )

    /** Rock/Ground types crush gravel for flint. Always yields flint (Pokémon-powered). */
    val GRAVEL_CRUSHER = ProcessingJob(
        name = "gravel_crusher",
        category = "factory",
        qualifyingMoves = setOf("rockslide"),
        particle = ParticleTypes.CRIT,
        inputCheck = { it.item == Items.GRAVEL },
        transformFn = { input -> listOf(ItemStack(Items.FLINT, input.count)) },
    )

    /** Psychic types crush amethyst shards into amethyst blocks. */
    val AMETHYST_FUSER = object : ProcessingJob(
        name = "amethyst_fuser",
        category = "factory",
        qualifyingMoves = setOf("psychic"),
        particle = ParticleTypes.WITCH,
        inputCheck = { it.item == Items.AMETHYST_SHARD && it.count >= 4 },
        transformFn = { input ->
            val batches = input.count / 4
            if (batches > 0) listOf(ItemStack(Items.AMETHYST_BLOCK, batches)) else emptyList()
        },
    ) {
        override val minExtractAmount: Int = 4
    }

    /** Fire types smelt raw copper into copper ingots in bulk. */
    val COPPER_SMELTER = ProcessingJob(
        name = "copper_smelter",
        category = "factory",
        qualifyingMoves = setOf("flamethrower"),
        particle = ParticleTypes.FLAME,
        inputCheck = { it.item == Items.RAW_COPPER },
        transformFn = { input -> listOf(ItemStack(Items.COPPER_INGOT, input.count)) },
    )

    /** Fire types smelt raw iron. */
    val IRON_SMELTER = ProcessingJob(
        name = "iron_smelter",
        category = "factory",
        qualifyingMoves = setOf("fireblast"),
        particle = ParticleTypes.FLAME,
        inputCheck = { it.item == Items.RAW_IRON },
        transformFn = { input -> listOf(ItemStack(Items.IRON_INGOT, input.count)) },
    )

    /** Fire types smelt raw gold. */
    val GOLD_SMELTER = ProcessingJob(
        name = "gold_smelter",
        category = "factory",
        qualifyingMoves = setOf("sacredfire"),
        particle = ParticleTypes.FLAME,
        inputCheck = { it.item == Items.RAW_GOLD },
        transformFn = { input -> listOf(ItemStack(Items.GOLD_INGOT, input.count)) },
    )

    // ═══════════════════════════════════════════════════════════════════
    //  TIER 3: ASSEMBLERS — Craft finished products from components
    //  (Pokémon "contributes" missing recipe ingredients thematically)
    // ═══════════════════════════════════════════════════════════════════

    /** Fire types light coal/charcoal into torches (4 per item). */
    val TORCH_CRAFTER = ProcessingJob(
        name = "torch_crafter",
        category = "factory",
        qualifyingMoves = setOf("willowisp"),
        particle = ParticleTypes.FLAME,
        inputCheck = { it.item == Items.COAL || it.item == Items.CHARCOAL },
        transformFn = { input -> listOf(ItemStack(Items.TORCH, input.count * 4)) },
    )

    /** Fighting types forge iron into rails. */
    val RAIL_FORGER = ProcessingJob(
        name = "rail_forger",
        category = "factory",
        qualifyingMoves = setOf("ironhead"),
        particle = ParticleTypes.CRIT,
        inputCheck = { it.item == Items.IRON_INGOT },
        transformFn = { input -> listOf(ItemStack(Items.RAIL, input.count * 4)) },
    )

    /** Pin missile Pokémon fletch flint into arrows. */
    val ARROW_FLETCHER = ProcessingJob(
        name = "arrow_fletcher",
        category = "factory",
        qualifyingMoves = setOf("pinmissile"),
        particle = ParticleTypes.CRIT,
        inputCheck = { it.item == Items.FLINT },
        transformFn = { input -> listOf(ItemStack(Items.ARROW, input.count * 4)) },
    )

    /** Explosion users pack gunpowder into TNT. */
    val TNT_MIXER = ProcessingJob(
        name = "tnt_mixer",
        category = "factory",
        qualifyingMoves = setOf("explosion"),
        fallbackSpecies = listOf("Voltorb", "Electrode", "Koffing", "Weezing"),
        particle = ParticleTypes.SMOKE,
        inputCheck = { it.item == Items.GUNPOWDER },
        transformFn = { input -> listOf(ItemStack(Items.TNT, input.count)) },
    )

    /** Steel types forge iron into buckets. */
    val BUCKET_FORGER = object : ProcessingJob(
        name = "bucket_forger",
        category = "factory",
        qualifyingMoves = setOf("metalclaw"),
        particle = ParticleTypes.CRIT,
        inputCheck = { it.item == Items.IRON_INGOT && it.count >= 3 },
        transformFn = { input ->
            val batches = input.count / 3
            if (batches > 0) listOf(ItemStack(Items.BUCKET, batches)) else emptyList()
        },
    ) {
        override val minExtractAmount: Int = 3
    }

    /** Steel defense types forge iron into hoppers. */
    val HOPPER_BUILDER = object : ProcessingJob(
        name = "hopper_builder",
        category = "factory",
        qualifyingMoves = setOf("irondefense"),
        particle = ParticleTypes.CRIT,
        inputCheck = { it.item == Items.IRON_INGOT && it.count >= 5 },
        transformFn = { input ->
            val batches = input.count / 5
            if (batches > 0) listOf(ItemStack(Items.HOPPER, batches)) else emptyList()
        },
    ) {
        override val minExtractAmount: Int = 5
    }

    /** Steel types forge iron nuggets into chains. */
    val CHAIN_FORGER = ProcessingJob(
        name = "chain_forger",
        category = "factory",
        qualifyingMoves = setOf("irontail"),
        particle = ParticleTypes.CRIT,
        inputCheck = { it.item == Items.IRON_NUGGET },
        transformFn = { input ->
            // 2 nuggets + 1 ingot = 1 chain, simplified to 3 nuggets = 1 chain
            val batches = input.count / 3
            if (batches > 0) listOf(ItemStack(Items.CHAIN, batches)) else emptyList()
        },
    )

    /** Fire types forge iron nuggets → lanterns. */
    val LANTERN_MAKER = object : ProcessingJob(
        name = "lantern_maker",
        category = "factory",
        qualifyingMoves = setOf("firepunch"),
        particle = ParticleTypes.FLAME,
        inputCheck = { it.item == Items.IRON_NUGGET && it.count >= 8 },
        transformFn = { input ->
            val batches = input.count / 8
            if (batches > 0) listOf(ItemStack(Items.LANTERN, batches)) else emptyList()
        },
    ) {
        override val minExtractAmount: Int = 8
    }

    /** Psychic types bind paper into books. */
    val BOOK_BINDER = object : ProcessingJob(
        name = "book_binder",
        category = "factory",
        qualifyingMoves = setOf("psychocut"),
        particle = ParticleTypes.ENCHANT,
        inputCheck = { it.item == Items.PAPER && it.count >= 3 },
        transformFn = { input ->
            val batches = input.count / 3
            if (batches > 0) listOf(ItemStack(Items.BOOK, batches)) else emptyList()
        },
    ) {
        override val minExtractAmount: Int = 3
    }

    /** Fighting types forge iron + redstone into pistons. */
    val PISTON_ASSEMBLER = ProcessingJob(
        name = "piston_assembler",
        category = "factory",
        qualifyingMoves = setOf("forcepalm"),
        particle = ParticleTypes.CRIT,
        inputCheck = { it.item == Items.IRON_INGOT },
        transformFn = { input -> listOf(ItemStack(Items.PISTON, input.count)) },
    )

    /** Bug/String types weave bamboo into scaffolding. */
    val SCAFFOLDING_WEAVER = ProcessingJob(
        name = "scaffolding_weaver",
        category = "factory",
        qualifyingMoves = setOf("silktrap"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        inputCheck = { it.item == Items.BAMBOO },
        transformFn = { input ->
            // 6 bamboo + 1 string = 6 scaffolding, simplified: 1 bamboo = 1 scaffolding
            listOf(ItemStack(Items.SCAFFOLDING, input.count))
        },
    )

    /** Electric types forge gold + redstone into clocks. */
    val CLOCK_MAKER = object : ProcessingJob(
        name = "clock_maker",
        category = "factory",
        qualifyingMoves = setOf("thunderwave"),
        particle = ParticleTypes.ELECTRIC_SPARK,
        inputCheck = { it.item == Items.GOLD_INGOT && it.count >= 4 },
        transformFn = { input ->
            val batches = input.count / 4
            if (batches > 0) listOf(ItemStack(Items.CLOCK, batches)) else emptyList()
        },
    ) {
        override val minExtractAmount: Int = 4
    }

    /** Magnetized Pokémon forge iron + redstone into compasses. */
    val COMPASS_MAKER = object : ProcessingJob(
        name = "compass_maker",
        category = "factory",
        qualifyingMoves = setOf("magnetrise"),
        fallbackSpecies = listOf("Magneton", "Magnezone", "Nosepass", "Probopass"),
        particle = ParticleTypes.ELECTRIC_SPARK,
        inputCheck = { it.item == Items.IRON_INGOT && it.count >= 4 },
        transformFn = { input ->
            val batches = input.count / 4
            if (batches > 0) listOf(ItemStack(Items.COMPASS, batches)) else emptyList()
        },
    ) {
        override val minExtractAmount: Int = 4
    }

    /** Grass types press sugar cane into paper AND sugar. */
    val SUGAR_REFINER = ProcessingJob(
        name = "sugar_refiner",
        category = "factory",
        qualifyingMoves = setOf("sweetscent"),
        particle = ParticleTypes.HAPPY_VILLAGER,
        inputCheck = { it.item == Items.SUGAR_CANE },
        transformFn = { input ->
            // Each sugar cane yields 1 sugar (Pokémon squeezes the juice)
            listOf(ItemStack(Items.SUGAR, input.count))
        },
    )

    /** Cooking types bake cookies from wheat. */
    val COOKIE_BAKER = object : ProcessingJob(
        name = "cookie_baker",
        category = "factory",
        qualifyingMoves = setOf("sweetkiss"),
        fallbackSpecies = listOf("Alcremie", "Slurpuff", "Swirlix"),
        particle = ParticleTypes.HEART,
        inputCheck = { it.item == Items.WHEAT && it.count >= 2 },
        transformFn = { input ->
            val batches = input.count / 2
            if (batches > 0) listOf(ItemStack(Items.COOKIE, batches * 8)) else emptyList()
        },
    ) {
        override val minExtractAmount: Int = 2
    }

    /** Smoker types dry kelp. */
    val KELP_DRYER = ProcessingJob(
        name = "kelp_dryer",
        category = "factory",
        qualifyingMoves = setOf("scald"),
        particle = ParticleTypes.SMOKE,
        inputCheck = { it.item == Items.KELP },
        transformFn = { input -> listOf(ItemStack(Items.DRIED_KELP, input.count)) },
    )

    /** Glass types fuse sand into glass panes efficiently. */
    val GLASS_PANER = object : ProcessingJob(
        name = "glass_paner",
        category = "factory",
        qualifyingMoves = setOf("mirrorshot"),
        particle = ParticleTypes.CRIT,
        inputCheck = { it.item == Items.GLASS && it.count >= 6 },
        transformFn = { input ->
            val batches = input.count / 6
            if (batches > 0) listOf(ItemStack(Items.GLASS_PANE, batches * 16)) else emptyList()
        },
    ) {
        override val minExtractAmount: Int = 6
    }

    fun register() {
        WorkerRegistry.registerAll(
            // Material Producers
            SLIME_PRODUCER, INK_PRODUCER, SILK_WEAVER, FEATHER_SHEDDER,
            EGG_LAYER, GUNPOWDER_MAKER, GLOWDUST_PRODUCER, STATIC_GENERATOR,
            WAX_PRODUCER, EMBER_PRODUCER, SNOWBALL_MAKER, PHANTOM_PRODUCER,
            PEARL_PRODUCER, CLAY_SHAPER, DRAGONS_BREATH_PRODUCER, FLINT_SHEDDER,
            // Refiners
            GLASS_BLOWER, BRICK_MAKER, CHARCOAL_BURNER, STONE_CARVER,
            BONE_GRINDER, WOOL_SHEARER, LEATHER_TANNER, CONCRETE_HARDENER,
            QUARTZ_POLISHER, NETHER_BRICK_MAKER, GRAVEL_CRUSHER, AMETHYST_FUSER,
            COPPER_SMELTER, IRON_SMELTER, GOLD_SMELTER,
            // Assemblers
            TORCH_CRAFTER, RAIL_FORGER, ARROW_FLETCHER, TNT_MIXER,
            BUCKET_FORGER, HOPPER_BUILDER, CHAIN_FORGER, LANTERN_MAKER,
            BOOK_BINDER, PISTON_ASSEMBLER, SCAFFOLDING_WEAVER,
            CLOCK_MAKER, COMPASS_MAKER, SUGAR_REFINER, COOKIE_BAKER,
            KELP_DRYER, GLASS_PANER,
        )
    }
}
