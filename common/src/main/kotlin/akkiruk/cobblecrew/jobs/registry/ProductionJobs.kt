/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.jobs.dsl.ProductionJob
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import akkiruk.cobblecrew.utilities.WorkSpeedBoostManager
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.loot.LootTables
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

/**
 * Production jobs. Self-generate items on cooldown, deposit in containers.
 * Includes DSL ProductionJob instances and custom Worker implementations for loot-based jobs.
 */
object ProductionJobs {

    val WOOL_PRODUCER = ProductionJob(
        name = "wool_producer",
        qualifyingMoves = setOf("cottonguard"),
        fallbackSpecies = listOf("Wooloo", "Dubwool"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.CLOUD,
        output = { _, _ -> listOf(ItemStack(Items.WHITE_WOOL)) },
    )

    val SILK_SPINNER = ProductionJob(
        name = "silk_spinner",
        qualifyingMoves = setOf("stringshot", "stickyweb"),
        fallbackSpecies = listOf("Spinarak", "Ariados"),
        defaultCooldownSeconds = 90,
        particle = ParticleTypes.CLOUD,
        output = { _, _ -> listOf(ItemStack(Items.STRING, 2)) },
    )

    val SLIME_SECRETOR = ProductionJob(
        name = "slime_secretor",
        qualifyingMoves = setOf("acidarmor"),
        fallbackSpecies = listOf("Goomy", "Sliggoo", "Goodra", "Grimer", "Muk"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.ITEM_SLIME,
        output = { _, _ -> listOf(ItemStack(Items.SLIME_BALL, 2)) },
    )

    val INK_SQUIRTER = ProductionJob(
        name = "ink_squirter",
        qualifyingMoves = setOf("octazooka"),
        fallbackSpecies = listOf("Octillery"),
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
        fallbackSpecies = listOf("Cubone", "Marowak"),
        defaultCooldownSeconds = 90,
        particle = ParticleTypes.CRIT,
        output = { _, _ -> listOf(ItemStack(Items.BONE), ItemStack(Items.BONE_MEAL, 2)) },
    )

    val PEARL_CREATOR = ProductionJob(
        name = "pearl_creator",
        qualifyingMoves = setOf("shellsmash"),
        fallbackSpecies = listOf("Clamperl"),
        defaultCooldownSeconds = 300,
        particle = ParticleTypes.BUBBLE,
        output = { _, _ -> listOf(ItemStack(Items.ENDER_PEARL)) },
    )

    val FEATHER_MOLTER = ProductionJob(
        name = "feather_molter",
        qualifyingMoves = setOf("roost"),
        defaultCooldownSeconds = 60,
        particle = ParticleTypes.CLOUD,
        output = { _, _ -> listOf(ItemStack(Items.FEATHER, 2)) },
    )

    val SCALE_SHEDDER = ProductionJob(
        name = "scale_shedder",
        qualifyingMoves = setOf("shedtail", "coil"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.CRIT,
        output = { _, _ -> listOf(ItemStack(Items.TURTLE_SCUTE), ItemStack(Items.PRISMARINE_SHARD)) },
    )

    val FRUIT_BEARER = ProductionJob(
        name = "fruit_bearer",
        qualifyingMoves = setOf("gravapple"),
        fallbackSpecies = listOf("Tropius", "Applin", "Flapple", "Appletun"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.COMPOSTER,
        output = { _, _ -> listOf(ItemStack(Items.APPLE, 2)) },
    )

    val COIN_MINTER = ProductionJob(
        name = "coin_minter",
        qualifyingMoves = setOf("payday"),
        fallbackSpecies = listOf("Meowth", "Persian", "Gholdengo"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.WAX_ON,
        output = { _, _ -> listOf(ItemStack(Items.GOLD_NUGGET, 3)) },
    )

    val GEM_CRAFTER = ProductionJob(
        name = "gem_crafter",
        qualifyingMoves = setOf("meteorbeam"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.WAX_ON,
        output = { _, _ -> listOf(ItemStack(Items.AMETHYST_SHARD, 2), ItemStack(Items.EMERALD)) },
    )

    val SPORE_RELEASER = ProductionJob(
        name = "spore_releaser",
        qualifyingMoves = setOf("spore"),
        fallbackSpecies = listOf("Foongus", "Amoonguss", "Paras", "Parasect", "Shroomish", "Breloom"),
        defaultCooldownSeconds = 90,
        particle = ParticleTypes.SPORE_BLOSSOM_AIR,
        output = { _, _ ->
            if (Math.random() > 0.5) listOf(ItemStack(Items.BROWN_MUSHROOM))
            else listOf(ItemStack(Items.RED_MUSHROOM))
        },
    )

    val POLLEN_PACKER = ProductionJob(
        name = "pollen_packer",
        qualifyingMoves = setOf("pollenpuff"),
        fallbackSpecies = listOf("Ribombee"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.WAX_ON,
        output = { _, _ -> listOf(ItemStack(Items.HONEY_BOTTLE)) },
    )

    val GIFT_GIVER = ProductionJob(
        name = "gift_giver",
        qualifyingMoves = setOf("present", "bestow"),
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
        fallbackSpecies = listOf("Chansey", "Blissey"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.HAPPY_VILLAGER,
        output = { _, _ -> listOf(ItemStack(Items.EGG)) },
    )

    val MILK_PRODUCER = ProductionJob(
        name = "milk_producer",
        qualifyingMoves = setOf("milkdrink"),
        fallbackSpecies = listOf("Miltank"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.SPLASH,
        output = { _, _ -> listOf(ItemStack(Items.MILK_BUCKET)) },
    )

    val ELECTRIC_CHARGER = ProductionJob(
        name = "electric_charger",
        qualifyingMoves = setOf("charge"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.ELECTRIC_SPARK,
        output = { _, _ -> listOf(ItemStack(Items.COPPER_INGOT)) },
    )

    val WAX_PRODUCER = ProductionJob(
        name = "wax_producer",
        qualifyingMoves = setOf("defendorder", "healorder"),
        fallbackSpecies = listOf("Combee", "Vespiquen"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.WAX_ON,
        output = { _, _ -> listOf(ItemStack(Items.HONEYCOMB, 2)) },
    )

    val POWDER_MAKER = ProductionJob(
        name = "powder_maker",
        qualifyingMoves = setOf("selfdestruct"),
        fallbackSpecies = listOf("Voltorb", "Electrode"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.SMOKE,
        output = { _, _ -> listOf(ItemStack(Items.GUNPOWDER, 2)) },
    )

    val ASH_COLLECTOR = ProductionJob(
        name = "ash_collector",
        qualifyingMoves = setOf("incinerate"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
        output = { _, _ -> listOf(ItemStack(Items.CHARCOAL)) },
    )

    val STATIC_GENERATOR = ProductionJob(
        name = "static_generator",
        qualifyingMoves = setOf("discharge"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.ELECTRIC_SPARK,
        output = { _, _ -> listOf(ItemStack(Items.REDSTONE, 2)) },
    )

    val SAP_TAPPER = ProductionJob(
        name = "sap_tapper",
        qualifyingMoves = setOf("hornleech"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.DRIPPING_HONEY,
        output = { _, _ ->
            if (Math.random() > 0.5) listOf(ItemStack(Items.SLIME_BALL))
            else listOf(ItemStack(Items.HONEY_BOTTLE))
        },
    )

    val TOXIN_DISTILLER = ProductionJob(
        name = "toxin_distiller",
        qualifyingMoves = setOf("poisonjab"),
        defaultCooldownSeconds = 120,
        particle = ParticleTypes.WITCH,
        output = { _, _ -> listOf(ItemStack(Items.FERMENTED_SPIDER_EYE)) },
    )

    val CRYSTAL_GROWER = ProductionJob(
        name = "crystal_grower",
        qualifyingMoves = setOf("ancientpower"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.WAX_ON,
        output = { _, _ -> listOf(ItemStack(Items.QUARTZ, 2)) },
    )

    val TEAR_COLLECTOR = ProductionJob(
        name = "tear_collector",
        qualifyingMoves = setOf("faketears"),
        defaultCooldownSeconds = 180,
        particle = ParticleTypes.FALLING_WATER,
        output = { _, _ -> listOf(ItemStack(Items.GHAST_TEAR)) },
    )

    // ── Loot-based Workers (migrated from legacy) ──────────────────

    object FishingLooter : Worker {
        override val name = "fishing_looter"
        override val priority = WorkerPriority.MOVE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("dive")
        private val lastGenTime = mutableMapOf<UUID, Long>()
        private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
        private val failedDeposits = mutableMapOf<UUID, MutableSet<BlockPos>>()

        init {
            JobConfigManager.registerDefault("production", name, JobConfig(
                enabled = true,
                cooldownSeconds = 120,
                qualifyingMoves = qualifyingMoves.toList(),
                treasureChance = 10,
                requiresWater = true,
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            return moves.any { it in eff }
        }

        override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
            val world = context.world
            val origin = context.origin
            val pid = pokemonEntity.pokemon.uuid
            if (config.requiresWater == true && !pokemonEntity.isTouchingWater) return
            val held = heldItems[pid]
            if (held.isNullOrEmpty()) {
                failedDeposits.remove(pid)
                produce(world, origin, pokemonEntity)
            } else {
                if (context is JobContext.Party) {
                    CobbleCrewInventoryUtils.deliverToPlayer(context.player, held, pokemonEntity)
                    heldItems.remove(pid)
                    failedDeposits.remove(pid)
                    return
                }
                CobbleCrewInventoryUtils.handleDepositing(world, origin, pokemonEntity, held, failedDeposits, heldItems)
            }
        }

        private fun produce(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val baseCd = (config.cooldownSeconds.takeIf { it > 0 } ?: 120) * 20L
            val cd = WorkSpeedBoostManager.adjustCooldown(baseCd, origin, now)
            if (now - last < cd) return

            val chance = (config.treasureChance?.takeIf { it > 0 } ?: 10).toDouble() / 100
            val useTreasure = world.random.nextFloat() < chance

            val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
                .add(LootContextParameters.ORIGIN, origin.toCenterPos())
                .add(LootContextParameters.TOOL, ItemStack(Items.FISHING_ROD))
                .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)
                .build(LootContextTypes.FISHING)

            val table = if (useTreasure)
                world.server.reloadableRegistries.getLootTable(LootTables.FISHING_TREASURE_GAMEPLAY)
            else
                world.server.reloadableRegistries.getLootTable(LootTables.FISHING_GAMEPLAY)

            val drops = table.generateLoot(lootParams)
            if (drops.isNotEmpty()) {
                lastGenTime[pid] = now
                heldItems[pid] = drops
            }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in heldItems
        override fun cleanup(pokemonId: UUID) {
            lastGenTime.remove(pokemonId)
            heldItems.remove(pokemonId)
            failedDeposits.remove(pokemonId)
        }
    }

    object PickupLooter : Worker {
        override val name = "pickup_looter"
        override val priority = WorkerPriority.MOVE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val lastGenTime = mutableMapOf<UUID, Long>()
        private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
        private val failedDeposits = mutableMapOf<UUID, MutableSet<BlockPos>>()

        init {
            JobConfigManager.registerDefault("production", name, JobConfig(
                enabled = true,
                cooldownSeconds = 120,
                requiredAbility = "pickup",
                lootTables = listOf("cobblemon:gameplay/pickup"),
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val req = config.requiredAbility?.lowercase() ?: "pickup"
            return ability.lowercase() == req
        }

        override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
            val world = context.world
            val origin = context.origin
            val pid = pokemonEntity.pokemon.uuid
            val held = heldItems[pid]
            if (held.isNullOrEmpty()) {
                failedDeposits.remove(pid)
                produce(world, origin, pokemonEntity)
            } else {
                if (context is JobContext.Party) {
                    CobbleCrewInventoryUtils.deliverToPlayer(context.player, held, pokemonEntity)
                    heldItems.remove(pid)
                    failedDeposits.remove(pid)
                    return
                }
                CobbleCrewInventoryUtils.handleDepositing(world, origin, pokemonEntity, held, failedDeposits, heldItems)
            }
        }

        private fun produce(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val baseCd = (config.cooldownSeconds.takeIf { it > 0 } ?: 120) * 20L
            val cd = WorkSpeedBoostManager.adjustCooldown(baseCd, origin, now)
            if (now - last < cd) return

            val tables = (config.lootTables ?: emptyList()).ifEmpty { listOf("cobblemon:gameplay/pickup") }
                .mapNotNull { Identifier.tryParse(it) }
            if (tables.isEmpty()) return

            val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
                .add(LootContextParameters.ORIGIN, origin.toCenterPos())
                .add(LootContextParameters.THIS_ENTITY, pokemonEntity)
                .build(LootContextTypes.CHEST)

            val key = RegistryKey.of(RegistryKeys.LOOT_TABLE, tables.random())
            val drops = world.server.reloadableRegistries.getLootTable(key).generateLoot(lootParams)
            if (drops.isNotEmpty()) {
                lastGenTime[pid] = now
                heldItems[pid] = drops
            }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in heldItems
        override fun cleanup(pokemonId: UUID) {
            lastGenTime.remove(pokemonId)
            heldItems.remove(pokemonId)
            failedDeposits.remove(pokemonId)
        }
    }

    object DiveCollector : Worker {
        override val name = "dive_collector"
        override val priority = WorkerPriority.MOVE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("waterfall")
        private val lastGenTime = mutableMapOf<UUID, Long>()
        private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
        private val failedDeposits = mutableMapOf<UUID, MutableSet<BlockPos>>()

        init {
            JobConfigManager.registerDefault("production", name, JobConfig(
                enabled = true,
                cooldownSeconds = 210,
                qualifyingMoves = qualifyingMoves.toList(),
                requiresWater = true,
                lootTables = listOf("cobblemon:gameplay/pickup"),
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            return moves.any { it in eff }
        }

        override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
            val world = context.world
            val origin = context.origin
            val pid = pokemonEntity.pokemon.uuid
            if (config.requiresWater == true && !pokemonEntity.isTouchingWater) return
            val held = heldItems[pid]
            if (held.isNullOrEmpty()) {
                failedDeposits.remove(pid)
                produce(world, origin, pokemonEntity)
            } else {
                if (context is JobContext.Party) {
                    CobbleCrewInventoryUtils.deliverToPlayer(context.player, held, pokemonEntity)
                    heldItems.remove(pid)
                    failedDeposits.remove(pid)
                    return
                }
                CobbleCrewInventoryUtils.handleDepositing(world, origin, pokemonEntity, held, failedDeposits, heldItems)
            }
        }

        private fun produce(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val baseCd = (config.cooldownSeconds.takeIf { it > 0 } ?: 210) * 20L
            val cd = WorkSpeedBoostManager.adjustCooldown(baseCd, origin, now)
            if (now - last < cd) return

            val tables = (config.lootTables ?: emptyList()).ifEmpty { listOf("cobblemon:gameplay/pickup") }
                .mapNotNull { Identifier.tryParse(it) }
            if (tables.isEmpty()) return

            val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
                .add(LootContextParameters.ORIGIN, origin.toCenterPos())
                .add(LootContextParameters.THIS_ENTITY, pokemonEntity)
                .build(LootContextTypes.CHEST)

            val key = RegistryKey.of(RegistryKeys.LOOT_TABLE, tables.random())
            val drops = world.server.reloadableRegistries.getLootTable(key).generateLoot(lootParams)
            if (drops.isNotEmpty()) {
                lastGenTime[pid] = now
                heldItems[pid] = drops
            }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in heldItems
        override fun cleanup(pokemonId: UUID) {
            lastGenTime.remove(pokemonId)
            heldItems.remove(pokemonId)
            failedDeposits.remove(pokemonId)
        }
    }

    object DigSiteExcavator : Worker {
        override val name = "dig_site_excavator"
        override val priority = WorkerPriority.TYPE
        override val targetCategory = BlockCategory.SUSPICIOUS

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("dig")
        private val lastGenTime = mutableMapOf<UUID, Long>()
        private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
        private val failedDeposits = mutableMapOf<UUID, MutableSet<BlockPos>>()
        private val targets = mutableMapOf<UUID, BlockPos>()

        init {
            JobConfigManager.registerDefault("production", name, JobConfig(
                enabled = true,
                cooldownSeconds = 120,
                qualifyingMoves = qualifyingMoves.toList(),
                lootTables = listOf("cobblemon:gameplay/pickup"),
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            return moves.any { it in eff }
        }

        override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
            val world = context.world
            val origin = context.origin
            val pid = pokemonEntity.pokemon.uuid
            val held = heldItems[pid]
            if (!held.isNullOrEmpty()) {
                if (context is JobContext.Party) {
                    CobbleCrewInventoryUtils.deliverToPlayer(context.player, held, pokemonEntity)
                    heldItems.remove(pid)
                    failedDeposits.remove(pid)
                    return
                }
                CobbleCrewInventoryUtils.handleDepositing(world, origin, pokemonEntity, held, failedDeposits, heldItems)
                return
            }
            failedDeposits.remove(pid)
            val target = targets[pid]
            if (target == null) {
                val found = findDigSpot(world, origin) ?: return
                if (!CobbleCrewNavigationUtils.isTargeted(found, world)) {
                    CobbleCrewNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                    CobbleCrewNavigationUtils.navigateTo(pokemonEntity, found)
                }
                return
            }
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.COMPOSTER, 3.0, WorkPhase.HARVESTING)) {
                generateLoot(world, target, pokemonEntity)
                CobbleCrewNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
            }
        }

        private fun findDigSpot(world: World, origin: BlockPos): BlockPos? {
            val cached = CobbleCrewCacheManager.getTargets(origin, BlockCategory.SUSPICIOUS)
            return cached
                .filter { pos ->
                    val above = world.getBlockState(pos.up())
                    above.isAir && !CobbleCrewNavigationUtils.isRecentlyExpired(pos, world)
                }
                .minByOrNull { it.getSquaredDistance(origin) }
        }

        private fun generateLoot(world: World, pos: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val baseCd = (config.cooldownSeconds.takeIf { it > 0 } ?: 120) * 20L
            val cd = WorkSpeedBoostManager.adjustCooldown(baseCd, pos, now)
            if (now - last < cd) return

            val tables = (config.lootTables ?: emptyList()).ifEmpty { listOf("cobblemon:gameplay/pickup") }
                .mapNotNull { Identifier.tryParse(it) }
            if (tables.isEmpty()) return

            val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
                .add(LootContextParameters.ORIGIN, pos.toCenterPos())
                .add(LootContextParameters.THIS_ENTITY, pokemonEntity)
                .build(LootContextTypes.CHEST)

            val key = RegistryKey.of(RegistryKeys.LOOT_TABLE, tables.random())
            val drops = world.server.reloadableRegistries.getLootTable(key).generateLoot(lootParams)
            if (drops.isNotEmpty()) {
                lastGenTime[pid] = now
                heldItems[pid] = drops
            }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in heldItems || pokemonId in targets
        override fun cleanup(pokemonId: UUID) {
            lastGenTime.remove(pokemonId)
            heldItems.remove(pokemonId)
            failedDeposits.remove(pokemonId)
            targets.remove(pokemonId)
        }
    }

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
            // Loot-based (migrated from legacy)
            FishingLooter,
            PickupLooter,
            DiveCollector,
            DigSiteExcavator,
        )
    }
}
