package de.bauloo.styles.mixin;

import de.bauloo.styles.StylesUiConfig;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.ShieldEntityModel;
import net.minecraft.client.texture.SpriteHolder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.ComponentMap;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shield Opacity when the ShieldStatus mod is installed. It replaces the vanilla
 * shield renderer: its render method queues lambdas that draw the plate and handle
 * straight into the entity buffers on a translucent layer, coloured with its
 * status colour. So the alpha goes into that colour.
 */
@Pseudo
@Mixin(targets = "walksy.shieldstatus.render.ShieldItemModelRenderer", remap = false)
public class ShieldStatusOpacityMixin {

	/** Alpha for the shield being queued right now; the lambdas are static, so this is too. */
	@Unique
	private static volatile float baulooStyles$alpha = 1.0F;

	@Inject(method = "render", at = @At("HEAD"), require = 0, remap = false)
	private void baulooStyles$start(ComponentMap data, ShieldEntityModel model, SpriteHolder sprites,
			ItemDisplayContext context, MatrixStack matrices, OrderedRenderCommandQueue queue,
			int light, int overlay, int seed, boolean glint, CallbackInfo ci) {
		StylesUiConfig.load();
		boolean hand = context.isFirstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
				|| context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
		baulooStyles$alpha = hand && StylesUiConfig.shieldOpacity < 100
				? Math.max(15, StylesUiConfig.shieldOpacity) / 100.0F : 1.0F;
	}

	/**
	 * Own shield colour: ShieldStatus draws its own texture, not the atlas one. Its grayscale
	 * mode makes that texture grey, and the status tint (our colour) then colours it fully.
	 * Not for see-through shields: the grey copy loses their clear parts, so those are only tinted.
	 */
	@com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "render", require = 0, remap = false,
			at = @At(value = "FIELD", remap = false, target = "Lwalksy/shieldstatus/config/Config;grayscaleTexture:Z"))
	private boolean baulooStyles$grey(boolean original) {
		// a shield style with clear parts keeps its own texture: the grey copy would fill the holes
		return original || (StylesUiConfig.shieldCustom && !de.bauloo.styles.TextureAlpha.shieldHasHoles());
	}

	@Unique
	private static boolean baulooStyles$logged;

	@Unique
	private static boolean baulooStyles$colorLogged;

	@Unique
	private static int baulooStyles$withAlpha(int argb) {
		// Own shield colour: the texture is bright grey, so ShieldStatus' calm "ready" tint
		// (greenish) becomes our colour. Other status colours (like red while disabled) stay.
		if (StylesUiConfig.shieldCustom) {
			int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
			if (!baulooStyles$colorLogged) {
				baulooStyles$colorLogged = true;
				de.bauloo.styles.BaulooStylesClient.LOGGER.info("[ShieldColor] ShieldStatus colour {}", Integer.toHexString(argb));
			}
			boolean warning = r > g + 40 && r > b + 40;
			if (!warning) {
				argb = (argb & 0xFF000000) | (StylesUiConfig.shieldColor & 0xFFFFFF);
			}
		}
		float alpha = baulooStyles$alpha;
		if (alpha >= 1.0F) {
			return argb;
		}
		int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** Handle and plate without patterns: ModelPart.render(matrices, consumer, light, overlay, color). */
	@ModifyArg(method = {"lambda$render$0", "lambda$render$2"}, require = 0, remap = false, index = 4,
			at = @At(value = "INVOKE", remap = false,
					target = "Lnet/minecraft/class_630;method_22699(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;III)V"))
	private static int baulooStyles$partColor(int color) {
		if (!baulooStyles$logged) {
			baulooStyles$logged = true;
			de.bauloo.styles.BaulooStylesClient.LOGGER.info("Shield opacity hooked into ShieldStatus (alpha {})", baulooStyles$alpha);
		}
		return baulooStyles$withAlpha(color);
	}

	/** Plate with banner patterns: the colour is the last argument of renderCanvas. */
	@ModifyArg(method = "lambda$render$1", require = 0, remap = false, index = 11,
			at = @At(value = "INVOKE", remap = false,
					target = "Lwalksy/shieldstatus/render/ShieldItemModelRenderer;renderCanvas(Lnet/minecraft/class_4587;Lnet/minecraft/class_11701;Lnet/minecraft/class_4588;IILnet/minecraft/class_630;ZLnet/minecraft/class_1767;Lnet/minecraft/class_9307;ZZI)V"))
	private int baulooStyles$canvasColor(int color) {
		return baulooStyles$withAlpha(color);
	}
}
