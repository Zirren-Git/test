package io.github.zirren.chatterbox.config;

import java.util.Arrays;
import java.util.Objects;

/**
 * A single mention rule: when {@code word} appears in an incoming chat message,
 * the configured alert plays — either a single sound or a composed melody.
 *
 * <p>The special token {@code {you}} matches the local player's username.</p>
 */
public class MentionRule {

	/** A pleasant little arpeggio used when a rule gains its first melody. */
	public static final int[] DEFAULT_MELODY = {
			6, 10, 13, 18, // C4 E4 G4 C5
			17, 13, 10, 6, // B4 G4 E4 C4
			8, 11, 15, 20, // D4 F4 A4 D5
			18, 13, 10, 6, // C5 G4 E4 C4
	};

	/** Word (or username, or {@code {you}}) to match. Matched as a whole word, case-insensitively by default. */
	public String word = "";
	/** Sound event id, e.g. {@code minecraft:block.note_block.pling}. */
	public String sound = "minecraft:block.note_block.pling";
	/** Volume multiplier, 1.0 = normal. */
	public float volume = 1.0f;
	/** Pitch; 0.5 = note 0, 1.0 = note 12, 2.0 = note 24 (like note blocks). */
	public float pitch = 1.0f;
	public boolean enabled = true;
	public boolean caseSensitive = false;

	// --- melody alert (Tune Maker) ---
	/** Melody steps: 0–24 = note-block note, -1 = rest. Non-empty (any note ≥ 0) = melody alert. */
	public int[] tune = null;
	/** Instrument used for the melody, e.g. {@code minecraft:block.note_block.harp}. */
	public String tuneInstrument = "minecraft:block.note_block.harp";
	/** Milliseconds per melody step (tempo). */
	public int tuneTempo = 200;

	public MentionRule() {
	}

	public MentionRule(String word, String sound, float volume, float pitch) {
		this.word = word;
		this.sound = sound;
		this.volume = volume;
		this.pitch = pitch;
	}

	/** @return true if this rule has a melody with at least one audible note. */
	public boolean hasTune() {
		if (tune == null) return false;
		for (int n : tune) {
			if (n >= 0 && n <= 24) return true;
		}
		return false;
	}

	/** Number of audible (non-rest) steps in the melody; 0 if none. */
	public int tuneNoteCount() {
		if (tune == null) return 0;
		int count = 0;
		for (int n : tune) {
			if (n >= 0 && n <= 24) count++;
		}
		return count;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof MentionRule that)) return false;
		return Objects.equals(word, that.word)
				&& Objects.equals(sound, that.sound)
				&& Float.compare(volume, that.volume) == 0
				&& Float.compare(pitch, that.pitch) == 0
				&& enabled == that.enabled
				&& caseSensitive == that.caseSensitive
				&& Arrays.equals(tune, that.tune)
				&& Objects.equals(tuneInstrument, that.tuneInstrument)
				&& tuneTempo == that.tuneTempo;
	}

	@Override
	public int hashCode() {
		return Objects.hash(word, sound, volume, pitch, enabled, caseSensitive,
				Arrays.hashCode(tune), tuneInstrument, tuneTempo);
	}
}
