package io.github.zirren.chatterbox.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.Lang;
import io.github.zirren.chatterbox.config.Shortcut;

/**
 * Edit (or create) one text shortcut: token + replacement.
 */
public class ShortcutEditScreen extends ChatterBoxScreen {

	private final Shortcut shortcut;
	private final boolean existing;

	private EditBox token;
	private EditBox replacement;
	private Button enabledButton;

	public ShortcutEditScreen(net.minecraft.client.gui.screens.Screen parent, Shortcut shortcut, boolean existing) {
		super(Component.translatable(existing ? "chatterbox.shortcuts.edit" : "chatterbox.shortcuts.add"), parent);
		this.shortcut = shortcut;
		this.existing = existing;
	}

	@Override
	protected void init() {
		token = new EditBox(this.font, this.width / 2 - 100, 30, 200, 16,
				Component.translatable("chatterbox.shortcuts.token"));
		token.setMaxLength(32);
		token.setValue(shortcut.token);
		addRenderableWidget(token);
		setInitialFocus(token);

		replacement = new EditBox(this.font, this.width / 2 - 100, 54, 200, 16,
				Component.translatable("chatterbox.shortcuts.replacement"));
		replacement.setMaxLength(256);
		replacement.setValue(shortcut.replacement);
		addRenderableWidget(replacement);

		enabledButton = Button.builder(cycleLabel("chatterbox.rules.enabled",
				Lang.tr(shortcut.enabled ? "chatterbox.rules.on" : "chatterbox.rules.off")), b -> {
					shortcut.enabled = !shortcut.enabled;
					b.setMessage(cycleLabel("chatterbox.rules.enabled",
							Lang.tr(shortcut.enabled ? "chatterbox.rules.on" : "chatterbox.rules.off")));
				}).pos(this.width / 2 - 100, 78).size(200, 20).build();
		addRenderableWidget(enabledButton);

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.rules.save"), b -> save())
				.pos(this.width / 2 - 155, this.height - 26).size(95, 20).build());
		if (existing) {
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.rules.delete"), b -> {
				Config.get().shortcuts.remove(shortcut);
				Config.get().save();
				onClose();
			}).pos(this.width / 2 - 55, this.height - 26).size(95, 20).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.cancel"), b -> onClose())
				.pos(this.width / 2 + 55, this.height - 26).size(95, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
		if (!token.getValue().isEmpty()) {
			String example = Lang.tr("chatterbox.shortcuts.example", token.getValue(), replacement.getValue());
			g.text(this.font, this.font.plainSubstrByWidth(example, this.width - 20), 10, this.height - 44, 0x707070, false);
		}
	}

	private void save() {
		String t = token.getValue().trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "");
		shortcut.token = t;
		shortcut.replacement = replacement.getValue();
		if (t.isEmpty() || shortcut.replacement.isEmpty()) {
			onClose();
			return;
		}
		if (!existing) {
			// replace an existing rule with the same token
			Config.get().shortcuts.removeIf(sc -> sc.token.equals(t));
			Config.get().shortcuts.add(shortcut);
		}
		Config.get().save();
		onClose();
	}
}
