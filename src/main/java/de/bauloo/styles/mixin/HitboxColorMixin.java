package de.bauloo.styles.mixin;

import de.bauloo.styles.StylesUiConfig;
import net.minecraft.client.render.debug.EntityHitboxDebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Hitbox Color: the white F3+B hitbox box in the colour chosen in Extras. */
@Mixin(value = EntityHitboxDebugRenderer.class, priority = 2000)
public class HitboxColorMixin {

	@Unique
	private static boolean baulooStyles$logged;

	@ModifyArg(method = "drawHitbox", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/DrawStyle;stroked(I)Lnet/minecraft/client/render/DrawStyle;", ordinal = 0))
	private int baulooStyles$hitboxColor(int color) {
		StylesUiConfig.load();
		if (!baulooStyles$logged) {
			baulooStyles$logged = true;
			de.bauloo.styles.BaulooStylesClient.LOGGER.info("[HitboxColor] vanilla hitbox drawn, custom={}", StylesUiConfig.hitboxCustom);
		}
		return StylesUiConfig.hitboxCustom ? 0xFF000000 | StylesUiConfig.hitboxColor : color;
	}
}
