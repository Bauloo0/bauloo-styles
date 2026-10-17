package de.bauloo.styles.mixin;

import de.bauloo.styles.StylesUiConfig;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.ShieldEntityModel;
import net.minecraft.client.render.item.model.special.ShieldModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.ComponentMap;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shield Opacity: shields held in hands (first and third person) are drawn see-through.
 * Swaps the solid layer for a translucent one and puts the alpha into the tint.
 */
@Mixin(ShieldModelRenderer.class)
public class ShieldOpacityMixin {

	@Unique
	private boolean baulooStyles$seeThrough;

	@Inject(method = "render(Lnet/minecraft/component/ComponentMap;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IIZI)V",
			at = @At("HEAD"))
	private void baulooStyles$start(ComponentMap data, ItemDisplayContext context, MatrixStack matrices,
			OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int seed, CallbackInfo ci) {
		StylesUiConfig.load();
		baulooStyles$seeThrough = (context.isFirstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) && StylesUiConfig.shieldOpacity < 100;
	}

	@Redirect(method = "render(Lnet/minecraft/component/ComponentMap;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IIZI)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/ShieldEntityModel;getLayer(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"))
	private RenderLayer baulooStyles$layer(ShieldEntityModel model, Identifier texture) {
		return baulooStyles$seeThrough ? RenderLayers.entityTranslucent(texture) : model.getLayer(texture);
	}

	@ModifyArg(method = "render(Lnet/minecraft/component/ComponentMap;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IIZI)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModelPart(Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IILnet/minecraft/client/texture/Sprite;ZZILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;I)V"),
			index = 8)
	private int baulooStyles$tint(int tint) {
		if (StylesUiConfig.shieldCustom) {
			// the texture is bright grey (ShieldColor), the colour comes from here
			tint = (tint & 0xFF000000) | (StylesUiConfig.shieldColor & 0xFFFFFF);
		}
		if (!baulooStyles$seeThrough) {
			return tint;
		}
		int alpha = Math.round(255 * Math.max(15, StylesUiConfig.shieldOpacity) / 100.0F);
		return (alpha << 24) | (tint & 0xFFFFFF);
	}
}
