package de.bauloo.styles.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.bauloo.styles.StylesUiConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hitbox Color when the CustomHitboxes mod draws the hitboxes: its outline box and its filled
 * box read red, green and blue from its own colour settings; those values are swapped for ours
 * (the fill keeps its own see-through alpha).
 */
@Pseudo
@Mixin(targets = "walksy.customhitboxes.render.vertex.VertexRenderer", remap = false, priority = 2000)
public class CustomHitboxesColorMixin {

	@Unique
	private static boolean baulooStyles$logged;

	@ModifyExpressionValue(method = {"drawOutlineBox", "drawFilledBox"}, require = 0, remap = false,
			at = @At(value = "INVOKE", remap = false,
					target = "Lmain/walksy/lib/core/config/local/options/type/WalksyLibColor;getRed()I"))
	private static int baulooStyles$red(int red) {
		if (!baulooStyles$logged) {
			baulooStyles$logged = true;
			de.bauloo.styles.BaulooStylesClient.LOGGER.info("[HitboxColor] CustomHitboxes outline hooked, custom={}", StylesUiConfig.hitboxCustom);
		}
		return StylesUiConfig.hitboxCustom ? (StylesUiConfig.hitboxColor >> 16) & 0xFF : red;
	}

	@ModifyExpressionValue(method = {"drawOutlineBox", "drawFilledBox"}, require = 0, remap = false,
			at = @At(value = "INVOKE", remap = false,
					target = "Lmain/walksy/lib/core/config/local/options/type/WalksyLibColor;getGreen()I"))
	private static int baulooStyles$green(int green) {
		return StylesUiConfig.hitboxCustom ? (StylesUiConfig.hitboxColor >> 8) & 0xFF : green;
	}

	@ModifyExpressionValue(method = {"drawOutlineBox", "drawFilledBox"}, require = 0, remap = false,
			at = @At(value = "INVOKE", remap = false,
					target = "Lmain/walksy/lib/core/config/local/options/type/WalksyLibColor;getBlue()I"))
	private static int baulooStyles$blue(int blue) {
		return StylesUiConfig.hitboxCustom ? StylesUiConfig.hitboxColor & 0xFF : blue;
	}
}
