package de.bauloo.styles.mixin;

import de.bauloo.styles.PlayerOutline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Player Outline: tells the renderer which players get the glow outline. */
@Mixin(MinecraftClient.class)
public class PlayerOutlineMixin {

	@Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
	private void baulooStyles$playerOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (PlayerOutline.outlines(entity)) {
			cir.setReturnValue(true);
		}
	}
}
