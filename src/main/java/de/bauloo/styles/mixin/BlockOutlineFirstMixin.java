package de.bauloo.styles.mixin;

import de.bauloo.styles.BlockOutline;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block Outline always wins: with our outline on, we draw it ourselves at the very start of
 * the outline method and skip the rest. A low priority makes this run before other outline
 * mods, so their outline never replaces ours. The lines are drawn straight through
 * VertexRendering, not through WorldRenderer.drawBlockOutline: other mods hook that method and
 * expect their own setup from earlier in the outline method, which never ran here (FireClient
 * crashed with a NullPointerException that way).
 */
@Mixin(value = WorldRenderer.class, priority = 100)
public abstract class BlockOutlineFirstMixin {

	@Inject(method = "renderTargetBlockOutline", at = @At("HEAD"), cancellable = true)
	private void baulooStyles$ourOutlineFirst(VertexConsumerProvider.Immediate consumers, MatrixStack matrices,
			boolean translucent, WorldRenderState world, CallbackInfo ci) {
		if (!BlockOutline.active()) {
			return;
		}
		OutlineRenderState outline = world.outlineRenderState;
		ci.cancel();
		if (outline == null || outline.isTranslucent() != translucent) {
			return; // vanilla draws nothing in this pass either
		}
		Vec3d cam = world.cameraRenderState.pos;
		BlockPos pos = outline.pos();
		VertexConsumer lines = consumers.getBuffer(RenderLayers.lines());
		VertexRendering.drawOutline(matrices, lines, outline.shape(), pos.getX() - cam.x, pos.getY() - cam.y,
				pos.getZ() - cam.z, BlockOutline.color(), BlockOutline.width());
		consumers.drawCurrentLayer();
	}
}
