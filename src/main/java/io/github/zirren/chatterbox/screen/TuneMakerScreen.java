package io.github.zirren.chatterbox.screen;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import io.github.zirren.chatterbox.Lang;
import io.github.zirren.chatterbox.chat.Instruments;
import io.github.zirren.chatterbox.chat.Sounds;
import io.github.zirren.chatterbox.chat.TunePlayer;
import io.github.zirren.chatterbox.config.Config;
import io.github.zirren.chatterbox.config.MentionRule;

/**
 * The Tune Maker: an Animal-Crossing-style melody composer for mention
 * alerts, dressed like Minecraft. A 16-step × 25-pitch note-block grid:
 * click a square to place a note, click it again to clear it, scroll or use
 * the arrows to move across octaves, pick a note-block instrument and a
 * tempo, then press play and watch the tune hop along the grid.
 */
public class TuneMakerScreen extends ChatterBoxScreen {

	private static final int STEPS = TunePlayer.MAX_STEPS;
	private static final int LOWEST_NOTE = 0;  // F♯3
	private static final int HIGHEST_NOTE = 24; // F♯5

	private final MentionRule rule;
	private final int[] originalTune;
	private final String originalInstrument;
	private final int originalTempo;

	private int[] notes = new int[STEPS];
	private String instrument;
	private int tempoMs;
	private int instrumentIndex;
	private int previewToken = -1;
	private boolean saved = false;

	// grid layout (computed in init)
	private int cellW = 13;
	private int cellH = 10;
	private int rows = 12;
	private int topNote = -1; // note shown in the top row; -1 = pick a default
	private int headerY, gridX, gridY, gridW, gridH, gutterW;

	private Button playButton;
	private Button tempoButton;
	private Button instrumentButton;

	public TuneMakerScreen(Screen parent, MentionRule rule) {
		super(Component.translatable("chatterbox.tune.title"), parent);
		this.rule = rule;
		this.originalTune = rule.tune == null ? null : rule.tune.clone();
		this.originalInstrument = rule.tuneInstrument;
		this.originalTempo = rule.tuneTempo;
		this.notes = rule.hasTune() ? pad(rule.tune.clone()) : MentionRule.DEFAULT_MELODY.clone();
		List<String> available = Instruments.available();
		this.instrument = available.contains(rule.tuneInstrument) ? rule.tuneInstrument : available.get(0);
		this.instrumentIndex = Math.max(0, available.indexOf(this.instrument));
		this.tempoMs = rule.tuneTempo >= 100 && rule.tuneTempo <= 500 ? rule.tuneTempo : 200;
	}

	/** Melodies are always saved as full 16-step arrays. */
	private static int[] pad(int[] tune) {
		int[] out = new int[STEPS];
		java.util.Arrays.fill(out, -1);
		System.arraycopy(tune, 0, out, 0, Math.min(tune.length, STEPS));
		return out;
	}

