package de.bauloo.styles.mixin;

import de.bauloo.styles.StylesUiConfig;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "No explosion sounds" in Extras: crystal and anchor explosions stay silent (only on your client). */
@Mixin(SoundManager.class)
public class ExplosionSoundMixin {

	@Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
			at = @At("HEAD"), cancellable = true)
	private void baulooStyles$mute(SoundInstance sound, CallbackInfoReturnable<net.minecraft.client.sound.SoundSystem.PlayResult> cir) {
		if (StylesUiConfig.noExplosionSound && sound != null
				&& "minecraft".equals(sound.getId().getNamespace())
				&& "entity.generic.explode".equals(sound.getId().getPath())) {
			cir.setReturnValue(net.minecraft.client.sound.SoundSystem.PlayResult.NOT_STARTED);
		}
	}
}
