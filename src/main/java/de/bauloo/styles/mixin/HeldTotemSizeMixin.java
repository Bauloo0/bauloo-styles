package de.bauloo.styles.mixin;

import de.bauloo.styles.StylesUiConfig;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Totem, Shield and Gap Size: scale the totem, shield or golden apple you hold in first person (either hand). */
@Mixin(HeldItemRenderer.class)
public class HeldTotemSizeMixin {

	@Unique
	private boolean baulooStyles$scaled;

	@Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
			at = @At("HEAD"))
	private void baulooStyles$scaleTotem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
			MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
		StylesUiConfig.load();
		int size = !context.isFirstPerson() ? 100
				: stack.isOf(Items.TOTEM_OF_UNDYING) ? StylesUiConfig.totemSize
				: stack.isOf(Items.SHIELD) ? StylesUiConfig.shieldSize
				: stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) ? StylesUiConfig.gapSize : 100;
		baulooStyles$scaled = size != 100;
		if (baulooStyles$scaled) {
			float f = size / 100.0F;
			matrices.push();
			matrices.scale(f, f, f);
		}
	}

	@Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
			at = @At("RETURN"))
	private void baulooStyles$restore(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
			MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
		if (baulooStyles$scaled) {
			baulooStyles$scaled = false;
			matrices.pop();
		}
	}
}
