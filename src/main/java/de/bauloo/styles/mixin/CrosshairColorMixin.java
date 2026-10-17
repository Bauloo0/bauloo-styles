package de.bauloo.styles.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import de.bauloo.styles.StylesCrosshair;
import de.bauloo.styles.StylesUiConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Crosshair Color: vanilla draws the crosshair inverted against the world; with a
 * custom colour (or gradient) it is drawn as a normal tinted texture instead.
 */
@Mixin(InGameHud.class)
public class CrosshairColorMixin {

	@WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"))
	private void baulooStyles$crosshairColor(DrawContext ctx, RenderPipeline pipeline, Identifier texture,
			int x, int y, int w, int h, Operation<Void> original) {
		StylesUiConfig.load();
		if (StylesUiConfig.crosshairCustom) {
			StylesCrosshair.draw(ctx, texture, x, y, w, h);
		} else {
			original.call(ctx, pipeline, texture, x, y, w, h);
		}
	}
}
