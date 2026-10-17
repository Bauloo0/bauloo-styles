package de.bauloo.styles.mixin;

import de.bauloo.styles.HandPreview;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a hand slider (totem or shield size, shield opacity) is dragged in the styles
 * menu, the main hand (right or left, as set in the game options) shows that item
 * for a moment, so you see the change even when you are not holding one. Only this frame's drawing changes, the inventory is untouched.
 */
@Mixin(HeldItemRenderer.class)
public class HandPreviewMixin {

	@Shadow
	private ItemStack mainHand;
	@Shadow
	private float equipProgressMainHand;
	@Shadow
	private float lastEquipProgressMainHand;

	@Unique
	private ItemStack baulooStyles$realMainHand;
	@Unique
	private float baulooStyles$realProgress;
	@Unique
	private float baulooStyles$realLastProgress;

	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
			at = @At("HEAD"))
	private void baulooStyles$showPreview(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue queue,
			ClientPlayerEntity player, int light, CallbackInfo ci) {
		ItemStack preview = HandPreview.stack();
		if (preview == null) {
			return;
		}
		baulooStyles$realMainHand = mainHand;
		baulooStyles$realProgress = equipProgressMainHand;
		baulooStyles$realLastProgress = lastEquipProgressMainHand;
		mainHand = preview;
		equipProgressMainHand = 1.0F;
		lastEquipProgressMainHand = 1.0F;
	}

	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
			at = @At("RETURN"))
	private void baulooStyles$restore(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue queue,
			ClientPlayerEntity player, int light, CallbackInfo ci) {
		if (baulooStyles$realMainHand != null) {
			mainHand = baulooStyles$realMainHand;
			equipProgressMainHand = baulooStyles$realProgress;
			lastEquipProgressMainHand = baulooStyles$realLastProgress;
			baulooStyles$realMainHand = null;
		}
	}
}
