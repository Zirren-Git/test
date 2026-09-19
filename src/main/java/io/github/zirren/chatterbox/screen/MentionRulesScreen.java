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
import io.github.zirren.chatterbox.config.MentionRule;

/**
 * Lists the mention rules (word → sound). Click a rule to edit it.
 */
public class MentionRulesScreen extends ChatterBoxScreen {

	private RuleList list;

	public MentionRulesScreen(net.minecraft.client.gui.screens.Screen parent) {
		super(Component.translatable("chatterbox.rules.title"), parent);
	}

	@Override
	protected void init() {
		list = new RuleList(this.minecraft, this.width, this.height - 30 - 54, 30, 26);
		list.refresh();
		addRenderableWidget(list);

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.rules.add"), b -> {
			MentionRule rule = new MentionRule("", "minecraft:block.note_block.pling", 1.0f, 1.0f);
			this.minecraft.gui.setScreen(new MentionRuleEditScreen(this, rule, false));
		}).pos(this.width / 2 - 155, this.height - 26).size(150, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.back"), b -> onClose())
				.pos(this.width / 2 + 5, this.height - 26).size(150, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
		if (Config.get().mentionRules.isEmpty()) {
			String msg = Lang.tr("chatterbox.rules.empty");
			g.text(this.font, msg, this.width / 2 - this.font.width(msg) / 2, this.height / 2 - 20, 0x808080, false);
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
				MentionRulesScreen.this.minecraft.gui.setScreen(new MentionRuleEditScreen(MentionRulesScreen.this, row.rule, true));
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
				if (hovered) {
					g.fill(left - 2, getContentY(), left + RuleList.this.getRowWidth() - 4, getContentY() + 26, 0x25FFFFFF);
				}
				String word = (rule.enabled ? "● " : "○ ") + (rule.word.isEmpty() ? "—" : rule.word);
				String rest = "→ " + rule.sound + String.format(Locale.ROOT, "   v%.1f  p%.2f", rule.volume, rule.pitch);
				String clipped = f.plainSubstrByWidth(word, RuleList.this.getRowWidth() - 8);
				g.text(f, clipped, left, y, rule.enabled ? 0xFFFFFF : 0x707070, false);
				String restClipped = f.plainSubstrByWidth(rest, RuleList.this.getRowWidth() - 8);
				g.text(f, restClipped, left + 4, y + 12, 0x909090, false);
			}
		}
	}
}
