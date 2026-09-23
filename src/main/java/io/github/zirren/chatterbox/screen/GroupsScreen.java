package io.github.zirren.chatterbox.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import io.github.zirren.chatterbox.chat.ChatStore;
import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.config.GroupChat;
import io.github.zirren.chatterbox.Lang;

/**
 * Group chat management: add, edit and delete groups. Click a group to
 * select it; double-click (or press Edit) to change it; Delete removes the
 * selected group after a confirm click.
 */
public class GroupsScreen extends ChatterBoxScreen {

	private static final long DOUBLE_CLICK_MS = 400L;
	private static final long DELETE_ARM_MS = 3000L;

	private GroupList list;
	private GroupList.Row selected;
	private long lastSelectTime;
	private boolean armedDelete;
	private long armedAt;

	private Button editButton;
	private Button deleteButton;

	public GroupsScreen(Screen parent) {
		super(Component.translatable("chatterbox.groups.title"), parent);
	}

	@Override
	protected void init() {
		list = new GroupList(this.minecraft, this.width, this.height - 30 - 54, 30, 26);
		list.refresh();
		addRenderableWidget(list);

		int w = 74;
		int gap = 4;
		int x0 = this.width / 2 - (w * 4 + gap * 3) / 2;
		int y = this.height - 26;

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.add"), b -> {
			openEdit(new GroupChat(""), false);
		}).pos(x0, y).size(w, 20).build());

		editButton = Button.builder(Component.translatable("chatterbox.groups.edit"), b -> {
			if (selected != null) {
				openEdit(selected.group, true);
			}
		}).pos(x0 + w + gap, y).size(w, 20).build();
		editButton.active = false;
		addRenderableWidget(editButton);

		deleteButton = Button.builder(Component.translatable("chatterbox.groups.delete"), b -> {
			if (selected == null) return;
			long now = System.currentTimeMillis();
			if (!armedDelete || now - armedAt > DELETE_ARM_MS) {
				// first click: ask for confirmation instead of deleting at once
				armedDelete = true;
				armedAt = now;
				deleteButton.setMessage(Component.translatable("chatterbox.groups.delete_confirm"));
				return;
			}
			String key = "#" + selected.group.name;
			Config.get().removeGroup(selected.group.name);
			selected = null;
			armedDelete = false;
			list.refresh();
			updateButtons();
			// if the deleted group is open in the chat, fall back to the DM overview
			if (ChatStore.INSTANCE.isInDmSubfolder() && key.equalsIgnoreCase(ChatStore.INSTANCE.activeDmPartner())) {
				ChatStore.INSTANCE.switchView(io.github.zirren.chatterbox.chat.Folder.DM, null);
			}
		}).pos(x0 + 2 * (w + gap), y).size(w, 20).build();
		deleteButton.active = false;
		addRenderableWidget(deleteButton);

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.back"), b -> onClose())
				.pos(x0 + 3 * (w + gap), y).size(w, 20).build());
	}

	private void openEdit(GroupChat group, boolean existing) {
		this.minecraft.gui.setScreen(new GroupEditScreen(this, group, existing));
	}

	private void updateButtons() {
		if (editButton != null) {
			editButton.active = selected != null;
		}
		if (deleteButton != null) {
			deleteButton.active = selected != null;
			if (!armedDelete) {
				deleteButton.setMessage(Component.translatable("chatterbox.groups.delete"));
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
		if (Config.get().groups.isEmpty()) {
			String msg = Lang.tr("chatterbox.groups.empty");
			drawWrapped(g, msg, this.height / 2 - 30);
		} else {
			String hint = Lang.tr("chatterbox.groups.hint");
			g.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, 19, 0xFF707070, false);
		}
	}

	/** Small centered word-wrap helper for the empty-state text. */
	private void drawWrapped(GuiGraphicsExtractor g, String text, int y) {
		int max = Math.min(360, this.width - 30);
		g.textWithWordWrap(this.font, Component.literal(text), this.width / 2 - max / 2, y, max, 0xFF808080);
	}

	private class GroupList extends AbstractSelectionList<GroupList.Entry> {

		GroupList(Minecraft client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void refresh() {
			clearEntries();
			for (GroupChat group : Config.get().groups) {
				addEntry(new Row(group));
			}
		}

		@Override
		public int getRowWidth() {
			return Math.min(420, this.width - 24);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}

		@Override
		protected int scrollBarX() {
			return this.width / 2 + this.getRowWidth() / 2 + 2;
		}

		@Override
		public void setSelected(@Nullable Entry entry) {
			super.setSelected(entry);
			if (entry instanceof Row row) {
				long now = System.currentTimeMillis();
				if (selected == row && now - lastSelectTime < DOUBLE_CLICK_MS) {
					// double-click on an already-selected group = edit
					lastSelectTime = 0;
					openEdit(row.group, true);
					return;
				}
				selected = row;
				lastSelectTime = now;
				armedDelete = false;
				updateButtons();
			}
		}

		abstract class Entry extends AbstractSelectionList.Entry<Entry> {
			// 26.x lists have no built-in click-to-select (see MentionRulesScreen)
			@Override
			public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
				if (event.button() == 0 && isMouseOver(event.x(), event.y())) {
					GroupList.this.setSelected(this);
					return true;
				}
				return false;
			}
		}

		private class Row extends Entry {
			private final GroupChat group;

			Row(GroupChat group) {
				this.group = group;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
				Font f = GroupsScreen.this.font;
				int left = GroupList.this.getRowLeft() + 2;
				int y = getContentY() + 2;
				boolean isSelected = GroupsScreen.this.selected == this;
				if (isSelected) {
					g.fill(left - 2, getContentY(), left + GroupList.this.getRowWidth() - 4, getContentY() + 26, 0x45FFFFFF);
				} else if (hovered) {
					g.fill(left - 2, getContentY(), left + GroupList.this.getRowWidth() - 4, getContentY() + 26, 0x25FFFFFF);
				}
				String title = "#" + group.name;
				String clipped = f.plainSubstrByWidth(title, GroupList.this.getRowWidth() - 8);
				g.text(f, clipped, left, y, 0xFFFFFFFF, false);
				String rest = group.members.isEmpty()
						? Lang.tr("chatterbox.groups.no_members")
						: String.join(", ", group.members);
				String restClipped = f.plainSubstrByWidth(rest, GroupList.this.getRowWidth() - 8);
				g.text(f, restClipped, left + 4, y + 12, 0xFF909090, false);
			}
		}
	}
}
