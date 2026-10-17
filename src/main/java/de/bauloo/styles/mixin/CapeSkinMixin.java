package de.bauloo.styles.mixin;

import de.bauloo.styles.Cosmetics;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Bauloo cape straight in the player's skin: cape mods like Wavey Capes read getSkin()
 * of the player instead of the render state, so CosmeticCapeMixin alone was not enough.
 * Client side only - nobody else sees it.
 */
@Mixin(AbstractClientPlayerEntity.class)
public class CapeSkinMixin {

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void baulooStyles$capeInSkin(CallbackInfoReturnable<SkinTextures> cir) {
		SkinTextures old = cir.getReturnValue();
		SkinTextures swapped = Cosmetics.withCape((PlayerLikeEntity) (Object) this, old);
		if (swapped != old) {
			cir.setReturnValue(swapped);
		}
	}
}
