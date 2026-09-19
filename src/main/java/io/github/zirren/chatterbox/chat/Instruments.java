package io.github.zirren.chatterbox.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * Note-block instruments (used by the Tune Maker and the friendly sound
 * names shown on buttons), plus note-name helpers for the vanilla note-block
 * pitch range F♯3 – F♯5 (notes 0 – 24).
 */
public final class Instruments {

	/** Instrument ids, melodic first, percussion last. */
	public static final List<String> ORDERED = List.of(
			"minecraft:block.note_block.harp",
			"minecraft:block.note_block.pling",
			"minecraft:block.note_block.bell",
			"minecraft:block.note_block.chime",
			"minecraft:block.note_block.flute",
			"minecraft:block.note_block.guitar",
			"minecraft:block.note_block.xylophone",
			"minecraft:block.note_block.iron_xylophone",
			"minecraft:block.note_block.cow_bell",
			"minecraft:block.note_block.bass",
			"minecraft:block.note_block.banjo",
			"minecraft:block.note_block.bit",
			"minecraft:block.note_block.didgeridoo",
			"minecraft:block.note_block.basedrum",
			"minecraft:block.note_block.snare",
			"minecraft:block.note_block.hat");

	private static final String[] SEMITONES = {"F#", "G", "G#", "A", "A#", "B", "C", "C#", "D", "D#", "E", "F"};

	private Instruments() {
	}

	/** All instruments that exist in the sound registry (never empty). */
	public static List<String> available() {
		List<String> out = new ArrayList<>();
		for (String id : ORDERED) {
			if (Sounds.exists(id)) {
				out.add(id);
			}
		}
		if (out.isEmpty()) {
			out.add("minecraft:block.note_block.harp");
		}
		return out;
	}

	/** "minecraft:block.note_block.iron_xylophone" → "Iron Xylophone". */
	public static String friendly(String soundId) {
		if (soundId == null || soundId.isEmpty()) {
			return "—";
		}
		String path = soundId.contains(":") ? soundId.substring(soundId.indexOf(':') + 1) : soundId;
		String suffix = path.startsWith("block.note_block.") ? path.substring("block.note_block.".length()) : path;
		suffix = suffix.replace('_', ' ').trim();
		if (suffix.isEmpty()) {
			return soundId;
		}
		return Character.toUpperCase(suffix.charAt(0)) + suffix.substring(1);
	}

	/** Note 0–24 → "F#3" … "F#5" (vanilla note-block range). */
	public static String noteName(int note) {
		if (note < 0 || note > 24) {
			return "—";
		}
		int octave = 3 + (note + 6) / 12;
		return SEMITONES[note % 12] + octave;
	}

	/** Vanilla note-block pitch formula: note 12 → 1.0. */
	public static float notePitch(int note) {
		return (float) Math.pow(2.0, (note - 12) / 12.0);
	}

	/** Inverse of {@link #notePitch}; used to display the pitch slider as a note. */
	public static int noteFromPitch(float pitch) {
		return Math.round(12.0f * (float) (Math.log(pitch) / Math.log(2.0)) + 12.0f);
	}
}