	@Override
	protected void init() {
		List<String> instruments = Instruments.available();

		// --- instrument picker: ‹ Instrument: Harp › ---------------------
		int rowY = 20;
		int pickerW = 170;
		int pickerX = this.width / 2 - (20 + 2 + pickerW + 2 + 20) / 2;
		addRenderableWidget(Button.builder(Component.literal("‹"), b -> cycleInstrument(-1, instruments))
				.pos(pickerX, rowY).size(20, 20).build());
		instrumentButton = Button.builder(instrumentLabel(), b -> previewNote(12))
				.pos(pickerX + 22, rowY).size(pickerW, 20).build();
		instrumentButton.setTooltip(Tooltip.create(Component.translatable("chatterbox.tune.instrument.tooltip")));
		addRenderableWidget(instrumentButton);
		addRenderableWidget(Button.builder(Component.literal("›"), b -> cycleInstrument(1, instruments))
				.pos(pickerX + 24 + pickerW, rowY).size(20, 20).build());

		// --- grid geometry ----------------------------------------------
		headerY = 46;
		int gridTop = headerY + 11;
		int gridBottom = this.height - 68;
		int available = gridBottom - gridTop;
		cellH = 10;
		if (available >= 25 * 9) {
			// full note-block range fits: show everything
			cellH = Math.min(12, available / 25);
			rows = 25;
		} else {
			rows = Math.max(5, Math.min(25, available / cellH));
		}
		gridH = rows * cellH;
		if (topNote < 0 || topNote > HIGHEST_NOTE + 1 - rows) {
			topNote = rows >= 25 ? 0 : Math.max(0, Math.min(12 - rows / 2, HIGHEST_NOTE + 1 - rows));
		}
		gutterW = this.font.width("F#5") + 6;
		gridW = cellW * STEPS;
		gridX = this.width / 2 - (gridW + gutterW) / 2 + gutterW;
		gridY = gridTop;

		// --- octave arrows (only when the range doesn't fully fit) -------
		if (rows < 25) {
			int ax = gridX + gridW + 4;
			addRenderableWidget(Button.builder(Component.literal("▲"), b -> scrollNotes(-6))
					.pos(ax, gridY).size(16, 12).build());
			addRenderableWidget(Button.builder(Component.literal("▼"), b -> scrollNotes(6))
					.pos(ax, gridY + 14).size(16, 12).build());
		}

		// --- controls: play · tempo · volume -----------------------------
		int cy = this.height - 58;
		int x0 = this.width / 2 - 148;
		playButton = Button.builder(playLabel(), b -> togglePlay())
				.pos(x0, cy).size(60, 20).build();
		addRenderableWidget(playButton);
		tempoButton = Button.builder(tempoLabel(), b -> {
			tempoMs = tempoMs > 250 ? 200 : (tempoMs > 160 ? 140 : 300);
			tempoButton.setMessage(tempoLabel());
		}).pos(x0 + 68, cy).size(100, 20).build();
		tempoButton.setTooltip(Tooltip.create(Component.translatable("chatterbox.tune.tempo.tooltip")));
		addRenderableWidget(tempoButton);
		AbstractSliderButton volumeSlider = new AbstractSliderButton(x0 + 176, cy, 120, 20,
				Component.empty(), (rule.volume - 0.1F) / 1.9F) {
			@Override
			protected void updateMessage() {
				rule.volume = 0.1F + 1.9F * (float) this.value;
				this.setMessage(Component.literal(Lang.tr("chatterbox.rules.volume",
						String.format(Locale.ROOT, "%.1f", rule.volume))));
			}

			@Override
			protected void applyValue() {
			}
		};
		volumeSlider.setMessage(Component.literal(Lang.tr("chatterbox.rules.volume",
				String.format(Locale.ROOT, "%.1f", rule.volume))));
		addRenderableWidget(volumeSlider);

		// --- bottom row: save · clear · cancel ---------------------------
		int by = this.height - 36;
		int bx = this.width / 2 - 128;
		addRenderableWidget(Button.builder(Component.translatable("chatterbox.tune.save"), b -> save())
				.pos(bx, by).size(80, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("chatterbox.tune.clear"), b -> {
			java.util.Arrays.fill(notes, -1);
			TunePlayer.stop(previewToken);
		}).pos(bx + 88, by).size(80, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("chatterbox.button.cancel"), b -> onClose())
				.pos(bx + 176, by).size(80, 20).build());
	}

