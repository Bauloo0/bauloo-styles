package de.bauloo.styles.mixin;

import de.bauloo.styles.PlayerOutline;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Outline thickness: swaps the glow post effect for one with a wider blur. */
@Mixin(WorldRenderer.class)
public class OutlineWidthMixin {

	@ModifyArg(method = "render",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gl/ShaderLoader;loadPostEffect(Lnet/minecraft/util/Identifier;Ljava/util/Set;)Lnet/minecraft/client/gl/PostEffectProcessor;"),
			index = 0)
	private Identifier baulooStyles$width(Identifier id) {
		return PlayerOutline.postEffect(id);
	}
}
