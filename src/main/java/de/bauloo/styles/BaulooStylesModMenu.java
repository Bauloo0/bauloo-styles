package de.bauloo.styles;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds the styles menu to Mod Menu. Made by bauloo. */
public class BaulooStylesModMenu implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return BaulooStylesConfigScreen::new;
	}
}
