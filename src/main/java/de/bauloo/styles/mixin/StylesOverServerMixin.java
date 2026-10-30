package de.bauloo.styles.mixin;

import de.bauloo.styles.ServerPacks;
import de.bauloo.styles.StylesUiConfig;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * "Styles on top": the enabled packs are listed bottom to top and a server pack always sits on top.
 * With the setting on, the style packs move above it - only while a server pack is there.
 */
@Mixin(ResourcePackManager.class)
public class StylesOverServerMixin {

	@Inject(method = "buildEnabledProfiles", at = @At("RETURN"), cancellable = true)
	private void baulooStyles$stylesOnTop(Collection<String> ids, CallbackInfoReturnable<List<ResourcePackProfile>> cir) {
		if (!StylesUiConfig.stylesOverServer) {
			return;
		}
		List<ResourcePackProfile> list = cir.getReturnValue();
		boolean server = false;
		for (ResourcePackProfile p : list) {
			if (p.getSource() == ResourcePackSource.SERVER) {
				server = true;
				break;
			}
		}
		if (!server) {
			return;
		}
		List<ResourcePackProfile> rest = new ArrayList<>();
		List<ResourcePackProfile> ours = new ArrayList<>();
		for (ResourcePackProfile p : list) {
			(ServerPacks.ours(p.getId()) ? ours : rest).add(p);
		}
		if (ours.isEmpty()) {
			return;
		}
		rest.addAll(ours);
		cir.setReturnValue(rest);
	}
}
