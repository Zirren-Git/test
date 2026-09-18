package io.github.zirren.chatterbox;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import io.github.zirren.chatterbox.screen.ConfigScreen;

/**
 * Mod Menu integration: adds a config screen button in the mods list.
 */
public class ChatterBoxModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
