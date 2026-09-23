package io.github.zirren.chatterbox.screen;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.Lang;
import io.github.zirren.chatterbox.chat.Instruments;
import io.github.zirren.chatterbox.chat.Sounds;
import io.github.zirren.chatterbox.chat.TunePlayer;
import io.github.zirren.chatterbox.config.MentionRule;

/**
 * Edit (or create) one mention rule: word, enabled flag, alert type — a
 * single sound (picker + pitch) or a composed melody (opens the Tune Maker) —
 * plus volume and a live preview button.
 */
public class MentionRuleEditScreen extends ChatterBoxScreen {

	private final MentionRule rule;
	private final boolean existing;

	private boolean melodyMode;

	private EditBox word;
	private Button soundButton;
	private Button modeButton;
	private Button melodyButton;

	public MentionRuleEditScreen(Screen parent, MentionRule rule, boolean existing) {
		super(Component.translatable(existing ? "chatterbox.rules.title" : "chatterbox.rules.add"), parent);
		this.rule = rule;
		this.existing = existing;
		this.melodyMode = rule.hasTune();
	}

	@Override
	protected void init() {
		word = new EditBox(this.font, this.width / 2 - 100, 28, 200, 16,
				Component.translatable("chatterbox.rules.word"));
		word.setMaxLength(64);
		word.setValue(rule.word);
		word.setHint(Component.literal("{you}"));
		addRenderableWidget(word);
		setInitialFocus(word);

		Button enabledButton = Button.builder(cycleLabel("chatterbox.rules.enabled",
				Lang.tr(rule.enabled ? "chatterbox.rules.on" : "chatterbox.rules.off")), b -> {
					rule.enabled = !rule.enabled;
					b.setMessage(cycleLabel("chatterbox.rules.enabled",
							Lang.tr(rule.enabled ? "chatterbox.rules.on" : "chatterbox.rules.off")));
				}).pos(this.width / 2 - 100, 50).size(200, 20).build();
		addRenderableWidget(enabledButton);

		// alert type: single sound ⇄ melody
		modeButton = Button.builder(modeLabel(), b -> switchMode())
				.pos(this.width / 2 - 100, 72).size(200, 20).build();
		modeButton.setTooltip(Tooltip.create(Component.translatable("chatterbox.rules.alert_type.tooltip")));
		addRenderableWidget(modeButton);

		if (!melodyMode) {
			// --- single sound -------------------------------------------
			soundButton = Button.builder(soundLabel(), b -> {
				open(new SoundPickerScreen(this, rule.sound, rule.pitch, rule.volume, id -> {
					rule.sound = id;
				}));
			}).pos(this.width / 2 - 100, 94).size(200, 20).build();
			soundButton.setTooltip(Tooltip.create(Component.literal(rule.sound)));
			addRenderableWidget(soundButton);

			AbstractSliderButton pitchSlider = new AbstractSliderButton(this.width / 2 - 100, 116, 200, 20,
					Component.empty(), (clampPitch(rule.pitch) - 0.5F) / 1.5F) {
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
		} else {
			// --- melody --------------------------------------------------
			melodyButton = Button.builder(Component.literal(
							Lang.tr("chatterbox.rules.open_tune")), b ->
							open(new TuneMakerScreen(this, rule)))
					.pos(this.width / 2 - 100, 94).size(200, 20).build();
			melodyButton.setTooltip(Tooltip.create(Component.translatable("chatterbox.rules.open_tune.tooltip")));
			addRenderableWidget(melodyButton);
		}

		// volume applies to both alert types
		AbstractSliderButton volumeSlider = new AbstractSliderButton(this.width / 2 - 100, 138, 200, 20,
				Component.empty(), (clampVolume(rule.volume) - 0.1F) / 1.9F) {
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

		// --- matching options: case · whole-word · where to listen ---------
		Button caseButton = Button.builder(caseLabel(), b -> {
			rule.caseSensitive = !rule.caseSensitive;
			b.setMessage(caseLabel());
		}).pos(this.width / 2 - 100, 160).size(98, 20).build();
		caseButton.setTooltip(Tooltip.create(Component.translatable("chatterbox.rules.case.tooltip")));
		addRenderableWidget(caseButton);

		Button matchButton = Button.builder(matchLabel(), b -> {
			rule.wholeWord = !rule.wholeWord;
			b.setMessage(matchLabel());
		}).pos(this.width / 2 + 2, 160).size(98, 20).build();
		matchButton.setTooltip(Tooltip.create(Component.translatable("chatterbox.rules.match.tooltip")));
		addRenderableWidget(matchButton);

		Button scopeButton = Button.builder(scopeLabel(), b -> {
			rule.scope = MentionRule.nextScope(rule.scope);
			b.setMessage(scopeLabel());
		}).pos(this.width / 2 - 100, 182).size(200, 20).build();
		scopeButton.setTooltip(Tooltip.create(Component.translatable("chatterbox.rules.scope.tooltip")));
		addRenderableWidget(scopeButton);

		// --- bottom row: save · preview · (delete) · cancel --------------
		if (existing) {
			int x0 = this.width / 2 - 148;
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.rules.save"), b -> save())
					.pos(x0, this.height - 26).size(72, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.rules.delete"), b -> {
				Config.get().mentionRules.remove(rule);
				Config.get().save();
				onClose();
			}).pos(x0 + 76, this.height - 26).size(72, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.rules.preview"), b -> preview())
					.pos(x0 + 152, this.height - 26).size(72, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.cancel"), b -> onClose())
					.pos(x0 + 228, this.height - 26).size(72, 20).build());
		} else {
			int x0 = this.width / 2 - 122;
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.rules.save"), b -> save())
					.pos(x0, this.height - 26).size(78, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.rules.preview"), b -> preview())
					.pos(x0 + 82, this.height - 26).size(78, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.cancel"), b -> onClose())
					.pos(x0 + 164, this.height - 26).size(78, 20).build());
		}
	}

	private void switchMode() {
		// keep what has been typed into the word box across the rebuild
		rule.word = word.getValue();
		if (!melodyMode) {
			if (!rule.hasTune()) {
				rule.tune = MentionRule.DEFAULT_MELODY.clone();
				if (rule.tuneInstrument == null || !Sounds.exists(rule.tuneInstrument)) {
					rule.tuneInstrument = Instruments.available().get(0);
				}
			}
			melodyMode = true;
		} else {
			rule.tune = null;
			melodyMode = false;
		}
		this.rebuildWidgets();
	}

	private void open(Screen screen) {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(screen);
		}
	}

	private void preview() {
		if (melodyMode && rule.hasTune()) {
			TunePlayer.play(rule.tuneInstrument, rule.tune, rule.volume,
					rule.tuneTempo <= 0 ? 200 : rule.tuneTempo);
		} else {
			Sounds.play(rule.sound, rule.pitch, rule.volume);
		}
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

	// ------------------------------------------------------------------
	// Labels
	// ------------------------------------------------------------------

	private Component modeLabel() {
		return cycleLabel("chatterbox.rules.alert_type",
				Lang.tr(melodyMode ? "chatterbox.rules.alert.melody" : "chatterbox.rules.alert.sound"));
	}

	private Component soundLabel() {
		return cycleLabel("chatterbox.rules.sound", Instruments.friendly(rule.sound));
	}

	private Component caseLabel() {
		return cycleLabel("chatterbox.rules.case",
				Lang.tr(rule.caseSensitive ? "chatterbox.rules.case.sensitive" : "chatterbox.rules.case.ignore"));
	}

	private Component matchLabel() {
		return cycleLabel("chatterbox.rules.match",
				Lang.tr(rule.wholeWord ? "chatterbox.rules.match.word" : "chatterbox.rules.match.anywhere"));
	}

	private Component scopeLabel() {
		return cycleLabel("chatterbox.rules.scope",
				Lang.tr("chatterbox.rules.scope." + MentionRule.normalizeScope(rule.scope)));
	}

	private Component volumeLabel() {
		return Component.literal(Lang.tr("chatterbox.rules.volume",
				String.format(Locale.ROOT, "%.1f", rule.volume)));
	}

	private Component pitchLabel() {
		return Component.literal(Lang.tr("chatterbox.rules.pitch",
				String.format(Locale.ROOT, "%.2f", rule.pitch),
				String.valueOf(Instruments.noteFromPitch(rule.pitch))));
	}

	private static float clampVolume(float v) {
		return Math.max(0.1F, Math.min(2.0F, v));
	}

	private static float clampPitch(float v) {
		return Math.max(0.5F, Math.min(2.0F, v));
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		drawTitle(g, 0xFFFFFFFF);
		try {
			if (melodyMode) {
				// melody summary line under the "Open Tune Maker" button
				String summary = Lang.tr("chatterbox.rules.melody_summary",
						Instruments.friendly(rule.tuneInstrument),
						String.valueOf(rule.tuneNoteCount()));
				g.text(this.font, summary, this.width / 2 - this.font.width(summary) / 2, 119, 0xFF909090, false);
			} else if (soundButton != null) {
				// the picker may have changed the sound while we were away
				soundButton.setMessage(soundLabel());
				soundButton.setTooltip(Tooltip.create(Component.literal(rule.sound)));
			}
		} catch (Throwable ignored) {
		}
	}
}
