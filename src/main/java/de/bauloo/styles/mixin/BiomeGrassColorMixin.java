package de.bauloo.styles.mixin;

import de.bauloo.styles.GrassColor;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Grass color for renderers that skip BiomeColors: Sodium builds chunks with its own colour cache,
 * which asks the biome directly. This also covers biomes with their own fixed grass colour
 * (badlands, mushroom fields).
 */
@Mixin(value = Biome.class, priority = 2000)
public class BiomeGrassColorMixin {

	@Inject(method = "getGrassColorAt", at = @At("RETURN"), cancellable = true)
	private void baulooStyles$grass(double x, double z, CallbackInfoReturnable<Integer> cir) {
		int color = GrassColor.shown();
		if (GrassColor.current() >= 0) {
			cir.setReturnValue(color);
		}
	}
}
