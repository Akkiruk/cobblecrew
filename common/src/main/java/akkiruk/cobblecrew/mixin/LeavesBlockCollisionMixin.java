/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.mixin;

import akkiruk.cobblecrew.state.StateManager;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.class)
public class LeavesBlockCollisionMixin {
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void allowWorkerPokemonThrough(BlockState state, BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!((Object) this instanceof LeavesBlock)) return;
        if (context instanceof EntityShapeContext entityContext) {
            var entity = entityContext.getEntity();
            if (entity instanceof PokemonEntity pokemon) {
                var workerState = StateManager.INSTANCE.get(pokemon.getPokemon().getUuid());
                if (workerState != null && workerState.getActiveJob() != null) {
                    cir.setReturnValue(VoxelShapes.empty());
                }
            }
        }
    }
}
