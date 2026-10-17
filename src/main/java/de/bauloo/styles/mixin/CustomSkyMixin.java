package de.bauloo.styles.mixin;

import de.bauloo.styles.CustomSky;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sky styles without Skyboxify: the packs' OptiFine skies go in right before the sun and moon, like OptiFine. */
@Mixin(SkyRendering.class)
public class CustomSkyMixin {

	@Inject(method = "renderCelestialBodies", at = @At("HEAD"), require = 0)
	private void baulooStyles$customSky(MatrixStack matrices, float sunAngle, float moonAngle, float starAngle,
			MoonPhase moonPhase, float alpha, float starBrightness, CallbackInfo ci) {
		try {
			CustomSky.render(matrices, sunAngle);
		} catch (Throwable t) {
			// never break the sky over a sky style - but say once what went wrong
			CustomSky.failed(t);
		}
	}
}
