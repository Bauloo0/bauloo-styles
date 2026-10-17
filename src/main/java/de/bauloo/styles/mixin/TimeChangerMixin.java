package de.bauloo.styles.mixin;

import de.bauloo.styles.StylesUiConfig;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Time changer (Sky tab): your own time of day on your screen only - sky, sun, moon and light all
 * read World.getTimeOfDay (the 1.21.11 timelines too). Only the client world; the server's time is
 * untouched and comes back with "Server".
 */
@Mixin(World.class)
public class TimeChangerMixin {

	@Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
	private void baulooStyles$time(CallbackInfoReturnable<Long> cir) {
		int time = StylesUiConfig.timeOverride;
		if (time >= 0 && (Object) this instanceof ClientWorld) {
			cir.setReturnValue((long) time);
		}
	}
}
