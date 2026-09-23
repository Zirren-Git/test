package io.github.zirren.chatterbox.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.config.GroupChat;
import io.github.zirren.chatterbox.Lang;

/**
 * Edit (or create) one group chat: its name and its member list. Members are
 * managed with an add box and a removable list; everyone in the list gets a
 * whisper when you write in the group, and players running ChatterBox join
 * the group automatically on the first message.
 */
public class GroupEditScreen extends ChatterBoxScreen {

	private final GroupChat group;
	private final boolean existing;
	private final List<String> members;

	private EditBox name;
	private EditBox memberName;
	private Button removeButton;
	private MemberList list;

	public GroupEditScreen(Screen parent, GroupChat group, boolean existing) {
		super(Component.translatable(existing ? "chatterbox.groups.title" : "chatterbox.groups.add"), parent);
		this.group = group;
		this.existing = existing;
		this.members = new ArrayList<>(group.members);
	}

	@Override
	protected void init() {
		name = new EditBox(this.font, this.width / 2 - 100, 28, 200, 16,
				Component.translatable("chatterbox.groups.name"));
		name.setMaxLength(16);
		name.setValue(group.name);
		name.setHint(Component.translatable("chatterbox.groups.name_hint"));
		addRenderableWidget(name);
		setInitialFocus(name);

		memberName = new EditBox(this.font, this.width / 2 - 100, 52, 130, 16,
				Component.translatable("chatterbox.groups.member_hint"));
		memberName.setMaxLength(32);
		memberName.setHint(Component.translatable("chatterbox.groups.member_hint"));
		addRenderableWidget(memberName);

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.groups.add_member"), b -> {
			addMember();
		}).pos(this.width / 2 + 34, 50).size(66, 20).build());

		list = new MemberList(this.minecraft, this.width, this.height - 30 - 78 - 54, 78, 14);
		list.refresh();
		addRenderableWidget(list);

		// --- bottom row: remove member · save · cancel --------------------
		int w = 88;
		int gap = 4;
		int x0 = this.width / 2 - (w * 3 + gap * 2) / 2;
		removeButton = Button.builder(Component.translatable("chatterbox.groups.remove_member"), b -> {
			if (list.selected != null) {
				members.removeIf(m -> m.equalsIgnoreCase(list.selected));
				list.selected = null;
				list.refresh();
				updateRemoveButton();
			}
		}).pos(x0, this.height - 26).size(w, 20).build();
		removeButton.active = false;
		addRenderableWidget(removeButton);

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.groups.save"), b -> save())
				.pos(x0 + w + gap, this.height - 26).size(w, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.cancel"), b -> onClose())
				.pos(x0 + 2 * (w + gap), this.height - 26).size(w, 20).build());
	}

	private void addMember() {
		String player = memberName.getValue().trim();
		if (player.isEmpty() || player.contains(",")) return;
		boolean dup = false;
		for (String m : members) {
			if (m.equalsIgnoreCase(player)) {
				dup = true;
				break;
			}
		}
		if (!dup) {
			members.add(player);
			list.refresh();
		}
		memberName.setValue("");
	}

	private void updateRemoveButton() {
		if (removeButton != null) {
			removeButton.active = list != null && list.selected != null;
		}
	}

	private void save() {
		String value = GroupChat.normalizeName(name.getValue());
		if (value.isEmpty()) {
			// nothing to store without a name
			onClose();
			return;
		}
		group.name = value;
		group.members = new ArrayList<>(members);
		Config.get().putGroup(group);
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
		if (members.isEmpty()) {
			String msg = Lang.tr("chatterbox.groups.member_empty");
			g.text(this.font, msg, this.width / 2 - this.font.width(msg) / 2, 84, 0xFF808080, false);
		}
		String note = Lang.tr("chatterbox.groups.auto_note");
		int w = this.font.width(note);
		g.text(this.font, note, this.width / 2 - w / 2, this.height - 42, 0xFF707070, false);
	}

	private class MemberList extends AbstractSelectionList<MemberList.Entry> {

		private @Nullable String selected;

		MemberList(Minecraft client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void refresh() {
			clearEntries();
			for (String member : members) {
				addEntry(new Row(member));
			}
			updateRemoveButton();
		}

		@Override
		public int getRowWidth() {
			return Math.min(300, this.width - 24);
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
				selected = row.member;
			} else {
				selected = null;
			}
			updateRemoveButton();
		}

		abstract class Entry extends AbstractSelectionList.Entry<Entry> {
			// 26.x lists have no built-in click-to-select (see MentionRulesScreen)
			@Override
			public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
				if (event.button() == 0 && isMouseOver(event.x(), event.y())) {
					MemberList.this.setSelected(this);
					return true;
				}
				return false;
			}
		}

		private class Row extends Entry {
			private final String member;

			Row(String member) {
				this.member = member;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
				Font f = GroupEditScreen.this.font;
				int left = MemberList.this.getRowLeft() + 2;
				int y = getContentY() + 2;
				boolean isSelected = member.equalsIgnoreCase(MemberList.this.selected);
				if (isSelected) {
					g.fill(left - 2, getContentY(), left + MemberList.this.getRowWidth() - 4, getContentY() + 14, 0x45FFFFFF);
				} else if (hovered) {
					g.fill(left - 2, getContentY(), left + MemberList.this.getRowWidth() - 4, getContentY() + 14, 0x25FFFFFF);
				}
				String clipped = f.plainSubstrByWidth(member, MemberList.this.getRowWidth() - 8);
				g.text(f, clipped, left, y, 0xFFFFFFFF, false);
			}
		}
	}
}
