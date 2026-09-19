package io.github.zirren.chatterbox.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.I18n;

import org.jspecify.annotations.Nullable;

import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.config.Shortcut;
import io.github.zirren.chatterbox.config.ShortcutsDefaults;

/**
 * Shortcut editor: static text shortcuts are clickable to edit; live-value
 * (dynamic) shortcuts toggle on/off when clicked.
 */
public class ShortcutsScreen extends ChatterBoxScreen {

	private ShortcutList list;

	public ShortcutsScreen(net.minecraft.client.gui.screens.Screen parent) {
		super(Component.translatable("chatterbox.shortcuts.title"), parent);
	}

	@Override
	protected void init() {
		list = new ShortcutList(this.minecraft, this.width, this.height - 30 - 76, 30, 24);
		list.refresh();
		addRenderableWidget(list);

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.shortcuts.add"), b -> {
			this.minecraft.gui.setScreen(new ShortcutEditScreen(this, new Shortcut("", ""), false));
		}).pos(this.width / 2 - 155, this.height - 26).size(150, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.back"), b -> onClose())
				.pos(this.width / 2 + 5, this.height - 26).size(150, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
		int y = this.height - 40;
		String hint = I18n.get("chatterbox.shortcuts.static") + " · " + I18n.get("chatterbox.shortcuts.dynamic");
		g.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, y, 0x707070, false);
	}

	private class ShortcutList extends AbstractSelectionList<ShortcutList.Entry> {

		ShortcutList(Minecraft client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void refresh() {
			clearEntries();
			for (Shortcut sc : Config.get().shortcuts) {
				addEntry(new StaticRow(sc));
			}
			for (String token : ShortcutsDefaults.DYNAMIC_TOKENS) {
				addEntry(new DynamicRow(token));
			}
		}

		@Override
		public int getRowWidth() {
			return Math.min(460, this.width - 24);
		}

		@Override
		protected int scrollBarX() {
			return this.width / 2 + this.getRowWidth() / 2 + 2;
		}

		@Override
		public void setSelected(@Nullable Entry entry) {
			super.setSelected(entry);
			if (entry instanceof StaticRow row) {
				ShortcutsScreen.this.minecraft.gui.setScreen(new ShortcutEditScreen(ShortcutsScreen.this, row.shortcut, true));
			} else if (entry instanceof DynamicRow row) {
				Config.get().toggleDynamicShortcut(row.token);
			}
		}

		abstract class Entry extends AbstractSelectionList.Entry<Entry> {
		}

		private class StaticRow extends Entry {
			private final Shortcut shortcut;

			StaticRow(Shortcut shortcut) {
				this.shortcut = shortcut;
			}

			@Override
			public Component getNarration() {
				return Component.literal(shortcut.token);
			}

			@Override
			public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
				Font f = ShortcutsScreen.this.font;
				int left = ShortcutList.this.getRowLeft() + 2;
				int y = getContentY() + 2;
				int w = ShortcutList.this.getRowWidth();
				if (hovered) {
					g.fill(left - 2, getContentY(), left + w - 4, getContentY() + 24, 0x25FFFFFF);
				}
				boolean on = shortcut.enabled;
				String token = "{" + shortcut.token + "}";
				String value = shortcut.replacement;
				g.text(f, f.plainSubstrByWidth(token, w - 8), left, y,
						on ? 0x55FF55 : 0x707070, false);
				g.text(f, f.plainSubstrByWidth("→ " + value, w - 8), left + 4, y + 12, on ? 0xFFFFFF : 0x707070, false);
			}
		}

		private class DynamicRow extends Entry {
			private final String token;

			DynamicRow(String token) {
				this.token = token;
			}

			@Override
			public Component getNarration() {
				return Component.literal(token);
			}

			@Override
			public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
				Font f = ShortcutsScreen.this.font;
				int left = ShortcutList.this.getRowLeft() + 2;
				int y = getContentY() + 2;
				int w = ShortcutList.this.getRowWidth();
				if (hovered) {
					g.fill(left - 2, getContentY(), left + w - 4, getContentY() + 24, 0x25FFFFFF);
				}
				boolean on = Config.get().isDynamicShortcutEnabled(token);
				String desc = ShortcutsDefaults.DYNAMIC_DESCRIPTIONS.getOrDefault(token, "");
				g.text(f, f.plainSubstrByWidth("{" + token + "}", w - 8), left, y,
						on ? 0xFFD35C : 0x707070, false);
				g.text(f, f.plainSubstrByWidth("→ " + desc, w - 8), left + 4, y + 12, on ? 0xB8B8B8 : 0x606060, false);
			}
		}
	}

}
