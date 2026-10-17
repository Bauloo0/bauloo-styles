package de.bauloo.styles.mixin;

import de.bauloo.styles.Cosmetics;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cosmetic cape: swaps the cape (and, if wanted, the elytra) of your own player for the one
 * picked in the Cosmetics menu, and gives players their player cape on admin screens (PlayerCapes).
 * Only the render state changes, so only your screen sees it.
 */
@Mixin(PlayerEntityRenderer.class)
public class CosmeticCapeMixin {

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
			at = @At("TAIL"))
	private void baulooStyles$cosmeticCape(PlayerLikeEntity player, PlayerEntityRenderState state, float tickDelta,
			CallbackInfo ci) {
		if (state.skinTextures == null || Cosmetics.capeFor(player) == null) {
			return;
		}
		// usually getSkin() (CapeSkinMixin) already has it; this keeps it if something else built the state
		state.skinTextures = Cosmetics.withCape(player, state.skinTextures);
		state.capeVisible = true;
	}
}