	// ------------------------------------------------------------------
	// Interaction
	// ------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		try {
			double mx = event.x();
			double my = event.y();
			// grid cells
			int col = (int) Math.floor((mx - gridX) / cellW);
			int rowIdx = (int) Math.floor((my - gridY) / cellH);
			if (col >= 0 && col < STEPS && rowIdx >= 0 && rowIdx < rows) {
				int note = topNote + (rows - 1 - rowIdx);
				if (note >= LOWEST_NOTE && note <= HIGHEST_NOTE) {
					if (notes[col] == note) {
						notes[col] = -1; // click the placed note again → rest
					} else {
						notes[col] = note;
						previewNote(note);
					}
					return true;
				}
			}
			// column headers: audition that step
			if (my >= headerY && my < gridY && mx >= gridX && mx < gridX + gridW) {
				int step = (int) ((mx - gridX) / cellW);
				previewNote(notes[step] >= 0 ? notes[step] : 12);
				return true;
			}
		} catch (Throwable ignored) {
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		// only scroll the note range when the pointer is over the grid itself
		boolean overGrid = rows < 25 && scrollY != 0
				&& mouseX >= gridX && mouseX < gridX + gridW
				&& mouseY >= gridY && mouseY < gridY + gridH;
		if (overGrid) {
			int newTop = clamp(topNote - (int) Math.signum(scrollY), 0, HIGHEST_NOTE + 1 - rows);
			if (newTop != topNote) {
				topNote = newTop;
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void cycleInstrument(int direction, List<String> instruments) {
		instrumentIndex = Math.floorMod(instrumentIndex + direction, instruments.size());
		instrument = instruments.get(instrumentIndex);
		instrumentButton.setMessage(instrumentLabel());
		previewNote(12);
	}

	private void scrollNotes(int delta) {
		topNote = clamp(topNote + delta, 0, HIGHEST_NOTE + 1 - rows);
	}

	private void previewNote(int note) {
		Sounds.preview(instrument, Instruments.notePitch(note), rule.volume);
	}

	private void togglePlay() {
		if (TunePlayer.isPlaying()) {
			TunePlayer.stop(previewToken);
		} else {
			previewToken = TunePlayer.play(instrument, notes, rule.volume, tempoMs);
		}
	}

	private void save() {
		rule.tune = notes.clone();
		rule.tuneInstrument = instrument;
		rule.tuneTempo = tempoMs;
		Config.get().save();
		saved = true;
		onClose();
	}

	@Override
	public void onClose() {
		if (!saved) {
			rule.tune = originalTune == null ? null : originalTune.clone();
			rule.tuneInstrument = originalInstrument;
			rule.tuneTempo = originalTempo;
		}
		super.onClose();
	}

	@Override
	public void removed() {
		TunePlayer.stop(previewToken);
		super.removed();
	}

	// ------------------------------------------------------------------
	// Labels
	// ------------------------------------------------------------------

	private Component instrumentLabel() {
		return Component.literal(Lang.tr("chatterbox.tune.instrument", Instruments.friendly(instrument)));
	}

	private Component playLabel() {
		return Component.literal(TunePlayer.isPlaying()
				? Lang.tr("chatterbox.tune.stop") : Lang.tr("chatterbox.tune.play"));
	}

	private Component tempoLabel() {
		String name = tempoMs > 250 ? "slow" : (tempoMs > 160 ? "normal" : "fast");
		return Component.literal(Lang.tr("chatterbox.tune.tempo", Lang.tr("chatterbox.tune.tempo." + name)));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		try {
			drawGrid(g, (int) mouseX, (int) mouseY);
		} catch (Throwable t) {
			// rendering must never crash the game
		}
		drawTitle(g, 0xFFFFFFFF);

		// play/stop label follows the player state
		Component play = playLabel();
		if (!playButton.getMessage().equals(play)) {
			playButton.setMessage(play);
		}

		String hint = Lang.tr("chatterbox.tune.hint");
		g.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, this.height - 22, 0x707070, false);
	}

	private void drawGrid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		float progress = TunePlayer.progressSteps();
		int playCol = progress >= 0 ? (int) progress : -1;

		// panel behind grid + header
		int panelX = gridX - gutterW;
		int panelW = gutterW + gridW;
		g.fill(panelX - 4, headerY - 2, panelX + panelW + 4, gridY + gridH + 4, 0xC80C0C12);
		g.fill(panelX - 4, headerY - 2, panelX + panelW + 4, headerY - 1, 0xFF565656);
		g.fill(panelX - 4, gridY + gridH + 3, panelX + panelW + 4, gridY + gridH + 4, 0xFF565656);
		g.fill(panelX - 4, headerY - 2, panelX - 3, gridY + gridH + 4, 0xFF565656);
		g.fill(panelX + panelW + 3, headerY - 2, panelX + panelW + 4, gridY + gridH + 4, 0xFF565656);

		// step numbers
		for (int step = 0; step < STEPS; step++) {
			String label = String.valueOf(step + 1);
			int cx = gridX + step * cellW + cellW / 2 - this.font.width(label) / 2;
			boolean active = step == playCol;
			g.text(this.font, label, cx, headerY, active ? 0x55FF55 : (step % 4 == 0 ? 0xB0B0B0 : 0x707070), false);
		}

		// hovered cell
		int hoverCol = (mouseX - gridX) / cellW;
		int hoverRow = (mouseY - gridY) / cellH;
		boolean hoverCell = hoverCol >= 0 && hoverCol < STEPS && hoverRow >= 0 && hoverRow < rows
				&& mouseX >= gridX && mouseY >= gridY;

		for (int row = 0; row < rows; row++) {
			int note = topNote + (rows - 1 - row);
			int y = gridY + row * cellH;

			// note names in the gutter; C rows highlighted like piano keys
			boolean isC = note % 12 == 6;
			String name = Instruments.noteName(note);
			g.text(this.font, name, gridX - this.font.width(name) - 3, y + (cellH - 8) / 2,
					isC ? 0xFFFFFF : 0x909090, false);
			if (isC) {
				g.fill(gridX, y, gridX + gridW, y + cellH, 0x14FFFFFF);
			}

			// row separators
			g.fill(gridX, y, gridX + gridW, y + 1, 0x2E303030);
		}
		// column separators, heavier every 4 steps (like bars in sheet music)
		for (int step = 0; step <= STEPS; step++) {
			int x = gridX + step * cellW;
			g.fill(x, gridY, x + 1, gridY + gridH, step % 4 == 0 ? 0xFF3A3A3A : 0x2E262626);
		}

		// playhead column
		if (playCol >= 0 && playCol < STEPS) {
			g.fill(gridX + playCol * cellW, gridY, gridX + (playCol + 1) * cellW, gridY + gridH, 0x2EFFFFFF);
		}

		// hover highlight
		if (hoverCell) {
			g.fill(gridX + hoverCol * cellW, gridY + hoverRow * cellH,
					gridX + (hoverCol + 1) * cellW, gridY + (hoverRow + 1) * cellH, 0x30FFFFFF);
		}

		// notes
		for (int step = 0; step < STEPS; step++) {
			int note = notes[step];
			if (note < LOWEST_NOTE || note > HIGHEST_NOTE) continue;
			int row = rows - 1 - (note - topNote);
			if (row < 0 || row >= rows) continue; // scrolled out of view
			int x = gridX + step * cellW;
			int y = gridY + row * cellH;
			boolean playing = step == playCol;
			g.fill(x + 1, y + 1, x + cellW - 1, y + cellH - 1, playing ? 0xFF6FCF5F : 0xFF3C8527);
			g.text(this.font, "♪", x + cellW / 2 - this.font.width("♪") / 2,
					y + (cellH - 8) / 2 - (playing ? 1 : 0), 0xFFFFFF, false);
		}

		// tooltip for the hovered cell
		if (hoverCell) {
			int note = topNote + (rows - 1 - hoverRow);
			if (note >= LOWEST_NOTE && note <= HIGHEST_NOTE) {
				String text = Lang.tr("chatterbox.tune.step", hoverCol + 1) + " · "
						+ Instruments.noteName(note) + " · " + Lang.tr("chatterbox.tune.note", note);
				if (notes[hoverCol] == note) {
					text += " ✓";
				}
				g.setTooltipForNextFrame(Component.literal(text), mouseX, mouseY);
			}
		}
	}
}
