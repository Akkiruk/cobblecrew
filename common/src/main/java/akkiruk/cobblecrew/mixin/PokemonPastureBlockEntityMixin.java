/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.mixin;

import akkiruk.cobblecrew.jobs.PastureWorkerManager;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thin shim — all logic lives in {@link PastureWorkerManager}.
 */
@Mixin(PokemonPastureBlockEntity.class)
public class PokemonPastureBlockEntityMixin {

	@Inject(at = @At("TAIL"), method = "TICKER$lambda$0")
	private static void onPastureTick(World world, BlockPos pos, BlockState state, PokemonPastureBlockEntity pasture, CallbackInfo ci) {
		PastureWorkerManager.INSTANCE.tickPasture(world, pos, pasture);
	}

	@Inject(method = "onBroken()V", at = @At("TAIL"), remap = false)
	private void onPastureBroken(CallbackInfo ci) {
		PastureWorkerManager.INSTANCE.onPastureBroken((PokemonPastureBlockEntity)(Object)this);
	}
}
