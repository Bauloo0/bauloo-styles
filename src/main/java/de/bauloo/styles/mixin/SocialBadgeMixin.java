package de.bauloo.styles.mixin;

import de.bauloo.styles.Watermark;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Social badge: the recipe book button draws your platform logo instead of the book.
 * Drawn right where the button draws itself, so it sits right however a mod scales the screen.
 */
@Mixin(TexturedButtonWidget.class)
public abstract class SocialBadgeMixin {

	@Shadow @Final protected ButtonTextures textures;

	@Inject(method = "drawIcon", at = @At("HEAD"), cancellable = true)
	private void baulooStyles$badge(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (textures.enabled().getPath().contains("recipe_book")
				&& Watermark.drawBadge(ctx, (TexturedButtonWidget) (Object) this)) {
			ci.cancel();
		}
	}
}
