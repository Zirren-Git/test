package io.github.zirren.chatterbox.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.I18n;

import java.util.Locale;

import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.config.MentionRule;

/**
 * Edit (or create) one mention rule: word, sound (opens the sound picker),
 * volume, pitch, enabled flag.
 */
public class MentionRuleEditScreen extends ChatterBoxScreen {

	private final MentionRule rule;
	private final boolean existing;

	private EditBox word;
	private Button soundButton;
	private Button enabledButton;

	public MentionRuleEditScreen(net.minecraft.client.gui.screens.Screen parent, MentionRule rule, boolean existing) {
		super(Component.translatable(existing ? "chatterbox.rules.title" : "chatterbox.rules.add"), parent);
		this.rule = rule;
		this.existing = existing;
	}

	@Override
	protected void init() {
		word = new EditBox(this.font, this.width / 2 - 100, 30, 200, 16,
				Component.translatable("chatterbox.rules.word"));
		word.setMaxLength(64);
		word.setValue(rule.word);
		word.setHint(Component.literal("{you}"));
		addRenderableWidget(word);
		setInitialFocus(word);

		soundButton = Button.builder(soundLabel(), b -> {
			this.minecraft.gui.setScreen(new SoundPickerScreen(this, rule.sound, rule.pitch, rule.volume, id -> {
				rule.sound = id;
			}));
		}).pos(this.width / 2 - 100, 54).size(200, 20).build();
		addRenderableWidget(soundButton);

		// Volume slider: 0.1 .. 2.0
		AbstractSliderButton volumeSlider = new AbstractSliderButton(this.width / 2 - 100, 78, 200, 20,
				Component.empty(), (rule.volume - 0.1F) / 1.9F) {
			@Override
			protected void updateMessage() {
				rule.volume = 0.1F + 1.9F * (float) this.value;
				this.setMessage(volumeLabel());
			}

			@Override
			protected void applyValue() {
			}
		};
		volumeSlider.setMessage(volumeLabel());
		addRenderableWidget(volumeSlider);

		// Pitch slider: 0.5 .. 2.0 mapped to note-block notes
		AbstractSliderButton pitchSlider = new AbstractSliderButton(this.width / 2 - 100, 102, 200, 20,
				Component.empty(), (rule.pitch - 0.5F) / 1.5F) {
			@Override
			protected void updateMessage() {
				rule.pitch = 0.5F + 1.5F * (float) this.value;
				this.setMessage(pitchLabel());
			}

			@Override
			protected void applyValue() {
			}
		};
		pitchSlider.setMessage(pitchLabel());
		addRenderableWidget(pitchSlider);

		enabledButton = Button.builder(cycleLabel("chatterbox.rules.enabled",
				I18n.get(rule.enabled ? "chatterbox.rules.on" : "chatterbox.rules.off")), b -> {
					rule.enabled = !rule.enabled;
					b.setMessage(cycleLabel("chatterbox.rules.enabled",
							I18n.get(rule.enabled ? "chatterbox.rules.on" : "chatterbox.rules.off")));
				}).pos(this.width / 2 - 100, 126).size(200, 20).build();
		addRenderableWidget(enabledButton);

		addRenderableWidget(Button.builder(Component.translatable("chatterbox.rules.save"), b -> save())
				.pos(this.width / 2 - 155, this.height - 26).size(95, 20).build());
		if (existing) {
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.rules.delete"), b -> {
				Config.get().mentionRules.remove(rule);
				Config.get().save();
				onClose();
			}).pos(this.width / 2 - 55, this.height - 26).size(95, 20).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.cancel"), b -> onClose())
				.pos(this.width / 2 + 55, this.height - 26).size(95, 20).build());
	}

	private Component soundLabel() {
		return Component.literal(I18n.get("chatterbox.rules.sound") + ": " + rule.sound);
	}

	private Component volumeLabel() {
		return Component.literal(I18n.get("chatterbox.rules.volume",
				String.format(Locale.ROOT, "%.1f", rule.volume)));
	}

	private Component pitchLabel() {
		int note = (int) Math.round(12.0 * Math.log(rule.pitch) / Math.log(2.0) + 12.0);
		return Component.literal(I18n.get("chatterbox.rules.pitch",
				String.format(Locale.ROOT, "%.2f", rule.pitch), String.valueOf(note)));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
		// refresh the sound button label (picker may have changed it)
		soundButton.setMessage(soundLabel());
	}

	private void save() {
		String value = word.getValue().trim();
		rule.word = value;
		if (rule.word.isEmpty()) {
			onClose();
			return;
		}
		if (!existing) {
			Config.get().mentionRules.add(rule);
		}
		Config.get().save();
		onClose();
	}
}
