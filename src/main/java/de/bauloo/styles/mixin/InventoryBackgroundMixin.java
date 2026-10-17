package de.bauloo.styles.mixin;

import de.bauloo.styles.StylesUiConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clear background (Inventory tab): the inventory and chests open without the dark veil and the
 * blur behind them, so the world stays visible. Only in a world and only for item screens -
 * menus like pause or options keep their normal background.
 */
@Mixin(Screen.class)
public class InventoryBackgroundMixin {

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void baulooStyles$clear(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (StylesUiConfig.invClearBackground && client.world != null && (Object) this instanceof HandledScreen<?>) {
			// subtitles wait for the background to be drawn; let them through
			client.inGameHud.renderDeferredSubtitles();
			ci.cancel();
		}
	}
}
