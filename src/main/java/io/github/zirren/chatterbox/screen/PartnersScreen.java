package io.github.zirren.chatterbox.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.I18n;

import org.jspecify.annotations.Nullable;

import io.github.zirren.chatterbox.chat.ChatStore;
import io.github.zirren.chatterbox.config.Config;

/**
 * DM partner management: add a sub-folder manually, remove partners you no
 * longer chat with.
 */
public class PartnersScreen extends ChatterBoxScreen {

	private PartnerList list;
	private EditBox name;
	private Button removeButton;
	private @Nullable String selected;

	public PartnersScreen(net.minecraft.client.gui.screens.Screen parent) {
		super(Component.translatable("chatterbox.partners.title"), parent);
	}

	@Override
	protected void init() {
		list = new PartnerList(this.minecraft, this.width, this.height - 30 - 54, 30, 14);
		list.refresh();
		addRenderableWidget(list);

		removeButton = Button.builder(Component.translatable("chatterbox.partners.remove_selected"), b -> {
			if (selected != null) {
				ChatStore.INSTANCE.removeDmPartner(selected);
				selected = null;
				list.refresh();
				removeButton.active = false;
			}
		}).pos(this.width / 2 - 155, this.height - 26).size(150, 20).build();
		removeButton.active = false;
		addRenderableWidget(removeButton);

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.back"), b -> onClose())
				.pos(this.width / 2 + 5, this.height - 26).size(150, 20).build());

		name = new EditBox(this.font, this.width / 2 - 155, this.height - 66, 200, 16,
				Component.translatable("chatterbox.partners.name"));
		name.setMaxLength(32);
		name.setHint(Component.translatable("chatterbox.partners.name"));
		addRenderableWidget(name);

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.partners.add"), b -> {
			String player = name.getValue().trim();
			if (!player.isEmpty()) {
				ChatStore.INSTANCE.addDmPartner(player);
				name.setValue("");
				list.refresh();
			}
		}).pos(this.width / 2 + 55, this.height - 68).size(95, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
		if (ChatStore.INSTANCE.dmPartners().isEmpty()) {
			String msg = I18n.get("chatterbox.partners.empty");
			g.text(this.font, msg, this.width / 2 - this.font.width(msg) / 2, this.height / 2 - 20, 0x808080, false);
		}
	}

	private class PartnerList extends AbstractSelectionList<PartnerList.Entry> {

		PartnerList(Minecraft client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void refresh() {
			clearEntries();
			for (String partner : ChatStore.INSTANCE.dmPartners()) {
				addEntry(new Row(partner));
			}
		}

		@Override
		public int getRowWidth() {
			return Math.min(360, this.width - 24);
		}

		@Override
		protected int scrollBarX() {
			return this.width / 2 + this.getRowWidth() / 2 + 2;
		}

		@Override
		public void setSelected(@Nullable Entry entry) {
			super.setSelected(entry);
			if (entry instanceof Row row) {
				selected = row.partner;
			} else {
				selected = null;
			}
			removeButton.active = selected != null;
		}

		abstract class Entry extends AbstractSelectionList.Entry<Entry> {
		}

		private class Row extends Entry {
			private final String partner;

			Row(String partner) {
				this.partner = partner;
			}

			@Override
			public Component getNarration() {
				return Component.literal(partner);
			}

			@Override
			public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
				Font f = PartnersScreen.this.font;
				int left = PartnerList.this.getRowLeft() + 2;
				int y = getContentY() + 3;
				if (hovered || partner.equals(selected)) {
					g.fill(left - 2, getContentY(), left + PartnerList.this.getRowWidth() - 4, getContentY() + 14, 0x25FFFFFF);
				}
				g.text(f, partner, left + 2, y, 0xFFFFFF, false);
			}
		}
	}
}
