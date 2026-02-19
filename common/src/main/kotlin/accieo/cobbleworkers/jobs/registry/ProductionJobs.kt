/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.registry

import accieo.cobbleworkers.jobs.WorkerRegistry
import accieo.cobbleworkers.jobs.dsl.ProductionJob
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes

/**
 * Production jobs (B1–B25). Self-generate items on cooldown, deposit in containers.
 * Legacy production jobs (DiveLooter, FishingLootGenerator, PickUpLooter) stay in LegacyJobs.
 */
object ProductionJobs {

    val WOOL_PRODUCER = ProductionJob(
        name = "wool_producer",
        qualifyingMoves = setOf("cottonspore", "cottonguard"),
        fallbackSpecies = listOf("Wooloo", "Dubwool", "Mareep", "Flaaffy"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.CLOUD,
        output = { _, _ -> listOf(ItemStack(Items.WHITE_WOOL)) },
    )

    val SILK_SPINNER = ProductionJob(
        name = "silk_spinner",
        qualifyingMoves = setOf("stringshot", "spiderweb", "electroweb", "stickyweb"),
        fallbackSpecies = listOf("Spinarak", "Ariados", "Joltik", "Galvantula", "Snom"),
        defaultCooldownSeconds = 90,
        particle = ParticleTypes.CLOUD,
        output = { _, _ -> listOf(ItemStack(Items.STRING, 2)) },
    )

    val SLIME_SECRETOR = ProductionJob(
        name = "slime_secretor",
        qualifyingMoves = setOf("acidarmor", "minimize"),
        fallbackSpecies = listOf("Goomy", "Sliggoo", "Goodra", "Gulpin", "Swalot", "Ditto"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.ITEM_SLIME,
        output = { _, _ -> listOf(ItemStack(Items.SLIME_BALL, 2)) },
    )

    val INK_SQUIRTER = ProductionJob(
        name = "ink_squirter",
        qualifyingMoves = setOf("octazooka", "waterspout"),
        fallbackSpecies = listOf("Octillery", "Inkay", "Malamar"),
        defaultCooldownSeconds = 90,
        particle = ParticleTypes.SQUID_INK,
        output = { _, pokemon ->
            val species = pokemon.pokemon.species.translatedName.string.lowercase()
            if (species == "octillery") listOf(ItemStack(Items.GLOW_INK_SAC))
            else listOf(ItemStack(Items.INK_SAC))
        },
    )

    val BONE_SHEDDER = ProductionJob(
        name = "bone_shedder",
        qualifyingMoves = setOf("bonerush", "shadowbone"),
        fallbackSpecies = listOf("Cubone", "Marowak", "Mandibuzz"),
        defaultCooldownSeconds = 90,
        particle = ParticleTypes.CRIT,
        output = { _, _ -> listOf(ItemStack(Items.BONE), ItemStack(Items.BONE_MEAL, 2)) },
    )

    val PEARL_CREATOR = ProductionJob(
        name = "pearl_creator",
        qualifyingMoves = setOf("shellsmash", "withdraw"),
        fallbackSpecies = listOf("Clamperl", "Shellder", "Cloyster"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.BUBBLE,
        output = { _, _ -> listOf(ItemStack(Items.PRISMARINE_SHARD, 2)) },
    )

    val FEATHER_MOLTER = ProductionJob(
        name = "feather_molter",
        qualifyingMoves = setOf("roost", "bravebird", "wingattack", "fly"),
        defaultCooldownSeconds = 60,
        particle = ParticleTypes.CLOUD,
        output = { _, _ -> listOf(ItemStack(Items.FEATHER, 2)) },
    )

    val SCALE_SHEDDER = ProductionJob(
        name = "scale_shedder",
        qualifyingMoves = setOf("shedtail", "coil"),
        fallbackSpecies = listOf("Dragonair", "Dragonite", "Gyarados", "Milotic", "Arbok"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.CRIT,
        output = { _, _ -> listOf(ItemStack(Items.TURTLE_SCUTE), ItemStack(Items.PRISMARINE_SHARD)) },
    )

    val FRUIT_BEARER = ProductionJob(
        name = "fruit_bearer",
        qualifyingMoves = setOf("gravapple", "appleacid"),
        fallbackSpecies = listOf("Tropius", "Applin", "Flapple", "Appletun"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.COMPOSTER,
        output = { _, _ -> listOf(ItemStack(Items.APPLE, 2)) },
    )

    val COIN_MINTER = ProductionJob(
        name = "coin_minter",
        qualifyingMoves = setOf("payday"),
        fallbackSpecies = listOf("Meowth", "Persian", "Perrserker"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.WAX_ON,
        output = { _, _ -> listOf(ItemStack(Items.GOLD_NUGGET, 3)) },
    )

    val GEM_CRAFTER = ProductionJob(
        name = "gem_crafter",
        qualifyingMoves = setOf("diamondstorm", "meteorbeam"),
        fallbackSpecies = listOf("Sableye", "Carbink", "Diancie"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.WAX_ON,
        output = { _, _ -> listOf(ItemStack(Items.AMETHYST_SHARD, 2), ItemStack(Items.EMERALD)) },
    )

    val SPORE_RELEASER = ProductionJob(
        name = "spore_releaser",
        qualifyingMoves = setOf("spore", "ragepowder", "sleeppowder"),
        fallbackSpecies = listOf("Paras", "Parasect", "Foongus", "Amoonguss"),
        defaultCooldownSeconds = 90,
        particle = ParticleTypes.SPORE_BLOSSOM_AIR,
        output = { _, _ ->
            if (Math.random() > 0.5) listOf(ItemStack(Items.BROWN_MUSHROOM))
            else listOf(ItemStack(Items.RED_MUSHROOM))
        },
    )

    val POLLEN_PACKER = ProductionJob(
        name = "pollen_packer",
        qualifyingMoves = setOf("pollenpuff", "floralhealing"),
        fallbackSpecies = listOf("Ribombee", "Cutiefly", "Comfey"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.WAX_ON,
        output = { _, _ -> listOf(ItemStack(Items.HONEY_BOTTLE)) },
    )

    val GIFT_GIVER = ProductionJob(
        name = "gift_giver",
        qualifyingMoves = setOf("present", "bestow", "fling"),
        fallbackSpecies = listOf("Delibird"),
        defaultCooldownSeconds = 300,
        particle = ParticleTypes.HAPPY_VILLAGER,
        output = { _, _ ->
            val gifts = listOf(
                Items.DIAMOND, Items.GOLD_INGOT, Items.IRON_INGOT,
                Items.EMERALD, Items.LAPIS_LAZULI, Items.COAL,
                Items.COOKIE, Items.SWEET_BERRIES, Items.SNOWBALL,
            )
            listOf(ItemStack(gifts.random()))
        },
    )

    val EGG_LAYER = ProductionJob(
        name = "egg_layer",
        qualifyingMoves = setOf("softboiled"),
        fallbackSpecies = listOf("Chansey", "Blissey", "Happiny", "Exeggcute"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.HAPPY_VILLAGER,
        output = { _, _ -> listOf(ItemStack(Items.EGG)) },
    )

    val MILK_PRODUCER = ProductionJob(
        name = "milk_producer",
        qualifyingMoves = setOf("milkdrink"),
        fallbackSpecies = listOf("Miltank", "Gogoat"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.SPLASH,
        output = { _, _ -> listOf(ItemStack(Items.MILK_BUCKET)) },
    )

    val ELECTRIC_CHARGER = ProductionJob(
        name = "electric_charger",
        qualifyingMoves = setOf("charge", "chargebeam", "thunderbolt"),
        fallbackSpecies = listOf("Jolteon", "Electrode", "Magneton", "Rotom"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.ELECTRIC_SPARK,
        output = { _, _ -> listOf(ItemStack(Items.GLOWSTONE_DUST, 2)) },
    )

    val WAX_PRODUCER = ProductionJob(
        name = "wax_producer",
        qualifyingMoves = setOf("defendorder", "healorder"),
        fallbackSpecies = listOf("Vespiquen"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.WAX_ON,
        output = { _, _ -> listOf(ItemStack(Items.HONEYCOMB, 2)) },
    )

    val POWDER_MAKER = ProductionJob(
        name = "powder_maker",
        qualifyingMoves = setOf("selfdestruct", "explosion", "mindblown"),
        fallbackSpecies = listOf("Voltorb", "Electrode", "Koffing", "Weezing"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.SMOKE,
        output = { _, _ -> listOf(ItemStack(Items.GUNPOWDER, 2)) },
    )

    val ASH_COLLECTOR = ProductionJob(
        name = "ash_collector",
        qualifyingMoves = setOf("incinerate"),
        fallbackSpecies = listOf("Torkoal", "Coalossal", "Rolycoly", "Carkol"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
        output = { _, _ -> listOf(ItemStack(Items.CHARCOAL)) },
    )

    val STATIC_GENERATOR = ProductionJob(
        name = "static_generator",
        qualifyingMoves = setOf("thunderwave", "discharge", "electrify"),
        fallbackType = "ELECTRIC",
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.ELECTRIC_SPARK,
        output = { _, _ -> listOf(ItemStack(Items.REDSTONE, 2)) },
    )

    val SAP_TAPPER = ProductionJob(
        name = "sap_tapper",
        qualifyingMoves = setOf("leechlife", "hornleech", "drainpunch"),
        fallbackSpecies = listOf("Heracross", "Pinsir"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.DRIPPING_HONEY,
        output = { _, _ ->
            if (Math.random() > 0.5) listOf(ItemStack(Items.SLIME_BALL))
            else listOf(ItemStack(Items.HONEY_BOTTLE))
        },
    )

    val TOXIN_DISTILLER = ProductionJob(
        name = "toxin_distiller",
        qualifyingMoves = setOf("poisonjab", "poisonfang"),
        typeGatedMoves = mapOf("toxic" to "POISON"),
        fallbackSpecies = listOf("Arbok", "Toxicroak", "Salazzle"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.WITCH,
        output = { _, _ -> listOf(ItemStack(Items.FERMENTED_SPIDER_EYE)) },
    )

    val CRYSTAL_GROWER = ProductionJob(
        name = "crystal_grower",
        qualifyingMoves = setOf("ancientpower", "rockpolish"),
        fallbackSpecies = listOf("Carbink", "Boldore", "Gigalith"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.WAX_ON,
        output = { _, _ -> listOf(ItemStack(Items.QUARTZ, 2)) },
    )

    val TEAR_COLLECTOR = ProductionJob(
        name = "tear_collector",
        qualifyingMoves = setOf("fakeout", "faketears", "tearfullook"),
        fallbackSpecies = listOf("Duskull", "Banette", "Misdreavus"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.FALLING_WATER,
        output = { _, _ -> listOf(ItemStack(Items.GHAST_TEAR)) },
    )

    fun register() {
        WorkerRegistry.registerAll(
            WOOL_PRODUCER,
            SILK_SPINNER,
            SLIME_SECRETOR,
            INK_SQUIRTER,
            BONE_SHEDDER,
            PEARL_CREATOR,
            FEATHER_MOLTER,
            SCALE_SHEDDER,
            FRUIT_BEARER,
            COIN_MINTER,
            GEM_CRAFTER,
            SPORE_RELEASER,
            POLLEN_PACKER,
            GIFT_GIVER,
            EGG_LAYER,
            MILK_PRODUCER,
            ELECTRIC_CHARGER,
            WAX_PRODUCER,
            POWDER_MAKER,
            ASH_COLLECTOR,
            STATIC_GENERATOR,
            SAP_TAPPER,
            TOXIN_DISTILLER,
            CRYSTAL_GROWER,
            TEAR_COLLECTOR,
        )
    }
}
