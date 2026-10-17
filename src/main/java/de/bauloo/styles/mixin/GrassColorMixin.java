package de.bauloo.styles.mixin;

import de.bauloo.styles.GrassColor;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.biome.ColorResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Grass color: grass blocks, tall grass and ferns get your colour (Extras or the Grass collection)
 * instead of the biome colour. Answered at HEAD: Sodium Extra with "Biome Colors" off cancels
 * getGrassColor at RETURN with the default green, and a cancelled RETURN callback skips every
 * RETURN callback after it - priority alone did not help. Grey world is applied on top (GrassColor.shown).
 */
@Mixin(value = BiomeColors.class, priority = 2000)
public class GrassColorMixin {

	@Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
	private static void baulooStyles$grass(BlockRenderView world, BlockPos pos, ColorResolver resolver,
										   CallbackInfoReturnable<Integer> cir) {
		if (resolver == BiomeColors.GRASS_COLOR) {
			GrassColor.mixinCalls++;
			if (GrassColor.current() >= 0) {
				cir.setReturnValue(GrassColor.shown());
			}
		}
	}

	@Inject(method = "getGrassColor", at = @At("HEAD"), cancellable = true)
	private static void baulooStyles$grassFirst(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		GrassColor.mixinCalls++;
		if (GrassColor.current() >= 0) {
			cir.setReturnValue(GrassColor.shown());
		}
	}
}
