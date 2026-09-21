package io.github.zirren.chatterbox.screen;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.Lang;
import io.github.zirren.chatterbox.chat.Instruments;
import io.github.zirren.chatterbox.config.MentionRule;

/**
 * Lists the mention rules (word → sound). Click a rule to select it;
 * double-click a rule (or press "Edit") to change it; "Delete" removes the
 * selected rule after a confirm click, so rules can never be lost by accident.
 */
public class MentionRulesScreen extends ChatterBoxScreen {

	private static final long DOUBLE_CLICK_MS = 400L;
	private static final long DELETE_ARM_MS = 3000L;

	private RuleList list;
	private RuleList.Row selected;
	private long lastSelectTime;
	private boolean armedDelete;
	private long armedAt;

	private Button editButton;
	private Button deleteButton;

	public MentionRulesScreen(net.minecraft.client.gui.screens.Screen parent) {
		super(Component.translatable("chatterbox.rules.title"), parent);
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
			MentionRule rule = new MentionRule("", "minecraft:block.note_block.pling", 1.0f, 1.0f);
			this.minecraft.gui.setScreen(new MentionRuleEditScreen(this, rule, false));
		}).pos(x0, y).size(w, 20).build());

		editButton = Button.builder(Component.translatable("chatterbox.rules.edit"), b -> {
			if (selected != null) {
				openEdit(selected.rule);
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
			Config.get().mentionRules.remove(selected.rule);
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

	private void openEdit(MentionRule rule) {
		this.minecraft.gui.setScreen(new MentionRuleEditScreen(this, rule, true));
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
		if (Config.get().mentionRules.isEmpty()) {
			String msg = Lang.tr("chatterbox.rules.empty");
			g.text(this.font, msg, this.width / 2 - this.font.width(msg) / 2, this.height / 2 - 20, 0xFF808080, false);
		} else {
			String hint = Lang.tr("chatterbox.rules.hint");
			g.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, 19, 0xFF707070, false);
		}
	}

	private class RuleList extends AbstractSelectionList<RuleList.Entry> {

		RuleList(Minecraft client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void refresh() {
			clearEntries();
			for (MentionRule rule : Config.get().mentionRules) {
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
					openEdit(row.rule);
					return;
				}
				selected = row;
				lastSelectTime = now;
				armedDelete = false;
				updateButtons();
			}
		}

		abstract class Entry extends AbstractSelectionList.Entry<Entry> {
		}

		private class Row extends Entry {
			private final MentionRule rule;

			Row(MentionRule rule) {
				this.rule = rule;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
				Font f = MentionRulesScreen.this.font;
				int left = RuleList.this.getRowLeft() + 2;
				int y = getContentY() + 2;
				boolean isSelected = MentionRulesScreen.this.selected == this;
				if (isSelected) {
					g.fill(left - 2, getContentY(), left + RuleList.this.getRowWidth() - 4, getContentY() + 26, 0x45FFFFFF);
				} else if (hovered) {
					g.fill(left - 2, getContentY(), left + RuleList.this.getRowWidth() - 4, getContentY() + 26, 0x25FFFFFF);
				}
				String word = (rule.enabled ? "● " : "○ ") + (rule.word.isEmpty() ? "—" : rule.word);
				String rest;
				if (rule.hasTune()) {
					rest = "♪ " + Lang.tr("chatterbox.rules.alert.melody") + " · "
							+ Instruments.friendly(rule.tuneInstrument) + " · "
							+ rule.tuneNoteCount() + Lang.tr("chatterbox.rules.notes_suffix");
				} else {
					rest = "→ " + Instruments.friendly(rule.sound) + String.format(Locale.ROOT,
							"  ·  v%.1f  ·  %s", rule.volume,
							Instruments.noteName(Instruments.noteFromPitch(rule.pitch)));
				}
				String clipped = f.plainSubstrByWidth(word, RuleList.this.getRowWidth() - 8);
				g.text(f, clipped, left, y, rule.enabled ? 0xFFFFFFFF : 0xFF707070, false);
				String restClipped = f.plainSubstrByWidth(rest, RuleList.this.getRowWidth() - 8);
				g.text(f, restClipped, left + 4, y + 12, 0xFF909090, false);
			}
		}
	}
}
