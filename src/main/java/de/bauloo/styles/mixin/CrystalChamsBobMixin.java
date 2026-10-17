package de.bauloo.styles.mixin;

import de.bauloo.styles.CrystalAge;
import de.bauloo.styles.StylesUiConfig;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Crystal Spin with Custom End Crystals installed: that mod computes the bob in its own
 * getYOffset(time, offset, speed, height, delay), where time = sped-up age + frame delta.
 * The age part is turned back into the real age, so only the turning changes speed.
 */
@Pseudo
@Mixin(targets = "tektonikal.crystalchams.CrystalChams", remap = false)
public class CrystalChamsBobMixin {

	@ModifyVariable(method = "getYOffset(FFFFF)F", at = @At("HEAD"), argsOnly = true, ordinal = 0,
			require = 0, remap = false)
	private static float baulooStyles$normalBob(float time) {
		if (StylesUiConfig.crystalSpin == 100) {
			return time;
		}
		float delta = MinecraftClient.getInstance().getRenderTickCounter().getDynamicDeltaTicks();
		return (time - delta) / CrystalAge.factor() + delta;
	}
}
