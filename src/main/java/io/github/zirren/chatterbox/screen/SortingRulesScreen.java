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

import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.config.SortRule;
import io.github.zirren.chatterbox.Lang;

/**
 * Custom sorting rules: teach ChatterBox where a server's messages belong.
 * Click a rule to select it; double-click (or press Edit) to change it;
 * Delete removes the selected rule after a confirm click.
 */
public class SortingRulesScreen extends ChatterBoxScreen {

	private static final long DOUBLE_CLICK_MS = 400L;
	private static final long DELETE_ARM_MS = 3000L;

	private RuleList list;
	private RuleList.Row selected;
	private long lastSelectTime;
	private boolean armedDelete;
	private long armedAt;

	private Button editButton;
	private Button deleteButton;

	public SortingRulesScreen(Screen parent) {
		super(Component.translatable("chatterbox.sorting.title"), parent);
	}

	@Override
	protected void init() {
		list = new RuleList(this.minecraft, this.width, this.height - 30 - 54, 30, 26);
		list.refresh();
		addRenderableWidget(list);

		int w = 74;
		int gap = 4;
		int x0 = this.width / 2 - (w * 4 + gap * 3) / 2;
		int y = this.height - 26;

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.add"), b -> {
			openEdit(new SortRule("", io.github.zirren.chatterbox.chat.Folder.SERVER, false), false);
		}).pos(x0, y).size(w, 20).build());

		editButton = Button.builder(Component.translatable("chatterbox.rules.edit"), b -> {
			if (selected != null) {
				openEdit(selected.rule, true);
			}
		}).pos(x0 + w + gap, y).size(w, 20).build();
		editButton.active = false;
		addRenderableWidget(editButton);

		deleteButton = Button.builder(Component.translatable("chatterbox.rules.delete"), b -> {
			if (selected == null) return;
			long now = System.currentTimeMillis();
			if (!armedDelete || now - armedAt > DELETE_ARM_MS) {
				// first click: ask for confirmation instead of deleting at once
				armedDelete = true;
				armedAt = now;
				deleteButton.setMessage(Component.translatable("chatterbox.rules.delete_confirm"));
				return;
			}
			Config.get().sortRules.remove(selected.rule);
			Config.get().save();
			selected = null;
			armedDelete = false;
			list.refresh();
			updateButtons();
		}).pos(x0 + 2 * (w + gap), y).size(w, 20).build();
		deleteButton.active = false;
		addRenderableWidget(deleteButton);

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.back"), b -> onClose())
				.pos(x0 + 3 * (w + gap), y).size(w, 20).build());
	}

	private void openEdit(SortRule rule, boolean existing) {
		this.minecraft.gui.setScreen(new SortingRuleEditScreen(this, rule, existing));
	}

	private void updateButtons() {
		if (editButton != null) {
			editButton.active = selected != null;
		}
		if (deleteButton != null) {
			deleteButton.active = selected != null;
			if (!armedDelete) {
				deleteButton.setMessage(Component.translatable("chatterbox.rules.delete"));
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
		if (Config.get().sortRules.isEmpty()) {
			g.textWithWordWrap(this.font, Component.literal(Lang.tr("chatterbox.sorting.empty")),
					this.width / 2 - 180, this.height / 2 - 30, Math.min(360, this.width - 30), 0xFF808080);
		} else {
			String hint = Lang.tr("chatterbox.sorting.hint");
			g.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, 19, 0xFF707070, false);
		}
	}

	private class RuleList extends AbstractSelectionList<RuleList.Entry> {

		RuleList(Minecraft client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void refresh() {
			clearEntries();
			for (SortRule rule : Config.get().sortRules) {
				addEntry(new Row(rule));
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
					// double-click on an already-selected rule = edit
					lastSelectTime = 0;
					openEdit(row.rule, true);
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
					RuleList.this.setSelected(this);
					return true;
				}
				return false;
			}
		}

		private class Row extends Entry {
			private final SortRule rule;

			Row(SortRule rule) {
				this.rule = rule;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
				Font f = SortingRulesScreen.this.font;
				int left = RuleList.this.getRowLeft() + 2;
				int y = getContentY() + 2;
				boolean isSelected = SortingRulesScreen.this.selected == this;
				if (isSelected) {
					g.fill(left - 2, getContentY(), left + RuleList.this.getRowWidth() - 4, getContentY() + 26, 0x45FFFFFF);
				} else if (hovered) {
					g.fill(left - 2, getContentY(), left + RuleList.this.getRowWidth() - 4, getContentY() + 26, 0x25FFFFFF);
				}
				String head = (rule.enabled ? "● " : "○ ") + (rule.pattern.isEmpty() ? "—" : rule.pattern);
				String clipped = f.plainSubstrByWidth(head, RuleList.this.getRowWidth() - 8);
				g.text(f, clipped, left, y, rule.enabled ? 0xFFFFFFFF : 0xFF707070, false);
				String rest = "→ " + Lang.tr(rule.folder().translationKey())
						+ " · " + Lang.tr(rule.regex ? "chatterbox.sorting.match.regex" : "chatterbox.sorting.match.contains");
				String restClipped = f.plainSubstrByWidth(rest, RuleList.this.getRowWidth() - 8);
				g.text(f, restClipped, left + 4, y + 12, 0xFF909090, false);
			}
		}
	}
}
