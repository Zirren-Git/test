package io.github.zirren.chatterbox.config;

import java.util.Objects;

/**
 * A single mention rule: when {@code word} appears in an incoming chat message,
 * the configured sound is played.
 *
 * <p>The special token {@code {you}} matches the local player's username.</p>
 */
public class MentionRule {
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

	public MentionRule() {
	}

	public MentionRule(String word, String sound, float volume, float pitch) {
		this.word = word;
		this.sound = sound;
		this.volume = volume;
		this.pitch = pitch;
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
				&& caseSensitive == that.caseSensitive;
	}

	@Override
	public int hashCode() {
		return Objects.hash(word, sound, volume, pitch, enabled, caseSensitive);
	}
}
