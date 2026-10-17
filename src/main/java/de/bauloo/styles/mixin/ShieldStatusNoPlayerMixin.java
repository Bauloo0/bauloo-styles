package de.bauloo.styles.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ShieldStatus reads your own player while it draws any shield. On the title screen there is none,
 * so a shield icon there (the Styles menu, Mod Menu) crashed the game. Without a player: no cooldown.
 */
@Pseudo
@Mixin(targets = "walksy.shieldstatus.manager.ShieldStateManager", remap = false)
public class ShieldStatusNoPlayerMixin {

	@Inject(method = "isCoolingDown", at = @At("HEAD"), cancellable = true, require = 0)
	private void baulooStyles$noPlayer(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
		if (MinecraftClient.getInstance().player == null) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getCooldownProgress", at = @At("HEAD"), cancellable = true, require = 0)
	private void baulooStyles$noPlayerProgress(PlayerEntity player, CallbackInfoReturnable<Float> cir) {
		if (MinecraftClient.getInstance().player == null) {
			cir.setReturnValue(0.0F);
		}
	}
}
