package de.bauloo.styles.mixin;

import de.bauloo.styles.AnchorGlow;
import de.bauloo.styles.FireColor;
import de.bauloo.styles.ShieldColor;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A new atlas holds the pack's textures again: write anchor charge, fire and shield colours back in. */
@Mixin(SpriteAtlasTexture.class)
public class AnchorAtlasMixin {

	@Shadow @Final private Identifier id;

	@Inject(method = "create", at = @At("TAIL"))
	private void baulooStyles$anchorGlow(SpriteLoader.StitchResult result, CallbackInfo ci) {
		if (SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE.equals(id)) {
			AnchorGlow.applyLive();
			de.bauloo.styles.AnchorPixelGlow.applyLive();
			FireColor.atlasRebuilt();
		} else if (TexturedRenderLayers.SHIELD_PATTERNS_ATLAS_TEXTURE.equals(id)) {
			ShieldColor.atlasRebuilt();
		}
	}
}
