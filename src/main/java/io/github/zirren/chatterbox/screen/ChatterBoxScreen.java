package io.github.zirren.chatterbox.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.I18n;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

/**
 * Small base for all ChatterBox screens: back-navigation to a parent screen
 * and a shared centered-title helper.
 */
abstract class ChatterBoxScreen extends Screen {

	protected final @Nullable Screen parent;

	protected ChatterBoxScreen(Component title, @Nullable Screen parent) {
		super(title);
		this.parent = parent;
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(parent);
		}
	}

	protected void drawTitle(GuiGraphicsExtractor g, int color) {
		g.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 6, color, true);
	}

	/** "Label: value" component used by cycle buttons. */
	protected static Component cycleLabel(String labelKey, String value) {
		return Component.literal(I18n.get(labelKey) + ": " + value);
	}
}
