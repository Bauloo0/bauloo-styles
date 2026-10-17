package de.bauloo.styles.mixin;

import de.bauloo.styles.OutlinePreview;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menu previews draw you without the name tag. */
@Mixin(LivingEntityRenderer.class)
public class OutlinePreviewMixin {

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void baulooStyles$noName(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
		if (OutlinePreview.hideName) {
			state.displayName = null;
		}
	}
}
