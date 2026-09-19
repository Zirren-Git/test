package io.github.zirren.chatterbox;

import net.minecraft.network.chat.Component;

/**
 * Small translation helper: flattens a translation key (with optional args)
 * to a plain string. Replaces {@code net.minecraft.locale.I18n}, which no
 * longer exists in Minecraft 26.x.
 */
public final class Lang {

	private Lang() {
	}

	public static String tr(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}
}
