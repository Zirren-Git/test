package io.github.zirren.chatterbox.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import io.github.zirren.chatterbox.chat.Folder;
import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.config.SortRule;
import io.github.zirren.chatterbox.Lang;

/**
 * Edit (or create) one sorting rule: the text to match, how to match it
 * (contains / regex) and the target folder. Rules run before every built-in
 * detection; normal player chat is never re-routed.
 */
public class SortingRuleEditScreen extends ChatterBoxScreen {

	/** Folder cycle order (excludes ALL and PINNED). */
	private static final Folder[] FOLDERS = {Folder.CHAT, Folder.DM, Folder.SERVER, Folder.JOINS, Folder.COMMAND, Folder.DEATH};

	private final SortRule rule;
	private final boolean existing;

	private EditBox pattern;
	private Button folderButton;
	private Button matchButton;
	private @org.jspecify.annotations.Nullable String lastError;

	public SortingRuleEditScreen(Screen parent, SortRule rule, boolean existing) {
		super(Component.translatable(existing ? "chatterbox.sorting.title" : "chatterbox.button.add"), parent);
		this.rule = rule;
		this.existing = existing;
	}

	@Override
	protected void init() {
		pattern = new EditBox(this.font, this.width / 2 - 100, 28, 200, 16,
				Component.translatable("chatterbox.sorting.pattern"));
		pattern.setMaxLength(100);
		pattern.setValue(rule.pattern);
		pattern.setHint(Component.translatable("chatterbox.sorting.pattern_hint"));
		addRenderableWidget(pattern);
		setInitialFocus(pattern);

		folderButton = Button.builder(folderLabel(), b -> {
			rule.folder = nextFolder().key;
			b.setMessage(folderLabel());
		}).pos(this.width / 2 - 100, 50).size(200, 20).build();
		folderButton.setTooltip(Tooltip.create(Component.translatable("chatterbox.sorting.folder.tooltip")));
		addRenderableWidget(folderButton);

		matchButton = Button.builder(matchLabel(), b -> {
			rule.regex = !rule.regex;
			b.setMessage(matchLabel());
		}).pos(this.width / 2 - 100, 72).size(200, 20).build();
		matchButton.setTooltip(Tooltip.create(Component.translatable("chatterbox.sorting.match.tooltip")));
		addRenderableWidget(matchButton);

		Button enabledButton = Button.builder(cycleLabel("chatterbox.rules.enabled",
				Lang.tr(rule.enabled ? "chatterbox.rules.on" : "chatterbox.rules.off")), b -> {
					rule.enabled = !rule.enabled;
					b.setMessage(cycleLabel("chatterbox.rules.enabled",
							Lang.tr(rule.enabled ? "chatterbox.rules.on" : "chatterbox.rules.off")));
				}).pos(this.width / 2 - 100, 94).size(200, 20).build();
		addRenderableWidget(enabledButton);

		// --- bottom row ---------------------------------------------------
		if (existing) {
			int x0 = this.width / 2 - 148;
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.rules.save"), b -> save())
					.pos(x0, this.height - 26).size(72, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.rules.delete"), b -> {
				Config.get().sortRules.remove(rule);
				Config.get().save();
				onClose();
			}).pos(x0 + 76, this.height - 26).size(72, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.cancel"), b -> onClose())
					.pos(x0 + 152, this.height - 26).size(72, 20).build());
		} else {
			int x0 = this.width / 2 - 122;
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.rules.save"), b -> save())
					.pos(x0, this.height - 26).size(78, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.cancel"), b -> onClose())
					.pos(x0 + 82, this.height - 26).size(78, 20).build());
		}
	}

	private Folder nextFolder() {
		Folder current = rule.folder();
		for (int i = 0; i < FOLDERS.length; i++) {
			if (FOLDERS[i] == current) {
				return FOLDERS[(i + 1) % FOLDERS.length];
			}
		}
		return FOLDERS[0];
	}

	private void save() {
		String value = pattern.getValue().trim();
		if (value.isEmpty()) {
			onClose();
			return;
		}
		if (rule.regex) {
			// validate before touching the live rule
			try {
				java.util.regex.Pattern.compile(value, java.util.regex.Pattern.CASE_INSENSITIVE);
			} catch (Throwable t) {
				lastError = t.getMessage();
				return; // keep the screen open, the error is drawn below
			}
		}
		lastError = null;
		rule.pattern = value;
		if (!existing) {
			Config.get().sortRules.add(rule);
		}
		Config.get().save();
		onClose();
	}

	// ------------------------------------------------------------------
	// Labels
	// ------------------------------------------------------------------

	private Component folderLabel() {
		return cycleLabel("chatterbox.sorting.folder", Lang.tr(rule.folder().translationKey()));
	}

	private Component matchLabel() {
		return cycleLabel("chatterbox.sorting.match",
				Lang.tr(rule.regex ? "chatterbox.sorting.match.regex" : "chatterbox.sorting.match.contains"));
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
		if (lastError != null) {
			String msg = Lang.tr("chatterbox.sorting.regex_error");
			g.text(this.font, msg, this.width / 2 - this.font.width(msg) / 2, 120, 0xFFFF6060, false);
			String clipped = this.font.plainSubstrByWidth(lastError, 240);
			g.text(this.font, clipped, this.width / 2 - this.font.width(clipped) / 2, 132, 0xFFB06060, false);
		} else {
			String note = Lang.tr("chatterbox.sorting.note");
			g.text(this.font, note, this.width / 2 - this.font.width(note) / 2, 124, 0xFF909090, false);
		}
	}
}
