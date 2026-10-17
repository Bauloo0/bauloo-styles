package de.bauloo.styles.mixin;

import de.bauloo.styles.CrystalSeeThrough;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * See-through crystals without an extra mod: vanilla draws the crystal with a cutout layer, where a
 * pixel is either fully there or fully gone. When the active crystal texture has half-clear pixels,
 * the crystal is drawn with a translucent layer instead, so those pixels show as glass.
 */
@Mixin(EndCrystalEntityRenderer.class)
public class CrystalSeeThroughMixin {

	@ModifyArg(method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"),
			index = 3, require = 0) // another crystal mod may draw crystals itself - then this just does nothing
	private RenderLayer baulooStyles$seeThrough(RenderLayer layer) {
		return CrystalSeeThrough.active() ? CrystalSeeThrough.layer() : layer;
	}
}
