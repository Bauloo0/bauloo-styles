package de.bauloo.styles.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.bauloo.styles.BlockOutline;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Block Outline: our colour and thickness for the lines around the targeted block. */
@Mixin(WorldRenderer.class)
public class BlockOutlineMixin {

	/** the wide dark line vanilla draws behind the normal one in high contrast mode */
	private static final float HIGH_CONTRAST_WIDTH = 7.0F;

	@WrapOperation(method = "renderTargetBlockOutline",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/render/WorldRenderer;drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;DDDLnet/minecraft/client/render/state/OutlineRenderState;IF)V"))
	private void baulooStyles$outline(WorldRenderer self, MatrixStack matrices, VertexConsumer lines,
									  double x, double y, double z, OutlineRenderState state, int color, float width,
									  Operation<Void> original) {
		if (!BlockOutline.active()) {
			original.call(self, matrices, lines, x, y, z, state, color, width);
		} else if (width != HIGH_CONTRAST_WIDTH) {
			original.call(self, matrices, lines, x, y, z, state, BlockOutline.color(), BlockOutline.width());
		}
	}
}
