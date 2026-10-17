package de.bauloo.styles.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.bauloo.styles.StylesUiConfig;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Totem Pop Size: scales the big totem that pops up on screen when a totem is used.
 * Scales around the call instead of inside the method, so it still works when another
 * mod replaces the inside of renderFloatingItem. Only x and y: scaling z too would look
 * the same size through the perspective.
 */
@Mixin(InGameOverlayRenderer.class)
public class TotemPopSizeMixin {

	@WrapOperation(method = "renderOverlays",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameOverlayRenderer;renderFloatingItem(Lnet/minecraft/client/util/math/MatrixStack;FLnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V"))
	private void baulooStyles$popSize(InGameOverlayRenderer self, MatrixStack matrices, float tickProgress,
			OrderedRenderCommandQueue queue, Operation<Void> original) {
		StylesUiConfig.load();
		if (StylesUiConfig.totemPopSize == 100) {
			original.call(self, matrices, tickProgress, queue);
			return;
		}
		float factor = StylesUiConfig.totemPopSize / 100.0F;
		matrices.push();
		matrices.scale(factor, factor, 1.0F);
		try {
			original.call(self, matrices, tickProgress, queue);
		} finally {
			matrices.pop();
		}
	}
}
