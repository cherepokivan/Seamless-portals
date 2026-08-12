package com.seamlessportals.client.mixin;

import com.seamlessportals.client.SeamlessPortalsClient;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the vanilla animated Nether-portal mesh before chunk rendering.
 *
 * <p>The old approach tried to draw a second surface at the same depth as the
 * vanilla translucent quad, so the original texture could win the depth test.
 * By preventing that quad from entering the chunk mesh, the custom depth-tested
 * portal scene is the only surface inside the obsidian frame.</p>
 */
@Mixin(ModelBlockRenderer.class)
public final class ModelBlockRendererMixin {
    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void seamlessPortals$hideVanillaPortal(
        BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
        BlockPos position, BlockState state, BlockStateModel model, long seed,
        CallbackInfo callback
    ) {
        if (state.is(Blocks.NETHER_PORTAL) && SeamlessPortalsClient.getConfig().enabled) {
            callback.cancel();
        }
    }
}
