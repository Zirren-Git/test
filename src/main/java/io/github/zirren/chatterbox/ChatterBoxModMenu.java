package io.github.zirren.chatterbox;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.zirren.chatterbox.screen.ConfigScreen;

/**
 * Mod Menu integration: adds a config screen button in the mods list.
 *
 * <p>Fully guarded: if the config screen cannot be built in a broken or
 * hostile modded environment, the parent screen is returned instead of
 * crashing the game.</p>
 */
public class ChatterBoxModMenu implements ModMenuApi {
	private static final Logger LOGGER = LoggerFactory.getLogger("ChatterBox");

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> {
			try {
				return new ConfigScreen(parent);
			} catch (Throwable t) {
				LOGGER.error("ChatterBox: could not open its config screen", t);
				return parent;
			}
		};
	}
}
