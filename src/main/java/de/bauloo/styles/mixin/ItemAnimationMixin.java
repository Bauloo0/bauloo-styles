package de.bauloo.styles.mixin;

import de.bauloo.styles.ItemAnimations;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Animated item textures (the Bauloo spears, animated totems ...) keep moving when Sodium Extra has
 * "Animations" off: that switch skips every Animator.tick() of every atlas. For the item atlas we tick
 * them here first; the atlas then uploads the changed frames as usual. Water, lava, fire and the other
 * block animations stay off.
 */
@Mixin(SpriteAtlasTexture.class)
public class ItemAnimationMixin {

	@Shadow
	private List<SpriteContents.Animator> animators;

	@Shadow
	@Final
	private Identifier id;

	@Inject(method = "tickAnimatedSprites", at = @At("HEAD"))
	private void baulooStyles$tickItems(CallbackInfo ci) {
		if (animators != null && !animators.isEmpty() && SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE.equals(id)
				&& ItemAnimations.sodiumExtraBlocksThem()) {
			for (SpriteContents.Animator animator : animators) {
				animator.tick();
			}
		}
	}
}
