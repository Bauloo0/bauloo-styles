package de.bauloo.styles.mixin;

import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.stream.Stream;

/**
 * Pack menus from other mods and clients build their own lists from ResourcePackOrganizer and never
 * call PackScreen.filter, so our packs are left out here too.
 */
@Mixin(ResourcePackOrganizer.class)
public class PackOrganizerMixin {

	@Inject(method = {"getDisabledPacks", "getEnabledPacks"}, at = @At("RETURN"), cancellable = true)
	private void baulooStyles$hideOurs(CallbackInfoReturnable<Stream<ResourcePackOrganizer.Pack>> cir) {
		cir.setReturnValue(cir.getReturnValue().filter(pack -> {
			String name = pack.getName().toLowerCase(Locale.ROOT);
			return !name.startsWith("bauloo_styles:")
					&& !name.contains("bauloo_upload_")
					&& !name.contains("bauloo_cloud_")
					&& !name.contains("bauloo_anchor_glow")
					&& !name.contains("bauloo_crystal_core")
					&& !name.contains("bauloo_glint");
		}));
	}
}
