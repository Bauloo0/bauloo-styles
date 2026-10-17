package de.bauloo.styles.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.bauloo.styles.HandPreview;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the shield preview is shown, the main hand is drawn blocking (as if right-click
 * were held), so the shield sits in front of you like in a fight. Drawing only.
 */
@Mixin(HeldItemRenderer.class)
public class HandPreviewBlockMixin {

	private static final String METHOD = "renderFirstPersonItem";

	@Unique
	private boolean baulooStyles$blocking;

	@Inject(method = METHOD, at = @At("HEAD"))
	private void baulooStyles$start(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
			float swingProgress, ItemStack stack, float equipProgress, MatrixStack matrices,
			OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
		ItemStack preview = HandPreview.stack();
		baulooStyles$blocking = hand == Hand.MAIN_HAND && preview != null && preview.isOf(Items.SHIELD)
				&& stack.isOf(Items.SHIELD);
	}

	@WrapOperation(method = METHOD, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isUsingItem()Z"))
	private boolean baulooStyles$using(AbstractClientPlayerEntity player, Operation<Boolean> original) {
		return baulooStyles$blocking || original.call(player);
	}

	@WrapOperation(method = METHOD, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getItemUseTimeLeft()I"))
	private int baulooStyles$timeLeft(AbstractClientPlayerEntity player, Operation<Integer> original) {
		return baulooStyles$blocking ? 72000 : original.call(player);
	}

	@WrapOperation(method = METHOD, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getActiveHand()Lnet/minecraft/util/Hand;"))
	private Hand baulooStyles$activeHand(AbstractClientPlayerEntity player, Operation<Hand> original) {
		return baulooStyles$blocking ? Hand.MAIN_HAND : original.call(player);
	}
}
