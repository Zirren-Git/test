package io.github.zirren.chatterbox;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/**
 * ChatterBox keybinds. Registered into the vanilla controls screen
 * (Miscellaneous category).
 */
public final class Keybinds {
	/** Opens the chat search screen (default K). */
	public static KeyMapping SEARCH;
	/** Pins/unpins the hovered chat message (default unbound; Ctrl+P also works in chat). */
	public static KeyMapping PIN;
	/** Opens the ChatterBox settings screen (default unbound). */
	public static KeyMapping CONFIG;
	/** Switch to the next chat folder (default unbound). */
	public static KeyMapping NEXT_FOLDER;
	/** Switch to the previous chat folder (default unbound). */
	public static KeyMapping PREV_FOLDER;

	private Keybinds() {
	}

	public static void register() {
		SEARCH = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.chatterbox.search", InputConstants.KEY_K, KeyMapping.Category.MISC));
		PIN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.chatterbox.pin", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC));
		CONFIG = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.chatterbox.config", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC));
		NEXT_FOLDER = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.chatterbox.next_folder", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC));
		PREV_FOLDER = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.chatterbox.prev_folder", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC));
	}
}
