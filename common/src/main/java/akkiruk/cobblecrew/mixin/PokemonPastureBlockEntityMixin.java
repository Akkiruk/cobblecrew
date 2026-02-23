/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.mixin;

import akkiruk.cobblecrew.CobbleCrew;
import akkiruk.cobblecrew.cache.CobbleCrewCacheManager;
import akkiruk.cobblecrew.jobs.JobContext;
import akkiruk.cobblecrew.jobs.WorkerDispatcher;
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PokemonPastureBlockEntity.class)
public class PokemonPastureBlockEntityMixin {
	/** Tick Pokémon dispatch every N ticks instead of every tick. */
	private static final int POKEMON_TICK_INTERVAL = 5;

	@Inject(at = @At("TAIL"), method = "TICKER$lambda$0")
	private static void init(World world, BlockPos blockPos, BlockState blockState, PokemonPastureBlockEntity pastureBlock, CallbackInfo ci) {
		if (world.isClient) return;

		JobContext.Pasture context;
		try {
			context = new JobContext.Pasture(blockPos, world);
			WorkerDispatcher.INSTANCE.tickAreaScan(context);
		} catch (Exception e) {
			CobbleCrew.LOGGER.error("[CobbleCrew] - Error processing WorkerDispatcher tickAreaScan", e);
			return;
		}

		// Throttle Pokémon dispatch — no need to evaluate jobs every tick.
		// Stagger per-pasture using blockPos hash so pastures don't all tick the same frame.
		if ((world.getTime() + (blockPos.hashCode() & 0x7FFFFFFF)) % POKEMON_TICK_INTERVAL != 0) return;

		CobbleCrewInventoryUtils.INSTANCE.tickAnimations(world);

		List<PokemonPastureBlockEntity.Tethering> tetheredPokemon = pastureBlock.getTetheredPokemon();
        for (PokemonPastureBlockEntity.Tethering tethering : tetheredPokemon) {
            if (tethering == null) continue;

            Pokemon pokemon;
            try {
                pokemon = tethering.getPokemon();
            } catch (Exception e) {
                CobbleCrew.LOGGER.error("[CobbleCrew] - Failed to get Pokémon from tethering: {}", e.getMessage());
                continue;
            }

            if (pokemon == null || pokemon.isFainted()) continue;

            PokemonEntity pokemonEntity = pokemon.getEntity();
            if (pokemonEntity == null) continue;

            PoseType poseType = pokemonEntity.getDataTracker().get(PokemonEntity.getPOSE_TYPE());
            if (poseType == PoseType.SLEEP) continue;

            try {
                WorkerDispatcher.INSTANCE.tickPokemon(context, pokemonEntity);
            } catch (Exception e) {
                CobbleCrew.LOGGER.error("[CobbleCrew] - Error processing WorkerDispatcher.tickPokemon {}", e.getMessage());
            }
        }
	}

	@Inject(method = "onBroken()V", at = @At("TAIL"), remap = false)
	private void onPastureBroken(CallbackInfo ci) {
		PokemonPastureBlockEntity self = (PokemonPastureBlockEntity)(Object)this;
		World world = self.getWorld();
		if (world != null && !world.isClient) {
			CobbleCrewCacheManager.INSTANCE.removePasture(self.getPos());
		}
	}
}
