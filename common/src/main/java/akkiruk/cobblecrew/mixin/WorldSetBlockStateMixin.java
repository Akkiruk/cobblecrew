/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.mixin;

import akkiruk.cobblecrew.listeners.BlockChangeNotifier;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts block changes to update pasture caches in real time,
 * replacing the 30-second scan cooldown for most block changes.
 */
@Mixin(World.class)
public class WorldSetBlockStateMixin {

	@Inject(
		method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
		at = @At("RETURN")
	)
	private void cobblecrew$onBlockChanged(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			BlockChangeNotifier.INSTANCE.onBlockChanged((World)(Object)this, pos, state);
		}
	}
}
