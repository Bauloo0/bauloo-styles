package de.bauloo.styles.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import de.bauloo.styles.LiveRecolor;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers which animation belongs to which sprite, so fire frames can be recoloured. */
@Mixin(SpriteContents.class)
public class SpriteAnimatorTrackMixin {

	@Inject(method = "createAnimator", at = @At("RETURN"))
	private void baulooStyles$track(GpuBufferSlice slice, int index, CallbackInfoReturnable<SpriteContents.Animator> cir) {
		if (cir.getReturnValue() != null) {
			LiveRecolor.ANIMATORS.put((SpriteContents) (Object) this, cir.getReturnValue());
		}
	}
}
